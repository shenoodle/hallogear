# HalloweenGear 2.0.0 – Complete Rebuild

Clean rebuild of the Halloween equipment plugin + resource pack for **Paper 1.21.11** (Java 21).

## What was fixed

- **Missing-texture (purple/black squares)** – Completely new resource pack with correct `items/`, `models/item/`, `equipment/` definitions and all texture paths resolving.
- Armor now uses **leather** bases so custom models and textures apply cleanly, while protection matches **Netherite** (3/8/6/3 armor, 3.0 toughness, 0.1 knockback resistance per piece).
- Attribute modifiers use stable namespaced keys – no duplication on re-equip / reload / reconnect.
- Modern 1.21.11 item components (`item_model`, `EquippableComponent`).
- Pack format 75 (compatible with 1.21.11–26.x range declared).

## Armor Sets

| Set | Helmet / Chest / Legs / Boots | Bonus per piece | Full-set ability (Sneak + F) |
|-----|-------------------------------|-----------------|------------------------------|
| **Pumpkin King** | Crown, Tunic, Greaves, Boots | +2 Max Health | **Hellfire Nova** – 6-block radius, 8 damage, 6 s fire, 4 s fire retaliation |
| **Wraith** | Hood, Shroud, Wrappings, Treads | +4 % Movement Speed | **Shadow Step** – 9-block teleport + 3 s invisibility |
| **Witch** | Hat, Robes, Leggings, Boots | +1 Max Absorption | **Hex Burst** – 7-block radius curse, self-regen + absorption |

Passive:
- Wraith: 20 % chance to completely dodge any incoming damage.
- Witch: regenerates 1.5 HP every 3 seconds while full set is worn.
- Pumpkin King: attackers who melee you while Hellfire Nova retaliation is active are set on fire.

## Weapons

- **Cursed Blade** (Netherite sword) – 12 damage, 1.7 attack speed, applies Wither + Weakness.
- **Reaper’s Scythe** (Netherite hoe base) – 25 % lifesteal (2 HP), **Soul Slash** right-click (8 dmg, 4.5 radius, heal up to 6 HP).
- **Bat Bow** – on impact spawns 6 bats that pulse 3 damage/s in 3.5 radius for 3 seconds.

## Installation

1. Build the plugin:
   ```bash
   cd HalloweenGear
   mvn clean package
   ```
   Copy `target/HalloweenGear.jar` into your server’s `plugins/` folder.

2. Install the resource pack:
   - Place `HalloweenGear-ResourcePack.zip` in the server’s resource-pack folder **or** host it and set `resource-pack=` in `server.properties` / Paper config.
   - Players must accept the pack (or force it).

3. Restart / `/halloween reload`, then give items:
   ```
   /halloween giveset pumpkin_king
   /halloween giveset wraith
   /halloween giveset witch
   /halloween give cursed_blade
   /halloween give reaper_scythe
   /halloween give bat_bow
   /halloween list
   ```

## Project layout

```
HalloweenGear/
├── pom.xml
├── README.md
├── HalloweenGear-ResourcePack.zip   ← ready-to-use pack
├── resourcepack/                    ← source of the pack
│   ├── pack.mcmeta
│   └── assets/halloweengear/
│       ├── equipment/
│       ├── items/
│       ├── models/item/
│       └── textures/
└── src/main/
    ├── java/dev/halloweengear/
    │   ├── HalloweenGear.java
    │   └── GearListener.java
    └── resources/
        ├── plugin.yml
        └── config.yml
```

## Config

All cooldowns, damage values, radii, dodge chance, etc. are in `config.yml`.  
**Important:** attribute values and lore are baked into the item when it is created. After changing stats, re-give the items.

## Compatibility

- Paper 1.21.11 (tested against paper-api 1.21.11-R0.1-SNAPSHOT)
- Java 21
- Resource-pack format 75 (min 75, max 88)

Enjoy the Halloween gear!
