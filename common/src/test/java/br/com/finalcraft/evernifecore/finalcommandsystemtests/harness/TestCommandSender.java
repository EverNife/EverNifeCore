package br.com.finalcraft.evernifecore.finalcommandsystemtests.harness;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A console-like {@link FCommandSender} (no {@link java.util.UUID}, so {@code isPlayer() == false})
 * that captures every message sent to it as plain legacy-formatted text (via
 * {@link FCColorUtil#componentToString}, the same serializer the codebase already uses to render
 * a {@link net.kyori.adventure.text.Component} back to a {@code §}-formatted string), so tests can
 * assert on the exact text a command sent without needing a real chat renderer.
 */
public class TestCommandSender implements FCommandSender {

    private final String name;
    private final Set<String> permissions = new HashSet<>();
    private final List<String> messages = new ArrayList<>();
    private final List<Component> components = new ArrayList<>();

    public TestCommandSender(String name) {
        this.name = name;
    }

    public TestCommandSender grant(String permission) {
        permissions.add(permission);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return null;
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        return permissions.contains(permission);
    }

    @Override
    public void sendMessage(@Nonnull Component component) {
        components.add(component);
        messages.add(FCColorUtil.componentToString(component));
    }

    @Override
    public Object getDelegate() {
        return this;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void clearMessages() {
        messages.clear();
    }

    public boolean anyMessageContains(String snippet) {
        return messages.stream().anyMatch(message -> message.contains(snippet));
    }

    public void assertAnyMessageContains(String snippet) {
        if (!anyMessageContains(snippet)) {
            fail("Expected a message containing [" + snippet + "] but got: " + messages);
        }
    }

    public void assertNoMessageSent() {
        assertTrue(messages.isEmpty(), "Expected no message to be sent, but got: " + messages);
    }

    /**
     * The hover text (plain legacy-formatted, every {@link HoverEvent} found joined by newlines)
     * attached anywhere in the FIRST sent message whose visible text contains
     * {@code visibleTextSnippet}, or {@code null} if no such message was sent or it carries no hover
     * at all. A {@link HoverEvent} isn't part of {@link FCColorUtil#componentToString}'s output
     * (legacy serialization only covers the visible text), so hover assertions (help-line
     * descriptions) need this instead of {@link #assertAnyMessageContains}. Searched recursively:
     * a {@code FancyFormatter} (e.g. an @Arg-built help line) attaches each segment's hover to that
     * segment's own child {@link Component}, not to the message's root component.
     */
    public @Nullable String hoverTextOfMessageContaining(String visibleTextSnippet) {
        for (int i = 0; i < components.size(); i++) {
            if (messages.get(i).contains(visibleTextSnippet)) {
                StringBuilder collected = new StringBuilder();
                collectHoverText(components.get(i), collected);
                return collected.length() == 0 ? null : collected.toString();
            }
        }
        return null;
    }

    /**
     * The click value attached anywhere in the FIRST sent message whose visible text contains
     * {@code visibleTextSnippet}, or {@code null} when there is none. A {@link ClickEvent} is no more
     * part of the legacy serialization than a hover is - see
     * {@link #hoverTextOfMessageContaining(String)} - and the value travels raw, uncoloured.
     */
    public @Nullable String clickValueOfMessageContaining(String visibleTextSnippet) {
        for (int i = 0; i < components.size(); i++) {
            if (messages.get(i).contains(visibleTextSnippet)) {
                return firstClickValue(components.get(i));
            }
        }
        return null;
    }

    private static @Nullable String firstClickValue(Component component) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null) {
            return clickEvent.value();
        }
        for (Component child : component.children()) {
            String found = firstClickValue(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void collectHoverText(Component component, StringBuilder out) {
        HoverEvent<?> hoverEvent = component.hoverEvent();
        if (hoverEvent != null && hoverEvent.value() instanceof Component) {
            if (out.length() > 0) out.append('\n');
            out.append(FCColorUtil.componentToString((Component) hoverEvent.value()));
        }
        for (Component child : component.children()) {
            collectHoverText(child, out);
        }
    }
}
