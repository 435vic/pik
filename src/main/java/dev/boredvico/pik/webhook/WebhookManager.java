package dev.boredvico.pik.webhook;

import com.google.gson.Gson;

import dev.boredvico.pik.Pik;
import dev.boredvico.pik.PikConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public class WebhookManager {
	static final String PLAYER_HEAD_API = "https://www.mc-heads.net/head/%1$s/500.png";
	static final String SERVER_GAME_AVATAR_URL =
		"https://files.boredvico.dev/mc/public/Repeating_Command_Block_JE2_BE1.png";
	static final String SERVER_ANNOUNCEMENT_AVATAR_URL =
		"https://files.boredvico.dev/mc/public/dios_pichula.jpg";

	static final Gson gson = new Gson();

	PikConfig.WebhookSettings settings;
	private final HttpClient client = HttpClient.newHttpClient();

	public WebhookManager(PikConfig.WebhookSettings settings) {
		this.settings = settings;
	}

	public void executeWebhook(WebhookMessage message) {
		if (!settings.webhooksEnabled.value()) return;
		String payload = gson.toJson(message);
		HttpRequest request;
		String webhookUrl = this.settings.webhook.value();
		try {
			request = HttpRequest.newBuilder()
				.uri(new URI(webhookUrl))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		} catch (URISyntaxException e) {
			Pik.LOGGER.error("Invalid request URI: {}", webhookUrl);
			return;
		}

		client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenAcceptAsync(res -> {
			if (res.statusCode() >= 400) {
				Pik.LOGGER.warn("Webhook POST error {}", res.statusCode());
			}
		});
	}

	public void registerEvents() {
		ServerMessageEvents.CHAT_MESSAGE.register(((message, sender, params) -> {
			// message sent from console
			if (sender.getDisplayName() == null) return;
			String author = sender.getDisplayName().getString();
			String content = message.decoratedContent().tryCollapseToString();
			if (content == null) {
				content = message.decoratedContent().getString();
			}
			WebhookMessage msg = WebhookMessage.fromChatMsg(author, content);
			executeWebhook(msg);
		}));

		ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) ->
			WebhookMessage.fromGameMsg(message, overlay, settings).ifPresent(this::executeWebhook));

		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) ->
			WebhookMessage.fromCommandMsg(message, source, params).ifPresent(this::executeWebhook));
	}
}
