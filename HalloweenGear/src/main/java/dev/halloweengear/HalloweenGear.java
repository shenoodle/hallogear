package dev.halloweengear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private static final Material[] NETHERITE = {
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
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

    private ItemStack armorPiece(String set, int i) {
        ItemStack item = new ItemStack(NETHERITE[i]);

        String color = SET_COLOR.get(set);
        List<String> lore = List.of(
                "",
                "<gray>Set: " + color + SET_NAME.get(set),
                "<gray>Wear all 4 pieces for:",
                "<yellow>" + SET_BONUS.get(set),
                "",
                "<gray>Netherite <dark_gray>|</dark_gray> Unbreakable <dark_gray>|</dark_gray> Fireproof",
                "",
                "<dark_gray>Halloween Collection");

        Map<Enchantment, Integer> ench = new LinkedHashMap<>();
        ench.put(Enchantment.PROTECTION, 4);
        ItemStack made = finish(item, set + "_" + SUFFIX[i], color + PIECE_NAMES.get(set)[i], lore, ench);

        if (usePack()) {
            // Custom worn-armor model from the resource pack (assets/halloweengear/equipment/<set>.json)
            ItemMeta meta = made.getItemMeta();
            EquippableComponent eq = meta.getEquippable();
            eq.setSlot(SLOTS[i]);
            if (i == 0) {
                // Helmets (pumpkin head, witch hat, wraith hood) are 3D models shown on the head
                // from the item model, so they have no worn-armor layer.
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
        return finish(new ItemStack(Material.NETHERITE_SWORD), "cursed_blade", "<#7B2CBF>Cursed Blade", lore, ench);
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
        return finish(new ItemStack(Material.NETHERITE_HOE), "reaper_scythe", "<#B91C1C>Reaper's Scythe", lore, ench);
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
        return finish(new ItemStack(Material.BOW), "bat_bow", "<#6D28D9>Bat Bow", lore, ench);
    }

    private ItemStack finish(ItemStack item, String id, String name, List<String> lore, Map<Enchantment, Integer> ench) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<!italic>" + name));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) loreComponents.add(mm("<!italic>" + line));
        meta.lore(loreComponents);
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (usePack()) {
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
