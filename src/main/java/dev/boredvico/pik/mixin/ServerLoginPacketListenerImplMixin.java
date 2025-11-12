package dev.boredvico.pik.mixin;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.boredvico.pik.util.RedactedWhitelistComponent;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {
    @Shadow
    static Logger LOGGER;

    @Shadow
    abstract String getUserName();

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
	// Minecraft logs disconnection messages, even if they span multiple lines.
	// Additionally, the not whitelisted message now contains sensitive info,
	// which is the otp token. This mixin ensures that this message is not output
	// to the console directly. The player manager mixin sends this wrapper class
	// as the disconnect reason.
	if (details.reason() instanceof RedactedWhitelistComponent) {
	    LOGGER.info("{} lost connection: not whitelisted", this.getUserName());
	    ci.cancel();
	}
    }
}
