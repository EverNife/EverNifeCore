package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.color.ColorEnum;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FCColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    // Hex-aware: colorfy() expands &#RRGGBB into the §x§R§R§G§G§B§B form, which only round-trips
    // through a serializer built with hexColors() + useUnusualXRepeatedCharacterHexFormat().
    private static final LegacyComponentSerializer LEGACY_SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public static String decolorfy(@Nullable String text) {
        if (text == null) return null;
        return text.replace('§', '&');
    }

    public static String colorfy(@Nullable String text) {
        if (text == null) return null;
        
        // Convert hex colors &#RRGGBB to §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        
        return buffer.toString().replace('&', '§');
    }

    public static List<String> colorfy(@Nullable List<String> text) {
        if (text == null) return null;
        return new ArrayList<>(
                Arrays.asList(
                        FCColorUtil.colorfy(String.join("\n", text)).split("\n", -1)
                )
        );
    }

    public static String stripColor(@Nullable final String input) {
        if (input == null) return null;
        return input.replaceAll("§[0-9a-fk-orx]", "");
    }

    public static List<String> stripColor(@Nullable final List<String> text) {
        if (text == null) return null;
        List<String> result = new ArrayList<>(text.size());
        for (String line : text) {
            result.add(stripColor(line));
        }
        return result;
    }

    public static Component colorfyComponent(@Nullable String text) {
        if (text == null) return Component.empty();
        String colored = colorfy(text);
        return LEGACY_SECTION_SERIALIZER.deserialize(colored);
    }

    public static List<Component> colorfyComponent(@Nullable List<String> text) {
        if (text == null) return null;
        List<Component> components = new ArrayList<>();
        for (String line : text) {
            components.add(colorfyComponent(line));
        }
        return components;
    }

    public static String componentToString(Component component) {
        return LEGACY_SECTION_SERIALIZER.serialize(component);
    }

    public static String getLastColors(@Nonnull String input) {
        String result = "";
        int length = input.length();

        // Search backwards from the end as it is faster
        for (int index = length - 1; index > -1; index--) {
            char section = input.charAt(index);
            if (section == ColorEnum.COLOR_CHAR && index < length - 1) {
                char c = input.charAt(index + 1);
                ColorEnum color = ColorEnum.getByChar(c);

                if (color != null) {
                    // Emit the actual code chars (§ + code) from the input; ColorEnum has no
                    // toString() override, so color.toString() would yield the enum name (GRAY, RESET).
                    result = String.valueOf(ColorEnum.COLOR_CHAR) + c + result;

                    // Once we find a color or reset we can stop searching
                    if (color.isColor() || color.equals(ColorEnum.RESET)) {
                        break;
                    }
                }
            }
        }

        return result;
    }
}
