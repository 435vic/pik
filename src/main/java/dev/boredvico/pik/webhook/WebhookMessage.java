package dev.boredvico.pik.webhook;

public class WebhookMessage {
	String username;
	String content;
	String avatar_url;

	public WebhookMessage(String username, String content, String avatar_url) {
		this.username = username;
		this.content = content;
		this.avatar_url = avatar_url;
	}

	public static WebhookMessage fromChatMsg(String author, String content) {
		return new WebhookMessage(author, content, String.format(WebhookManager.PLAYER_HEAD_API, author));
	}

	public static WebhookMessage fromGameMsg(String content) {
		return new WebhookMessage("Server", "**" + content + "**", WebhookManager.SERVER_PFP);
	}
}
