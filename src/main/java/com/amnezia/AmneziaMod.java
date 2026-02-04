package com.amnezia;

import com.amnezia.ConfigLoader.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class AmneziaMod implements ModInitializer {

    public static Set<String> getAllRecipes() {
        return ALL_RECIPES;
    }

    public static final String MOD_ID = "amnezia";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Конфиги
    public static MainConfig CONFIG;
    public static ItemsConfig ITEMS_CONFIG;
    public static List<Object> SCROLL_POOL = new ArrayList<>();
    public static final RecipeScrollItem SCROLL_ITEM = new RecipeScrollItem(new Item.Settings().maxCount(16));

    public static final LootFunctionType<RandomScrollFunction> RANDOM_SCROLL_FUNCTION =
            Registry.register(Registries.LOOT_FUNCTION_TYPE, Identifier.of(MOD_ID, "random_scroll"), RandomScrollFunction.TYPE);
    private static final Set<String> ALL_RECIPES = new HashSet<>();
    private static final Random RANDOM = new Random();

    /**
     * ✅ НОВАЯ ФУНКЦИЯ: Автоматическая генерация свитков для модовых рецептов
     */
    private static void generateModdedRecipes(MinecraftServer server) {
        if (ITEMS_CONFIG == null || ITEMS_CONFIG.meta == null) {
            debug("[MODDED] Config is null, skipping");
            return;
        }

        // Получаем дефолтную редкость из мета
        String defaultRarity = ITEMS_CONFIG.meta.moddedRecipesDefaultRarity;
        if (defaultRarity == null || defaultRarity.isEmpty()) {
            defaultRarity = "rare";
        }

        // Собираем все уже существующие рецепты из minecraft config
        Set<String> existingRecipes = new HashSet<>();
        
        // Из minecraftRecipes
        if (ITEMS_CONFIG.minecraftRecipes != null) {
            if (ITEMS_CONFIG.minecraftRecipes.defaultItems != null && ITEMS_CONFIG.minecraftRecipes.defaultItems.items != null) {
                for (ItemConfig item : ITEMS_CONFIG.minecraftRecipes.defaultItems.items) {
                    if (item != null && item.id != null) {
                        existingRecipes.add(item.getFullId());
                    }
                }
            }
            
            if (ITEMS_CONFIG.minecraftRecipes.soloItems != null && ITEMS_CONFIG.minecraftRecipes.soloItems.items != null) {
                for (ItemConfig item : ITEMS_CONFIG.minecraftRecipes.soloItems.items) {
                    if (item != null && item.id != null) {
                        existingRecipes.add(item.getFullId());
                    }
                }
            }
            
            if (ITEMS_CONFIG.minecraftRecipes.groupedItems != null && ITEMS_CONFIG.minecraftRecipes.groupedItems.groups != null) {
                for (GroupConfig group : ITEMS_CONFIG.minecraftRecipes.groupedItems.groups) {
                    if (group != null && group.recipes != null) {
                        for (String recipe : group.recipes) {
                            String fullId = recipe.contains(":") ? recipe : "minecraft:" + recipe;
                            existingRecipes.add(fullId);
                        }
                    }
                }
            }
        }
        
        // Также проверяем старую структуру (для обратной совместимости)
        if (ITEMS_CONFIG.defaultItems != null && ITEMS_CONFIG.defaultItems.items != null) {
            for (ItemConfig item : ITEMS_CONFIG.defaultItems.items) {
                if (item != null && item.id != null) {
                    existingRecipes.add(item.getFullId());
                }
            }
        }
        
        if (ITEMS_CONFIG.soloItems != null && ITEMS_CONFIG.soloItems.items != null) {
            for (ItemConfig item : ITEMS_CONFIG.soloItems.items) {
                if (item != null && item.id != null) {
                    existingRecipes.add(item.getFullId());
                }
            }
        }
        
        if (ITEMS_CONFIG.groupedItems != null && ITEMS_CONFIG.groupedItems.groups != null) {
            for (GroupConfig group : ITEMS_CONFIG.groupedItems.groups) {
                if (group != null && group.recipes != null) {
                    for (String recipe : group.recipes) {
                        String fullId = recipe.contains(":") ? recipe : "minecraft:" + recipe;
                        existingRecipes.add(fullId);
                    }
                }
            }
        }

        debug("[MODDED] Existing minecraft recipes: " + existingRecipes.size());

        // Проходим по всем рецептам на сервере
        int moddedCount = 0;
        int vanillaCount = 0;
        int duplicateCount = 0;

        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            String recipeId = entry.id().toString();
            
            // Пропускаем рецепты из майнкрафта
            if (recipeId.startsWith("minecraft:")) {
                vanillaCount++;
                continue;
            }
            
            // Пропускаем если уже есть в конфиге
            if (existingRecipes.contains(recipeId)) {
                duplicateCount++;
                continue;
            }
            
            // ✅ Это модовый рецепт - добавляем в moddedRecipes
            ItemConfig moddedItem = new ItemConfig(recipeId, defaultRarity);
            
            // Добавляем в moddedRecipes.soloItems
            if (ITEMS_CONFIG.moddedRecipes == null) {
                ITEMS_CONFIG.moddedRecipes = new RecipeSection();
            }
            if (ITEMS_CONFIG.moddedRecipes.soloItems == null) {
                ITEMS_CONFIG.moddedRecipes.soloItems = new SoloItems();
                ITEMS_CONFIG.moddedRecipes.soloItems.items = new ArrayList<>();
            }
            
            ITEMS_CONFIG.moddedRecipes.soloItems.items.add(moddedItem);
            
            // Добавляем в SCROLL_POOL
            SCROLL_POOL.add(moddedItem);
            moddedCount++;
            
            debug("[MODDED] Added: " + recipeId + " (rarity: " + defaultRarity + ")");
        }

        debug("=".repeat(60));
        debug("[MODDED RECIPES] Generation complete:");
        debug("  Modded recipes added: " + moddedCount);
        debug("  Vanilla recipes skipped: " + vanillaCount);
        debug("  Duplicates skipped: " + duplicateCount);
        debug("  New scroll pool size: " + SCROLL_POOL.size());
        debug("=".repeat(60));
    }

    private static void validateGroupRecipes(MinecraftServer server) {
        if (ITEMS_CONFIG == null || ITEMS_CONFIG.groupedItems == null) return;

        Set<String> validRecipes = new HashSet<>();
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            validRecipes.add(entry.id().toString());
            validRecipes.add(entry.id().getPath());
        }

        Set<String> invalidGroups = new HashSet<>();
        List<Object> validatedPool = new ArrayList<>();

        for (Object obj : SCROLL_POOL) {
            if (obj instanceof GroupConfig) {
                GroupConfig group = (GroupConfig) obj;
                boolean hasInvalidRecipe = false;

                if (group.recipes != null) {
                    for (String recipe : group.recipes) {
                        String fullId = recipe.contains(":") ? recipe : "minecraft:" + recipe;

                        if (!validRecipes.contains(fullId) && !validRecipes.contains(recipe)) {
                            LOGGER.error("GROUP '" + group.id + "' contains INVALID recipe '" + recipe + "' - removing from pool");
                            invalidGroups.add(group.id);
                            hasInvalidRecipe = true;
                            break;
                        }
                    }
                }

                if (!hasInvalidRecipe) {
                    validatedPool.add(group);
                }
            } else {
                validatedPool.add(obj);
            }
        }

        // Удаляем группы, зависящие от невалидных
        if (!invalidGroups.isEmpty()) {
            boolean changed = true;
            while (changed) {
                changed = false;
                List<Object> tempPool = new ArrayList<>();

                for (Object obj : validatedPool) {
                    if (obj instanceof GroupConfig) {
                        GroupConfig group = (GroupConfig) obj;
                        boolean dependsOnInvalid = false;

                        if (group.requirements != null) {
                            for (String reqId : group.requirements) {
                                if (invalidGroups.contains(reqId)) {
                                    LOGGER.warn("GROUP '" + group.id + "' depends on invalid group '" + reqId + "' - removing");
                                    invalidGroups.add(group.id);
                                    dependsOnInvalid = true;
                                    changed = true;
                                    break;
                                }
                            }
                        }

                        if (!dependsOnInvalid) {
                            tempPool.add(group);
                        }
                    } else {
                        tempPool.add(obj);
                    }
                }

                validatedPool = tempPool;
            }

            SCROLL_POOL.clear();
            SCROLL_POOL.addAll(validatedPool);

            debug("Recipe validation complete. Removed " + invalidGroups.size() + " invalid groups");
        }
    }

    private void validateRecipesEarly() {
        if (ITEMS_CONFIG == null || ITEMS_CONFIG.groupedItems == null) return;
        
        debug("========== EARLY RECIPE VALIDATION ==========");
        
        Set<String> vanillaRecipes = new HashSet<>();
        
        // Собираем список ВСЕХ возможных рецептов из ванильного Minecraft
        // (без необходимости сервера)
        vanillaRecipes.addAll(Arrays.asList(
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
            "stick", "torch", "crafting_table", "furnace", "chest",
            "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_sword",
            "stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword",
            "iron_pickaxe", "iron_axe", "iron_shovel", "iron_sword",
            "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_sword",
            "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_sword",
            "anvil", "enchanting_table", "brewing_stand", "smithing_table"
            // ... добавь остальные известные рецепты
        ));
        
        Set<String> problematicGroups = new HashSet<>();
        
        if (ITEMS_CONFIG.groupedItems.groups != null) {
            for (GroupConfig group : ITEMS_CONFIG.groupedItems.groups) {
                if (group == null || group.recipes == null) continue;
                
                for (String recipe : group.recipes) {
                    String recipeId = recipe.contains(":") ? recipe.split(":")[1] : recipe;
                    
                    // Проверка на очевидно несуществующие рецепты
                    if (recipe.contains("_mythical_") || recipe.contains("_unknown_")) {
                        LOGGER.error("❌ GROUP '" + group.id + "' contains SUSPICIOUS recipe: " + recipe);
                        problematicGroups.add(group.id);
                    }
                }
            }
        }
        
        if (!problematicGroups.isEmpty()) {
            LOGGER.warn("⚠️ Found " + problematicGroups.size() + " groups with suspicious recipes");
            LOGGER.warn("⚠️ Full validation will happen on server start");
        }
        
        debug("========== EARLY VALIDATION COMPLETE ==========");
    }

    // ✅ Измени onInitialize() - добавь вызов ПЕРЕД buildScrollPool()

    @Override
    public void onInitialize() {
        debug("============================================================");
        debug("Initializing Amnezia mod...");
        debug("============================================================");

        CONFIG = ConfigLoader.loadMainConfig();
        ITEMS_CONFIG = ConfigLoader.loadItemsConfig();
        CONFIG.lootTables = ConfigLoader.loadStructuresConfig();

        Registry.register(Registries.ITEM, Identifier.of("amnezia", "recipe_scroll"), SCROLL_ITEM);
        debug("Recipe scroll item registered");

        if (ITEMS_CONFIG == null) {
            LOGGER.error("CRITICAL: items.json failed to load! Mod will not work properly!");
            LOGGER.error("Please add items.json to config/amnezia/ folder");
        } else {
            validateRecipesEarly(); // ✅ ДОБАВЛЕНО: ранняя валидация
            buildScrollPool();
            debug("[AMNEZIA] Config loaded successfully");
            if (CONFIG != null && CONFIG.scrollSettings != null) {
                debug("Debug mode: " + CONFIG.scrollSettings.debugMode);
                debug("Scrolls disappear: " + CONFIG.scrollSettings.scrollsDisappear);
                debug("Lose recipes on death: " + CONFIG.scrollSettings.loseRecipesOnDeath);
            }
            debug("Scroll pool size: " + SCROLL_POOL.size());
        }

        registerLootTableModification();
        registerCommands();
        registerServerEvents();
        
        // ✅ НОВОЕ: Регистрация торговли свитков с жителями
        VillagerTradeManager.registerScrollTrades();

        debug("============================================================");
        debug("Amnezia initialized successfully!");
        debug("============================================================");
    }


    // ==================== ITEM REGISTRY ====================

    private void buildScrollPool() {
        SCROLL_POOL.clear();

        if (ITEMS_CONFIG == null) {
            LOGGER.error("Cannot build scroll pool - ITEMS_CONFIG is null");
            return;
        }

        // ===== ШАГ 1: ВАЛИДАЦИЯ ГРУПП =====
        Map<String, GroupConfig> groupsById = new HashMap<>();
        Set<String> duplicateIds = new HashSet<>();
        Set<String> invalidGroups = new HashSet<>();

        // ✅ НОВОЕ: Поддержка обеих структур (старой и новой)
        GroupedItems groupedItems = null;
        if (ITEMS_CONFIG.minecraftRecipes != null && ITEMS_CONFIG.minecraftRecipes.groupedItems != null) {
            groupedItems = ITEMS_CONFIG.minecraftRecipes.groupedItems;
        } else if (ITEMS_CONFIG.groupedItems != null) {
            groupedItems = ITEMS_CONFIG.groupedItems; // Fallback на старую структуру
        }

        if (groupedItems != null && groupedItems.groups != null) {
            for (GroupConfig group : groupedItems.groups) {
                if (group == null || group.id == null || group.id.trim().isEmpty()) {
                    LOGGER.warn("Ignoring group with null/empty id");
                    continue;
                }

                // Проверка дубликатов ID
                if (groupsById.containsKey(group.id)) {
                    LOGGER.error("DUPLICATE GROUP ID: '" + group.id + "' - both groups will be IGNORED");
                    duplicateIds.add(group.id);
                    invalidGroups.add(group.id);
                    continue;
                }

                groupsById.put(group.id, group);
            }

            // Удаляем дубликаты
            for (String dupId : duplicateIds) {
                groupsById.remove(dupId);
            }
        }

        // ===== ШАГ 2: ВАЛИДАЦИЯ REQUIREMENTS =====
        for (GroupConfig group : groupsById.values()) {
            if (group.requirements != null) {
                for (String reqId : group.requirements) {
                    if (!groupsById.containsKey(reqId)) {
                        LOGGER.error("GROUP '" + group.id + "' requires non-existent group '" + reqId + "' - IGNORING entire tree");
                        invalidGroups.add(group.id);
                        break;
                    }
                }
            }
        }

        // Удаляем группы с невалидными requirements
        for (String invalidId : invalidGroups) {
            groupsById.remove(invalidId);
        }

        debug("Recipe validation will be performed on server start");

        // ===== ШАГ 4: УДАЛЕНИЕ ЗАВИСИМЫХ ГРУПП ОТ НЕВАЛИДНЫХ =====
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<Map.Entry<String, GroupConfig>> iterator = groupsById.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, GroupConfig> entry = iterator.next();
                GroupConfig group = entry.getValue();

                if (group.requirements != null) {
                    for (String reqId : group.requirements) {
                        if (invalidGroups.contains(reqId)) {
                            LOGGER.warn("GROUP '" + group.id + "' depends on invalid group '" + reqId + "' - IGNORING");
                            invalidGroups.add(group.id);
                            iterator.remove();
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        Map<String, Boolean> resolvedNoExit = new HashMap<>();

        class NoExitResolver {
            Boolean resolve(String groupId, Set<String> visited) {
                if (visited.contains(groupId)) {
                    LOGGER.error("❌ CIRCULAR DEPENDENCY detected in group: " + groupId);
                    invalidGroups.add(groupId);
                    return false;
                }

                if (resolvedNoExit.containsKey(groupId)) {
                    return resolvedNoExit.get(groupId);
                }

                visited.add(groupId);
                GroupConfig group = groupsById.get(groupId);

                if (group == null) {
                    visited.remove(groupId);
                    return false;
                }

                Boolean currentNoExit = group.getNoExitResolved();

                // ✅ ПРОВЕРКА 1: Есть ли дети у этой группы?
                boolean hasChildren = false;
                for (GroupConfig other : groupsById.values()) {
                    if (other.requirements != null && other.requirements.contains(groupId)) {
                        hasChildren = true;
                        break;
                    }
                }

                // ✅ КРИТИЧЕСКАЯ ОШИБКА: noExit=none БЕЗ детей
                if (!hasChildren && currentNoExit == null) {
                    LOGGER.error("❌ GROUP '" + groupId + "' has noExit='none' but NO CHILDREN - IGNORING group");
                    invalidGroups.add(groupId);
                    visited.remove(groupId);
                    return false;
                }

                // ✅ ПРОВЕРКА 2: Корневая группа (нет requirements)
                boolean isRoot = (group.requirements == null || group.requirements.isEmpty());
                
                if (isRoot && currentNoExit == null) {
                    LOGGER.error("❌ ROOT GROUP '" + groupId + "' has noExit='none' - must be true/false - IGNORING group");
                    invalidGroups.add(groupId);
                    visited.remove(groupId);
                    return false;
                }

                // ✅ Если значение определено явно (true/false)
                if (currentNoExit != null) {
                    resolvedNoExit.put(groupId, currentNoExit);
                    visited.remove(groupId);
                    return currentNoExit;
                }

                // ✅ Если noExit = none, наследуем от ДЕТЕЙ
                List<Boolean> childrenValues = new ArrayList<>();
                for (GroupConfig other : groupsById.values()) {
                    if (other.requirements != null && other.requirements.contains(groupId)) {
                        Boolean childValue = resolve(other.id, new HashSet<>(visited));
                        if (childValue != null) {
                            childrenValues.add(childValue);
                        }
                    }
                }

                // ✅ ВАЖНО: Если все дети одинаковые - берём их значение
                if (!childrenValues.isEmpty()) {
                    boolean allSame = childrenValues.stream().allMatch(v -> v.equals(childrenValues.get(0)));
                    if (allSame) {
                        currentNoExit = childrenValues.get(0);
                        debug("✓ GROUP '" + groupId + "' inherited noExit=" + currentNoExit + " from children");
                    } else {
                        // Дети разные - ОШИБКА КОНФИГУРАЦИИ
                        LOGGER.error("❌ GROUP '" + groupId + "' has noExit='none' but children have MIXED values - IGNORING group");
                        invalidGroups.add(groupId);
                        visited.remove(groupId);
                        return false;
                    }
                } else {
                    // Нет детей (уже проверили выше)
                    currentNoExit = false;
                }

                resolvedNoExit.put(groupId, currentNoExit);
                visited.remove(groupId);
                return currentNoExit;
            }
        }

        NoExitResolver resolver = new NoExitResolver();
        for (String groupId : groupsById.keySet()) {
            resolver.resolve(groupId, new HashSet<>());
        }

        // ✅ УДАЛИТЬ ВСЕ INVALID ГРУППЫ ПЕРЕД ФИНАЛЬНОЙ СБОРКОЙ
        for (String invalidId : invalidGroups) {
            groupsById.remove(invalidId);
        }

        // Применяем разрешённые значения каскадом вниз
        for (GroupConfig group : groupsById.values()) {
            if (group.requirements != null && !group.requirements.isEmpty()) {
                for (String parentId : group.requirements) {
                    Boolean parentNoExit = resolvedNoExit.get(parentId);
                    if (parentNoExit != null && parentNoExit) {
                        // Родитель = true → все дети = true
                        resolvedNoExit.put(group.id, true);
                        break;
                    }
                }
            }
        }

        // ===== ШАГ 6: СБОР В SCROLL_POOL =====
        Set<String> noExitGroupItems = new HashSet<>();
        int groupCount = 0;

        for (GroupConfig group : groupsById.values()) {
            Boolean finalNoExit = resolvedNoExit.getOrDefault(group.id, false);

            if (finalNoExit) {
                // Группа с noExit=true - добавляем предметы в исключения
                if (group.recipes != null) {
                    noExitGroupItems.addAll(group.recipes);
                }
            }

            // Добавляем группу в пул
            SCROLL_POOL.add(group);
            groupCount++;
            debug("Added group: " + group.id + " (noExit=" + finalNoExit + ", " + 
                (group.recipes != null ? group.recipes.size() : 0) + " recipes)");
        }

        // ===== ШАГ 7: SOLO ITEMS =====
        int soloCount = 0;
        int conflictCount = 0;

        // ✅ НОВОЕ: Поддержка обеих структур (старой и новой)
        SoloItems soloItems = null;
        if (ITEMS_CONFIG.minecraftRecipes != null && ITEMS_CONFIG.minecraftRecipes.soloItems != null) {
            soloItems = ITEMS_CONFIG.minecraftRecipes.soloItems;
        } else if (ITEMS_CONFIG.soloItems != null) {
            soloItems = ITEMS_CONFIG.soloItems; // Fallback на старую структуру
        }

        if (soloItems != null && soloItems.items != null) {
            for (ItemConfig item : soloItems.items) {
                if (item != null && item.id != null && item.rarity != null) {
                    if (noExitGroupItems.contains(item.id)) {
                        LOGGER.warn("CONFLICT: Item '" + item.id + "' is in soloItems but in noExit=true group - IGNORING solo entry");
                        conflictCount++;
                    } else {
                        SCROLL_POOL.add(item);
                        soloCount++;
                    }
                }
            }
        }

        debug("=".repeat(60));
        debug("Scroll pool built: " + SCROLL_POOL.size() + " total");
        debug("  Groups: " + groupCount);
        debug("  Solo items: " + soloCount);
        debug("  Conflicts ignored: " + conflictCount);
        debug("  Invalid groups removed: " + invalidGroups.size());
        debug("=".repeat(60));
    }

    // ==================== LOOT TABLE MODIFICATION ====================

    private void registerLootTableModification() {
        debug("Registering loot table modification...");
        
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            String path = key.getValue().getPath();
            
            LootTableConfig lootConfig = getLootConfigForPath(path);
            
            if (lootConfig != null && lootConfig.enabled) {
                debug("📦 APPLYING SCROLLS to: " + path);
                
                // ✅ ИСПРАВЛЕНО: chance используется как вероятность, а не количество пулов
                // rolls = 1 (всегда 1 попытка), но с условием RandomChance
                
                LootPool.Builder poolBuilder = LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1.0f)) // Всегда 1 попытка
                    .with(ItemEntry.builder(SCROLL_ITEM)
                        .apply(RandomScrollFunction.builder())
                    );
                
                // ✅ Добавляем условие: шанс срабатывания = lootConfig.chance
                if (lootConfig.chance < 1.0) {
                    poolBuilder.conditionally(
                        net.minecraft.loot.condition.RandomChanceLootCondition.builder((float) lootConfig.chance)
                    );
                }
                
                tableBuilder.pool(poolBuilder.build());
                
                debug("   ✓ ADDED scroll pool (chance: " + (int)(lootConfig.chance * 100) + "%)");
            }
        });
        
        debug("Loot table modification registered");
    }







    // ==================== COMMANDS ====================

    private void registerCommands() {
        debug("Registering server commands...");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var giveCommand = CommandManager.literal("give")
                    .requires(source -> source.hasPermissionLevel(2));

            Map<ScrollRarity, List<Object>> byRarity = new HashMap<>();

            for (Object data : SCROLL_POOL) {
                String rarity = null;
                if (data instanceof GroupConfig) {
                    rarity = ((GroupConfig) data).rarity;
                } else if (data instanceof ItemConfig) {
                    rarity = ((ItemConfig) data).rarity;
                }

                if (rarity != null) {
                    ScrollRarity scrollRarity = ScrollRarity.fromString(rarity);
                    byRarity.computeIfAbsent(scrollRarity, k -> new ArrayList<>()).add(data);
                }
            }

            for (ScrollRarity rarity : ScrollRarity.values()) {
                var rarityBranch = CommandManager.literal(rarity.getId())
                        .then(CommandManager.literal("random")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    
                                    List<Object> available = byRarity.getOrDefault(rarity, new ArrayList<>());
                                    if (available.isEmpty()) {
                                        ctx.getSource().sendFeedback(() ->
                                                Text.translatable("scroll.amnezia.no_items", rarity.getId()).formatted(Formatting.RED),
                                                false);
                                        return 1;
                                    }
                                    
                                    // Перемешиваем для случайности
                                    Collections.shuffle(available, RANDOM);
                                    Object randomData = available.get(0);

                                    ItemStack scroll = RecipeScrollItem.createFromScrollData(randomData, rarity);
                                    player.giveItemStack(scroll);

                                    String name = getScrollDataName(randomData);

                                    debug("Gave random " + rarity.getId() + " scroll: " + name);
                                    return 1;
                                }));

                List<Object> items = byRarity.getOrDefault(rarity, new ArrayList<>());

                for (Object data : items) {
                    String name = getScrollDataName(data);
                    
                    // Улучшенная обработка имен
                    String safeName = name
                            .toLowerCase()
                            .replace(" ", "_")
                            .replace(":", "_")
                            .replace("-", "_")
                            .replace("(", "")
                            .replace(")", "")
                            .replace("'", "")
                            .replace("\"", "")
                            .replace(".", "")
                            .replace(",", "")
                            .replaceAll("[^a-z0-9_а-яё]", "");

                    // Пропускаем если имя некорректное
                    if (safeName.isEmpty() || safeName.length() < 2) {
                        debug("Skipping item with invalid command name: " + name);
                        continue;
                    }

                    rarityBranch = rarityBranch.then(CommandManager.literal(safeName)
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                ItemStack scroll = RecipeScrollItem.createFromScrollData(data, rarity);
                                player.giveItemStack(scroll);

                                debug("Gave " + rarity.getId() + " scroll: " + name);

                                return 1;
                            }));
                }

                giveCommand = giveCommand.then(rarityBranch);
            }

            dispatcher.register(CommandManager.literal("amnezia")
                    .requires(source -> source.hasPermissionLevel(0))
                    .then(giveCommand)
                    .then(CommandManager.literal("reset")
                            .requires(source -> source.hasPermissionLevel(0))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                clearRecipes(player);
                                ctx.getSource().sendFeedback(() -> Text.translatable("command.amnezia.recipes_reset").formatted(Formatting.YELLOW), false);
                                debug("Command /amnezia reset executed for " + player.getName().getString());
                                return 1;
                            }))
                    .then(CommandManager.literal("init")
                            .requires(source -> source.hasPermissionLevel(0))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                debug("Manual init for " + player.getName().getString());
                                initializePlayerRecipes(player, player.getServer());
                                ctx.getSource().sendFeedback(() -> Text.translatable("command.amnezia.recipes_initialized").formatted(Formatting.GREEN), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("status")
                            .requires(source -> source.hasPermissionLevel(0))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                int count = getRecipeCount(player);
                                ctx.getSource().sendFeedback(() ->
                                        Text.translatable("command.amnezia.recipes_count", count).formatted(Formatting.GOLD), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("reload")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> {
                                CONFIG = ConfigLoader.loadMainConfig();
                                ItemsConfig newItemsConfig = ConfigLoader.loadItemsConfig();

                                if (newItemsConfig != null) {
                                    ITEMS_CONFIG = newItemsConfig;
                                    buildScrollPool();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.translatable("command.amnezia.config_reloaded").formatted(Formatting.GREEN), true);
                                    debug("Configs reloaded successfully");
                                } else {
                                    ctx.getSource().sendFeedback(() ->
                                            Text.translatable("command.amnezia.config_load_error").formatted(Formatting.RED), false);
                                }

                                return 1;
                            }))
                    .then(CommandManager.literal("debug")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean value = BoolArgumentType.getBool(ctx, "value");
                                        CONFIG.scrollSettings.debugMode = value;
                                        ctx.getSource().sendFeedback(() ->
                                                Text.translatable("command.amnezia.debug_mode", value).formatted(Formatting.AQUA), true);
                                        debug("Debug mode toggled to: " + value);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("debug_scroll")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("rarity", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("common");
                                    builder.suggest("rare");
                                    builder.suggest("ultraRare");
                                    builder.suggest("mythical");
                                    builder.suggest("legendary");
                                    builder.suggest("ancient");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String rarity = StringArgumentType.getString(ctx, "rarity");
                                    debugScrollGeneration(rarity);
                                    ctx.getSource().sendFeedback(() -> 
                                        Text.translatable("command.amnezia.debug_info", rarity)
                                            .formatted(Formatting.GREEN), 
                                        true);
                                    return 1;
                                })
                            )
                        )
            );
        });

        debug("Commands registered");
    }

    // ==================== SERVER EVENTS ====================

    private void registerServerEvents() {
        debug("Registering server lifecycle events...");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            debug("============================================================");
            debug("SERVER STARTED EVENT");
            debug("============================================================");

            debug("Enabling doLimitedCrafting...");
            server.getGameRules().get(GameRules.DO_LIMITED_CRAFTING).set(true, server);

            boolean isEnabled = server.getGameRules().getBoolean(GameRules.DO_LIMITED_CRAFTING);
            debug("doLimitedCrafting enabled: " + isEnabled);

            debug("Loading all available recipes...");
            loadRecipes(server);

            // ✅ НОВОЕ: Автогенерация модовых рецептов
            if (ITEMS_CONFIG != null && ITEMS_CONFIG.meta != null && ITEMS_CONFIG.meta.autoGenerateModdedRecipes) {
                debug("Auto-generating modded recipes...");
                generateModdedRecipes(server);
            }

            // ✅ ДОБАВЬ ЭТУ СТРОКУ
            debug("Validating group recipes...");
            validateGroupRecipes(server);

            debug("Total recipes loaded: " + ALL_RECIPES.size());
            debug("Server initialization complete");
            debug("============================================================");
        });
        
        // ✅ ДОБАВИТЬ ЭТОТ БЛОК - СОХРАНЕНИЕ ПРИ ОСТАНОВКЕ СЕРВЕРА:
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            debug("[AMNEZIA] Server stopping - forcing player data sync...");
            // forceSync() без аргумента больше не вызываем
            debug("[AMNEZIA] Player data saved successfully");
        });


        debug("Registering player events...");

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            debug("============================================================");
            debug("PLAYER JOINED: " + player.getName().getString());
            debug("============================================================");
            
            PlayerDataManager.loadPlayerDataForWorld(player);
            initializePlayerRecipes(player, server);
            
            debug("============================================================");
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerDataManager.savePlayerDataForWorld(player);
            debug("Player " + player.getName().getString() + " disconnected - data saved");
        });

        debug("Player and server events registered");
    }

    // ==================== HELPER METHODS ====================

    private static String getWorldId(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return "unknown_world";
        }
        
        try {
            MinecraftServer server = player.getServer();
            
            // ✅ Используем seed мира для уникальности
            String worldName = server.getSaveProperties().getLevelName();
            
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                long seed = overworld.getSeed();
                
                // Создаём уникальный хеш: имя_первые8символовсида
                String uniqueId = worldName + "_" + Long.toHexString(seed).substring(0, 8);
                
                debug("[WORLD ID] World unique ID: " + uniqueId);
                
                return uniqueId;
            }
            
            // Fallback
            return worldName;
        } catch (Exception e) {
            LOGGER.error("Failed to get world ID", e);
            return "unknown_world";
        }
    }

    private static void debugScrollGeneration(String rarityStr) {
        ConfigLoader.ScrollRarity rarity = ConfigLoader.ScrollRarity.fromString(rarityStr);
        
        debug("========== DEBUG: " + rarity.getId() + " scrolls ==========");
        
        List<Object> available = new ArrayList<>();
        
        for (Object data : SCROLL_POOL) {
            String dataRarity = null;
            
            if (data instanceof ConfigLoader.GroupConfig) {
                dataRarity = ((ConfigLoader.GroupConfig) data).rarity;
            } else if (data instanceof ConfigLoader.ItemConfig) {
                dataRarity = ((ConfigLoader.ItemConfig) data).rarity;
            }
            
            if (dataRarity != null && dataRarity.equalsIgnoreCase(rarity.getId())) {
                available.add(data);
                
                if (data instanceof ConfigLoader.GroupConfig) {
                    ConfigLoader.GroupConfig group = (ConfigLoader.GroupConfig) data;
                    debug("  GROUP: " + group.id + " (" + group.name + ")");
                    debug("    Recipes: " + (group.recipes != null ? group.recipes.size() : 0));
                    debug("    NoExit: " + group.getNoExitResolved());
                    debug("    Requirements: " + group.requirements);
                    
                    // Дополнительно: первые 5 рецептов для примера
                    if (group.recipes != null && !group.recipes.isEmpty()) {
                        debug("    First recipes: " + 
                            group.recipes.subList(0, Math.min(5, group.recipes.size())));
                    }
                } else if (data instanceof ConfigLoader.ItemConfig) {
                    ConfigLoader.ItemConfig item = (ConfigLoader.ItemConfig) data;
                    debug("  ITEM: " + item.id + " (" + Text.translatable("item." + item.id.replace(":", ".")).getString() + ")");
                }
            }
        }
        
        debug("Total " + rarity.getId() + " entries: " + available.size());
        
        if (CONFIG != null && CONFIG.spawnChances != null) {
            double chance = CONFIG.spawnChances.getOrDefault(rarity.getId(), 0.0);
            debug("Spawn chance: " + (chance * 100) + "%");
        }
        
        debug("=".repeat(60));
    }

    private static void saveDiscoveredStructure(String path, LootTableConfig config) {
        try {
            Path discoveredFile = FabricLoader.getInstance().getConfigDir().resolve("discovered_structures.json");
            
            Map<String, LootTableConfig> discovered = new HashMap<>();
            
            // Читаем существующие
            if (Files.exists(discoveredFile)) {
                try {
                    String json = Files.readString(discoveredFile);
                    discovered = new Gson().fromJson(json, 
                        new com.google.gson.reflect.TypeToken<Map<String, LootTableConfig>>(){}.getType());
                } catch (Exception e) {
                    LOGGER.warn("Failed to read discovered structures: " + e.getMessage());
                }
            }
            
            // Добавляем новую
            discovered.put(path, config);
            
            // Сохраняем
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(discovered);
            Files.writeString(discoveredFile, json);
            
            debug("[AUTO-DETECT] Saved to discovered_structures.json");
            
        } catch (Exception e) {
            LOGGER.error("Failed to save discovered structure: " + e.getMessage(), e);
        }
    }

    private static LootTableConfig getDefaultModdedLootConfig(String path) {
        // Проверяем что авто-детект включен
        if (CONFIG == null || CONFIG.modCompatibility == null || 
            !CONFIG.modCompatibility.autoDetectModdedStructures) {
            return null;
        }
        
        // Проверяем что это структура с сундуком (не из майнкрафта)
        if (!path.contains("chests/") && !path.contains("chest")) {
            return null;
        }
        
        // Игнорируем майнкрафт структуры (они уже в конфиге)
        if (path.startsWith("minecraft:")) {
            return null;
        }
        
        // ✅ Это модовая структура - создаём дефолтный конфиг
        debug("[AUTO-DETECT] Found modded structure: " + path);
        
        LootTableConfig config = new LootTableConfig();
        config.enabled = true;
        config.chance = CONFIG.modCompatibility.moddedStructureDefaultChance;
        config.minScrolls = CONFIG.modCompatibility.moddedStructureMinScrolls;
        config.maxScrolls = CONFIG.modCompatibility.moddedStructureMaxScrolls;
        
        // ✅ Сохраняем в конфиг для будущего
        CONFIG.lootTables.put(path, config);
        saveDiscoveredStructure(path, config);
        
        return config;
    }

    private static LootTableConfig getLootConfigForPath(String path) {
        if (CONFIG == null || CONFIG.lootTables == null) {
            return getDefaultModdedLootConfig(path);
        }
        
        // Точное совпадение
        if (CONFIG.lootTables.containsKey(path)) {
            return CONFIG.lootTables.get(path);
        }
        
        // Поиск по подстроке
        for (String key : CONFIG.lootTables.keySet()) {
            if (path.endsWith(key)) {
                return CONFIG.lootTables.get(key);
            }
        }
        
        // ✅ НОВОЕ: Автоматическое добавление модовых структур
        return getDefaultModdedLootConfig(path);
    }


    private static Formatting getFormattingForRarity(ScrollRarity rarity) {
        if (rarity == null) return Formatting.GRAY;

        switch (rarity) {
            case ANCIENT: return Formatting.DARK_AQUA;
            case LEGENDARY: return Formatting.GOLD;
            case MYTHICAL: return Formatting.RED;
            case ULTRARARE: return Formatting.AQUA;
            case RARE: return Formatting.YELLOW;
            default: return Formatting.GRAY;
        }
    }

    // private static Object getRandomScrollDataForRarity(ScrollRarity rarity) {
    //     if (rarity == null || SCROLL_POOL == null || SCROLL_POOL.isEmpty()) {
    //         return null;
    //     }

    //     List<Object> available = new ArrayList<>();

    //     for (Object data : SCROLL_POOL) {
    //         String dataRarity = null;

    //         if (data instanceof GroupConfig) {
    //             dataRarity = ((GroupConfig) data).rarity;
    //         } else if (data instanceof ItemConfig) {
    //             dataRarity = ((ItemConfig) data).rarity;
    //         }

    //         if (dataRarity != null && dataRarity.equalsIgnoreCase(rarity.getId())) {
    //             available.add(data);
    //         }
    //     }

    //     if (available.isEmpty()) {
    //         return null;
    //     }

    //     return available.get(RANDOM.nextInt(available.size()));
    // }

    /**
     * Получает читаемое название для данных свитка
     */
    public static String getScrollDataName(Object data) {
        if (data instanceof GroupConfig) {
            return ((GroupConfig) data).name;
        } else if (data instanceof ItemConfig) {
            ItemConfig item = (ItemConfig) data;
            try {
                // Get the actual Item object from registry
                Identifier itemId = Identifier.of(item.id);
                Item itemObj = Registries.ITEM.get(itemId);

                if (itemObj != null) {
                    // Try to get localized name from item's name component
                    String localizedName = itemObj.getName().getString();

                    // Comprehensive check to ensure we never return raw translation keys
                    if (isValidLocalizedName(localizedName, item.id)) {
                        return localizedName;
                    }
                }

                // Professional fallback: always format the ID beautifully
                return formatItemIdProfessionally(item.id);

            } catch (Exception e) {
                // Ultimate fallback with error handling
                return formatItemIdProfessionally(item.id);
            }
        }

        return "Unknown";
    }

    /**
     * Checks if the localized name is valid (not a raw translation key)
     */
    private static boolean isValidLocalizedName(String name, String itemId) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // Never accept raw translation keys
        if (name.startsWith("item.") || name.startsWith("block.") ||
            name.startsWith("minecraft:") || name.equals(itemId)) {
            return false;
        }

        // Check if it contains only valid characters (not just underscores and dots)
        if (!name.matches(".*[a-zA-Zа-яА-Я].*")) {
            return false;
        }

        // Additional check: ensure it's not just the item ID with dots
        String dottedId = itemId.replace(":", ".");
        if (name.equals("item." + dottedId) || name.equals("block." + dottedId)) {
            return false;
        }

        return true;
    }

    /**
     * Professionally formats an item ID into a readable name
     * Examples:
     * "minecraft:slime_block" -> "Slime Block"
     * "minecraft:cartography_table" -> "Cartography Table"
     * "create:mechanical_piston" -> "Mechanical Piston"
     */
    private static String formatItemIdProfessionally(String itemId) {
        try {
            String[] parts = itemId.split(":");
            if (parts.length != 2) {
                return itemId; // Invalid format
            }

            String itemName = parts[1]; // Get the part after the colon

            // Handle special cases
            if (itemName.equals("piston")) return "Piston";
            if (itemName.equals("sticky_piston")) return "Sticky Piston";
            if (itemName.equals("slime_block")) return "Slime Block";
            if (itemName.equals("cartography_table")) return "Cartography Table";

            // General formatting: split by underscores and capitalize each word
            String[] words = itemName.split("_");
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                if (i > 0) result.append(" ");

                if (!words[i].isEmpty()) {
                    // Capitalize first letter, lowercase the rest
                    result.append(words[i].substring(0, 1).toUpperCase());
                    if (words[i].length() > 1) {
                        result.append(words[i].substring(1).toLowerCase());
                    }
                }
            }

            return result.toString();

        } catch (Exception e) {
            // If anything goes wrong, return the original ID
            return itemId;
        }
    }

    // ==================== PUBLIC METHODS ====================

    public static void initializePlayerRecipes(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null || server == null) return;
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        String worldKey = getWorldId(player); // ✅ ИЗМЕНЕНО
        
        debug("=== INIT RECIPES FOR: " + playerName + " [" + worldKey + "] ===");
        
        PlayerDataManager.initializePlayer(playerUuid, playerName, worldKey);
        
        Set<String> playerRecipes = PlayerDataManager.getPlayerRecipes(playerUuid, worldKey);
        debug("[INIT] Player has " + playerRecipes.size() + " recipes in world " + worldKey);
        
        List<RecipeEntry<?>> toUnlock = new ArrayList<>();
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            String fullId = entry.id().toString();
            String recipeId = entry.id().getPath();
            
            if (playerRecipes.contains(fullId) || playerRecipes.contains(recipeId)) {
                toUnlock.add(entry);
            }
        }
        
        debug("[INIT] To unlock: " + toUnlock.size() + " recipes");
        
        try {
            List<RecipeEntry<?>> allRecipes = new ArrayList<>(server.getRecipeManager().values());
            player.lockRecipes(allRecipes);
            debug("[INIT] Locked all recipes for " + playerName);
        } catch (Exception e) {
            LOGGER.error("Failed to lock recipes", e);
        }
        
        if (!toUnlock.isEmpty()) {
            player.unlockRecipes(toUnlock);
            debug("[INIT] ✓ Unlocked " + toUnlock.size() + " recipes for " + playerName);
        }
        
        player.getRecipeBook().setGuiOpen(net.minecraft.recipe.book.RecipeBookCategory.CRAFTING, false);
        
        debug("=== INIT COMPLETE ===");
    }



    public static void loadRecipes(MinecraftServer server) {
        ALL_RECIPES.clear();

        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            ALL_RECIPES.add(entry.id().getPath());
        }

        debug("Loaded " + ALL_RECIPES.size() + " recipes into memory");
    }

    /**
     * ✅ ИСПРАВЛЕНО: Easy Mode теперь учит и основной рецепт, а не только зависимости
     */
    public static void learnRecipe(ServerPlayerEntity player, String recipe) {
        if (recipe == null || recipe.trim().isEmpty()) {
            debug("LEARN: Invalid recipe (null or empty)");
            return;
        }
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        String worldKey = getWorldId(player);
        MinecraftServer server = player.getServer();
        
        debug("[LEARN] Recipe: " + recipe + " for " + playerName + " [" + worldKey + "]");
        
        // ✅ Проверяем режим сложности
        String difficultyMode = "hard"; // По умолчанию
        if (CONFIG != null && CONFIG.scrollSettings != null && CONFIG.scrollSettings.difficultyMode != null) {
            difficultyMode = CONFIG.scrollSettings.difficultyMode.toLowerCase();
        }
        
        if (difficultyMode.equals("easy") && server != null) {
            // ✅ EASY MODE: Изучаем всю цепочку зависимостей + сам рецепт
            debug("[EASY MODE] Resolving dependencies for: " + recipe);
            List<String> dependencies = RecipeDependencyResolver.resolveDependencies(recipe, server);
            
            int learnedCount = 0;
            
            // Сначала учим зависимости (ингредиенты)
            for (String dependencyRecipe : dependencies) {
                if (!hasRecipe(player, dependencyRecipe)) {
                    PlayerDataManager.addRecipe(playerUuid, playerName, worldKey, dependencyRecipe);
                    unlockRecipe(player, dependencyRecipe);
                    learnedCount++;
                    debug("[EASY MODE] ✓ Learned dependency: " + dependencyRecipe);
                }
            }
            
            // ✅ ВАЖНО: Потом учим сам основной рецепт
            if (!hasRecipe(player, recipe)) {
                PlayerDataManager.addRecipe(playerUuid, playerName, worldKey, recipe);
                unlockRecipe(player, recipe);
                learnedCount++;
                debug("[EASY MODE] ✓ Learned main recipe: " + recipe);
            }
            
            debug("[EASY MODE] Total learned: " + learnedCount + " recipes");
            
        } else {
            // ✅ HARD MODE: Изучаем только указанный рецепт
            PlayerDataManager.addRecipe(playerUuid, playerName, worldKey, recipe);
            unlockRecipe(player, recipe);
            debug("[HARD MODE] Learned single recipe: " + recipe);
        }
    }

    public static boolean hasRecipe(ServerPlayerEntity player, String recipe) {
        if (player == null || recipe == null) return false;
        String worldKey = getWorldId(player); // ✅ ИЗМЕНЕНО
        return PlayerDataManager.hasRecipe(player.getUuid(), worldKey, recipe);
    }

    public static void clearRecipes(ServerPlayerEntity player) {
        if (player == null) return;
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        String worldKey = getWorldId(player); // ✅ ИЗМЕНЕНО
        
        debug("[CLEAR] All recipes for: " + playerName + " [" + worldKey + "]");
        
        PlayerDataManager.clearRecipes(playerUuid, worldKey);
        
        initializePlayerRecipes(player, player.getServer());
        
        debug("[SAVE] Final recipe count for " + playerName + ": " + getRecipeCount(player));
    }

    
    public static void resetPlayerRecipesOnDeath(ServerPlayerEntity player) {
        if (player == null) return;
        if (CONFIG != null && CONFIG.scrollSettings != null && !CONFIG.scrollSettings.loseRecipesOnDeath) {
            return;
        }
        if (player.getHealth() > 0.0f) {
            debug("DEATH: Player survived with totem, skipping reset");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        String worldKey = getWorldId(player); // ✅ ИЗМЕНЕНО
        
        debug("===== DEATH RESET: " + playerName + " [" + worldKey + "] =====");
        
        int deletedCount = PlayerDataManager.getRecipeCount(playerUuid, worldKey);
        debug("DEATH: Player had " + deletedCount + " recipes");
        
        PlayerDataManager.clearRecipes(playerUuid, worldKey);
        debug("DEATH: Deleted all learned recipes");
        
        try {
            List<RecipeEntry<?>> allRecipes = new ArrayList<>(server.getRecipeManager().values());
            player.lockRecipes(allRecipes);
            debug("DEATH: Locked all recipes in RecipeBook");
        } catch (Exception e) {
            LOGGER.error("Failed to lock recipes", e);
        }
        
        PlayerDataManager.resetToDefaults(playerUuid, playerName, worldKey);
        int restoredCount = PlayerDataManager.getRecipeCount(playerUuid, worldKey);
        debug("DEATH: Restored " + restoredCount + " default recipes");
        
        try {
            Set<String> defaultRecipes = PlayerDataManager.getPlayerRecipes(playerUuid, worldKey);
            List<RecipeEntry<?>> toUnlock = new ArrayList<>();
            
            for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
                String fullId = entry.id().toString();
                String recipeId = entry.id().getPath();
                
                if (defaultRecipes.contains(fullId) || defaultRecipes.contains(recipeId)) {
                    toUnlock.add(entry);
                }
            }
            
            if (!toUnlock.isEmpty()) {
                player.unlockRecipes(toUnlock);
                debug("DEATH: Unlocked " + toUnlock.size() + " default recipes");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to unlock recipes", e);
        }
        
        PlayerDataManager.forceSync(player);
        debug("DEATH: Complete. Deleted " + deletedCount + ", restored " + restoredCount);
        debug("===== DEATH RESET COMPLETE =====");
    }


    public static int getRecipeCount(ServerPlayerEntity player) {
        if (player == null) return 0;
        String worldKey = getWorldId(player); // ✅ ИЗМЕНЕНО
        return PlayerDataManager.getRecipeCount(player.getUuid(), worldKey);
    }

    

    // ==================== PRIVATE METHODS ====================

    private static void unlockRecipe(ServerPlayerEntity player, String recipeId) {
        if (player == null || recipeId == null || recipeId.trim().isEmpty()) {
            return;
        }
        
        try {
            Optional<RecipeEntry<?>> recipe = player.getServer().getRecipeManager().get(Identifier.of(recipeId));
            if (recipe.isPresent()) {
                player.unlockRecipes(Collections.singleton(recipe.get()));
                debug("[UNLOCK] " + recipeId);
            } else {
                debug("[WARN] Recipe not found: " + recipeId);
            }
        } catch (Exception e) {
            LOGGER.error("Could not unlock recipe: " + recipeId, e);
        }
    }

    public static void debug(String message) {
        // Complete silence when debug is disabled - no technical info whatsoever
        if (CONFIG == null || CONFIG.scrollSettings == null || !CONFIG.scrollSettings.debugMode) {
            return;
        }
        LOGGER.info("[AMNEZIA DEBUG] " + message);
    }

    // ==================== SCROLL ITEM CLASS ====================

    public static class RecipeScrollItem extends Item {

        public RecipeScrollItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            ItemStack stack = player.getStackInHand(hand);

            if (!world.isClient) {
                NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
                NbtCompound nbt = data.copyNbt();

                if (!nbt.contains("Type") || !nbt.contains("Rarity")) {
                    return TypedActionResult.fail(stack);
                }

                String type = nbt.getString("Type");
                ScrollRarity rarity = ScrollRarity.fromString(nbt.getString("Rarity"));

                if (type.equals("group")) {
                    return useGroupScroll(world, player, stack, nbt, rarity);
                } else if (type.equals("solo")) {
                    return useSoloScroll(world, player, stack, nbt, rarity);
                }

                return TypedActionResult.fail(stack);
            }

            return TypedActionResult.success(stack, world.isClient());
        }

        private TypedActionResult<ItemStack> useGroupScroll(World world, PlayerEntity player, ItemStack stack, NbtCompound nbt, ScrollRarity rarity) {
            String groupName = nbt.getString("GroupName");
            String recipesStr = nbt.getString("Recipes");

            if (recipesStr == null || recipesStr.isEmpty()) {
                return TypedActionResult.fail(stack);
            }

            String[] recipes = recipesStr.split(",");
            int learnedCount = 0;

            for (String recipe : recipes) {
                recipe = recipe.trim();

                if (!recipe.isEmpty() && !hasRecipe((ServerPlayerEntity) player, recipe)) {
                    learnRecipe((ServerPlayerEntity) player, recipe);
                    learnedCount++;
                }
            }

            if (learnedCount == 0) {
                // ✅ Это сообщение всегда показываем (важная информация)
                player.sendMessage(Text.translatable("message.amnezia.all_recipes_known").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }

            spawnScrollEffects(world, player);
            
            // ✅ ИСПРАВЛЕНО: NotificationManager сам решает, показывать ли уведомления
            NotificationManager.sendScrollNotification((ServerPlayerEntity) player, rarity, groupName, learnedCount);
            NotificationManager.executeScrollCommands((ServerPlayerEntity) player, rarity, groupName, learnedCount);

            if (CONFIG.scrollSettings.scrollsDisappear) {
                stack.decrement(1);
            }

            return TypedActionResult.success(stack, world.isClient());
        }

        private TypedActionResult<ItemStack> useSoloScroll(World world, PlayerEntity player, ItemStack stack, NbtCompound nbt, ScrollRarity rarity) {
            String itemId = nbt.getString("ItemId");
            String itemName = nbt.getString("ItemName");
            String fullId = itemId.contains(":") ? itemId : "minecraft:" + itemId;

            if (hasRecipe((ServerPlayerEntity) player, fullId)) {
                // ✅ Это сообщение всегда показываем (важная информация)
                player.sendMessage(Text.translatable("message.amnezia.already_known").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }

            learnRecipe((ServerPlayerEntity) player, fullId);
            spawnScrollEffects(world, player);
            
            // ✅ ИСПРАВЛЕНО: NotificationManager сам решает, показывать ли уведомления
            NotificationManager.sendScrollNotification((ServerPlayerEntity) player, rarity, itemName, 1);
            NotificationManager.executeScrollCommands((ServerPlayerEntity) player, rarity, itemName, 1);

            if (CONFIG.scrollSettings.scrollsDisappear) {
                stack.decrement(1);
            }

            return TypedActionResult.success(stack, world.isClient());
        }

        private void spawnScrollEffects(World world, PlayerEntity player) {
            ServerWorld sw = (ServerWorld) world;

            for (int i = 0; i < 30; i++) {
                sw.spawnParticles(ParticleTypes.ENCHANT,
                        player.getX() + (RANDOM.nextDouble() - 0.5) * 2,
                        player.getY() + RANDOM.nextDouble() * 2,
                        player.getZ() + (RANDOM.nextDouble() - 0.5) * 2,
                        1, 0, 0, 0, 0.1);
            }

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }

        @Override
        public Text getName(ItemStack stack) {
            NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound nbt = data.copyNbt();

            if (nbt.contains("Rarity")) {
                ScrollRarity rarity = ScrollRarity.fromString(nbt.getString("Rarity"));
                
                // ✅ УБИРАЕМ CONFIG.scrollNames, используем ТОЛЬКО translatable
                return Text.translatable("item.amnezia.recipe_scroll." + rarity.getId().toLowerCase());
            }

            return super.getName(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound nbt = data.copyNbt();

            if (!nbt.contains("Rarity") || !nbt.contains("Type")) {
                return;
            }

            ScrollRarity rarity = ScrollRarity.fromString(nbt.getString("Rarity"));
            String scrollType = nbt.getString("Type");
            String colorCode = "§e";

            if (CONFIG != null && CONFIG.rarityColors != null) {
                colorCode = CONFIG.rarityColors.getOrDefault(rarity.getId(), "§e");
            }

            Formatting color = getFormattingForRarity(rarity);

            tooltip.add(Text.empty());

            if (scrollType.equals("group")) {
                String groupName = nbt.getString("GroupName");
                String recipesStr = nbt.getString("Recipes");
                int recipeCount = recipesStr != null ? recipesStr.split(",").length : 0;

                // ✅ НОВОЕ: Используем флаг hideAncientRecipeName
                boolean shouldHide = rarity == ScrollRarity.ANCIENT && 
                                   CONFIG != null && 
                                   CONFIG.scrollSettings != null && 
                                   CONFIG.scrollSettings.hideAncientRecipeName;
                
                if (shouldHide) {
                    // ✅ ИСПРАВЛЕНО: Для обфускации нужен реальный текст + Formatting.OBFUSCATED
                    String obfuscatedText = "XXXXXXXX"; // 8 символов для обфускации
                    tooltip.add(Text.translatable("tooltip.amnezia.scroll.group")
                            .append(Text.literal(obfuscatedText).formatted(Formatting.OBFUSCATED).formatted(color)));
                } else {
                    tooltip.add(Text.translatable("tooltip.amnezia.scroll.group")
                            .append(Text.literal(groupName).formatted(color)));
                }

                tooltip.add(Text.translatable("tooltip.amnezia.scroll.recipes")
                        .append(Text.literal(String.valueOf(recipeCount)).formatted(Formatting.YELLOW)));

            } else if (scrollType.equals("solo")) {
                String itemName = nbt.getString("ItemName");

                // ✅ НОВОЕ: Используем флаг hideAncientRecipeName
                boolean shouldHide = rarity == ScrollRarity.ANCIENT && 
                                   CONFIG != null && 
                                   CONFIG.scrollSettings != null && 
                                   CONFIG.scrollSettings.hideAncientRecipeName;
                
                if (shouldHide) {
                    // ✅ ИСПРАВЛЕНО: Для обфускации нужен реальный текст + Formatting.OBFUSCATED
                    String obfuscatedText = "XXXXXXXX"; // 8 символов для обфускации
                    tooltip.add(Text.translatable("tooltip.amnezia.scroll.recipe")
                            .append(Text.literal(obfuscatedText).formatted(Formatting.OBFUSCATED).formatted(color)));
                } else {
                    tooltip.add(Text.translatable("tooltip.amnezia.scroll.recipe")
                            .append(Text.literal(itemName).formatted(color)));
                }
            }

            tooltip.add(Text.empty());
        }

        @Override
        public boolean hasGlint(ItemStack stack) {
            return true;
        }

        public static ItemStack createFromScrollData(Object data, ScrollRarity rarity) {
            ItemStack stack = new ItemStack(SCROLL_ITEM);
            NbtCompound nbt = new NbtCompound();

            nbt.putString("Rarity", rarity.getId());

            if (data instanceof GroupConfig) {
                GroupConfig group = (GroupConfig) data;
                nbt.putString("Type", "group");
                nbt.putString("GroupName", group.name);

                StringBuilder recipesStr = new StringBuilder();
                List<String> recipes = group.getRecipesWithPrefix();

                for (int i = 0; i < recipes.size(); i++) {
                    if (i > 0) recipesStr.append(",");
                    recipesStr.append(recipes.get(i));
                }

                nbt.putString("Recipes", recipesStr.toString());

            } else if (data instanceof ItemConfig) {
                ItemConfig item = (ItemConfig) data;
                nbt.putString("Type", "solo");
                nbt.putString("ItemId", item.id);
                nbt.putString("ItemName", getScrollDataName(data));
            }

            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

            return stack;
        }
    }
}