package dev.halloweengear;

import org.bukkit.Color;

import java.util.Locale;

/** The three Halloween armor sets. Piece order is always: helmet, chestplate, leggings, boots. */
public enum ArmorSet {

    PUMPKIN_KING("pumpkin_king", "Pumpkin King", "#FF7518",
            new String[]{"Pumpkin King's Crown", "Pumpkin King's Tunic", "Pumpkin King's Greaves", "Pumpkin King's Boots"},
            new String[]{"Wear the grin. Rule the harvest.", "Hollowed out by a thousand autumns.",
                    "Stitched from scarecrow and cinder.", "Where it walks, the fields burn orange."}),

    WRAITH("wraith", "Wraith", "#9FB4C7",
            new String[]{"Wraith Hood", "Wraith Shroud", "Wraith Wrappings", "Wraith Treads"},
            new String[]{"Faces forget what the hood remembers.", "Woven from the last breath of the dying.",
                    "They bind nothing, yet nothing binds you.", "The floor never quite notices."}),

    WITCH("witch", "Witch", "#B05CE0",
            new String[]{"Witch's Hat", "Witch's Robe", "Witch's Leggings", "Witch's Boots"},
            new String[]{"Brims with secrets and bad ideas.", "Stained by every brew gone wrong.",
                    "Pockets full of newt and nerve.", "Curled toes, curling spells."});

    public static final String[] SUFFIX = {"helmet", "chestplate", "leggings", "boots"};

    public final String id;
    public final String displayName;
    public final String hex;
    public final String[] pieceNames;
    public final String[] flavor;

    ArmorSet(String id, String displayName, String hex, String[] pieceNames, String[] flavor) {
        this.id = id;
        this.displayName = displayName;
        this.hex = hex;
        this.pieceNames = pieceNames;
        this.flavor = flavor;
    }

    /** Leather dye colour for this set. */
    public Color dye() {
        return Color.fromRGB(Integer.parseInt(hex.substring(1), 16));
    }

    /** MiniMessage colour tag, e.g. {@code <#FF7518>}. */
    public String tag() {
        return "<" + hex + ">";
    }

    public String pieceId(int piece) {
        return id + "_" + SUFFIX[piece];
    }

    public static ArmorSet byId(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (ArmorSet set : values()) {
            if (set.id.equals(s)) return set;
        }
        return null;
    }
}
