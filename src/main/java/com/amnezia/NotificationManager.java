package com.amnezia;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import java.util.List;

/**
 * Управление уведомлениями для свитков
 */
public class NotificationManager {

    /**
     * Заменяет все плейсхолдеры в строке
     */
    private static String replacePlaceholders(String text, ServerPlayerEntity player, 
                                              String itemName, ConfigLoader.ScrollRarity rarity, 
                                              int count, int totalRecipes) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String colorCode = AmneziaMod.CONFIG.rarityColors.getOrDefault(rarity.getId(), "§e");
        
        // ✅ НОВОЕ: Скрываем название если это Ancient и включена настройка
        String displayedItemName = itemName;
        if (rarity == ConfigLoader.ScrollRarity.ANCIENT && 
            AmneziaMod.CONFIG != null && 
            AmneziaMod.CONFIG.scrollSettings != null && 
            AmneziaMod.CONFIG.scrollSettings.hideAncientRecipeName) {
            
            String placeholder = AmneziaMod.CONFIG.scrollSettings.unknownPlaceholder;
            if (placeholder == null || placeholder.isEmpty()) {
                placeholder = "§k§k§k§k§k§k§k§k";
            }
            displayedItemName = placeholder;
        }
        
        return text
                // Плейсхолдеры игрока
                .replace("<player>", player.getName().getString())
                .replace("<player_name>", player.getName().getString())
                .replace("<player_uuid>", player.getUuidAsString())
                
                // Плейсхолдеры предмета (используем скрытое имя если нужно)
                .replace("<item_name>", displayedItemName)
                .replace("<item>", displayedItemName)
                
                // Плейсхолдеры редкости
                .replace("<rarity>", getRarityDisplayName(rarity))
                .replace("<rarity_id>", rarity.getId())
                .replace("<rarity_color>", colorCode)
                
                // Плейсхолдеры количества
                .replace("<count>", String.valueOf(count))
                .replace("<recipes_count>", String.valueOf(count))
                
                // Плейсхолдеры прогресса
                .replace("<recipes_learned>", String.valueOf(AmneziaMod.getRecipeCount(player)))
                .replace("<total_recipes>", String.valueOf(totalRecipes))
                
                // Плейсхолдеры позиции
                .replace("<x>", String.valueOf((int) player.getX()))
                .replace("<y>", String.valueOf((int) player.getY()))
                .replace("<z>", String.valueOf((int) player.getZ()))
                .replace("<world>", player.getWorld().getRegistryKey().getValue().toString())
                
                // Плейсхолдеры здоровья/опыта
                .replace("<health>", String.valueOf((int) player.getHealth()))
                .replace("<max_health>", String.valueOf((int) player.getMaxHealth()))
                .replace("<level>", String.valueOf(player.experienceLevel))
                
                // Переводимые плейсхолдеры
                .replace("<learned_text>", Text.translatable("notification.learned_text").getString())
                .replace("<learned_text_lower>", Text.translatable("notification.learned_text_lower").getString())
                .replace("<learned_ultra_rare_scroll>", Text.translatable("notification.learned_ultra_rare_scroll").getString())
                .replace("<learned_ancient_scroll>", Text.translatable("notification.learned_ancient_scroll").getString())
                .replace("<ancient_scroll_emoji>", "🌟")
                
                // Старые форматы (обратная совместимость) - тоже используем скрытое имя
                .replace("{itemName}", displayedItemName)
                .replace("%item_name%", displayedItemName)
                .replace("{rarity}", getRarityDisplayName(rarity))
                .replace("%rarity_color%", colorCode)
                
