# HalloweenGear 2.0.1 – Simple Leather Edition

Paper 1.21.11 plugin. **No custom armor models required.**

## What you get
- **Vanilla leather armor** dyed per set (orange / spectral gray / purple)
- **Netherite-level protection** (3/8/6/3 armor, 3 toughness, 0.1 KB resist per piece)
- Full set abilities (Sneak + F)
- Cursed Blade, Reaper’s Scythe, Bat Bow (vanilla models, custom effects)

## Sets
| Set | Color | Bonus | Ability |
|-----|-------|-------|---------|
| Pumpkin King | Orange | +2 Max HP / piece | Hellfire Nova |
| Wraith | Blue-gray | +4% Speed / piece | Shadow Step |
| Witch | Purple | +1 Absorption / piece | Hex Burst |

## Build & install
```bash
mvn clean package
# copy target/HalloweenGear.jar → plugins/
```

```
/halloween giveset pumpkin_king
/halloween giveset wraith
/halloween giveset witch
/halloween give cursed_blade
/halloween give reaper_scythe
/halloween give bat_bow
```

Resource pack is **optional**. Armor and weapons use vanilla models. You can keep BlissGems alone.
