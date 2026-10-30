package dev.halloweengear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds and identifies every Halloween item. */
public final class Gear {

    public static final String CURSED_BLADE = "cursed_blade";
    public static final String REAPER_SCYTHE = "reaper_scythe";
    public static final String BAT_BOW = "bat_bow";

    /** Armor is dyed leather with netherite stats applied through attributes. */
    private static final Material[] ARMOR = {
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
    };
    private static final EquipmentSlotGroup[] GROUPS = {
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
            EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET
    };

    private final HalloweenGear plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final NamespacedKey idKey;

    public Gear(HalloweenGear plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    public MiniMessage mini() {
        return mini;
    }

    /** Parses MiniMessage with italics switched off (item names/lore are italic by default). */
    public Component c(String text) {
        return mini.deserialize("<!italic>" + text);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    // ------------------------------------------------------------------ identification

    /** Returns the Halloween item id stored on the stack, or null if it is not one of ours. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public List<String> itemIds() {
        List<String> ids = new ArrayList<>(List.of(CURSED_BLADE, REAPER_SCYTHE, BAT_BOW));
        for (ArmorSet set : ArmorSet.values()) {
            for (int i = 0; i < 4; i++) ids.add(set.pieceId(i));
        }
        return ids;
    }

    public List<String> setIds() {
        List<String> ids = new ArrayList<>();
        for (ArmorSet set : ArmorSet.values()) ids.add(set.id);
        return ids;
    }

    /** Creates an item by id, or returns null if the id is unknown. */
    public ItemStack create(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        switch (s) {
            case CURSED_BLADE: return cursedBlade();
            case REAPER_SCYTHE: return reaperScythe();
            case BAT_BOW: return batBow();
            default: break;
        }
        for (ArmorSet set : ArmorSet.values()) {
            for (int i = 0; i < 4; i++) {
                if (set.pieceId(i).equals(s)) return armorPiece(set, i);
            }
        }
        return null;
    }

    public List<ItemStack> createSet(ArmorSet set) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.add(armorPiece(set, i));
        return out;
    }

    // ------------------------------------------------------------------ armor

    public ItemStack armorPiece(ArmorSet set, int piece) {
        FileConfiguration cfg = plugin.getConfig();
        ItemStack item = new ItemStack(ARMOR[piece]);
        ItemMeta meta = item.getItemMeta();
        String id = set.pieceId(piece);

        meta.displayName(c(set.tag() + set.pieceNames[piece]));
        List<Component> lore = new ArrayList<>();
        for (String line : armorLore(set, piece)) lore.add(c(line));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);

        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(set.dye());
        }

