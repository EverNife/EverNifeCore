package br.com.finalcraft.evernifecore.minecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Adventure {@link Component} helpers (no Bukkit dependency), so they stay unit-testable
 * without a running server.
 */
public class FCComponentUtil {

    /**
     * Splits a component into one component per line, breaking on {@code '\n'} wherever it occurs -
     * in a component's text content (what {@code colorfyComponent} produces) or as a newline child -
     * and carrying each ancestor's style, hover and click onto every emitted piece. An old client
     * renders a raw {@code '\n'} as a glyph instead of a line break, so the legacy BaseComponent path
     * has to send one chat message per line.
     */
    public static List<Component> splitNewlines(Component root) {
        List<TextComponent.Builder> lines = new ArrayList<>();
        lines.add(Component.text());
        collectLines(root, Style.empty(), lines);

        List<Component> result = new ArrayList<>(lines.size());
        for (TextComponent.Builder line : lines) {
            result.add(line.build());
        }
        return result;
    }

    private static void collectLines(Component component, Style inherited, List<TextComponent.Builder> lines) {
        // Child style wins; whatever it leaves unset falls back to the ancestor style, so hover/click
        // set on a parent still reach every leaf we flatten out.
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);

        if (component instanceof TextComponent) {
            String content = ((TextComponent) component).content();
            if (!content.isEmpty()) {
                String[] pieces = content.split("\n", -1);
                for (int i = 0; i < pieces.length; i++) {
                    if (i > 0) {
                        lines.add(Component.text());
                    }
                    if (!pieces[i].isEmpty()) {
                        lines.get(lines.size() - 1).append(Component.text(pieces[i]).style(effective));
                    }
                }
            }
        }

        for (Component child : component.children()) {
            collectLines(child, effective, lines);
        }
    }
}
