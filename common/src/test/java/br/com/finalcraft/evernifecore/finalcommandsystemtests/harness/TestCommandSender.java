package br.com.finalcraft.evernifecore.finalcommandsystemtests.harness;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import net.kyori.adventure.text.Component;

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
}
