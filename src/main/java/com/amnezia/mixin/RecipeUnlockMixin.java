package com.amnezia.mixin;

import com.amnezia.AmneziaMod;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * ✅ ИСПРАВЛЕНО: Убрали публичный статический метод, используем только проверку стека
 */
@Mixin(ServerPlayerEntity.class)
public class RecipeUnlockMixin {

    @Inject(method = "unlockRecipes", at = @At("HEAD"), cancellable = true)
    private void onUnlockRecipes(Collection<RecipeEntry<?>> recipes, CallbackInfoReturnable<Integer> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        // ✅ Проверка: вызов идёт от AmneziaMod?
        if (isCalledFromAmneziaMod()) {
            if (AmneziaMod.CONFIG != null && AmneziaMod.CONFIG.scrollSettings.debugMode) {
                AmneziaMod.debug("[UNLOCK MIXIN] ✓ ALLOWED: " + recipes.size() + " recipes");
            }
            return; // Разрешаем
        }
        
        // ❌ БЛОКИРУЕМ внешние источники
        if (AmneziaMod.CONFIG != null && AmneziaMod.CONFIG.scrollSettings.debugMode) {
            AmneziaMod.debug("[UNLOCK MIXIN] ✗ BLOCKED: " + recipes.size() + " recipes");
        }
        cir.setReturnValue(0);
        cir.cancel();
    }

    /**
     * ✅ ОПТИМИЗИРОВАННАЯ проверка стека (макс 20 фреймов)
     */
    private boolean isCalledFromAmneziaMod() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        // Ограничиваем проверку первыми 20 фреймами
        int limit = Math.min(stackTrace.length, 20);
        
        for (int i = 0; i < limit; i++) {
            String className = stackTrace[i].getClassName();
            String methodName = stackTrace[i].getMethodName();
            
            // ✅ Проверяем вызовы из AmneziaMod
            if (className.equals("com.amnezia.AmneziaMod")) {
                // Разрешаем только из определённых методов
                if (methodName.equals("unlockRecipe") ||
                    methodName.equals("initializePlayerRecipes") ||
                    methodName.equals("learnRecipe") ||
                    methodName.equals("resetPlayerRecipesOnDeath")) {
                    return true;
                }
            }
            
            // ✅ Также разрешаем из PlayerDataManager
            if (className.equals("com.amnezia.PlayerDataManager")) {
                return true;
            }
        }
        
        return false;
    }
}