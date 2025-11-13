package dev.boredvico.pik.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.boredvico.pik.Pik;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Lightweight abstraction of Netty, creates an Express-like interface for implementing
 * an API. Binds to a Unix socket in mode 660.
 * Designed to be put behind a reverse proxy for security.
 */
public class ApiServer {

    private EventLoopGroup bossGroup;
    private Channel channel;
    private final String socketPath;
    private final Router router;
    private final Gson gson;
    private final MinecraftServer mcInstance;

    public ApiServer(
        String socketPath,
        MinecraftServer mcInstance,
        String prefix,
        Gson gson
    ) {
        this.gson = gson;
        this.socketPath = socketPath;
        this.router = new Router(prefix);
        this.mcInstance = mcInstance;
    }

    public ApiServer(
        String socketPath,
        MinecraftServer mcInstance,
        String prefix
    ) {
        this(socketPath, mcInstance, prefix, new Gson());
    }

    public ApiServer(String socketPath, MinecraftServer mcInstance) {
        this(socketPath, mcInstance, "", new Gson());
    }

    public Router getRouter() {
        return router;
    }

    public void start() throws Exception {
        Pik.LOGGER.info("Starting API server...");
        if (!Epoll.isAvailable()) {
            throw new UnsupportedOperationException(
                "Unix Sockets not available."
            );
        }

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("Pik-API-Boss-%d")
            .setDaemon(true)
            .build();
        this.bossGroup = new EpollEventLoopGroup(1, threadFactory);

        // Delete existing socket if present
        Files.deleteIfExists(Paths.get(socketPath));

        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(bossGroup)
            .channel(EpollServerDomainSocketChannel.class)
            .childHandler(
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch
                            .pipeline()
                            .addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(1048576)) // 1MB max
                            .addLast(
                                new HttpApiHandler(router, gson, mcInstance)
                            );
                    }
                }
            );

        channel = bootstrap
            .bind(new DomainSocketAddress(socketPath))
            .sync()
            .channel();

        // Set socket permissions (owner+group read/write only)
        Files.setPosixFilePermissions(
            Paths.get(socketPath),
            PosixFilePermissions.fromString("rw-rw----")
        );

        Pik.LOGGER.info("API server listening on " + socketPath);
    }

    public void stop() {
        Pik.LOGGER.info("Shutting down API server...");
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        try {
            Files.deleteIfExists(Paths.get(socketPath));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Pik.LOGGER.info("API server stopped. bye bye");
    }

    /**
     * Router that maps HTTP methods and paths to handlers.
     * Supports path parameters like /players/:name
     */
    public static class Router {

        private final List<Route> routes = new ArrayList<>();
        private final String prefix;

        public Router() {
            this.prefix = "";
        }

        public Router(String prefix) {
            this.prefix = prefix;
        }

        public Router get(String path, RouteHandler handler) {
            routes.add(new Route(prefix, HttpMethod.GET, path, handler));
            return this;
        }

        public Router post(String path, RouteHandler handler) {
            routes.add(new Route(prefix, HttpMethod.POST, path, handler));
            return this;
        }

        public Router put(String path, RouteHandler handler) {
            routes.add(new Route(prefix, HttpMethod.PUT, path, handler));
            return this;
        }

        public Router delete(String path, RouteHandler handler) {
            routes.add(new Route(prefix, HttpMethod.DELETE, path, handler));
            return this;
        }

        public Router patch(String path, RouteHandler handler) {
            routes.add(new Route(prefix, HttpMethod.PATCH, path, handler));
            return this;
        }

        RouteMatch match(HttpMethod method, String path) {
            for (Route route : routes) {
                RouteMatch match = route.matches(method, path);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }

        private static class Route {

            private final HttpMethod method;
            private final Pattern pattern;
            private final List<String> paramNames;
            private final RouteHandler handler;

            Route(
                String prefix,
                HttpMethod method,
                String path,
                RouteHandler handler
            ) {
                this.method = method;
                this.handler = handler;
                this.paramNames = new ArrayList<>();

                // Convert /path/:param/:other to regex
                String regex = prefix + path;
                Pattern paramPattern = Pattern.compile(
                    ":([a-zA-Z][a-zA-Z0-9_]*)"
                );
                Matcher matcher = paramPattern.matcher(path);

                while (matcher.find()) {
                    paramNames.add(matcher.group(1));
                    regex = regex.replace(":" + matcher.group(1), "([^/]+)");
                }

                this.pattern = Pattern.compile("^" + regex + "$");
            }

            RouteMatch matches(HttpMethod method, String path) {
                if (this.method != method) {
                    return null;
                }

                Matcher matcher = pattern.matcher(path);
                if (!matcher.matches()) {
                    return null;
                }

                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), matcher.group(i + 1));
                }

                return new RouteMatch(handler, params);
            }
        }
    }

    public static class RouteMatch {

        final RouteHandler handler;
        final Map<String, String> pathParams;

        RouteMatch(RouteHandler handler, Map<String, String> pathParams) {
            this.handler = handler;
            this.pathParams = pathParams;
        }
    }

    /**
     * Context object passed to route handlers with request data and response methods.
     */
    public static class Context {

        // internal context
        public final Gson gson;
        private final MinecraftServer mcInstance;
        private final ChannelHandlerContext netCtx;

        // request context
        private final FullHttpRequest request;
        private final Map<String, String> pathParams;
        private final Map<String, String> queryParams;

        // response context
        private Object responseBody;
        private HttpResponseStatus status = HttpResponseStatus.OK;
        private final Map<String, String> responseHeaders = new HashMap<>();

        Context(
            ChannelHandlerContext netCtx,
            FullHttpRequest request,
            Map<String, String> pathParams,
            Gson gson,
            MinecraftServer instance
        ) {
            this.gson = gson;
            this.netCtx = netCtx;
            this.request = request;
            this.mcInstance = instance;
            this.pathParams = pathParams;
            this.queryParams = parseQueryParams(request.uri());
            responseHeaders.put(
                HttpHeaderNames.CONTENT_TYPE.toString(),
                "application/json"
            );
        }

        public String pathParam(String name) {
            return pathParams.get(name);
        }

        public String queryParam(String name) {
            return queryParams.get(name);
        }

        public String queryParam(String name, String defaultValue) {
            return queryParams.getOrDefault(name, defaultValue);
        }

        public Map<String, String> queryParams() {
            return queryParams;
        }

        public String body() {
            return request.content().toString(CharsetUtil.UTF_8);
        }

        public <T> T bodyAsClass(Class<T> clazz) {
            return gson.fromJson(body(), clazz);
        }

        public JsonObject bodyAsJson() {
            return JsonParser.parseString(body()).getAsJsonObject();
        }

        public MinecraftServer getMinecraft() {
            return mcInstance;
        }

        public void json(Object obj) {
            this.responseBody = obj;
            this.send();
        }

        public void send() {
            Object body = this.responseBody;
            if (status == HttpResponseStatus.NO_CONTENT) {
                body = null;
            } else if (body == null && status.code() < 300) {
                body = Map.of("success", true);
            }
            HttpApiHandler.sendResponse(
                netCtx,
                status,
                responseBody,
                responseHeaders,
                gson,
                false
            );
        }

        public Context status(int code) {
            this.status = HttpResponseStatus.valueOf(code);
            return this;
        }

        public Context status(HttpResponseStatus status) {
            this.status = status;
            return this;
        }

        public Context header(String name, String value) {
            responseHeaders.put(name, value);
            return this;
        }

        Object getResponseBody() {
            return responseBody;
        }

        HttpResponseStatus getStatus() {
            return status;
        }

        Map<String, String> getResponseHeaders() {
            return responseHeaders;
        }

        private Map<String, String> parseQueryParams(String uri) {
            Map<String, String> params = new HashMap<>();
            int queryStart = uri.indexOf('?');
            if (queryStart == -1) {
                return params;
            }

            String query = uri.substring(queryStart + 1);
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                } else if (pair.length == 1) {
                    params.put(pair[0], "");
                }
            }
            return params;
        }
    }

    @FunctionalInterface
    public interface RouteHandler {
        void handle(Context ctx) throws Exception;
    }

    /**
     * Netty handler that processes HTTP requests and routes them.
     */
    private static class HttpApiHandler
        extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final Router router;
        private final Gson gson;
        private final MinecraftServer mcInstance;

        HttpApiHandler(Router router, Gson gson, MinecraftServer instance) {
            this.mcInstance = instance;
            this.router = router;
            this.gson = gson;
        }

        @Override
        protected void channelRead0(
            ChannelHandlerContext ctx,
            FullHttpRequest request
        ) {
            String rawUri = request.uri();
            String path;
            
            try {
                path = new java.net.URI(rawUri).getPath();
            } catch (java.net.URISyntaxException e) {
                HttpApiHandler.sendResponse(
                    ctx, 
                    HttpResponseStatus.BAD_REQUEST, 
                    Map.of(), 
                    Map.of(), 
                    gson, 
                    false
                );
                return;
            }

            RouteMatch match = router.match(request.method(), path);

            if (match == null) {
                HttpApiHandler.sendResponse(
                    ctx,
                    HttpResponseStatus.NOT_FOUND,
                    Map.of("error", "Not found", "path", path),
                    Map.of(),
                    gson,
                    false
                );
                return;
            }

            Context context = new Context(
                ctx,
                request,
                match.pathParams,
                gson,
                mcInstance
            );
            try {
                match.handler.handle(context);
            } catch (Exception e) {
                e.printStackTrace();
                HttpApiHandler.sendResponse(
                    ctx,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("error", "internal server error", "path", path),
                    Map.of(),
                    gson,
                    false
                );
            }
        }

        @Override
        public void exceptionCaught(
            ChannelHandlerContext ctx,
            Throwable cause
        ) {
            cause.printStackTrace();
            ctx.close();
        }

        public static void sendResponse(
            ChannelHandlerContext ctx,
            HttpResponseStatus status,
            Object body,
            Map<String, String> headers,
            Gson gson,
            boolean keepAlive
        ) {
            ByteBuf buffer = Unpooled.EMPTY_BUFFER;
            int bufferLen = 0;

            if (body != null) {
                String json = body instanceof String
                    ? (String) body
                    : gson.toJson(body);
                byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
                bufferLen = bytes.length;
                buffer = Unpooled.wrappedBuffer(bytes);
            }

            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                buffer
            );

            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bufferLen);
            response
                .headers()
                .set(
                    HttpHeaderNames.CONNECTION,
                    keepAlive
                        ? HttpHeaderValues.KEEP_ALIVE
                        : HttpHeaderValues.CLOSE
                );

            // Set default content-type if not provided
            if (
                bufferLen != 0 &&
                !headers.containsKey(HttpHeaderNames.CONTENT_TYPE.toString())
            ) {
                response
                    .headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            }

            // Add custom headers
            headers.forEach((name, value) ->
                response.headers().set(name, value)
            );

            ctx
                .writeAndFlush(response)
                .addListener(ChannelFutureListener.CLOSE);
        }
    }

    // // Example usage in a Fabric mod
    // public static void main(String[] args) throws Exception {
    //     UnixSocketApiServer server = new UnixSocketApiServer("/tmp/minecraft-api.sock");
    //
    //     // Define routes
    //     server.getRouter()
    //         .get("/health", ctx -> {
    //             ctx.json(Map.of("status", "ok"));
    //         })
    //
    //         .get("/stats", ctx -> {
    //             ctx.json(Map.of(
    //                 "players", 5,
    //                 "maxPlayers", 20,
    //                 "tps", 20.0,
    //                 "uptime", 3600
    //             ));
    //         })
    //
    //         .get("/players", ctx -> {
    //             ctx.json(List.of("Player1", "Player2", "Player3"));
    //         })
    //
    //         .get("/players/:name", ctx -> {
    //             String name = ctx.pathParam("name");
    //             ctx.json(Map.of(
    //                 "name", name,
    //                 "health", 20,
    //                 "level", 5
    //             ));
    //         })
    //
    //         .get("/whitelist", ctx -> {
    //             // Return whitelist
    //             ctx.json(List.of("Admin", "Player1", "Player2"));
    //         })
    //
    //         .post("/whitelist", ctx -> {
    //             JsonObject body = ctx.bodyAsJson();
    //             String player = body.get("player").getAsString();
    //
    //             // Add to whitelist logic here
    //             System.out.println("Adding " + player + " to whitelist");
    //
    //             ctx.status(201).json(Map.of(
    //                 "success", true,
    //                 "player", player
    //             ));
    //         })
    //
    //         .delete("/whitelist/:player", ctx -> {
    //             String player = ctx.pathParam("player");
    //
    //             // Remove from whitelist logic here
    //             System.out.println("Removing " + player + " from whitelist");
    //
    //             ctx.json(Map.of(
    //                 "success", true,
    //                 "player", player
    //             ));
    //         })
    //
    //         .post("/command", ctx -> {
    //             JsonObject body = ctx.bodyAsJson();
    //             String command = body.get("command").getAsString();
    //
    //             // Execute command on server thread
    //             System.out.println("Executing command: " + command);
    //
    //             ctx.json(Map.of(
    //                 "success", true,
    //                 "command", command
    //             ));
    //         });
    //
    //     server.start();
    //
    //     // Keep running
    //     Thread.currentThread().join();
    // }
}
