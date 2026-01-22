package com.amnezia;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Явный импорт для избежания конфликта с net.minecraft.util.path.SymlinkFinder.PathType
import java.nio.file.Path;

public class PlayerDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("amnezia");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static final Map<UUID, Map<String, PlayerData>> CACHE = new ConcurrentHashMap<>();
    private static final ReadWriteLock SAVE_LOCK = new ReentrantReadWriteLock();
    
    private static long lastSaveTime = 0;
    private static final long SAVE_INTERVAL_MS = 10000;
    private static volatile boolean dirty = false;
    
    // ========== DATA CLASS ==========
    public static class PlayerData {
        public String name;
        public String worldKey;
        public Set<String> recipes;
        public long lastLogin;
        public long timePlayed;
        
        private transient ReadWriteLock recipeLock = new ReentrantReadWriteLock();
        
        public PlayerData(String name, String worldKey) {
            this.name = name;
            this.worldKey = worldKey;
            this.recipes = ConcurrentHashMap.newKeySet();
            this.lastLogin = System.currentTimeMillis();
            this.timePlayed = 0;
        }
        
        private void initLock() {
            if (recipeLock == null) {
                recipeLock = new ReentrantReadWriteLock();
            }
            if (recipes != null && !(recipes instanceof ConcurrentHashMap.KeySetView)) {
                Set<String> temp = recipes;
                recipes = ConcurrentHashMap.newKeySet();
                recipes.addAll(temp);
            }
        }
        
        public void addRecipe(String recipe) {
            recipeLock.writeLock().lock();
            try {
                recipes.add(recipe);
            } finally {
                recipeLock.writeLock().unlock();
            }
        }
        
        public void removeRecipe(String recipe) {
            recipeLock.writeLock().lock();
            try {
                recipes.remove(recipe);
            } finally {
                recipeLock.writeLock().unlock();
            }
        }
        
        public void clearRecipes() {
            recipeLock.writeLock().lock();
            try {
                recipes.clear();
            } finally {
                recipeLock.writeLock().unlock();
            }
        }
        
        public boolean hasRecipe(String recipe) {
            recipeLock.readLock().lock();
            try {
                return recipes.contains(recipe);
            } finally {
                recipeLock.readLock().unlock();
            }
        }
        
        public Set<String> getRecipesCopy() {
            recipeLock.readLock().lock();
            try {
                return new HashSet<>(recipes);
            } finally {
                recipeLock.readLock().unlock();
            }
        }
        
        public int getRecipeCount() {
            recipeLock.readLock().lock();
            try {
                return recipes.size();
            } finally {
                recipeLock.readLock().unlock();
            }
        }
    }
    
    /**
     * ✅ ИСПРАВЛЕНИЕ: Получаем УНИКАЛЬНЫЙ путь к файлу мира
     */
    private static Path getWorldDataFile(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return null;
        }
        try {
            MinecraftServer server = player.getServer();
            
            // ✅ getRunDirectory() уже возвращает Path в Minecraft 1.21.1
            java.nio.file.Path worldPath = server.getRunDirectory();
            
            // Получаем UUID мира для уникальности
            String worldId = getWorldId(player);
            
            java.nio.file.Path amneziaDir = worldPath.resolve("amnezia_data").resolve(worldId);
            Files.createDirectories(amneziaDir);
            
            java.nio.file.Path dataFile = amneziaDir.resolve("player_data.json");
            
            AmneziaMod.debug("[WORLD ID] Using data file: " + dataFile.toString());
            
            return dataFile;
        } catch (Exception e) {
            LOGGER.error("Failed to get world data file", e);
        }
        return null;
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Получить уникальный ID мира (комбинация имени + timestamp папки)
     */
    private static String getWorldId(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return "unknown_world";
        }
        
        try {
            MinecraftServer server = player.getServer();
            
            // ✅ РЕШЕНИЕ: Используем DataFixer UUID мира (уникальный для каждого мира)
            String worldName = server.getSaveProperties().getLevelName();
            
            // Получаем overworld для доступа к уникальным данным
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                // Используем seed + dimension ID для уникальности
                long seed = overworld.getSeed();
                String dimensionId = overworld.getRegistryKey().getValue().toString();
                
                // Создаём уникальный хеш
                String uniqueId = worldName + "_" + Long.toHexString(seed).substring(0, 8);
                
                AmneziaMod.debug("[WORLD ID] World unique ID: " + uniqueId);
                
                return uniqueId;
            }
            
            // Fallback: просто имя мира
            return worldName;
        } catch (Exception e) {
            LOGGER.error("Failed to get world ID", e);
            return "unknown_world";
        }
    }
    
    // ========== ЗАГРУЗКА ==========
    public static void loadPlayerData() {
        // Пустой метод для обратной совместимости
    }
    
    public static void loadPlayerDataForWorld(ServerPlayerEntity player) {
        SAVE_LOCK.writeLock().lock();
        try {
            CACHE.clear();
            
            Path dataFile = getWorldDataFile(player);
            if (dataFile == null) return;
            
            String worldId = getWorldId(player);
            
            if (!Files.exists(dataFile)) {
                AmneziaMod.debug("PLAYERDATA: player_data.json not found in world " + worldId + ", creating new");
                return;
            }
            
            try {
                String content = Files.readString(dataFile);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                
                if (root.has("players")) {
                    JsonObject playersObj = root.getAsJsonObject("players");
                    
                    for (String uuidStr : playersObj.keySet()) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            JsonObject playerData = playersObj.getAsJsonObject(uuidStr);
                            
                            PlayerData data = new PlayerData(
                                playerData.get("name").getAsString(),
                                worldId
                            );
                            data.lastLogin = playerData.get("lastLogin").getAsLong();
                            data.timePlayed = playerData.get("timePlayed").getAsLong();
                            
                            JsonArray recipesArr = playerData.getAsJsonArray("recipes");
                            for (JsonElement elem : recipesArr) {
                                data.recipes.add(elem.getAsString());
                            }
                            
                            data.initLock();
                            
                            Map<String, PlayerData> worldData = new ConcurrentHashMap<>();
                            worldData.put(worldId, data);
                            CACHE.put(uuid, worldData);
                            
                            AmneziaMod.debug("PLAYERDATA: Loaded " + data.name + " for world " + worldId + " with " + data.getRecipeCount() + " recipes");
                        } catch (Exception e) {
                            LOGGER.error("Failed to load player data", e);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse player_data.json", e);
            }
        } finally {
            SAVE_LOCK.writeLock().unlock();
        }
    }
    
    // ========== СОХРАНЕНИЕ ==========
    public static void savePlayerData() {
        // Пустой метод для обратной совместимости
    }
    
    public static void savePlayerDataForWorld(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (!dirty && (now - lastSaveTime < SAVE_INTERVAL_MS)) {
            return;
        }
        
        SAVE_LOCK.writeLock().lock();
        try {
            savePlayerDataInternalForWorld(player);
        } finally {
            SAVE_LOCK.writeLock().unlock();
        }
    }
    
    private static void savePlayerDataInternalForWorld(ServerPlayerEntity player) {
        try {
            Path dataFile = getWorldDataFile(player);
            if (dataFile == null) return;
            
            String worldId = getWorldId(player);
            
            JsonObject root = new JsonObject();
            
            JsonObject metadata = new JsonObject();
            metadata.addProperty("version", "1.0");
            metadata.addProperty("worldId", worldId);
            metadata.addProperty("lastModified", Instant.now().toString());
            metadata.addProperty("totalPlayers", CACHE.size());
            root.add("metadata", metadata);
            
            JsonObject playersObj = new JsonObject();
            for (Map.Entry<UUID, Map<String, PlayerData>> playerEntry : CACHE.entrySet()) {
                PlayerData data = playerEntry.getValue().get(worldId);
                if (data != null) {
                    JsonObject dataObj = new JsonObject();
                    dataObj.addProperty("name", data.name);
                    dataObj.addProperty("lastLogin", data.lastLogin);
                    dataObj.addProperty("timePlayed", data.timePlayed);
                    
                    JsonArray recipesArr = new JsonArray();
                    for (String recipe : data.getRecipesCopy()) {
                        recipesArr.add(recipe);
                    }
                    dataObj.add("recipes", recipesArr);
                    
                    playersObj.add(playerEntry.getKey().toString(), dataObj);
                }
            }
            root.add("players", playersObj);
            
            Path tempFile = dataFile.resolveSibling("player_data.json.tmp");
            Files.writeString(tempFile, GSON.toJson(root));
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            lastSaveTime = System.currentTimeMillis();
            dirty = false;
            
            AmneziaMod.debug("PLAYERDATA: Saved " + CACHE.size() + " players to world " + worldId);
        } catch (Exception e) {
            LOGGER.error("CRITICAL: Failed to save player_data.json", e);
        }
    }
    
    // ========== ОПЕРАЦИИ С РЕЦЕПТАМИ ==========
    
    private static PlayerData getOrCreatePlayerData(UUID playerUuid, String playerName, String worldKey) {
        Map<String, PlayerData> worldData = CACHE.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        return worldData.computeIfAbsent(worldKey, k -> new PlayerData(playerName, worldKey));
    }
    
    public static void addRecipe(UUID playerUuid, String playerName, String worldKey, String recipe) {
        PlayerData data = getOrCreatePlayerData(playerUuid, playerName, worldKey);
        data.addRecipe(recipe);
        data.lastLogin = System.currentTimeMillis();
        dirty = true;
        
        AmneziaMod.debug("PLAYERDATA: Added recipe '" + recipe + "' to " + playerName + " in world " + worldKey);
    }
    
    public static void removeRecipe(UUID playerUuid, String worldKey, String recipe) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData != null) {
            PlayerData data = worldData.get(worldKey);
            if (data != null) {
                data.removeRecipe(recipe);
                data.lastLogin = System.currentTimeMillis();
                dirty = true;
            }
        }
    }
    
    public static void clearRecipes(UUID playerUuid, String worldKey) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData != null) {
            PlayerData data = worldData.get(worldKey);
            if (data != null) {
                int count = data.getRecipeCount();
                data.clearRecipes();
                data.lastLogin = System.currentTimeMillis();
                dirty = true;
                
                AmneziaMod.debug("PLAYERDATA: Cleared " + count + " recipes for " + data.name + " in world " + worldKey);
            }
        }
    }
    
    public static void resetToDefaults(UUID playerUuid, String playerName, String worldKey) {
        PlayerData data = new PlayerData(playerName, worldKey);
        
        if (AmneziaMod.ITEMS_CONFIG != null && 
            AmneziaMod.ITEMS_CONFIG.defaultItems != null && 
            AmneziaMod.ITEMS_CONFIG.defaultItems.items != null) {
            
            for (ConfigLoader.ItemConfig item : AmneziaMod.ITEMS_CONFIG.defaultItems.items) {
                if (item != null && item.id != null) {
                    data.addRecipe(item.getFullId());
                }
            }
        }
        
        data.initLock();
        Map<String, PlayerData> worldData = CACHE.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        worldData.put(worldKey, data);
        data.lastLogin = System.currentTimeMillis();
        dirty = true;
        
        AmneziaMod.debug("PLAYERDATA: Reset " + playerName + " to " + data.getRecipeCount() + " default recipes in world " + worldKey);
    }
    
    public static Set<String> getPlayerRecipes(UUID playerUuid, String worldKey) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData == null) return new HashSet<>();
        
        PlayerData data = worldData.get(worldKey);
        if (data == null) return new HashSet<>();
        
        return data.getRecipesCopy();
    }
    
    public static boolean hasRecipe(UUID playerUuid, String worldKey, String recipe) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData == null) return false;
        
        PlayerData data = worldData.get(worldKey);
        return data != null && data.hasRecipe(recipe);
    }
    
    public static int getRecipeCount(UUID playerUuid, String worldKey) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData == null) return 0;
        
        PlayerData data = worldData.get(worldKey);
        return data != null ? data.getRecipeCount() : 0;
    }
    
    public static boolean playerExists(UUID playerUuid, String worldKey) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        return worldData != null && worldData.containsKey(worldKey);
    }
    
    public static void initializePlayer(UUID playerUuid, String playerName, String worldKey) {
        if (!playerExists(playerUuid, worldKey)) {
            AmneziaMod.debug("PLAYERDATA: New player in world " + worldKey + ", creating default recipes");
            resetToDefaults(playerUuid, playerName, worldKey);
        } else {
            Map<String, PlayerData> worldData = CACHE.get(playerUuid);
            PlayerData data = worldData.get(worldKey);
            data.lastLogin = System.currentTimeMillis();
            dirty = true;
            AmneziaMod.debug("PLAYERDATA: Existing player in world " + worldKey + ", " + data.getRecipeCount() + " recipes");
        }
    }
    
    public static void forceSync(ServerPlayerEntity player) {
        dirty = true;
        lastSaveTime = 0;
        savePlayerDataForWorld(player);
    }
    
    public static void updatePlayerTime(UUID playerUuid, String worldKey, long timePlayedMs) {
        Map<String, PlayerData> worldData = CACHE.get(playerUuid);
        if (worldData != null) {
            PlayerData data = worldData.get(worldKey);
            if (data != null) {
                data.timePlayed += timePlayedMs;
                dirty = true;
            }
        }
    }
}