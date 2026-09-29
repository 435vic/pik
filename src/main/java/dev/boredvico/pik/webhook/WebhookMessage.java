package dev.boredvico.pik.webhook;

import dev.boredvico.pik.PikConfig;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;

public class WebhookMessage {
	private static final String SERVER_AUTHOR = "Server";
	private static final String GOD_AUTHOR = "God";

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

	public static Optional<WebhookMessage> fromGameMsg(
		Component message, boolean overlay, PikConfig.WebhookSettings settings) {
		if (overlay) return Optional.empty();

		return parseGameEvent(message)
			.filter(event -> shouldSend(event, settings))
			.map(WebhookMessage::fromGameEvent);
	}

	public static Optional<WebhookMessage> fromCommandMsg(
		PlayerChatMessage message, CommandSourceStack source, ChatType.Bound params) {
		if (source.isPlayer()) return Optional.empty();

		Component decorated = params.decorate(message.decoratedContent());
		return parseSayMessage(decorated).or(() -> parseSayMessage(message.decoratedContent()));
	}

	private static WebhookMessage fromGameEvent(GameEvent event) {
		return switch (event) {
			case JoinEvent joinEvent -> new WebhookMessage(
				SERVER_AUTHOR,
				"**" + joinEvent.playerName() + " joined the game**",
				WebhookManager.SERVER_GAME_AVATAR_URL);
			case LeaveEvent leaveEvent -> new WebhookMessage(
				SERVER_AUTHOR,
				"**" + leaveEvent.playerName() + " left the game**",
				WebhookManager.SERVER_GAME_AVATAR_URL);
			case AdvancementEvent advancementEvent -> new WebhookMessage(
				SERVER_AUTHOR,
				"**" + advancementEvent.playerName() + " made "
					+ advancementEvent.advancementType() + " advancement:** "
					+ advancementEvent.advancementTitle(),
				WebhookManager.SERVER_GAME_AVATAR_URL);
			case DeathEvent deathEvent -> new WebhookMessage(
				SERVER_AUTHOR,
				"**" + deathEvent.rawMessage() + "**",
				WebhookManager.SERVER_GAME_AVATAR_URL);
		};
	}

	private static boolean shouldSend(GameEvent event, PikConfig.WebhookSettings settings) {
		return switch (event) {
			case JoinEvent joinEvent -> settings.sendJoinLeave.value();
			case LeaveEvent leaveEvent -> settings.sendJoinLeave.value();
			case AdvancementEvent advancementEvent -> settings.sendAdvancements.value();
			case DeathEvent deathEvent -> settings.sendDeaths.value();
		};
	}

	private static Optional<WebhookMessage> parseSayMessage(Component message) {
		if (!(message.getContents() instanceof TranslatableContents translatableContents)) {
			return Optional.empty();
		}

		if (!"chat.type.announcement".equals(translatableContents.getKey())) {
			return Optional.empty();
		}

		return Optional.of(new WebhookMessage(
			GOD_AUTHOR,
			getArgText(translatableContents.getArgs(), 1),
			WebhookManager.SERVER_ANNOUNCEMENT_AVATAR_URL));
	}

	private static Optional<GameEvent> parseGameEvent(Component message) {
		if (!(message.getContents() instanceof TranslatableContents translatableContents)) {
			return Optional.empty();
		}

		String key = translatableContents.getKey();
		Object[] args = translatableContents.getArgs();

		return switch (key) {
			case "multiplayer.player.joined", "multiplayer.player.joined.renamed" ->
				Optional.of(new JoinEvent(getArgText(args, 0)));
			case "multiplayer.player.left" -> Optional.of(new LeaveEvent(getArgText(args, 0)));
			default -> {
				if (key.startsWith("chat.type.advancement.")) {
					String advancementType = key.substring("chat.type.advancement.".length());
					yield Optional.of(new AdvancementEvent(
						getArgText(args, 0), getArgText(args, 1), advancementType));
				}

				if (key.startsWith("death.")) {
					yield Optional.of(new DeathEvent(getArgText(args, 0), message.getString()));
				}

				yield Optional.empty();
			}
		};
	}

	private static String getArgText(Object[] args, int index) {
		if (index >= args.length || args[index] == null) {
			return "Unknown";
		}

		Object arg = args[index];
		if (arg instanceof Component component) {
			return component.getString();
		}

		return arg.toString();
	}

	private sealed interface GameEvent
		permits JoinEvent, LeaveEvent, AdvancementEvent, DeathEvent {}

	private record JoinEvent(String playerName) implements GameEvent {}
	private record LeaveEvent(String playerName) implements GameEvent {}
	private record AdvancementEvent(
		String playerName, String advancementTitle, String advancementType) implements GameEvent {}
	private record DeathEvent(String playerName, String rawMessage) implements GameEvent {}
}
