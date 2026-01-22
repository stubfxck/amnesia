package com.amnezia.mixin.client;  // ← исправь пакет!

import net.minecraft.client.MinecraftClient;  // ← MinecraftClient!
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)  // ← MinecraftClient!
public class ExampleClientMixin {
    @Inject(at = @At("HEAD"), method = "run()V")
    private void run(CallbackInfo info) {
        System.out.println("Amnezia client running!");
    }
}
