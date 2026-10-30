# HalloweenGear 1.2.0

Paper 1.21.11 plugin: three Halloween armor sets and three weapons.

* **Armor** is dyed **leather** with **netherite stats** (armor 3/8/6/3, toughness 3, knockback resistance 0.1 per piece), unbreakable and fireproof.
  * Pumpkin King (orange) - +Max Health, Night Vision / Fire Resistance / Strength II / Resistance I, burning thorns, **Hellfire Nova**
  * Wraith (grey-blue) - +Movement Speed, Speed II / Jump Boost I / Slow Falling, dodge chance, **Shadow Step**
  * Witch (purple) - +Max Absorption, Water Breathing / Haste II, regen + Poison immunity, **Hex Burst**
  * Full-set abilities: sneak + press the Swap Hands key (F).
* **Weapons:** Cursed Blade (Wither + Weakness), Reaper's Scythe (lifesteal + right-click Soul Slash), Bat Bow (arrows burst into biting bats).

Commands: `/halloween give <item> [player]`, `/halloween giveset <set> [player]`, `/halloween list`, `/halloween reload` (aliases `/hgear`, `/hw`).

Weapon item models are `halloweengear:cursed_blade`, `halloweengear:reaper_scythe`, `halloweengear:bat_bow`
(see the resource pack). Armor uses the normal dyed-leather look, so no armor textures are needed.

Build: `mvn clean package` -> `target/HalloweenGear.jar` (the GitHub Action does this on every push).
Gear is baked at creation time - re-give items after changing `config.yml`.
