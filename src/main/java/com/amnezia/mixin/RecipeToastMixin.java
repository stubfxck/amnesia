package com.amnezia.mixin;

import com.amnezia.AmneziaMod;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ✅ Подавляет отправку пакета с уведомлением о рецептах
 */
@Mixin(ServerCommonNetworkHandler.class)
public class RecipeToastMixin {
    
    private static final ThreadLocal<Boolean> SUPPRESS_TOAST = ThreadLocal.withInitial(() -> false);
    
    public static void setSuppressToast(boolean suppress) {
        SUPPRESS_TOAST.set(suppress);
    }
    
    public static boolean shouldSuppressToast() {
        return SUPPRESS_TOAST.get();
    }
    
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayNetworkHandler) {
            String packetName = packet.getClass().getSimpleName();
            boolean suppress = shouldSuppressToast();
            AmneziaMod.debug("Packet: " + packetName + ", suppress: " + suppress);
            if (suppress) {
                if (packetName.equals("UnlockRecipesS2CPacket")) {
                    AmneziaMod.debug("Suppressing UnlockRecipesS2CPacket");
                    ci.cancel();
                }
            }
        }
    }
}