package dev.boredvico.pik.mixin;

import java.net.SocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.boredvico.pik.OtpManager;
import dev.boredvico.pik.util.RedactedWhitelistComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

@Mixin(PlayerList.class)
abstract class PlayerManagerMixin {
	@Inject(
		method = "canPlayerLogin",
		at = @At(
			value = "CONSTANT",
			args = "stringValue=multiplayer.disconnect.not_whitelisted"
		),
		cancellable = true
	)
	private void customNotWhitelistedMessage(SocketAddress addr, NameAndId nameAndId, CallbackInfoReturnable<Component> cir) {
		String otp = OtpManager.INSTANCE.getOtp(nameAndId.id());

		Component message = Component.empty()
			.append(Component.literal("International Pichulas ")
					.withStyle(ChatFormatting.LIGHT_PURPLE))
			.append(Component.literal("ONLY")
					.withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE, ChatFormatting.BOLD))
			.append(Component.literal(".\n\nType the following command in the server's Discord to link your account and gain access:\n\n"))
			.append(Component.literal("/link %s\n\n".formatted(otp))
					.withStyle(ChatFormatting.GOLD))
			.append(Component.literal("This code will be valid for 5 minutes only.\n\n")
					.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal("you can type the command in any channel, make sure it starts with slash though!\n")
					.append("that way only the bot will see it.")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

		cir.setReturnValue(new RedactedWhitelistComponent(message));
	}
}

