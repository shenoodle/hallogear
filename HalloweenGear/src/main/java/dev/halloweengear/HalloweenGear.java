package dev.halloweengear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HalloweenGear 2.0.0 – Complete rebuild for Paper 1.21.11
 * Leather-base armor with Netherite-level protection + custom models.
 */
public final class HalloweenGear extends JavaPlugin {

    public static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};
    public static final List<String> SETS = List.of("pumpkin_king", "wraith", "witch");

    // Leather base so custom textures/models apply cleanly
    private static final Material[] ARMOR = {
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
    private static final EquipmentSlotGroup[] GROUPS = {
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
            EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Map<String, String> SET_NAME = Map.of(
            "pumpkin_king", "Pumpkin King",
            "wraith", "Wraith",
            "witch", "Witch");
    private static final Map<String, String> SET_COLOR = Map.of(
            "pumpkin_king", "<#FF7518>",
            "wraith", "<#9FB4C7>",
            "witch", "<#B05CE0>");

    private static final Map<String, String[]> PIECE_NAMES = Map.of(
            "pumpkin_king", new String[]{
                    "Pumpkin King's Crown", "Pumpkin King's Tunic",
                    "Pumpkin King's Greaves", "Pumpkin King's Boots"},
            "wraith", new String[]{
                    "Wraith Hood", "Wraith Shroud",
                    "Wraith Wrappings", "Wraith Treads"},
            "witch", new String[]{
                    "Witch's Hat", "Witch's Robes",
                    "Witch's Leggings", "Witch's Boots"});

    private final MiniMessage mm = MiniMessage.miniMessage();
    private NamespacedKey idKey;
    private GearListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        idKey = new NamespacedKey(this, "item_id");
        listener = new GearListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("HalloweenGear 2.0.0 enabled – leather-base armor + clean resource pack.");
    }

    @Override
    public void onDisable() {
        if (listener != null) listener.shutdown();
    }

    public NamespacedKey getIdKey() {
        return idKey;
    }

    public String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isHalloweenItem(ItemStack item) {
        return getId(item) != null;
    }

    public List<String> allItemIds() {
        List<String> ids = new ArrayList<>();
        for (String set : SETS) {
            for (String s : SUFFIX) ids.add(set + "_" + s);
        }
        ids.add("cursed_blade");
        ids.add("reaper_scythe");
        ids.add("bat_bow");
        return ids;
    }

    public ItemStack createItem(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "cursed_blade" -> cursedBlade();
            case "reaper_scythe" -> reaperScythe();
            case "bat_bow" -> batBow();
            default -> {
                for (String set : SETS) {
                    for (int i = 0; i < 4; i++) {
                        if (id.equals(set + "_" + SUFFIX[i])) {
                            yield armorPiece(set, i);
                        }
                    }
                }
                yield null;
            }
        };
    }

    // ------------------------------------------------------------------ armor

    /** slot: 0 helmet, 1 chestplate, 2 leggings, 3 boots */
    public ItemStack armorPiece(String set, int slot) {
        String id = set + "_" + SUFFIX[slot];
        FileConfiguration c = getConfig();
        ItemStack item = new ItemStack(ARMOR[slot]);
        ItemMeta meta = item.getItemMeta();

        decorate(meta, id,
                SET_COLOR.get(set) + PIECE_NAMES.get(set)[slot],
                armorLore(set, slot),
                armorEnchants(set, slot));

        // Explicit attributes replace defaults – re-add full Netherite-level protection
        List<Integer> pts = c.getIntegerList("armor.stats.armor");
        if (pts.size() != 4) pts = List.of(3, 8, 6, 3);
        double toughness = c.getDouble("armor.stats.toughness", 3.0);
        double knockback = c.getDouble("armor.stats.knockback-resistance", 0.1);
        EquipmentSlotGroup grp = GROUPS[slot];

        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey(this, "armor_" + id), pts.get(slot),
                AttributeModifier.Operation.ADD_NUMBER, grp));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                new NamespacedKey(this, "toughness_" + id), toughness,
                AttributeModifier.Operation.ADD_NUMBER, grp));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                new NamespacedKey(this, "knockback_" + id), knockback,
                AttributeModifier.Operation.ADD_NUMBER, grp));

        // Set-specific bonus
        NamespacedKey bonusKey = new NamespacedKey(this, "bonus_" + id);
        switch (set) {
            case "pumpkin_king" -> meta.addAttributeModifier(Attribute.MAX_HEALTH,
                    new AttributeModifier(bonusKey,
                            c.getDouble("armor.bonus.pumpkin-king-max-health", 2.0),
                            AttributeModifier.Operation.ADD_NUMBER, grp));
            case "wraith" -> meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(bonusKey,
                            c.getDouble("armor.bonus.wraith-move-speed", 0.04),
                            AttributeModifier.Operation.ADD_SCALAR, grp));
            case "witch" -> meta.addAttributeModifier(Attribute.MAX_ABSORPTION,
                    new AttributeModifier(bonusKey,
                            c.getDouble("armor.bonus.witch-absorption", 1.0),
                            AttributeModifier.Operation.ADD_NUMBER, grp));
            default -> { }
        }

        // Vanilla leather look – dyed per set (no custom models)
        // Pumpkin King = orange, Wraith = spectral blue-gray, Witch = purple
        if (meta instanceof LeatherArmorMeta leather) {
            Color dye = switch (set) {
                case "pumpkin_king" -> Color.fromRGB(255, 117, 24);   // orange
                case "wraith" -> Color.fromRGB(159, 180, 199);        // spectral gray-blue
                case "witch" -> Color.fromRGB(176, 92, 224);          // purple
                default -> Color.fromRGB(160, 101, 64);
            };
            leather.setColor(dye);
        }

        // Hide attribute clutter; show dye so the color is visible
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    private Map<Enchantment, Integer> armorEnchants(String set, int slot) {
        Map<Enchantment, Integer> e = new LinkedHashMap<>();
        e.put(Enchantment.PROTECTION, 5);
        switch (set) {
            case "pumpkin_king" -> e.put(Enchantment.FIRE_PROTECTION, 5);
            case "wraith" -> e.put(Enchantment.PROJECTILE_PROTECTION, 5);
            case "witch" -> e.put(Enchantment.BLAST_PROTECTION, 5);
            default -> { }
        }
        switch (slot) {
            case 0 -> {
                e.put(Enchantment.RESPIRATION, 3);
                e.put(Enchantment.AQUA_AFFINITY, 1);
            }
            case 3 -> e.put(Enchantment.FEATHER_FALLING, 4);
            default -> { }
        }
        e.put(Enchantment.UNBREAKING, 3);
        e.put(Enchantment.MENDING, 1);
        return e;
    }

    private List<Component> armorLore(String set, int slot) {
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Part of the " + SET_COLOR.get(set) + SET_NAME.get(set) + " <gray>set"));
        lore.add(Component.empty());
        switch (set) {
            case "pumpkin_king" -> {
                lore.add(mm.deserialize("<#FF7518>Hellfire Nova <dark_gray>(Sneak + F)"));
                lore.add(mm.deserialize("<gray>Fire retaliation & +Max Health"));
            }
            case "wraith" -> {
                lore.add(mm.deserialize("<#9FB4C7>Shadow Step <dark_gray>(Sneak + F)"));
                lore.add(mm.deserialize("<gray>Dodge chance & +Movement Speed"));
            }
            case "witch" -> {
                lore.add(mm.deserialize("<#B05CE0>Hex Burst <dark_gray>(Sneak + F)"));
                lore.add(mm.deserialize("<gray>Regen, Absorption & +Max Absorption"));
            }
            default -> { }
        }
        return lore;
    }

    // ------------------------------------------------------------------ weapons

    public ItemStack cursedBlade() {
        FileConfiguration c = getConfig();
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        String id = "cursed_blade";

        decorate(meta, id, "<#8B0000>Cursed Blade", List.of(
                mm.deserialize("<gray>Inflicts Wither and Weakness"),
                mm.deserialize("<dark_gray>A blade forged in cursed flame")
        ), Map.of(
                Enchantment.SHARPNESS, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.MENDING, 1,
                Enchantment.FIRE_ASPECT, 2
        ));

        // Override attack damage / speed
        double dmg = c.getDouble("weapons.cursed-blade.attack-damage", 12.0);
        double spd = c.getDouble("weapons.cursed-blade.attack-speed", 1.7);
        // Vanilla netherite sword: 8 damage, 1.6 speed. We set absolute values.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(this, "dmg_" + id), dmg - 1.0, // base is 1
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(this, "spd_" + id), spd - 4.0, // base is 4
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.setItemModel(new NamespacedKey(this, id)); // custom resource-pack model
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack reaperScythe() {
        FileConfiguration c = getConfig();
        ItemStack item = new ItemStack(Material.NETHERITE_HOE); // hoe base for scythe feel
        ItemMeta meta = item.getItemMeta();
        String id = "reaper_scythe";

        decorate(meta, id, "<#4B0082>Reaper's Scythe", List.of(
                mm.deserialize("<gray>25% chance to steal 2 HP on hit"),
                mm.deserialize("<#4B0082>Soul Slash <dark_gray>(Right-click)"),
                mm.deserialize("<dark_gray>Harvest the living")
        ), Map.of(
                Enchantment.SHARPNESS, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.MENDING, 1
        ));

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(this, "dmg_" + id), 9.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(this, "spd_" + id), -2.4,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        meta.setItemModel(new NamespacedKey(this, id)); // custom resource-pack model
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack batBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        String id = "bat_bow";

        decorate(meta, id, "<#2F2F2F>Bat Bow", List.of(
                mm.deserialize("<gray>Fires a swarm of bats on impact"),
                mm.deserialize("<dark_gray>The night answers the call")
        ), Map.of(
                Enchantment.POWER, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.MENDING, 1,
                Enchantment.INFINITY, 1
        ));

        meta.setItemModel(new NamespacedKey(this, id)); // custom resource-pack model
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ helpers

    private void decorate(ItemMeta meta, String id, String name, List<Component> lore,
                          Map<Enchantment, Integer> enchants) {
        meta.displayName(mm.deserialize(name));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        for (var e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("halloween")) return false;
        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<gold>/halloween <give|giveset|list|reload>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(mm.deserialize("<gold>HalloweenGear items:"));
                for (String id : allItemIds()) {
                    sender.sendMessage(mm.deserialize("<gray>- <white>" + id));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("halloweengear.admin")) {
                    sender.sendMessage(mm.deserialize("<red>No permission."));
                    return true;
                }
                reloadConfig();
                sender.sendMessage(mm.deserialize("<green>Config reloaded. Re-give items for stat changes."));
            }
            case "give" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    sender.sendMessage(mm.deserialize("<red>No permission."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /halloween give <item_id> [player]"));
                    return true;
                }
                ItemStack stack = createItem(args[1]);
                if (stack == null) {
                    sender.sendMessage(mm.deserialize("<red>Unknown item: " + args[1]));
                    return true;
                }
                Player target = args.length >= 3
                        ? getServer().getPlayer(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(mm.deserialize("<red>Player not found."));
                    return true;
                }
                target.getInventory().addItem(stack);
                sender.sendMessage(mm.deserialize("<green>Gave " + args[1] + " to " + target.getName()));
            }
            case "giveset" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    sender.sendMessage(mm.deserialize("<red>No permission."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /halloween giveset <pumpkin_king|wraith|witch> [player]"));
                    return true;
                }
                String set = args[1].toLowerCase(Locale.ROOT);
                if (!SETS.contains(set)) {
                    sender.sendMessage(mm.deserialize("<red>Unknown set. Use pumpkin_king, wraith or witch."));
                    return true;
                }
                Player target = args.length >= 3
                        ? getServer().getPlayer(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(mm.deserialize("<red>Player not found."));
                    return true;
                }
                for (int i = 0; i < 4; i++) {
                    target.getInventory().addItem(armorPiece(set, i));
                }
                sender.sendMessage(mm.deserialize("<green>Gave full " + set + " set to " + target.getName()));
            }
            default -> sender.sendMessage(mm.deserialize("<gold>/halloween <give|giveset|list|reload>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("halloween")) return List.of();
        if (args.length == 1) {
            return List.of("give", "giveset", "list", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                return allItemIds().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (args[0].equalsIgnoreCase("giveset")) {
                return SETS.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
        }
        return List.of();
    }
}
