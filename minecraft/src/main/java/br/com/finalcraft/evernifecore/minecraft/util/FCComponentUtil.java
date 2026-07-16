package br.com.finalcraft.evernifecore.minecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.option.OptionState;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Adventure {@link Component} helpers for the legacy {@code player.spigot().sendMessage(...)} path.
 * <p>
 * Free of any Bukkit dependency (only Adventure and the server-provided Bungee chat classes), so it
 * stays unit-testable without a running server.
 */
public class FCComponentUtil {

    // adventure emits the Minecraft 1.21.5+ snake_case click/hover JSON by default, which pre-1.21.5
    // clients (down to 1.7.10) do not read - so force BOTH the modern and the legacy field names. The
    // JSON is then parsed into real md_5 components, which the server re-serialises in its own native
    // dialect, so every client ends up with the shape it understands.
    private static final GsonComponentSerializer COMPAT_GSON = GsonComponentSerializer.builder()
            .options(OptionState.optionState()
                    .value(JSONOptions.EMIT_CLICK_EVENT_TYPE, JSONOptions.ClickEventValueMode.BOTH)
                    .value(JSONOptions.EMIT_HOVER_EVENT_TYPE, JSONOptions.HoverEventValueMode.ALL)
                    .build())
            .build();

    /**
     * Converts an Adventure component into real md_5 {@link BaseComponent}s (not Adventure's opaque
     * {@code AdapterComponent}), so {@code player.spigot().sendMessage(...)} hands the server ordinary
     * components it will serialise in its own version's chat-JSON format - preserving click/hover on
     * every client from 1.7.10 up.
     */
    public static BaseComponent[] toBaseComponents(Component component) {
        return ComponentSerializer.parse(COMPAT_GSON.serialize(component));
    }

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
