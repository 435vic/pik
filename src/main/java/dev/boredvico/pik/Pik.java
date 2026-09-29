package dev.boredvico.pik;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boredvico.pik.api.ApiServer;
import dev.boredvico.pik.api.PikApi;
import dev.boredvico.pik.webhook.WebhookManager;

public class Pik implements DedicatedServerModInitializer {
	public static final String MOD_ID = "pik";
	public static final PikConfig CONFIG = PikConfig.createToml(Paths.get("config"), "", "pik", PikConfig.class);

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	WebhookManager webhooks;
	ApiServer apiServer;

	@Override
	public void onInitializeServer() {
		webhooks = new WebhookManager(CONFIG.webhookSettings);
		webhooks.registerEvents();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			apiServer = new ApiServer(CONFIG.apiSettings.socketPath.value(), server, "/api/v1");
			PikApi.register(apiServer.getRouter());
			try {
				apiServer.start();
			} catch (Exception e) {
				Pik.LOGGER.error("Error while starting API server.", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			apiServer.stop();
			OtpManager.INSTANCE.shutdown();
		});
	}
}
