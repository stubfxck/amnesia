package com.amnezia;

import com.amnezia.ConfigLoader.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomScrollFunction implements LootFunction {

    public static final MapCodec<RandomScrollFunction> CODEC =
            MapCodec.unit(RandomScrollFunction::new);
    
    public static final LootFunctionType<RandomScrollFunction> TYPE =
            new LootFunctionType<>(CODEC);

    private static final Random RANDOM = new Random();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements LootFunction.Builder {
        @Override
        public LootFunction build() {
            return new RandomScrollFunction();
        }
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        if (stack.getItem() != AmneziaMod.SCROLL_ITEM) {
            return stack;
        }

        MinecraftServer server = context.getWorld().getServer();
        if (server == null) {
            AmneziaMod.debug("[SCROLL GEN] Server is null, skipping");
            return ItemStack.EMPTY; // ✅ Возвращаем пустой стек вместо сломанного свитка
        }

        // ✅ КРИТИЧЕСКАЯ ПРОВЕРКА: Пул не должен быть пустым
        if (AmneziaMod.SCROLL_POOL == null || AmneziaMod.SCROLL_POOL.isEmpty()) {
            AmneziaMod.LOGGER.error("❌ SCROLL_POOL is EMPTY! Cannot generate scroll. Check items.json");
            return ItemStack.EMPTY;
        }

        ScrollRarity rarity = getRandomRarity();
        Object scrollData = getRandomScrollDataForRarity(rarity);

        if (scrollData != null) {
            createScroll(stack, scrollData, rarity);
            return stack;
        } else {
            // ✅ Нет данных для этой редкости - возвращаем пустой стек
            AmneziaMod.debug("[SCROLL GEN] No data for rarity: " + rarity.getId() + ", skipping");
            return ItemStack.EMPTY;
        }
    }

    private void createScroll(ItemStack stack, Object scrollData, ScrollRarity rarity) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Rarity", rarity.getId());

        if (scrollData instanceof GroupConfig) {
            GroupConfig group = (GroupConfig) scrollData;
            nbt.putString("Type", "group");
            nbt.putString("GroupName", group.name);

            StringBuilder recipesStr = new StringBuilder();
            List<String> recipes = group.getRecipesWithPrefix();

            for (int i = 0; i < recipes.size(); i++) {
                if (i > 0) recipesStr.append(",");
                recipesStr.append(recipes.get(i));
            }

            nbt.putString("Recipes", recipesStr.toString());
            AmneziaMod.debug("[SCROLL GEN] Created group scroll: " + group.name + " (" + recipes.size() + " recipes)");

        } else if (scrollData instanceof ItemConfig) {
            ItemConfig item = (ItemConfig) scrollData;
            nbt.putString("Type", "solo");
            nbt.putString("ItemId", item.id);
            nbt.putString("ItemName", Text.translatable("item." + item.id.replace(":", ".")).getString());
            AmneziaMod.debug("[SCROLL GEN] Created solo scroll: " + Text.translatable("item." + item.id.replace(":", ".")).getString());
        }

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private Object getRandomScrollDataForRarity(ScrollRarity rarity) {
        List<Object> available = new ArrayList<>();

        for (Object data : AmneziaMod.SCROLL_POOL) {
            String dataRarity = null;

            if (data instanceof GroupConfig) {
                dataRarity = ((GroupConfig) data).rarity;
            } else if (data instanceof ItemConfig) {
                dataRarity = ((ItemConfig) data).rarity;
            }

            if (dataRarity != null && dataRarity.equalsIgnoreCase(rarity.getId())) {
                available.add(data);
            }
        }

        if (available.isEmpty()) {
            return null;
        }

        // Перемешиваем список для большей случайности
        Collections.shuffle(available, RANDOM);
        return available.get(0);
    }

    private ScrollRarity getRandomRarity() {
        double roll = RANDOM.nextDouble();

        if (AmneziaMod.CONFIG == null || AmneziaMod.CONFIG.spawnChances == null) {
            return ScrollRarity.COMMON;
        }

        double ancient   = AmneziaMod.CONFIG.spawnChances.getOrDefault("ancient",   0.005);
        double legendary = AmneziaMod.CONFIG.spawnChances.getOrDefault("legendary", 0.02);
        double mythical  = AmneziaMod.CONFIG.spawnChances.getOrDefault("mythical",  0.05);
        double ultraRare = AmneziaMod.CONFIG.spawnChances.getOrDefault("ultraRare", 0.10);
        double rare      = AmneziaMod.CONFIG.spawnChances.getOrDefault("rare",      0.20);

        if (roll < ancient) return ScrollRarity.ANCIENT;
        roll -= ancient;

        if (roll < legendary) return ScrollRarity.LEGENDARY;
        roll -= legendary;

        if (roll < mythical) return ScrollRarity.MYTHICAL;
        roll -= mythical;

        if (roll < ultraRare) return ScrollRarity.ULTRARARE;
        roll -= ultraRare;

        if (roll < rare) return ScrollRarity.RARE;

        return ScrollRarity.COMMON;
    }

    @Override
    public LootFunctionType<RandomScrollFunction> getType() {
        return TYPE;
    }
}