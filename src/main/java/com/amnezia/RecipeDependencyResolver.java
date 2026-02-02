package com.amnezia;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * ✅ Система разрешения зависимостей рецептов для Easy Mode
 * Рекурсивно находит все рецепты, необходимые для крафта предмета
 */
public class RecipeDependencyResolver {
    
    /**
     * ✅ ИСПРАВЛЕНО: Разрешает все зависимости рецепта (для Easy Mode)
     * Теперь учит ингредиенты (палки, доски), а не то, что можно скрафтить из результата
     * 
     * @param recipeId ID рецепта для разрешения
     * @param server Сервер
     * @return Список всех рецептов-зависимостей (ингредиентов)
     */
    public static List<String> resolveDependencies(String recipeId, MinecraftServer server) {
        Set<String> resolved = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        // ✅ Находим рецепт
        Optional<? extends RecipeEntry<?>> recipeOpt = findRecipe(recipeId, server);
        
        if (recipeOpt.isEmpty()) {
            AmneziaMod.debug("[EASY MODE] Recipe not found: " + recipeId);
            return new ArrayList<>();
        }
        
        // ✅ Рекурсивно собираем зависимости (ингредиенты)
        resolveDependenciesRecursive(recipeOpt.get(), server, resolved, visiting);
        
        List<String> result = new ArrayList<>(resolved);
        AmneziaMod.debug("[EASY MODE] Resolved " + result.size() + " ingredient recipes for " + recipeId + ": " + result);
        
        return result;
    }
    
    /**
     * ✅ ИСПРАВЛЕНО: Рекурсивно разрешает зависимости-ингредиенты
     */
    private static void resolveDependenciesRecursive(RecipeEntry<?> recipeEntry, MinecraftServer server, 
                                                      Set<String> resolved, Set<String> visiting) {
        String recipeId = recipeEntry.id().toString();
        
        // Защита от циклических зависимостей
        if (visiting.contains(recipeId)) {
            AmneziaMod.debug("[EASY MODE] Circular dependency detected: " + recipeId);
            return;
        }
        
        visiting.add(recipeId);
        
        Recipe<?> recipe = recipeEntry.value();
        
        // ✅ Получаем ингредиенты текущего рецепта
        List<Ingredient> ingredients = getIngredients(recipe);
        
        // ✅ Для каждого ингредиента ищем рецепты его создания
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                Item item = stack.getItem();
                Identifier itemId = Registries.ITEM.getId(item);
                
                // ✅ Ищем все рецепты, которые создают этот предмет
                List<RecipeEntry<?>> ingredientRecipes = findRecipesProducing(itemId.toString(), server);
                
                for (RecipeEntry<?> ingredientRecipe : ingredientRecipes) {
                    String ingredientRecipeId = ingredientRecipe.id().toString();
                    
                    // ✅ Добавляем рецепт ингредиента в resolved
                    if (!resolved.contains(ingredientRecipeId) && !visiting.contains(ingredientRecipeId)) {
                        resolved.add(ingredientRecipeId);
                        
                        // ✅ Рекурсивно обрабатываем зависимости этого ингредиента
                        resolveDependenciesRecursive(ingredientRecipe, server, resolved, visiting);
                    }
                }
            }
        }
        
        visiting.remove(recipeId);
    }
    
    /**
     * Находит рецепт по ID
     */
    private static Optional<? extends RecipeEntry<?>> findRecipe(String recipeId, MinecraftServer server) {
        Identifier id = Identifier.tryParse(recipeId);
        if (id == null) {
            id = Identifier.of("minecraft", recipeId);
        }
        
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            if (entry.id().toString().equals(id.toString()) || 
                entry.id().toString().equals(recipeId)) {
                return Optional.of(entry);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * ✅ ИСПРАВЛЕНО: Возвращает RecipeEntry вместо String
     * Находит все рецепты, производящие данный предмет
     */
    private static List<RecipeEntry<?>> findRecipesProducing(String itemId, MinecraftServer server) {
        List<RecipeEntry<?>> recipes = new ArrayList<>();
        
        Identifier targetId = Identifier.tryParse(itemId);
        if (targetId == null) {
            return recipes;
        }
        
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            Recipe<?> recipe = entry.value();
            
            // Проверяем только крафтинг-рецепты (не плавку и т.д.)
            if (!(recipe instanceof CraftingRecipe)) {
                continue;
            }
            
            ItemStack result = recipe.getResult(server.getRegistryManager());
            if (result.isEmpty()) {
                continue;
            }
            
            Identifier resultId = Registries.ITEM.getId(result.getItem());
            
            if (resultId.equals(targetId)) {
                recipes.add(entry);
            }
        }
        
        return recipes;
    }
    
    /**
     * Извлекает ингредиенты из рецепта
     */
    private static List<Ingredient> getIngredients(Recipe<?> recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            ingredients.addAll(shaped.getIngredients());
            
        } else if (recipe instanceof ShapelessRecipe) {
            ShapelessRecipe shapeless = (ShapelessRecipe) recipe;
            ingredients.addAll(shapeless.getIngredients());
            
        } else if (recipe instanceof CraftingRecipe) {
            // Общий случай для других крафтинг-рецептов
            ingredients.addAll(recipe.getIngredients());
        }
        
        return ingredients;
    }
    
    /**
     * Проверяет есть ли у предмета рецепт крафта
     */
    public static boolean hasRecipe(String itemId, MinecraftServer server) {
        return !findRecipesProducing(itemId, server).isEmpty();
    }
}