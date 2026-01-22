package com.amnezia.mixin;

import com.amnezia.AmneziaMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ✅ ИСПРАВЛЕНО: Перехват смерти ПОСЛЕ проверки Totem of Undying
 */
@Mixin(ServerPlayerEntity.class)
public class DeathMixin {
    
    /**
     * ✅ Inject в TAIL = ПОСЛЕ выполнения метода onDeath
     * К этому моменту игрок УЖЕ мёртв или воскрешён тотемом
     */
    @Inject(method = "onDeath", at = @At("TAIL"))
    private void onPlayerDeathAfter(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        if (!player.getWorld().isClient) {
            // Проверка происходит внутри resetPlayerRecipesOnDeath
            // Если player.getHealth() > 0 (тотем сработал), reset не произойдёт
            AmneziaMod.debug("DEATH MIXIN: Player " + player.getName().getString() + " death event");
            AmneziaMod.resetPlayerRecipesOnDeath(player);
        }
    }
}