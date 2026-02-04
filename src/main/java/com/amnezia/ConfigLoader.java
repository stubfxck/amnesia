package com.amnezia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("amnezia");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIGDIR = FabricLoader.getInstance().getConfigDir().resolve("amnezia");

    // ==================== MAIN CONFIG ====================
    public static class MainConfig {
        public String language = "en_us";
        public ScrollSettings scrollSettings = new ScrollSettings();
        public Map<String, Double> spawnChances = new HashMap<>();
        public Map<String, LootTableConfig> lootTables = new HashMap<>();
        public NotificationConfig notifications = new NotificationConfig();
        public Map<String, String> scrollNames = new HashMap<>();
        public Map<String, String> rarityColors = new HashMap<>();
        public Map<String, String> rarityNames = new HashMap<>();
        public CommandConfig commands = new CommandConfig();
        public ModCompatibilityConfig modCompatibility = new ModCompatibilityConfig();
        public VillagerTradesConfig villagerTrades = new VillagerTradesConfig(); // ✅ НОВОЕ

        public MainConfig() {
            // Defaults - only set if not loaded from JSON
            // Don't set defaults here as they will be overridden by JSON anyway
            // Language-specific defaults are handled after JSON loading
        }
    }

    public static class ScrollSettings {
        public boolean scrollsDisappear = true;
        public boolean loseRecipesOnDeath = true;
        public boolean debugMode = false;
        
        // ✅ НОВОЕ: Скрытие названий рецептов в Ancient свитках
        public boolean hideAncientRecipeName = true;
        public String unknownPlaceholder = "§k§k§k§k§k§k§k§k";
        
        // ✅ НОВОЕ: Режим сложности изучения рецептов
        public String difficultyMode = "hard"; // "easy" или "hard"
    }

    public static class LootTableConfig {
        public boolean enabled = true;
        public double chance = 0.5;
        public int minScrolls = 1;
        public int maxScrolls = 1;
    }

    public static class NotificationConfig {
        public String type = "chat";
        public String chatFormat = "§6[§e<item_name>§6] - <rarity_color><rarity> §7(<learned_text> <recipes_learned>/<total_recipes>)";
        public String actionbarFormat = "§e<item_name> §7| <rarity_color><rarity>";
        public TitleFormat titleFormat = new TitleFormat();
        
        public static class TitleFormat {
            public String title = "<rarity_color><rarity>";
            public String subtitle = "§7<player> <learned_text_lower> §f<item_name>";
        }
    }

    public static class CommandConfig {
        public boolean enabled = false;
        public Map<String, List<String>> commandsByRarity = new HashMap<>();

        public CommandConfig() {
            commandsByRarity.put("ancient", new ArrayList<>());
            commandsByRarity.put("legendary", new ArrayList<>());
            commandsByRarity.put("mythical", new ArrayList<>());
            commandsByRarity.put("ultraRare", new ArrayList<>());
            commandsByRarity.put("rare", new ArrayList<>());
            commandsByRarity.put("common", new ArrayList<>());
        }
    }

    public static class ModCompatibilityConfig {
        public boolean hideModdedRecipes = true;
        public boolean generateScrollsForModdedRecipes = true;
        public String moddedRecipeDefaultRarity = "rare";
        
        // ✅ НОВОЕ: Динамическое добавление модовых структур
        public boolean autoDetectModdedStructures = true;
        public double moddedStructureDefaultChance = 0.5;
        public int moddedStructureMinScrolls = 1;
        public int moddedStructureMaxScrolls = 1;
    }

    // ✅ НОВОЕ: Настройки торговли свитков с жителями
    public static class VillagerTradesConfig {
        public boolean enabled = true;
        public int level = 5;
        public int basePriceEmeralds = 40;
        public int maxUses = 1;
        public int experience = 30;
        public boolean intellectualBonus = true;
    }

    // ==================== ITEMS CONFIG ====================
    public static class ItemsConfig {
        public MetaInfo meta;
        
        // ✅ НОВАЯ СТРУКТУРА: Разделение на minecraft и modded рецепты
        public RecipeSection minecraftRecipes;
        public RecipeSection moddedRecipes;
        
        // ⚠️ DEPRECATED: Старые поля для обратной совместимости
        public DefaultItems defaultItems;
        public SoloItems soloItems;
        public GroupedItems groupedItems;
        public Warnings warnings;
        
        /**
         * Метод для миграции старой структуры в новую
         */
        public void migrateToNewStructure() {
            if (minecraftRecipes == null && defaultItems != null) {
                // Старая структура - мигрируем
                minecraftRecipes = new RecipeSection();
                minecraftRecipes.defaultItems = defaultItems;
                minecraftRecipes.soloItems = soloItems;
                minecraftRecipes.groupedItems = groupedItems;
                
                LOGGER.info("Migrated old items.json structure to new format");
            }
            
            // Создаём moddedRecipes если его нет
            if (moddedRecipes == null) {
                moddedRecipes = new RecipeSection();
                moddedRecipes.defaultItems = new DefaultItems();
                moddedRecipes.defaultItems.items = new ArrayList<>();
                moddedRecipes.soloItems = new SoloItems();
                moddedRecipes.soloItems.items = new ArrayList<>();
                moddedRecipes.groupedItems = new GroupedItems();
                moddedRecipes.groupedItems.groups = new ArrayList<>();
            }
        }
    }
    
    /**
     * ✅ НОВЫЙ КЛАСС: Секция рецептов (minecraft или modded)
     */
    public static class RecipeSection {
        public DefaultItems defaultItems;
        public SoloItems soloItems;
        public GroupedItems groupedItems;
    }

    public static class MetaInfo {
        public String version;
        public List<String> minecraftversions;
        public String author;
        public String totalitems;
        public String coverage;
        
        // ✅ НОВЫЕ ПОЛЯ для модовой совместимости
        public boolean autoGenerateModdedRecipes = true;
        public String moddedRecipesDefaultRarity = "rare";
    }

    public static class DefaultItems {
        public String comment;
        public List<ItemConfig> items;
    }

    public static class SoloItems {
        public String comment;
        public List<ItemConfig> items;
    }

    public static class GroupedItems {
        public String comment;
        public List<GroupConfig> groups;
    }

    public static class Warnings {
        public String comment;
        public boolean checkconflicts;
        public List<String> conflicttypes;
    }

    public static class ItemConfig {
        public String id;
        public String rarity;

        public ItemConfig() {}

        public ItemConfig(String id, String rarity) {
            this.id = id;
            this.rarity = rarity;
        }

        public String getFullId() {
            if (id == null) return "minecraft:air";
            return id.contains(":") ? id : "minecraft:" + id;
        }
    }

    public static class GroupConfig {
        public String id;
        public String name;
        public String rarity;
        public Object noExit; // может быть Boolean или String "none"
        public String description;
        public List<String> recipes;
        public List<String> requirements;

        // Утилита для получения boolean значения noExit
        public Boolean getNoExitResolved() {
            if (noExit == null) return false;
            if (noExit instanceof Boolean) return (Boolean) noExit;
            if (noExit instanceof String) {
                String str = ((String) noExit).toLowerCase();
                if (str.equals("none")) return null; // null = не определено
                return Boolean.parseBoolean(str);
            }
            return false;
        }

        public List<String> getRecipesWithPrefix() {
            List<String> result = new ArrayList<>();
            if (recipes == null) return result;

            for (String recipe : recipes) {
                if (recipe != null && !recipe.isEmpty()) {
                    if (!recipe.contains(":")) {
                        result.add("minecraft:" + recipe);
                    } else {
                        result.add(recipe);
                    }
                }
            }
            return result;
        }
    }

    // ==================== SCROLL RARITY ====================
    public enum ScrollRarity {
        COMMON("common"),
        RARE("rare"),
        ULTRARARE("ultraRare"),
        MYTHICAL("mythical"),
        LEGENDARY("legendary"),
        ANCIENT("ancient");

        private final String id;

        ScrollRarity(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static ScrollRarity fromString(String str) {
            if (str == null) return COMMON;
            for (ScrollRarity r : values()) {
                if (r.id.equalsIgnoreCase(str)) return r;
            }
            return COMMON;
        }
    }


    // ==================== LOADING METHODS ====================
    private static void copyDefaultFile(String resourcePath, Path targetPath) {
        try {
            if (Files.exists(targetPath)) {
                LOGGER.debug("File already exists: " + targetPath.getFileName());
                return;
            }

            InputStream inputStream = ConfigLoader.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                LOGGER.error("Could not find resource: " + resourcePath);
                return;
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath);
            LOGGER.debug("Copied default file: " + targetPath.getFileName());
            inputStream.close();
        } catch (Exception e) {
            LOGGER.error("Failed to copy default file: " + resourcePath, e);
        }
    }

    /**
     * ✅ Удаляет строки с _comment из JSON, корректно обрабатывая запятые
     */
    private static String removeJsonComments(String jsonContent) {
        String[] lines = jsonContent.split("\\r?\\n");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // Пропускаем строки с _comment
            if (trimmed.contains("_comment")) {
                // Если предыдущая строка заканчивается запятой, удаляем запятую
                if (result.length() > 0) {
                    String currentResult = result.toString();
                    int lastCommaIndex = currentResult.lastIndexOf(',');
                    if (lastCommaIndex > 0) {
                        // Проверяем что после запятой только пробелы/переносы
                        String afterComma = currentResult.substring(lastCommaIndex + 1).trim();
                        if (afterComma.isEmpty()) {
                            // Удаляем запятую
                            result.setLength(lastCommaIndex);
                            result.append("\n");
                        }
                    }
                }
                continue;
            }
            
            result.append(line).append("\n");
        }
        
        return result.toString();
    }

    /**
     * ✅ ИСПРАВЛЕНО: Правильно удаляет комментарии без поломки JSON
     */
    public static MainConfig loadMainConfig() {
        try {
            Files.createDirectories(CONFIGDIR);
            Path configFile = CONFIGDIR.resolve("config.json");

            if (!Files.exists(configFile)) {
                LOGGER.debug("config.json not found, copying default from resources...");
                copyDefaultFile("/assets/amnezia/config/amnezia/config.json", configFile);
            }

            if (!Files.exists(configFile)) {
                LOGGER.warn("Could not copy default config, creating programmatically...");
                MainConfig defaultConfig = new MainConfig();
                setConfigDefaults(defaultConfig);
                saveMainConfig(defaultConfig);
                return defaultConfig;
            }

            try {
                // ✅ ИСПРАВЛЕНО: Читаем и фильтруем комментарии
                String jsonContent = Files.readString(configFile);
                String filteredJson = removeJsonComments(jsonContent);
                
                Gson gsonWithComments = new GsonBuilder().setPrettyPrinting().setLenient().create();
                MainConfig config = gsonWithComments.fromJson(filteredJson, MainConfig.class);

                if (config == null) {
                    LOGGER.error("Failed to parse config.json, using defaults");
                    MainConfig defaultConfig = new MainConfig();
                    setConfigDefaults(defaultConfig);
                    return defaultConfig;
                }

                // Ensure all maps are initialized
                if (config.spawnChances == null) config.spawnChances = new HashMap<>();
                if (config.lootTables == null) config.lootTables = new HashMap<>();
                if (config.scrollNames == null) config.scrollNames = new HashMap<>();
                if (config.rarityColors == null) config.rarityColors = new HashMap<>();
                if (config.rarityNames == null) config.rarityNames = new HashMap<>();
                if (config.scrollSettings == null) config.scrollSettings = new ScrollSettings();
                if (config.notifications == null) config.notifications = new NotificationConfig();
                if (config.commands == null) config.commands = new CommandConfig();
                if (config.modCompatibility == null) config.modCompatibility = new ModCompatibilityConfig();

                // Set defaults for missing values
                setConfigDefaults(config);

                return config;
            } catch (NumberFormatException e) {
                LOGGER.error("Failed to parse number in config.json - check for comments or invalid values: " + e.getMessage());
                MainConfig defaultConfig = new MainConfig();
                setConfigDefaults(defaultConfig);
                return defaultConfig;
            } catch (Exception e) {
                LOGGER.error("Failed to load main config: " + e.getMessage(), e);
                MainConfig defaultConfig = new MainConfig();
                setConfigDefaults(defaultConfig);
                return defaultConfig;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load main config: " + e.getMessage(), e);
            MainConfig defaultConfig = new MainConfig();
            setConfigDefaults(defaultConfig);
            return defaultConfig;
        }
    }

    private static void setConfigDefaults(MainConfig config) {
        // Set defaults only if not already set
        if (config.scrollSettings.scrollsDisappear == false && config.scrollSettings.loseRecipesOnDeath == false && config.scrollSettings.debugMode == false) {
            // If all are false, this might be a fresh config, set defaults
            config.scrollSettings.scrollsDisappear = true;
            config.scrollSettings.loseRecipesOnDeath = true;
            config.scrollSettings.debugMode = false;
        }

        // Set spawn chances defaults if empty
        if (config.spawnChances.isEmpty()) {
            config.spawnChances.put("ancient", 0.005);
            config.spawnChances.put("legendary", 0.02);
            config.spawnChances.put("mythical", 0.05);
            config.spawnChances.put("ultraRare", 0.10);
            config.spawnChances.put("rare", 0.20);
            config.spawnChances.put("common", 0.40);
        }

        // Set scroll names defaults if empty (language-aware)
        if (config.scrollNames.isEmpty()) {
            boolean isRussian = "ru_ru".equals(config.language);
            if (isRussian) {
                config.scrollNames.put("common", "§7Обычный свиток");
                config.scrollNames.put("rare", "§eРедкий свиток");
                config.scrollNames.put("ultraRare", "§bУльтра редкий свиток");
                config.scrollNames.put("mythical", "§dМифический свиток");
                config.scrollNames.put("legendary", "§6Легендарный свиток");
                config.scrollNames.put("ancient", "§3Древний свиток");
            } else {
                config.scrollNames.put("common", "§7Common Scroll");
                config.scrollNames.put("rare", "§eRare Scroll");
                config.scrollNames.put("ultraRare", "§bUltra Rare Scroll");
                config.scrollNames.put("mythical", "§dMythical Scroll");
                config.scrollNames.put("legendary", "§6Legendary Scroll");
                config.scrollNames.put("ancient", "§3Ancient Scroll");
            }
        }

        // Set rarity colors defaults if empty
        if (config.rarityColors.isEmpty()) {
            config.rarityColors.put("common", "§7");
            config.rarityColors.put("rare", "§e");
            config.rarityColors.put("ultraRare", "§b");
            config.rarityColors.put("mythical", "§d");
            config.rarityColors.put("legendary", "§6");
            config.rarityColors.put("ancient", "§3");
        }

        // Set rarity names defaults if empty (language-aware)
        if (config.rarityNames.isEmpty()) {
            boolean isRussian = "ru_ru".equals(config.language);
            if (isRussian) {
                config.rarityNames.put("common", "Обычный");
                config.rarityNames.put("rare", "Редкий");
                config.rarityNames.put("ultraRare", "Ультра редкий");
                config.rarityNames.put("mythical", "Мифический");
                config.rarityNames.put("legendary", "Легендарный");
                config.rarityNames.put("ancient", "Древний");
            } else {
                config.rarityNames.put("common", "Common");
                config.rarityNames.put("rare", "Rare");
                config.rarityNames.put("ultraRare", "Ultra Rare");
                config.rarityNames.put("mythical", "Mythical");
                config.rarityNames.put("legendary", "Legendary");
                config.rarityNames.put("ancient", "Ancient");
            }
        }
    }

    public static void saveMainConfig(MainConfig config) {
        try {
            Files.createDirectories(CONFIGDIR);
            Path configFile = CONFIGDIR.resolve("config.json");
            try {
                Writer writer = Files.newBufferedWriter(configFile);
                GSON.toJson(config, writer);
                writer.close();
                LOGGER.debug("Config saved successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to save main config: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save main config: " + e.getMessage(), e);
        }
    }

    public static ItemsConfig loadItemsConfig() {
        try {
            Files.createDirectories(CONFIGDIR);
            Path itemsFile = CONFIGDIR.resolve("items.json");

            if (!Files.exists(itemsFile)) {
                LOGGER.debug("items.json not found, copying default from resources...");
                copyDefaultFile("/assets/amnezia/data/amnezia/items.json", itemsFile);
            }

            if (!Files.exists(itemsFile)) {
                LOGGER.error("CRITICAL ERROR: Could not create items.json!");
                LOGGER.error("Please manually copy items.json to config/amnezia");
                return null;
            }

            try {
                Reader reader = Files.newBufferedReader(itemsFile);
                ItemsConfig config = GSON.fromJson(reader, ItemsConfig.class);
                reader.close();

                if (config == null) {
                    LOGGER.error("Failed to parse items.json - file is invalid");
                    return null;
                }

                // Validate and initialize structure
                if (config.defaultItems == null) {
                    LOGGER.warn("items.json missing defaultItems section");
                    config.defaultItems = new DefaultItems();
                    config.defaultItems.items = new ArrayList<>();
                }

                if (config.soloItems == null) {
                    LOGGER.warn("items.json missing soloItems section");
                    config.soloItems = new SoloItems();
                    config.soloItems.items = new ArrayList<>();
                }

                if (config.groupedItems == null) {
                    LOGGER.warn("items.json missing groupedItems section");
                    config.groupedItems = new GroupedItems();
                    config.groupedItems.groups = new ArrayList<>();
                }

                // Ensure lists are not null
                if (config.defaultItems.items == null) config.defaultItems.items = new ArrayList<>();
                if (config.soloItems.items == null) config.soloItems.items = new ArrayList<>();
                if (config.groupedItems.groups == null) config.groupedItems.groups = new ArrayList<>();

                // ✅ НОВОЕ: Мигрируем старую структуру в новую
                config.migrateToNewStructure();

                AmneziaMod.debug("Items config loaded successfully");
                return config;
            } catch (Exception e) {
                LOGGER.error("Failed to load items config: " + e.getMessage(), e);
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load items config: " + e.getMessage(), e);
            return null;
        }
    }

    public static Map<String, LootTableConfig> loadStructuresConfig() {
        try {
            Path structuresPath = FabricLoader.getInstance().getModContainer("amnezia").orElseThrow().findPath("assets/amnezia/data/amnezia/structures.json").orElseThrow();
            Gson gsonWithComments = new GsonBuilder().setPrettyPrinting().setLenient().create();
            return gsonWithComments.fromJson(Files.newBufferedReader(structuresPath), StructuresConfig.class).lootTables;
        } catch (Exception e) {
            LOGGER.error("Failed to load structures config: " + e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public static class StructuresConfig {
        public Map<String, LootTableConfig> lootTables;
    }
}