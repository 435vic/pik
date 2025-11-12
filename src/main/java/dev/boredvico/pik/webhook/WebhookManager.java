package dev.boredvico.pik.webhook;

import com.google.gson.Gson;

import dev.boredvico.pik.Pik;
import dev.boredvico.pik.PikConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class WebhookManager {
	static final String PLAYER_HEAD_API = "https://www.mc-heads.net/head/%1$s/500.png";
	static final String SERVER_PFP = "https://gamepedia.cursecdn.com/minecraft_gamepedia/7/76/Impulse_Command_Block.gif";

	static final Gson gson = new Gson();

	PikConfig.WebhookSettings settings;

	public WebhookManager(PikConfig.WebhookSettings settings) {
		this.settings = settings;
	}

	public void executeWebhook(WebhookMessage message) {
		if (!settings.webhooksEnabled.value()) return;
		String payload = gson.toJson(message);
		HttpRequest request;
		String webhook_url = this.settings.webhook.value();
		try {
			Pik.LOGGER.info("Avatar URL is {}", message.avatar_url);
			request = HttpRequest.newBuilder()
				.uri(new URI(webhook_url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		} catch (URISyntaxException e) {
			Pik.LOGGER.error("Invalid request URI: {}", webhook_url);
			return;
		}

		HttpClient client = HttpClient.newHttpClient();
		CompletableFuture<HttpResponse<Void>> response =
			client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
		response.thenAcceptAsync(res -> {
			if (res.statusCode() >= 400) {
				Pik.LOGGER.warn("Webhook POST error {}: {}", res.statusCode(), res.body());
			}
		});
	}
}

