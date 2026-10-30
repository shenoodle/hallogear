package dev.halloweengear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HalloweenGear extends JavaPlugin {

    public static final List<String> SETS = List.of("pumpkin_king", "wraith", "witch");
    private static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};
    private static final Material[] LEATHER = {
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
    private static final EquipmentSlotGroup[] SLOT_GROUPS = {
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
    private static final Map<String, String> SET_BONUS = Map.of(
            "pumpkin_king", "Night Vision, Fire Resistance, Strength I",
            "wraith", "Speed II, Slow Falling, chance to dodge attacks",
            "witch", "Water Breathing, Haste I, slow self-healing");
    private static final Map<String, int[]> PIECE_COLORS = Map.of(
            "pumpkin_king", new int[]{0xFF7518, 0xFF7518, 0x1C1C1C, 0x1C1C1C},
            "wraith", new int[]{0xB8C7D6, 0x4A5666, 0x4A5666, 0xB8C7D6},
            "witch", new int[]{0x2E8B3A, 0x6A2C91, 0x6A2C91, 0x2E8B3A});
    private static final Map<String, String[]> PIECE_NAMES = Map.of(
            "pumpkin_king", new String[]{"Pumpkin King's Crown", "Pumpkin King's Tunic", "Pumpkin King's Greaves", "Pumpkin King's Boots"},
            "wraith", new String[]{"Wraith Hood", "Wraith Shroud", "Wraith Wrappings", "Wraith Treads"},
            "witch", new String[]{"Witch's Hat", "Witch's Robe", "Witch's Leggings", "Witch's Boots"});

    private final MiniMessage mini = MiniMessage.miniMessage();
    private NamespacedKey idKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        idKey = new NamespacedKey(this, "item_id");
        GearListener listener = new GearListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.startTasks();
        getLogger().info("HalloweenGear enabled.");
    }

    // ---------- helpers used by the listener ----------

    public Component mm(String text) {
        return mini.deserialize(text);
    }

    public String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public List<String> allItemIds() {
        List<String> ids = new ArrayList<>();
        for (String set : SETS) {
            for (String suffix : SUFFIX) ids.add(set + "_" + suffix);
        }
        ids.add("cursed_blade");
        ids.add("reaper_scythe");
        ids.add("bat_bow");
        return ids;
    }

    // ---------- item creation ----------

    public ItemStack createItem(String rawId) {
        if (rawId == null) return null;
        String id = rawId.toLowerCase(Locale.ROOT);
        for (String set : SETS) {
            for (int i = 0; i < 4; i++) {
                if (id.equals(set + "_" + SUFFIX[i])) return armorPiece(set, i);
            }
        }
        return switch (id) {
            case "cursed_blade" -> cursedBlade();
            case "reaper_scythe" -> reaperScythe();
            case "bat_bow" -> batBow();
            default -> null;
        };
    }

    private boolean usePack() {
        return getConfig().getBoolean("resource-pack.enabled", true);
    }

    /** Armor only uses the pack's models if both switches are on (off by default). */
    private boolean armorPack() {
        return usePack() && getConfig().getBoolean("armor.use-resource-pack", false);
    }

    private ItemStack armorPiece(String set, int i) {
        ItemStack item = new ItemStack(LEATHER[i]);
        LeatherArmorMeta lm = (LeatherArmorMeta) item.getItemMeta();
        lm.setColor(Color.fromRGB(PIECE_COLORS.get(set)[i]));

        // Netherite-level stats (configurable). Custom modifiers replace the leather defaults.
        List<Integer> armorPoints = getConfig().getIntegerList("armor.stats.armor");
        if (armorPoints.size() < 4) armorPoints = List.of(3, 8, 6, 3);
        double toughness = getConfig().getDouble("armor.stats.toughness", 3.0);
        double knockback = getConfig().getDouble("armor.stats.knockback-resistance", 0.1);
        EquipmentSlotGroup slot = SLOT_GROUPS[i];
        lm.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey(this, "armor_" + SUFFIX[i]), armorPoints.get(i),
                AttributeModifier.Operation.ADD_NUMBER, slot));
        lm.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                new NamespacedKey(this, "toughness_" + SUFFIX[i]), toughness,
                AttributeModifier.Operation.ADD_NUMBER, slot));
        lm.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                new NamespacedKey(this, "knockback_" + SUFFIX[i]), knockback,
                AttributeModifier.Operation.ADD_NUMBER, slot));
        item.setItemMeta(lm);

        String color = SET_COLOR.get(set);
        List<String> lore = List.of(
                "",
                "<gray>Set: " + color + SET_NAME.get(set),
                "<gray>Wear all 4 pieces for:",
                "<yellow>" + SET_BONUS.get(set),
                "",
                "<gray>Netherite-level protection <dark_gray>|</dark_gray> Unbreakable <dark_gray>|</dark_gray> Fireproof",
                "",
                "<dark_gray>Halloween Collection");

        Map<Enchantment, Integer> ench = new LinkedHashMap<>();
        ench.put(Enchantment.PROTECTION, 4);
        ItemStack made = finish(item, set + "_" + SUFFIX[i], color + PIECE_NAMES.get(set)[i], lore, ench, armorPack());

        if (armorPack()) {
            // Optional: custom worn-armor model from the resource pack
            ItemMeta meta = made.getItemMeta();
            EquippableComponent eq = meta.getEquippable();
            eq.setSlot(SLOTS[i]);
            if (i == 0) {
                // Helmets are 3D head models shown from the item model, so no worn-armor layer
                eq.setModel(null);
            } else {
                NamespacedKey model = NamespacedKey.fromString("halloweengear:" + set);
                if (model != null) eq.setModel(model);
            }
            meta.setEquippable(eq);
            made.setItemMeta(meta);
        }
        return made;
    }

    private ItemStack cursedBlade() {
        List<String> lore = List.of(
                "",
                "<gray>A blade that hungers for the living.",
                "<dark_purple>Hits inflict Wither and Weakness,",
                "<dark_purple>and the curse can spread to nearby foes.",
                "<red>Right-click: Cursed Nova <gray>(costs a little health)",
                "",
                "<gray>Netherite <dark_gray>|</dark_gray> Unbreakable <dark_gray>|</dark_gray> Fireproof",
                "<dark_gray>Halloween Collection");
        Map<Enchantment, Integer> ench = new LinkedHashMap<>();
        ench.put(Enchantment.SHARPNESS, 4);
        ench.put(Enchantment.LOOTING, 3);
        return finish(new ItemStack(Material.NETHERITE_SWORD), "cursed_blade", "<#7B2CBF>Cursed Blade", lore, ench, usePack());
    }

    private ItemStack reaperScythe() {
        List<String> lore = List.of(
                "",
                "<gray>Harvests souls from the fallen.",
                "<red>Hits can steal life.",
                "<red>Kills store souls (up to 5).",
                "<red>Right-click: Soul Slash <gray>- spends all souls",
                "<gray>for extra damage and healing.",
                "",
                "<gray>Netherite <dark_gray>|</dark_gray> Unbreakable <dark_gray>|</dark_gray> Fireproof",
                "<dark_gray>Halloween Collection");
        Map<Enchantment, Integer> ench = new LinkedHashMap<>();
        ench.put(Enchantment.SHARPNESS, 5);
        ench.put(Enchantment.LOOTING, 3);
        return finish(new ItemStack(Material.NETHERITE_HOE), "reaper_scythe", "<#B91C1C>Reaper's Scythe", lore, ench, usePack());
    }

    private ItemStack batBow() {
        List<String> lore = List.of(
                "",
                "<gray>Shadows take wing.",
                "<dark_purple>Drawn arrows burst into a swarm of bats",
                "<dark_purple>that bite everything near the impact",
                "<dark_purple>and heal you.",
                "<red>Full draw: bigger swarm that also blinds.",
                "",
                "<gray>Unbreakable <dark_gray>|</dark_gray> Fireproof",
                "<dark_gray>Halloween Collection");
        Map<Enchantment, Integer> ench = new LinkedHashMap<>();
        ench.put(Enchantment.POWER, 4);
        return finish(new ItemStack(Material.BOW), "bat_bow", "<#6D28D9>Bat Bow", lore, ench, usePack());
    }

    private ItemStack finish(ItemStack item, String id, String name, List<String> lore, Map<Enchantment, Integer> ench, boolean withModel) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<!italic>" + name));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) loreComponents.add(mm("<!italic>" + line));
        meta.lore(loreComponents);
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (withModel) {
            // Custom inventory/hand model from the resource pack (assets/halloweengear/items/<id>.json)
            NamespacedKey model = NamespacedKey.fromString("halloweengear:" + id);
            if (model != null) meta.setItemModel(model);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ---------- commands ----------

    private void say(CommandSender to, String text) {
        to.sendMessage(mm(text));
    }

    private void giveTo(Player target, String id) {
        ItemStack item = createItem(id);
        if (item == null) return;
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            say(sender, "<gold>HalloweenGear <gray>- /" + label + " <give|giveset|list|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                say(sender, "<gold>Items: <gray>" + String.join(", ", allItemIds()));
                say(sender, "<gold>Sets: <gray>" + String.join(", ", SETS));
            }
            case "reload" -> {
                if (!sender.hasPermission("halloweengear.admin")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                reloadConfig();
                say(sender, "<green>HalloweenGear config reloaded.");
            }
            case "give" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    say(sender, "<red>Usage: /" + label + " give <item> [player]");
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) return true;
                if (createItem(args[1]) == null) {
                    say(sender, "<red>Unknown item. Use /" + label + " list");
                    return true;
                }
                giveTo(target, args[1]);
                say(sender, "<green>Gave " + args[1].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
            }
            case "giveset" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    say(sender, "<red>Usage: /" + label + " giveset <set> [player]");
                    return true;
                }
                String set = args[1].toLowerCase(Locale.ROOT);
                if (!SETS.contains(set)) {
                    say(sender, "<red>Unknown set. Sets: " + String.join(", ", SETS));
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) return true;
                for (String suffix : SUFFIX) giveTo(target, set + "_" + suffix);
                say(sender, "<green>Gave the " + SET_NAME.get(set) + " set to " + target.getName() + ".");
            }
            default -> say(sender, "<red>Unknown subcommand.");
        }
        return true;
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) say(sender, "<red>Player not found: " + args[index]);
            return p;
        }
        if (sender instanceof Player self) return self;
        say(sender, "<red>Specify a player.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("give", "giveset", "list", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return filter(allItemIds(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("giveset")) return filter(SETS, args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveset"))) return null;
        return List.of();
    }

    private List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(t)) out.add(o);
        }
        return out;
    }
}