                // Цветовые коды
                .replace("&", "§");
    }

    /**
     * Получает отображаемое имя редкости
     */
    private static String getRarityDisplayName(ConfigLoader.ScrollRarity rarity) {
        // ✅ Используем файлы локализации вместо конфига
        String translationKey = "rarity.amnezia." + rarity.getId().toLowerCase();
        String translated = Text.translatable(translationKey).getString();
        
        // Если перевод не найден, возвращаем ID
        if (translated.equals(translationKey)) {
            return rarity.getId();
        }
        
        return translated;
    }

    public static void sendScrollNotification(ServerPlayerEntity player,
                                            ConfigLoader.ScrollRarity rarity,
                                            String itemName,
                                            int count) {
        // ✅ ДОБАВЛЕН DEBUG
        AmneziaMod.debug("[NOTIFICATION] Called with type: " + 
            (AmneziaMod.CONFIG != null && AmneziaMod.CONFIG.notifications != null 
                ? AmneziaMod.CONFIG.notifications.type 
                : "null"));
        
        if (AmneziaMod.CONFIG == null || AmneziaMod.CONFIG.notifications == null) {
            AmneziaMod.debug("[NOTIFICATION] Config is null, returning");
            return;
        }

        ConfigLoader.NotificationConfig notif = AmneziaMod.CONFIG.notifications;
        if (notif.type == null) {
            AmneziaMod.debug("[NOTIFICATION] notif.type is null, returning");
            return;
        }

        // ✅ Проверка на none
        if (notif.type.equalsIgnoreCase("none")) {
            AmneziaMod.debug("[NOTIFICATION] Notifications disabled (type: none)");
            return;
        }

        int totalRecipes = AmneziaMod.getAllRecipes().size();

        switch (notif.type.toLowerCase()) {
            case "chat": {
                AmneziaMod.debug("[NOTIFICATION] Sending chat notification");
                if (notif.chatFormat != null && !notif.chatFormat.isEmpty()) {
                    String chatMsg = replacePlaceholders(notif.chatFormat, player, itemName, 
                                                        rarity, count, totalRecipes);
                    player.sendMessage(Text.literal(chatMsg), false);
                }
                break;
            }

            case "actionbar": {
                AmneziaMod.debug("[NOTIFICATION] Sending actionbar notification");
                if (notif.actionbarFormat != null && !notif.actionbarFormat.isEmpty()) {
                    String actionMsg = replacePlaceholders(notif.actionbarFormat, player, itemName, 
                                                        rarity, count, totalRecipes);
                    player.sendMessage(Text.literal(actionMsg), true);
                }
                break;
            }

            case "title": {
                AmneziaMod.debug("[NOTIFICATION] Sending title notification");
                if (notif.titleFormat.title != null && !notif.titleFormat.title.isEmpty()) {
                    String titleText = replacePlaceholders(notif.titleFormat.title, player, itemName, 
                                                        rarity, count, totalRecipes);
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText)));
                }
                
                if (notif.titleFormat.subtitle != null && !notif.titleFormat.subtitle.isEmpty()) {
                    String subtitleText = replacePlaceholders(notif.titleFormat.subtitle, player, itemName, 
                                                            rarity, count, totalRecipes);
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(subtitleText)));
                }
                break;
            }
            
            default: {
                AmneziaMod.debug("[NOTIFICATION] Unknown notification type: " + notif.type);
                break;
            }
        }
    }

    public static void executeScrollCommands(ServerPlayerEntity player, ConfigLoader.ScrollRarity rarity, String itemName, int count) {
        if (AmneziaMod.CONFIG == null || AmneziaMod.CONFIG.commands == null) {
            return;
        }

        ConfigLoader.CommandConfig cmdConfig = AmneziaMod.CONFIG.commands;
        if (!cmdConfig.enabled || cmdConfig.commandsByRarity == null) {
            return;
        }

        List<String> commands = cmdConfig.commandsByRarity.get(rarity.getId());
        if (commands == null || commands.isEmpty()) {
            return;
        }

        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        int totalRecipes = AmneziaMod.getAllRecipes().size();

        for (String cmd : commands) {
            if (cmd != null && !cmd.trim().isEmpty()) {
                try {
                    String finalCmd = replacePlaceholders(cmd, player, itemName, rarity, count, totalRecipes);
                    
                    // ✅ ИСПРАВЛЕНИЕ: Выполнять команду ТИХО (без вывода в чат)
                    server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(), // ← ДОБАВЛЕНО withSilent()
                        finalCmd
                    );
                    
                    AmneziaMod.debug("Executed command silently: " + finalCmd);
                } catch (Exception e) {
                    AmneziaMod.LOGGER.error("Failed to execute scroll command: " + cmd, e);
                }
            }
        }
    }
}