package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
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
 * A minimal but honest {@link FPlayer} fake: real name/uuid (so {@code isPlayer() == true}),
 * a settable permission set, and captured messages - the same capture strategy as
 * {@link TestCommandSender}. {@link #getLocation()} returns {@code null}; nothing in the FinalCMD
 * dispatch/help/tab paths under test reads it.
 */
public class TestFPlayerSender implements FPlayer {

    private final String name;
    private final UUID uniqueId;
    private final Set<String> permissions = new HashSet<>();
    private final List<String> messages = new ArrayList<>();
    private final List<Component> components = new ArrayList<>();

    public TestFPlayerSender(String name, UUID uniqueId) {
        this.name = name;
        this.uniqueId = uniqueId;
    }

    public TestFPlayerSender(String name) {
        this(name, UUID.randomUUID());
    }

    public TestFPlayerSender grant(String permission) {
        permissions.add(permission);
        return this;
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public FLocation getLocation() {
        return null;
    }

    @Override
    public boolean teleportTo(FLocation targetLocation) {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
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
        components.clear();
    }

    /** The hover of the first message whose visible text contains {@code visibleTextSnippet} - see {@link TestCommandSender#hoverTextOfMessageContaining(String)}. */
    public @Nullable String hoverTextOfMessageContaining(String visibleTextSnippet) {
        for (int index = 0; index < components.size(); index++) {
            if (messages.get(index).contains(visibleTextSnippet)) {
                StringBuilder collected = new StringBuilder();
                collectHoverText(components.get(index), collected);
                return collected.length() == 0 ? null : collected.toString();
            }
        }
        return null;
    }

    /** The click value of the first message whose visible text contains {@code visibleTextSnippet}. */
    public @Nullable String clickValueOfMessageContaining(String visibleTextSnippet) {
        for (int index = 0; index < components.size(); index++) {
            if (messages.get(index).contains(visibleTextSnippet)) {
                return firstClickValue(components.get(index));
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
}
