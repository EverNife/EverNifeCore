package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * What a sender was told, kept twice: as the {@link Component} that was delivered and as the legacy
 * {@code §}-formatted text it renders to.
 *
 * <p>Both are needed because they answer different questions. The text is what a test asserts a
 * command said; a hover and a click are no part of legacy serialization at all, so anything about
 * them has to be read off the component - recursively, because a formatter attaches each hover to
 * the segment that carries it, never to the root.</p>
 */
public final class CapturedMessages {

    private final List<String> messages = new ArrayList<>();
    private final List<Component> components = new ArrayList<>();

    /** Records one delivered message, which is all a sender has to do to be assertable. */
    public void record(Component component) {
        components.add(component);
        messages.add(FCColorUtil.componentToString(component));
    }

    /** Every message so far, as legacy-formatted text, oldest first. */
    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void clear() {
        messages.clear();
        components.clear();
    }

    public boolean anyContains(String snippet) {
        return messages.stream().anyMatch(message -> message.contains(snippet));
    }

    public void assertAnyContains(String snippet) {
        if (!anyContains(snippet)) {
            fail("Expected a message containing [" + snippet + "] but got: " + messages);
        }
    }

    public void assertNothingSent() {
        assertTrue(messages.isEmpty(), "Expected no message to be sent, but got: " + messages);
    }

    /**
     * Every hover found anywhere in the FIRST message whose visible text contains
     * {@code visibleTextSnippet}, joined by newlines - or {@code null} when no such message was sent
     * or it carries no hover at all.
     */
    public @Nullable String hoverTextOfMessageContaining(String visibleTextSnippet) {
        Component found = firstContaining(visibleTextSnippet);
        if (found == null) {
            return null;
        }
        StringBuilder collected = new StringBuilder();
        collectHoverText(found, collected);
        return collected.length() == 0 ? null : collected.toString();
    }

    /**
     * The click value attached anywhere in the FIRST message whose visible text contains
     * {@code visibleTextSnippet}, or {@code null} when there is none. The value travels raw,
     * uncoloured.
     */
    public @Nullable String clickValueOfMessageContaining(String visibleTextSnippet) {
        Component found = firstContaining(visibleTextSnippet);
        return found == null ? null : firstClickValue(found);
    }

    private @Nullable Component firstContaining(String visibleTextSnippet) {
        for (int index = 0; index < components.size(); index++) {
            if (messages.get(index).contains(visibleTextSnippet)) {
                return components.get(index);
            }
        }
        return null;
    }

    private static @Nullable String firstClickValue(Component component) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && !clickEvent.value().isEmpty()) {
            return clickEvent.value();
        }
        for (Component child : component.children()) {
            String found = firstClickValue(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collectHoverText(Component component, StringBuilder out) {
        HoverEvent<?> hoverEvent = component.hoverEvent();
        if (hoverEvent != null && hoverEvent.value() instanceof Component) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(FCColorUtil.componentToString((Component) hoverEvent.value()));
        }
        for (Component child : component.children()) {
            collectHoverText(child, out);
        }
    }

}
