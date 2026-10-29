package dev.halloweengear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HalloweenGear extends JavaPlugin {

    public static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};
    public static final List<String> SETS = List.of("pumpkin_king", "wraith", "witch");

    private static final Material[] ARMOR = {
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
    private static final EquipmentSlotGroup[] GROUPS = {
            EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST,
            EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Map<String, String> SET_NAME = Map.of(
            "pumpkin_king", "Pumpkin King", "wraith", "Wraith", "witch", "Witch");
    private static final Map<String, String> SET_COLOR = Map.of(
            "pumpkin_king", "<#FF7518>", "wraith", "<#9FB4C7>", "witch", "<#B05CE0>");

    private static final Map<String, String[]> PIECE_NAMES = Map.of(
            "pumpkin_king", new String[]{"Pumpkin King's Crown", "Pumpkin King's Tunic",
                    "Pumpkin King's Greaves", "Pumpkin King's Boots"},
            "wraith", new String[]{"Wraith Hood", "Wraith Shroud", "Wraith Wrappings", "Wraith Treads"},
            "witch", new String[]{"Witch's Hat", "Witch's Robe", "Witch's Leggings", "Witch's Boots"});

    private static final Map<String, String[]> FLAVOR = Map.of(
            "pumpkin_king", new String[]{
                    "Wear the grin. Rule the harvest.",
                    "Hollowed out by a thousand autumns.",
                    "Stitched from scarecrow and cinder.",
                    "Where it walks, the fields burn orange."},
            "wraith", new String[]{
                    "Faces forget what the hood remembers.",
                    "Woven from the last breath of the dying.",
                    "They bind nothing, yet nothing binds you.",
                    "The floor never quite notices."},
            "witch", new String[]{
                    "Brims with secrets and bad ideas.",
                    "Stained by every brew gone wrong.",
                    "Pockets full of newt and nerve.",
                    "Curled toes, curling spells."});

    private final MiniMessage mini = MiniMessage.miniMessage();
    private NamespacedKey idKey;
    private GearListener listener;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        idKey = new NamespacedKey(this, "item_id");
        listener = new GearListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        listener.startTasks();
        if (getCommand("halloween") != null) {
            getCommand("halloween").setExecutor(this);
            getCommand("halloween").setTabCompleter(this);
        }
        getLogger().info("HalloweenGear enabled.");
    }

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
            for (String s : SUFFIX) ids.add(set + "_" + s);
        }
        ids.add("cursed_blade");
        ids.add("reaper_scythe");
        ids.add("bat_bow");
        return ids;
    }

    public ItemStack createItem(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        switch (id) {
            case "cursed_blade": return cursedBlade();
            case "reaper_scythe": return reaperScythe();
            case "bat_bow": return batBow();
            default: break;
        }
        for (String set : SETS) {
            for (int i = 0; i < 4; i++) {
                if (id.equals(set + "_" + SUFFIX[i])) return armorPiece(set, i);
            }
        }
        return null;
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

        // Setting ANY attribute modifier replaces the item's default ones, so re-add netherite's base stats.
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

        // Set-specific extra attribute.
        NamespacedKey bonusKey = new NamespacedKey(this, "bonus_" + id);
        switch (set) {
            case "pumpkin_king" -> meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                    bonusKey, c.getDouble("armor.bonus.pumpkin-king-max-health", 2.0),
                    AttributeModifier.Operation.ADD_NUMBER, grp));
            case "wraith" -> meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                    bonusKey, c.getDouble("armor.bonus.wraith-move-speed", 0.04),
                    AttributeModifier.Operation.ADD_SCALAR, grp));
            case "witch" -> meta.addAttributeModifier(Attribute.MAX_ABSORPTION, new AttributeModifier(
                    bonusKey, c.getDouble("armor.bonus.witch-absorption", 1.0),
                    AttributeModifier.Operation.ADD_NUMBER, grp));
            default -> { }
        }

        // Resource pack hooks: inventory icon + worn-armor texture.
        meta.setItemModel(new NamespacedKey(this, id));
        EquippableComponent eq = meta.getEquippable();
        eq.setSlot(SLOTS[slot]);
        eq.setModel(new NamespacedKey(this, set));
        eq.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
        meta.setEquippable(eq);

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
            case 1 -> e.put(Enchantment.THORNS, set.equals("pumpkin_king") ? 5 : 3);
            case 2 -> e.put(Enchantment.SWIFT_SNEAK, 3);
            case 3 -> {
                e.put(Enchantment.FEATHER_FALLING, 5);
                if (set.equals("witch")) e.put(Enchantment.DEPTH_STRIDER, 3);
                else e.put(Enchantment.SOUL_SPEED, 3);
            }
            default -> { }
        }
        return e;
    }

    private List<String> armorLore(String set, int slot) {
        String col = SET_COLOR.get(set);
        FileConfiguration c = getConfig();
        List<String> l = new ArrayList<>();
        l.add("<dark_gray>Halloween Collection <dark_gray>\u2726 " + col + "Mythic");
        l.add("");
        l.add("<gray><i>\"" + FLAVOR.get(set)[slot] + "\"");
        l.add("");
        l.add(col + "Piece Bonus");
        switch (set) {
            case "pumpkin_king" -> l.add("<dark_gray>\u25B8 <white>+"
                    + num(c.getDouble("armor.bonus.pumpkin-king-max-health", 2.0)) + " Max Health");
            case "wraith" -> l.add("<dark_gray>\u25B8 <white>+"
                    + num(c.getDouble("armor.bonus.wraith-move-speed", 0.04) * 100) + "% Movement Speed");
            case "witch" -> l.add("<dark_gray>\u25B8 <white>+"
                    + num(c.getDouble("armor.bonus.witch-absorption", 1.0)) + " Max Absorption");
            default -> { }
        }
        l.add("");
        l.add(col + "Full Set Bonus <dark_gray>(4/4)");
        double cd;
        switch (set) {
            case "pumpkin_king" -> {
                cd = c.getDouble("armor.abilities.pumpkin_king.cooldown-seconds", 25);
                l.add("<gold>\u25B8 <gray>Night Vision, Fire Resistance,");
                l.add("<gold>\u25B8 <gray>Strength II, Resistance I");
                l.add("<gold>\u25B8 <gray>Attackers are set ablaze");
                l.add("<gold>\u25B8 <red>Hellfire Nova <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Burst of flame that scorches and");
                l.add("<gray>   hurls back everything nearby.");
                l.add("<dark_gray>   Cooldown: " + num(cd) + "s");
            }
            case "wraith" -> {
                cd = c.getDouble("armor.abilities.wraith.cooldown-seconds", 12);
                l.add("<gold>\u25B8 <gray>Speed II, Jump Boost I, Slow Falling");
                l.add("<gold>\u25B8 <gray>" + num(c.getDouble("armor.wraith-dodge-chance", 0.20) * 100)
                        + "% chance to phase through damage");
                l.add("<gold>\u25B8 <red>Shadow Step <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Blink forward and vanish from sight");
                l.add("<gray>   for a few moments.");
                l.add("<dark_gray>   Cooldown: " + num(cd) + "s");
            }
            case "witch" -> {
                cd = c.getDouble("armor.abilities.witch.cooldown-seconds", 20);
                l.add("<gold>\u25B8 <gray>Water Breathing, Haste II");
                l.add("<gold>\u25B8 <gray>Heal " + num(c.getDouble("armor.witch-heal-amount", 1.5))
                        + " HP every 3s, immune to Poison");
                l.add("<gold>\u25B8 <red>Hex Burst <dark_gray>[Sneak + Swap Hands]");
                l.add("<gray>   Curse nearby foes with Poison, Slowness");
                l.add("<gray>   and Weakness while you mend yourself.");
                l.add("<dark_gray>   Cooldown: " + num(cd) + "s");
            }
            default -> { }
        }
        l.add("");
        l.add("<gray>Unbreakable <dark_gray>|</dark_gray> <gray>Fireproof");
        return l;
    }

    private static String num(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------ weapons (unchanged behaviour)

    public ItemStack cursedBlade() {
        ItemStack item = weapon(Material.NETHERITE_SWORD, "cursed_blade", "<#7B2CBF>Cursed Blade",
                List.of("<gray>A blade that hungers for the living.", "",
                        "<dark_purple>Hits inflict Wither and Weakness."),
                Map.of(Enchantment.SHARPNESS, 6, Enchantment.LOOTING, 3, Enchantment.SWEEPING_EDGE, 3,
                        Enchantment.FIRE_ASPECT, 2));
        // Adding any attribute modifier replaces the sword's defaults, so set both damage and speed.
        // Values are the TOTAL shown on the tooltip; the player's base (1.0 dmg / 4.0 speed) is subtracted.
        double dmg = getConfig().getDouble("weapons.cursed-blade.attack-damage", 12.0);
        double spd = getConfig().getDouble("weapons.cursed-blade.attack-speed", 1.7);
        ItemMeta meta = item.getItemMeta();
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(this, "blade_damage"), dmg - 1.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(this, "blade_speed"), spd - 4.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack reaperScythe() {
        return weapon(Material.DIAMOND_HOE, "reaper_scythe", "<#B91C1C>Reaper's Scythe",
                List.of("<gray>Harvests souls from the fallen.", "",
                        "<red>Hits have a chance to steal life.",
                        "<red>Right-click: Soul Slash <gray>(cooldown)"),
                Map.of(Enchantment.SHARPNESS, 6, Enchantment.LOOTING, 3));
    }

    public ItemStack batBow() {
        return weapon(Material.BOW, "bat_bow", "<#6D28D9>Bat Bow",
                List.of("<gray>Shadows take wing.", "",
                        "<dark_purple>Arrows burst into a swarm of bats",
                        "<dark_purple>that bite everything near the impact."),
                Map.of(Enchantment.POWER, 6));
    }

    private ItemStack weapon(Material mat, String id, String name, List<String> lore,
                             Map<Enchantment, Integer> ench) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        List<String> full = new ArrayList<>(lore);
        full.add("");
        full.add("<gray>Unbreakable <dark_gray>|</dark_gray> <gray>Fireproof");
        full.add("<dark_gray>Halloween Collection");
        decorate(meta, id, name, full, ench);
        meta.setItemModel(new NamespacedKey(this, id));
        item.setItemMeta(meta);
        return item;
    }

    /** Shared: name, lore, enchants, unbreakable, id tag. */
    private void decorate(ItemMeta meta, String id, String name, List<String> lore,
                          Map<Enchantment, Integer> ench) {
        meta.displayName(mm("<!italic>" + name));
        List<Component> comps = new ArrayList<>();
        for (String line : lore) {
            comps.add(line.isEmpty() ? Component.empty() : mm("<!italic>" + line));
        }
        meta.lore(comps);
        for (Map.Entry<Enchantment, Integer> en : ench.entrySet()) {
            meta.addEnchant(en.getKey(), en.getValue(), true);
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
    }

    // ------------------------------------------------------------------ commands

    private void say(CommandSender to, String msg) {
        to.sendMessage(mm(msg));
    }

    private void giveTo(Player target, String id) {
        ItemStack item = createItem(id);
        if (item == null) return;
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
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
                if (createItem(args[1]) == null) {
                    say(sender, "<red>Unknown item. Use /" + label + " list");
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) return true;
                giveTo(target, args[1]);
                say(sender, "<green>Gave " + args[1] + " to " + target.getName() + ".");
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
                for (String s : SUFFIX) giveTo(target, set + "_" + s);
                say(sender, "<green>Gave the " + SET_NAME.get(set) + " set to " + target.getName() + ".");
            }
            default -> say(sender, "<red>Unknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("give", "giveset", "list", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return filter(allItemIds(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("giveset")) return filter(SETS, args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveset"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String typed) {
        List<String> out = new ArrayList<>();
        String t = typed.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
