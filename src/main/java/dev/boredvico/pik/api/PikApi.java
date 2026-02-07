package dev.boredvico.pik.api;

import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.boredvico.pik.OtpManager;
import dev.boredvico.pik.Pik;
import dev.boredvico.pik.api.ApiServer.Context;
import dev.boredvico.pik.api.ApiServer.Router;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

public class PikApi {

    public static void register(Router router) {
        router
            .get("/health", ctx -> {
                ctx.json(Map.of("status", "ok"));
            })
            .get("/stats", PikApi::getStats)
            .post("/chat", PikApi::postChat)
            .post("/whitelist/:name", PikApi::addWhitelist)
            .delete("/whitelist/:name", PikApi::removeWhitelist)
            .get("/otp/:code", PikApi::checkOtp);
    }

    public static void getStats(Context ctx) {
        var server = ctx.getMinecraft();
        ctx
            .getMinecraft()
            .execute(() -> {
                ctx
                    .status(200)
                    .json(
                        Map.of(
                            "online",
                            true,
                            "playerCount",
                            server.getPlayerList().getPlayerCount()
                        )
                    );
            });
    }

    public static void postChat(Context ctx) {
        DiscordMessage message = ctx.bodyAsClass(DiscordMessage.class);
        if (message == null || !message.isValid()) {
            ctx.status(400).send();
            return;
        }

        ctx
            .getMinecraft()
            .execute(() -> {
                ctx
                    .getMinecraft()
                    .getPlayerList()
                    .broadcastSystemMessage(message.asMinecraftChat(), false);
            });
        ctx.status(204).send();
    }

    public static void addWhitelist(Context ctx) {
        Optional<NameAndId> player = getPlayer(ctx);

        if (player.isEmpty()) {
            ctx.status(404).send();
            return;
        }

        UserWhiteListEntry entry = new UserWhiteListEntry(player.get());

        ctx
            .getMinecraft()
            .execute(() -> {
                ctx.getMinecraft().getPlayerList().getWhiteList().add(entry);
                ctx.status(204).send();
            });
    }

    public static void removeWhitelist(Context ctx) {
        Optional<NameAndId> player = getPlayer(ctx);

        if (player.isEmpty()) {
            ctx.status(404).send();
            return;
        }

        UserWhiteListEntry entry = new UserWhiteListEntry(player.get());

        ctx
            .getMinecraft()
            .execute(() -> {
                ctx.getMinecraft().getPlayerList().getWhiteList().remove(entry);
                ctx.status(204).send();
            });
    }

    private static Optional<NameAndId> getPlayer(Context ctx) {
        String name = ctx.pathParam("name");

        return ctx.getMinecraft().services().nameToIdCache().get(name);
    }

    public static void checkOtp(Context ctx) {
        String otp = ctx.pathParam("code");

        if (otp == null) {
            ctx.status(400).send();
            return;
        }

        Optional<UUID> mcPlayer = OtpManager.INSTANCE.consume(otp);
        if (mcPlayer.isEmpty()) {
            ctx.status(404).send();
            return;
        }

        try {
            ProfileResult profile = ctx
                .getMinecraft()
                .services()
                .sessionService()
                .fetchProfile(mcPlayer.get(), true);
            if (profile == null) {
                // otp map contains an invalid UUID
                // no idea how it could happen
                Pik.LOGGER.error(
                    "UUID associated with OTP code does not exist!!!"
                );
                ctx.status(500).send();
                return;
            }

            ctx
                .status(200)
                .json(
                    Map.of(
                        "otp",
                        otp,
                        "uuid",
                        profile.profile().id(),
                        "name",
                        profile.profile().name()
                    )
                );
        } catch (Exception e) {
            Pik.LOGGER.error(
                "Error fetching profile {} for link request, {}",
                mcPlayer.get(),
                e
            );
            ctx.status(500).send();
        }
    }

    record DiscordMessage(String author, String content) {
        private static final Style AUTHOR_STYLE = Style.EMPTY.withColor(
            ChatFormatting.LIGHT_PURPLE
        ).withHoverEvent(
            new HoverEvent.ShowText(
                Component.literal("This message is from Discord.")
            )
        );

        public boolean isValid() {
            return this.author != null && this.content != null;
        }

        public Component asMinecraftChat() {
            return Component.empty()
                .append(
                    Component.literal("<%s> ".formatted(author)).setStyle(
                        AUTHOR_STYLE
                    )
                )
                .append(Component.literal(content));
        }
    }
}
