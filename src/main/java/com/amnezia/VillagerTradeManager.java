package com.amnezia;

import com.amnezia.ConfigLoader.ScrollRarity;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;  // ← ИЗМЕНЕНО
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import java.util.Optional;

/**
 * ✅ Управление торговлей свитков с жителями
 * Все профессии на уровне 5 предлагают случайные свитки за изумруды
 */
public class VillagerTradeManager {
    
    private static final Random RANDOM = Random.create();  // ← ИЗМЕНЕНО
    
    /**
     * Регистрирует торговлю свитков для всех профессий жителей
     */
    public static void registerScrollTrades() {
        // ✅ Проверяем включена ли торговля в конфиге
        if (AmneziaMod.CONFIG == null || 
            AmneziaMod.CONFIG.villagerTrades == null || 
            !AmneziaMod.CONFIG.villagerTrades.enabled) {
            AmneziaMod.debug("[VILLAGER TRADES] Villager trades are disabled in config");
            return;
        }
        
        AmneziaMod.debug("[VILLAGER TRADES] Registering scroll trades for all professions...");
        
        // Уровень из конфига
        int level = AmneziaMod.CONFIG.villagerTrades.level;
        if (level < 1 || level > 5) {
            AmneziaMod.LOGGER.warn("[VILLAGER TRADES] Invalid level " + level + ", using default: 5");
            level = 5;
        }
        
        // ===== БИБЛИОТЕКАРЬ (выше шанс на редкие свитки) =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 40, VillagerProfession.LIBRARIAN));
        });
        
        // ===== КАРТОГРАФ =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 42, VillagerProfession.CARTOGRAPHER));
        });
        
        // ===== СВЯЩЕННИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CLERIC, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 44, VillagerProfession.CLERIC));
        });
        
        // ===== ОРУЖЕЙНИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.ARMORER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 45, VillagerProfession.ARMORER));
        });
        
        // ===== МЯСНИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.BUTCHER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 38, VillagerProfession.BUTCHER));
        });
        
        // ===== КУЗНЕЦ =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.WEAPONSMITH, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 46, VillagerProfession.WEAPONSMITH));
        });
        
        // ===== ИНСТРУМЕНТАЛЬЩИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.TOOLSMITH, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 45, VillagerProfession.TOOLSMITH));
        });
        
        // ===== ФЕРМЕР =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.FARMER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 35, VillagerProfession.FARMER));
        });
        
        // ===== РЫБАК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.FISHERMAN, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 36, VillagerProfession.FISHERMAN));
        });
        
        // ===== СТРЕЛОК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.FLETCHER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 38, VillagerProfession.FLETCHER));
        });
        
        // ===== КОЖЕВНИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LEATHERWORKER, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 37, VillagerProfession.LEATHERWORKER));
        });
        
        // ===== КАМЕНЩИК =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.MASON, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 39, VillagerProfession.MASON));
        });
        
        // ===== ПАСТУХ =====
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.SHEPHERD, level, factories -> {
            factories.add((entity, random) -> createScrollTrade(entity, random, 36, VillagerProfession.SHEPHERD));
        });
        
        AmneziaMod.debug("[VILLAGER TRADES] ✓ Scroll trades registered for 13 professions");
    }
    
    /**
     * Создаёт сделку на покупку случайного свитка
     * 
     * @param entity Житель
     * @param random Генератор случайных чисел (Minecraft Random)
     * @param emeraldPrice Цена в изумрудах
     * @param profession Профессия жителя (влияет на редкость)
     * @return TradeOffer или null если не удалось создать
     */
    private static TradeOffer createScrollTrade(Entity entity, Random random, int emeraldPrice, VillagerProfession profession) {
        try {
            // Генерируем редкость с учётом профессии
            ScrollRarity rarity = getWeightedRarity(random, profession);
            
            // Создаём случайный свиток этой редкости
            ItemStack scroll = createRandomScrollOfRarity(rarity, random);
            
            if (scroll.isEmpty()) {
                AmneziaMod.debug("[VILLAGER TRADES] Failed to create scroll for profession: " + profession);
                return null;
            }
            
            // Создаём сделку с настройками из конфига
            TradedItem payment = new TradedItem(Items.EMERALD, emeraldPrice);
            
            int maxUses = AmneziaMod.CONFIG.villagerTrades.maxUses;
            int xp = AmneziaMod.CONFIG.villagerTrades.experience;
            
            TradeOffer offer = new TradeOffer(
                payment,              // Цена (изумруды)
                scroll,               // Товар (свиток)
                maxUses,              // Максимум использований (из конфига)
                xp,                   // XP за сделку (из конфига)
                0.05f                 // Множитель цены
            );
            
            AmneziaMod.debug("[VILLAGER TRADES] Created trade: " + emeraldPrice + " emeralds → " + 
                           rarity.getId() + " scroll (" + profession + ")");
            
            return offer;
            
        } catch (Exception e) {
            AmneziaMod.LOGGER.error("[VILLAGER TRADES] Error creating scroll trade: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Определяет редкость свитка с учётом профессии
     * Библиотекарь и картограф дают больше шансов на редкие свитки (если включено)
     */
    private static ScrollRarity getWeightedRarity(Random random, VillagerProfession profession) {
        boolean isIntellectual = (profession == VillagerProfession.LIBRARIAN || 
                                 profession == VillagerProfession.CARTOGRAPHER ||
                                 profession == VillagerProfession.CLERIC);
        
        // Проверяем включён ли бонус из конфига
        boolean applyBonus = isIntellectual && 
                           AmneziaMod.CONFIG != null && 
                           AmneziaMod.CONFIG.villagerTrades != null && 
                           AmneziaMod.CONFIG.villagerTrades.intellectualBonus;
        
        double roll = random.nextDouble();
        
        if (applyBonus) {
            // Библиотекарь, картограф, священник: выше шансы на редкие
            if (roll < 0.01) return ScrollRarity.ANCIENT;      // 1%
            if (roll < 0.05) return ScrollRarity.LEGENDARY;    // 4%
            if (roll < 0.15) return ScrollRarity.MYTHICAL;     // 10%
            if (roll < 0.30) return ScrollRarity.ULTRARARE;    // 15%
            if (roll < 0.55) return ScrollRarity.RARE;         // 25%
            return ScrollRarity.COMMON;                         // 45%
        } else {
            // Остальные профессии: стандартные шансы
            if (roll < 0.005) return ScrollRarity.ANCIENT;     // 0.5%
            if (roll < 0.025) return ScrollRarity.LEGENDARY;   // 2%
            if (roll < 0.075) return ScrollRarity.MYTHICAL;    // 5%
            if (roll < 0.175) return ScrollRarity.ULTRARARE;   // 10%
            if (roll < 0.375) return ScrollRarity.RARE;        // 20%
            return ScrollRarity.COMMON;                         // 62.5%
        }
    }
    
    /**
     * Создаёт случайный свиток заданной редкости
     */
    private static ItemStack createRandomScrollOfRarity(ScrollRarity rarity, Random random) {
        if (AmneziaMod.SCROLL_POOL == null || AmneziaMod.SCROLL_POOL.isEmpty()) {
            AmneziaMod.LOGGER.error("[VILLAGER TRADES] SCROLL_POOL is empty!");
            return ItemStack.EMPTY;
        }
        
        // Фильтруем пул по редкости
        var available = AmneziaMod.SCROLL_POOL.stream()
            .filter(data -> {
                if (data instanceof ConfigLoader.GroupConfig) {
                    return ((ConfigLoader.GroupConfig) data).rarity.equalsIgnoreCase(rarity.getId());
                } else if (data instanceof ConfigLoader.ItemConfig) {
                    return ((ConfigLoader.ItemConfig) data).rarity.equalsIgnoreCase(rarity.getId());
                }
                return false;
            })
            .toList();
        
        if (available.isEmpty()) {
            AmneziaMod.debug("[VILLAGER TRADES] No scrolls available for rarity: " + rarity.getId());
            return ItemStack.EMPTY;
        }
        
        // Выбираем случайный элемент
        Object scrollData = available.get(random.nextInt(available.size()));
        
        // Создаём свиток через RandomScrollFunction
        ItemStack scroll = new ItemStack(AmneziaMod.SCROLL_ITEM);
        
        if (scrollData instanceof ConfigLoader.GroupConfig) {
            ConfigLoader.GroupConfig group = (ConfigLoader.GroupConfig) scrollData;
            scroll = AmneziaMod.RecipeScrollItem.createFromScrollData(group, rarity);
        } else if (scrollData instanceof ConfigLoader.ItemConfig) {
            ConfigLoader.ItemConfig item = (ConfigLoader.ItemConfig) scrollData;
            scroll = AmneziaMod.RecipeScrollItem.createFromScrollData(item, rarity);
        }
        
        return scroll;
    }
}
