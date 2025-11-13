package dev.boredvico.pik;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import java.io.IOException;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boredvico.pik.api.ApiServer;
import dev.boredvico.pik.api.PikApi;
import dev.boredvico.pik.webhook.WebhookManager;
import dev.boredvico.pik.webhook.WebhookMessage;

public class Pik implements DedicatedServerModInitializer {
	public static final String MOD_ID = "pik";
	public static final PikConfig CONFIG = PikConfig.createToml(Paths.get("config"), "", "pik", PikConfig.class);

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	WebhookManager webhooks;
	ApiServer apiServer;

	@Override
	public void onInitializeServer() {
		ServerMessageEvents.CHAT_MESSAGE.register(((message, sender, params) -> {
			// message sent from console
			if (sender.getDisplayName() == null) return;
			String author = sender.getDisplayName().getString();
			String content = message.decoratedContent().tryCollapseToString();
			WebhookMessage msg = WebhookMessage.fromChatMsg(author, content);
			webhooks.executeWebhook(msg);
		}));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			webhooks = new WebhookManager(CONFIG.webhookSettings);
			apiServer = new ApiServer("/dev/shm/mijnpik.sock", server, "/api/v1");
			PikApi.register(apiServer.getRouter());
			try {
				apiServer.start();
			} catch (Exception e) {
				Pik.LOGGER.error("Error while starting API server.");
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			apiServer.stop();
			OtpManager.INSTANCE.shutdown();
		});
	}
}
