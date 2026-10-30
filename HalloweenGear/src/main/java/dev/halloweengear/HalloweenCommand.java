package dev.halloweengear;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /halloween <give|giveset|list|reload> */
public final class HalloweenCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "giveset", "list", "reload");

    private final HalloweenGear plugin;
    private final Gear gear;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public HalloweenCommand(HalloweenGear plugin, Gear gear) {
        this.plugin = plugin;
        this.gear = gear;
    }

    private void say(CommandSender to, String text) {
        to.sendMessage(mm.deserialize(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            say(sender, "<gold>HalloweenGear <gray>- /" + label + " \\<give|giveset|list|reload>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                say(sender, "<gold>Items: <gray>" + String.join(", ", gear.itemIds()));
                say(sender, "<gold>Sets: <gray>" + String.join(", ", gear.setIds()));
            }
            case "reload" -> {
                if (!sender.hasPermission("halloweengear.admin")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                plugin.reloadConfig();
                say(sender, "<green>HalloweenGear config reloaded.");
            }
            case "give" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    say(sender, "<red>Usage: /" + label + " give \\<item> [player]");
                    return true;
                }
                ItemStack item = gear.create(args[1]);
                if (item == null) {
                    say(sender, "<red>Unknown item. Use /" + label + " list");
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) return true;
                give(target, item);
                say(sender, "<green>Gave " + args[1].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
            }
            case "giveset" -> {
                if (!sender.hasPermission("halloweengear.give")) {
                    say(sender, "<red>You don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    say(sender, "<red>Usage: /" + label + " giveset \\<set> [player]");
                    return true;
                }
                ArmorSet set = ArmorSet.byId(args[1]);
                if (set == null) {
                    say(sender, "<red>Unknown set. Sets: " + String.join(", ", gear.setIds()));
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) return true;
                for (ItemStack piece : gear.createSet(set)) give(target, piece);
                say(sender, "<green>Gave the " + set.displayName + " set to " + target.getName() + ".");
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

    private void give(Player target, ItemStack item) {
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            List<String> pool = sub.equals("give") ? gear.itemIds() : sub.equals("giveset") ? gear.setIds() : List.of();
            for (String s : pool) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
