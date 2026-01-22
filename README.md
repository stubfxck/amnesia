# 🎮 Amnezia - Recipe Discovery Mod

> **A complete reimagining of the Minecraft crafting system**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-orange.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-CC0--1.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/Status-Alpha-yellow.svg)](https://github.com/stubfxck/amnesia/releases)

---

## 📖 Concept

Players start with **only basic recipes** (planks, sticks, wooden tools). All other recipes must be **discovered as scrolls** of varying rarity in structure chests throughout the world.

### 🎯 Features

- ✨ **6 rarity tiers** for scrolls (Common → Ancient)
- 📦 **782 unique recipes** with configurable rarity
- 🏛️ **Hierarchical group system** (tools, armor, blocks)
- 🌍 **60+ structures** with customizable loot
- 🔄 **Recipe loss on death** (optional)
- 🌐 **Full localization** (EN/RU)
- ⚙️ **Flexible configuration** via JSON files

---

## 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the mod from [Releases](https://github.com/stubfxck/amnesia/releases)
3. Place the `.jar` file in your `mods/` folder
4. Launch the game and enjoy!

---

## 🎨 Rarity System

| Rarity | Color | Spawn Chance | Examples |
|--------|-------|--------------|----------|
| **Ancient** | Dark Aqua | 0.5% | Netherite Ingot, Beacon |
| **Legendary** | Gold | 2% | Enchanting Table, Anvil |
| **Mythical** | Red | 5% | Diamond Tools, Ender Chest |
| **Ultra Rare** | Aqua | 10% | Iron Armor, Crossbow |
| **Rare** | Green | 20% | Iron Tools, Compass |
| **Common** | Gray | 40% | Stone Tools, Beds |

---

## 📂 Configuration Structure

```
config/amnezia/
├── config.json          # Main mod settings
├── items.json           # Recipe rarity distribution
└── structures.json      # Loot table settings (auto-generated)
```

### ⚙️ config.json

```json
{
  "language": "en_us",
  "scrollSettings": {
    "scrollsDisappear": true,
    "loseRecipesOnDeath": true,
    "debugMode": false
  },
  "spawnChances": {
    "ancient": 0.005,
    "legendary": 0.02,
    "mythical": 0.05,
    "ultraRare": 0.10,
    "rare": 0.20,
    "common": 0.40
  },
  "notifications": {
    "type": "chat",
    "chatFormat": "§6[§e<item_name>§6] - <rarity_color><rarity>"
  },
  "commands": {
    "enabled": false,
    "commandsByRarity": {
      "ancient": [
        "experience add <player> 100 points",
        "give <player> minecraft:diamond 1"
      ]
    }
  }
}
```

**Notification types:** `"none"`, `"chat"`, `"actionbar"`, `"title"`

### 📋 items.json

```json
{
  "defaultItems": {
    "items": [
      {"id": "minecraft:oak_planks", "rarity": "common"},
      {"id": "minecraft:stick", "rarity": "common"}
    ]
  },
  "soloItems": {
    "items": [
      {"id": "minecraft:diamond_pickaxe", "rarity": "mythical"}
    ]
  },
  "groupedItems": {
    "groups": [
      {
        "id": "iron_tools",
        "name": "Iron Tools",
        "rarity": "rare",
        "noExit": false,
        "recipes": ["minecraft:iron_pickaxe", "minecraft:iron_axe"],
        "requirements": ["tools_parent"]
      }
    ]
  }
}
```

---

## 🎮 Game Mechanics

### Starting Recipes

Players know from the beginning:

- 🪵 All types of planks
- 🪓 Wooden tools
- ⛏️ Stone tools
- 🔦 Torch

### Group System

Recipes are organized into **hierarchical groups**:

```
tools_parent (ancient scroll)
├── stone_tools (common)
├── iron_tools (rare)
├── diamond_tools (mythical)
└── golden_tools (rare)
```

Learning a **parent group** unlocks access to child groups!

### Notifications

3 notification types when learning:

- 💬 **Chat** - message in chat
- 📊 **Actionbar** - text above hotbar
- 🎯 **Title** - large notification in center of screen

**Placeholders:** `<player>`, `<item_name>`, `<rarity>`, `<rarity_color>`, `<recipes_learned>`, `<total_recipes>`

---

## 🔧 Commands

### Player Commands

```
/amnezia reset         - Reset all recipes
/amnezia init          - Initialize recipes
/amnezia status        - Show number of known recipes
```

### Admin Commands

```
/amnezia reload        - Reload configs
/amnezia debug <true|false> - Toggle debug mode

/amnezia give <rarity> random
/amnezia give <rarity> <scroll_name>
```

**Examples:**
```
/amnezia give ancient random
/amnezia give mythical diamond_tools
```

---

## 🌍 Mod Compatibility

The mod **automatically detects** recipes from other mods:

```json
"modCompatibility": {
  "hideModdedRecipes": true,
  "generateScrollsForModdedRecipes": true,
  "moddedRecipeDefaultRarity": "rare"
}
```

⚠️ **Current version:** This feature is partially implemented, full integration is in development.

---

## 🐛 Known Issues

- [ ] Anvil/Enchanting/Brewing work without learning recipes
- [ ] Ancient compendium scrolls (Blacksmith's, Alchemist's) not implemented
- [ ] Brewing doesn't require recipes
- [ ] No intermediate ingredient checking

See full list in [Issues](https://github.com/stubfxck/amnesia/issues)

---

## 📅 Development Roadmap

### v1.0 (Beta)
- ✅ Basic scroll system
- ✅ 782 recipes
- ✅ Rarity system
- ✅ Configuration

### v2.0 (Full Release)
- [ ] **Ancient Compendium Scrolls**
  - Blacksmith's Compendium (all anvil operations)
  - Alchemist's Manual (all potions)
  - Enchanter's Tome (all enchantments)
- [ ] **Operation blocking** without recipes
  - Anvil (repair, combining)
  - Enchanting Table
  - Brewing Stand
- [ ] **Brewing system**
- [ ] **Auto-generation for modded recipes**

### v3.0 (Extended)
- [ ] Intermediate recipe checking
- [ ] API for other mods

---

## ⚠️ Disclaimer

> **Important:** The code was generated using AI (Claude 3.5 Sonnet). The author only understands Java syntax and structure but **did not write the project manually**. This mod was created to quickly implement an idea for a personal modpack. Code quality may not meet production standards.

**If you find a bug** - report it in [Issues](https://github.com/stubfxck/amnesia/issues). Pull requests are welcome!

---
