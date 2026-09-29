package dev.boredvico.pik;

import folk.sisby.kaleido.api.ReflectiveConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Matches;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.SerializedName;
import folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue;

public class PikConfig extends ReflectiveConfig {

	@Comment("Webhook settings")
	@SerializedName("webhook")
	public final WebhookSettings webhookSettings = new WebhookSettings();

	@Comment("API Settings")
	public final ApiSettings apiSettings = new ApiSettings();

	public static class WebhookSettings extends Section {
		@SerializedName("enabled")
		public final TrackedValue<Boolean> webhooksEnabled = this.value(false);

		@Comment("Send player join/leave messages.")
		public final TrackedValue<Boolean> sendJoinLeave = this.value(true);

		@Comment("Send player death messages.")
		public final TrackedValue<Boolean> sendDeaths = this.value(true);

		@Comment("Send advancement announcements.")
		public final TrackedValue<Boolean> sendAdvancements = this.value(true);

		@Comment("The webhooks the server will use for chat updates.")
		@Matches("https:\\/\\/discord\\.com\\/api\\/webhooks\\/\\d+\\/[\\w-]+")
		public final TrackedValue<String> webhook = this.value("https://discord.com/api/webhooks/0/your-webhook-here");
	}

	public static class ApiSettings extends Section {
		@SerializedName("enabled")
		@Comment("Enable the Javalin web server for the API.")
		public final TrackedValue<Boolean> apiEnabled = this.value(true);

		@SerializedName("socket_path")
		@Comment("The Unix domain socket path used for the REST API.")
		public final TrackedValue<String> socketPath = this.value("/dev/shm/mijnpik.sock");
	}
}
