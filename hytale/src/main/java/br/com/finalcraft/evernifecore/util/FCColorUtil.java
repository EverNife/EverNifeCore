package br.com.finalcraft.evernifecore.util;

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
    private static final LegacyComponentSerializer LEGACY_SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

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
        for (int i = 0; i < text.size(); i++) {
            text.set(i, stripColor(text.get(i)));
        }
        return text;
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
}
