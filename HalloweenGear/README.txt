HalloweenGear for Paper 1.21.11
===============================

Armor is dyed LEATHER with netherite-level stats (20 armor, 12 toughness, 0.4 knockback
resistance) plus Protection IV. Weapons are real NETHERITE items (sword, hoe) and a bow.
Everything is unbreakable and fireproof (dropped items can't burn, explode, or despawn).
Armor looks like dyed leather unless you set armor.use-resource-pack: true in config.yml.

RESOURCE PACK
  Items use custom models from a resource pack (namespace "halloweengear"):
    assets/halloweengear/items/<item id>.json        inventory/hand model
    assets/halloweengear/equipment/<set>.json        worn armor model (pumpkin_king, wraith, witch)
  Helmets are 3D head models (pumpkin head, witch hat, wraith hood) and the Wraith chestplate
  adds a hanging cloak on the back.
  Players need the pack or the items show as missing-texture.
  No pack? Set  resource-pack.enabled: false  in config.yml and re-give the items
  (they will look like plain netherite).
  Hosting: put the pack zip in a GitHub repo and use the RAW link, not the page link:
    https://raw.githubusercontent.com/<user>/<repo>/main/<file>.zip
  then set resource-pack, resource-pack-sha1 (and optionally resource-pack-id) in server.properties.

ITEMS
  Armor sets (full 4-piece bonus):
    pumpkin_king  - Night Vision, Fire Resistance, Strength I, flame particles
    wraith        - Speed II, Slow Falling, 15% chance to dodge attacks, soul particles
    witch         - Water Breathing, Haste I, slow self-healing, witch particles

  Cursed Blade (netherite sword)
    Hits: Wither + Weakness; 15% chance the curse spreads to up to 3 nearby enemies.
    Right-click: Cursed Nova - blast around you (damage + Wither II + Slowness).
                 Costs 2 HP, 10s cooldown. Won't fire if you're too low on health.

  Reaper's Scythe (netherite hoe)
    Hits: 25% chance to steal life.
    Kills: store a soul (max 5).
    Right-click: Soul Slash - cone attack in front of you, 8s cooldown. Spends all stored
                 souls: +2 damage and +1 HP healed per soul.

  Bat Bow
    Arrows drawn at least half way burst into a bat swarm on impact that bites everything
    nearby once per second for 3 seconds and heals you while it's hitting.
    Fully drawn shots are empowered: double the bats, bigger radius, and they blind enemies.

COMMANDS (aliases: /hgear, /hw)
  /halloween list
  /halloween give <item> [player]      e.g. /halloween give cursed_blade Steve
  /halloween giveset <set> [player]    e.g. /halloween giveset witch Steve
  /halloween reload
  Item ids for armor look like pumpkin_king_helmet, wraith_chestplate, witch_boots, etc.
  To hook into a shop or crate plugin, run from console:  halloween give reaper_scythe %player%
  NOTE: items already given before an update keep their old data; give new ones after updating.

PERMISSIONS (both default to op)
  halloweengear.give
  halloweengear.admin

All numbers (damage, chances, cooldowns, durations) are in plugins/HalloweenGear/config.yml.

BUILDING THE JAR (GitHub Actions)
  Upload the CONTENTS of this folder to a repo (pom.xml and src at the top level) with the
  workflow in .github/workflows/. Open the Actions tab, open the finished run, and download
  the HalloweenGear artifact. See build.yml in this folder for the workflow text.
