package dev.boredvico.pik.api;

import java.util.Map;

import dev.boredvico.pik.api.ApiServer.Context;
import dev.boredvico.pik.api.ApiServer.Router;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

public class PikApi {
    static public void register(Router router) {
	router
	    .get("/health", ctx -> {
		ctx.json(Map.of("status", "ok"));
	    })
	    .post("/chat", PikApi::postChat);
    }

    public static void postChat(Context ctx) {
	DiscordMessage message = ctx.bodyAsClass(DiscordMessage.class);
	if (message == null || !message.isValid()) {
	    ctx.status(403).send(); 
	    return;
	}

	ctx.getMinecraft().execute(() -> {
	    ctx.getMinecraft().getPlayerList().broadcastSystemMessage(message.asMinecraftChat() , false); 
	});
	ctx.status(204).send();
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