        // Netherite stats (config-driven).
        List<Integer> armorVals = cfg.getIntegerList("armor.stats.armor");
        if (armorVals.size() < 4) armorVals = List.of(3, 8, 6, 3);
        double toughness = cfg.getDouble("armor.stats.toughness", 3.0);
        double knockback = cfg.getDouble("armor.stats.knockback-resistance", 0.1);
        EquipmentSlotGroup group = GROUPS[piece];

        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                key("armor_" + id), armorVals.get(piece), AttributeModifier.Operation.ADD_NUMBER, group));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                key("toughness_" + id), toughness, AttributeModifier.Operation.ADD_NUMBER, group));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                key("knockback_" + id), knockback, AttributeModifier.Operation.ADD_NUMBER, group));

        // Per-piece set bonus.
        switch (set) {
            case PUMPKIN_KING -> meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                    key("bonus_" + id), cfg.getDouble("armor.bonus.pumpkin-king-max-health", 2.0),
                    AttributeModifier.Operation.ADD_NUMBER, group));
            case WRAITH -> meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                    key("bonus_" + id), cfg.getDouble("armor.bonus.wraith-move-speed", 0.04),
                    AttributeModifier.Operation.ADD_SCALAR, group));
            case WITCH -> meta.addAttributeModifier(Attribute.MAX_ABSORPTION, new AttributeModifier(
                    key("bonus_" + id), cfg.getDouble("armor.bonus.witch-absorption", 1.0),
                    AttributeModifier.Operation.ADD_NUMBER, group));
        }

        for (Map.Entry<Enchantment, Integer> e : armorEnchants(set, piece).entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        item.setItemMeta(meta);
        return item;
    }

    private Map<Enchantment, Integer> armorEnchants(ArmorSet set, int piece) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        m.put(Enchantment.PROTECTION, 4);
        m.put(Enchantment.FIRE_PROTECTION, 4);
        m.put(Enchantment.PROJECTILE_PROTECTION, 4);
        m.put(Enchantment.BLAST_PROTECTION, 4);
        switch (piece) {
            case 0 -> {
                m.put(Enchantment.RESPIRATION, 3);
                m.put(Enchantment.AQUA_AFFINITY, 1);
            }
            case 1 -> {
                if (set == ArmorSet.PUMPKIN_KING) m.put(Enchantment.THORNS, 3);
            }
            case 2 -> m.put(Enchantment.SWIFT_SNEAK, 3);
            default -> {
                m.put(Enchantment.FEATHER_FALLING, 4);
                m.put(Enchantment.DEPTH_STRIDER, 3);
                m.put(Enchantment.SOUL_SPEED, 3);
            }
        }
        return m;
    }

    private List<String> armorLore(ArmorSet set, int piece) {
        FileConfiguration cfg = plugin.getConfig();
        String col = set.tag();
        List<String> l = new ArrayList<>();
        l.add("<dark_gray>Halloween Collection <dark_gray>✦ " + col + "Mythic");
        l.add("");
        l.add("<gray><i>\"" + set.flavor[piece] + "\"");
        l.add("");
        l.add(col + "Piece Bonus");
        switch (set) {
            case PUMPKIN_KING -> l.add("<dark_gray>▸ <white>+" + num(cfg.getDouble("armor.bonus.pumpkin-king-max-health", 2.0)) + " Max Health");
            case WRAITH -> l.add("<dark_gray>▸ <white>+" + num(cfg.getDouble("armor.bonus.wraith-move-speed", 0.04) * 100) + "% Movement Speed");
            case WITCH -> l.add("<dark_gray>▸ <white>+" + num(cfg.getDouble("armor.bonus.witch-absorption", 1.0)) + " Max Absorption");
        }
        l.add("");
        l.add(col + "Full Set Bonus <dark_gray>(4/4)");
        switch (set) {
            case PUMPKIN_KING -> {
                l.add("<gold>▸ <gray>Night Vision, Fire Resistance,");
                l.add("<gold>▸ <gray>Strength II, Resistance I");
                l.add("<gold>▸ <gray>Attackers are set ablaze");
                l.add("<gold>▸ <red>Hellfire Nova <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Burst of flame that scorches and");
                l.add("<gray>   hurls back everything nearby.");
                l.add("<dark_gray>   Cooldown: " + num(cfg.getDouble("armor.abilities.pumpkin_king.cooldown-seconds", 25)) + "s");
            }
            case WRAITH -> {
                l.add("<gold>▸ <gray>Speed II, Jump Boost I, Slow Falling");
                l.add("<gold>▸ <gray>" + num(cfg.getDouble("armor.wraith-dodge-chance", 0.20) * 100) + "% chance to phase through damage");
                l.add("<gold>▸ <red>Shadow Step <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Blink forward and vanish from sight");
                l.add("<gray>   for a few moments.");
                l.add("<dark_gray>   Cooldown: " + num(cfg.getDouble("armor.abilities.wraith.cooldown-seconds", 12)) + "s");
            }
            case WITCH -> {
                l.add("<gold>▸ <gray>Water Breathing, Haste II");
                l.add("<gold>▸ <gray>Heal " + num(cfg.getDouble("armor.witch-heal-amount", 1.5)) + " HP every 3s, immune to Poison");
                l.add("<gold>▸ <red>Hex Burst <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Curse nearby foes with Poison, Slowness");
                l.add("<gray>   and Weakness while you mend yourself.");
                l.add("<dark_gray>   Cooldown: " + num(cfg.getDouble("armor.abilities.witch.cooldown-seconds", 20)) + "s");
            }
        }
        l.add("");
        l.add("<gray>Unbreakable <dark_gray>|</dark_gray> <gray>Fireproof");
        return l;
    }

    // ------------------------------------------------------------------ weapons

    public ItemStack cursedBlade() {
        FileConfiguration cfg = plugin.getConfig();
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c("<#7B2CBF>Cursed Blade"));
        meta.lore(lines(
                "<gray>A blade that hungers for the living.",
                "",
                "<dark_purple>Hits inflict Wither and Weakness.",
                "",
                "<dark_gray>Halloween Collection"));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, CURSED_BLADE);
        meta.setItemModel(key(CURSED_BLADE));
        meleeStats(meta, "blade", cfg.getDouble("weapons.cursed-blade.attack-damage", 12.0),
                cfg.getDouble("weapons.cursed-blade.attack-speed", 1.7));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack reaperScythe() {
        FileConfiguration cfg = plugin.getConfig();
        ItemStack item = new ItemStack(Material.DIAMOND_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c("<#B91C1C>Reaper's Scythe"));
        meta.lore(lines(
                "<gray>Harvests souls from the fallen.",
                "",
                "<red>Hits have a chance to steal life.",
                "<red>Right-click: Soul Slash <gray>(" + num(cfg.getDouble("weapons.reaper-scythe.slash-cooldown-seconds", 8)) + "s cooldown)",
                "",
                "<dark_gray>Halloween Collection"));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, REAPER_SCYTHE);
        meta.setItemModel(key(REAPER_SCYTHE));
        meleeStats(meta, "scythe", cfg.getDouble("weapons.reaper-scythe.attack-damage", 9.0),
                cfg.getDouble("weapons.reaper-scythe.attack-speed", 1.6));
        meta.addEnchant(Enchantment.SHARPNESS, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack batBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c("<#6D28D9>Bat Bow"));
        meta.lore(lines(
                "<gray>Shadows take wing.",
                "",
                "<dark_purple>Arrows burst into a swarm of bats",
                "<dark_purple>that bite everything near the impact.",
                "",
                "<dark_gray>Halloween Collection"));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, BAT_BOW);
        meta.setItemModel(key(BAT_BOW));
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    /** Sets total attack damage / speed (player base is 1.0 damage and 4.0 speed). */
    private void meleeStats(ItemMeta meta, String name, double totalDamage, double totalSpeed) {
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                key(name + "_damage"), totalDamage - 1.0, AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                key(name + "_speed"), totalSpeed - 4.0, AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));
    }

    private List<Component> lines(String... raw) {
        List<Component> out = new ArrayList<>();
        for (String s : raw) out.add(c(s));
        return out;
    }

    static String num(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
