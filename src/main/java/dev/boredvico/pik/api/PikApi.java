package dev.boredvico.pik.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.yggdrasil.ProfileResult;

import dev.boredvico.pik.OtpManager;
import dev.boredvico.pik.Pik;
import dev.boredvico.pik.api.ApiServer.Context;
import dev.boredvico.pik.api.ApiServer.Router;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

public class PikApi {
    static public void register(Router router) {
	router
	    .get("/health", ctx -> {
		ctx.json(Map.of("status", "ok"));
	    })
	    .post("/chat", PikApi::postChat)
	    .post("/whitelist/:uuid", PikApi::addWhitelist)
	    .delete("/whitelist/:uuid", PikApi::removeWhitelist)
	    .get("/otp/:code", PikApi::checkOtp);
    }

    public static void postChat(Context ctx) {
	DiscordMessage message = ctx.bodyAsClass(DiscordMessage.class);
	if (message == null || !message.isValid()) {
	    ctx.status(400).send(); 
	    return;
	}

	ctx.getMinecraft().execute(() -> {
	    ctx.getMinecraft().getPlayerList().broadcastSystemMessage(message.asMinecraftChat() , false); 
	});
	ctx.status(204).send();
    }

    public static void addWhitelist(Context ctx) {
	Optional<NameAndId> player = getPlayer(ctx);

	if (player.isEmpty()) {
	    return;
	}

	UserWhiteListEntry entry = new UserWhiteListEntry(player.get());		
	
	ctx.getMinecraft().execute(() -> {
	    ctx.getMinecraft().getPlayerList().getWhiteList().add(entry);
	    ctx.status(204).send();
	});
    }

    public static void removeWhitelist(Context ctx) {
	Optional<NameAndId> player = getPlayer(ctx);

	if (player.isEmpty()) {
	    return;
	}

	UserWhiteListEntry entry = new UserWhiteListEntry(player.get());

	ctx.getMinecraft().execute(() -> {
	    ctx.getMinecraft().getPlayerList().getWhiteList().remove(entry);
	    ctx.status(204).send();
	});
    }

    private static Optional<NameAndId> getPlayer(Context ctx) {
	UUID uuid;
	try {
	    uuid = UUID.fromString(ctx.pathParam("uuid"));
	} catch (IllegalArgumentException e) {
	    ctx.status(400).json(Map.of("error", "invalid uuid"));
	    return Optional.empty();
	}

	return ctx.getMinecraft().services().nameToIdCache().get(uuid);
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
	    ProfileResult profile = ctx.getMinecraft().services().sessionService().fetchProfile(mcPlayer.get(), true);
	    if (profile == null) {
		// otp map contains an invalid UUID
		// no idea how it could happen
		Pik.LOGGER.error("UUID associated with OTP code does not exist!!!");
		ctx.status(500).send();
		return;
	    }

	    ctx.status(200).json(Map.of(
		"otp", otp,
		"uuid", profile.profile().id(),
		"name", profile.profile().name()
	    ));
	} catch (Exception e) {
	    Pik.LOGGER.error("Error fetching profile {} for link request, {}", mcPlayer.get(), e);
	    ctx.status(500).send();
	}
    }

    class DiscordMessage {
	private String author;
	private String content;

	private static final Style AUTHOR_STYLE = Style.EMPTY
	    .withColor(ChatFormatting.DARK_PURPLE)
	    .withHoverEvent(new HoverEvent.ShowText(Component.literal("This message is from Discord.")));

	public void setAuthor(String author) {
	    this.author = author;
	}

	public void setContent(String content) {
	    this.content = content;
	}

	public String getAuthor() {
	    return author;
	}

	public String getContent() {
	    return content;
	}

	public boolean isValid() {
	    return this.author != null && this.content != null;
	}

	public Component asMinecraftChat() {
	    return Component.literal("<%s> ".formatted(author))
		.setStyle(AUTHOR_STYLE)
		.append(
			Component.literal(content)
		       );
	}
    }
}

