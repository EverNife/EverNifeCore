package br.com.finalcraft.evernifecore.color;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

public enum ColorEnum {
    BLACK('0', 0x00),
    DARK_BLUE('1', 0x1),
    DARK_GREEN('2', 0x2),
    DARK_AQUA('3', 0x3),
    DARK_RED('4', 0x4),
    DARK_PURPLE('5', 0x5),
    GOLD('6', 0x6),
    GRAY('7', 0x7),
    DARK_GRAY('8', 0x8),
    BLUE('9', 0x9),
    GREEN('a', 0xA),
    AQUA('b', 0xB),
    RED('c', 0xC),
    LIGHT_PURPLE('d', 0xD),
    YELLOW('e', 0xE),
    WHITE('f', 0xF),
    MAGIC('k', 0x10, true),
    BOLD('l', 0x11, true),
    STRIKETHROUGH('m', 0x12, true),
    UNDERLINE('n', 0x13, true),
    ITALIC('o', 0x14, true),
    RESET('r', 0x15);

    public static final char COLOR_CHAR = '\u00A7'; // §

    private final char code;
    private final int intCode;
    private final boolean isFormat;
    private final String toString;

    private static final Map<Integer, ColorEnum> BY_ID = Maps.newHashMap();
    private static final Map<Character, ColorEnum> BY_CHAR = Maps.newHashMap();

    ColorEnum(char code, int intCode) {
        this(code, intCode, false);
    }

    ColorEnum(char code, int intCode, boolean isFormat) {
        this.code = code;
        this.intCode = intCode;
        this.isFormat = isFormat;
        this.toString = new String(new char[]{COLOR_CHAR, code});
    }

    public boolean isFormat() {
        return isFormat;
    }

    public boolean isColor() {
        return !isFormat && this != RESET;
    }

    @Nullable
    public static ColorEnum getByChar(char code) {
        return BY_CHAR.get(code);
    }

    @Nullable
    public static ColorEnum getByChar(@NotNull String code) {
        Preconditions.checkArgument(code != null, "Code cannot be null");
        Preconditions.checkArgument(code.length() > 0, "Code must have at least one char");
        return BY_CHAR.get(code.charAt(0));
    }

    static {
        for (ColorEnum color : values()) {
            BY_ID.put(color.intCode, color);
            BY_CHAR.put(color.code, color);
        }
    }
}
