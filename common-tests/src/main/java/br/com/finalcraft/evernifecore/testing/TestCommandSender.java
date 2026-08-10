package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final CapturedMessages captured = new CapturedMessages();

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
        captured.record(component);
    }

    @Override
    public Object getDelegate() {
        return this;
    }

    /** What this sender was told, for the assertions the shortcuts below do not cover. */
    public CapturedMessages getCaptured() {
        return captured;
    }

    public List<String> getMessages() {
        return captured.getMessages();
    }

    public void clearMessages() {
        captured.clear();
    }

    public boolean anyMessageContains(String snippet) {
        return captured.anyContains(snippet);
    }

    public void assertAnyMessageContains(String snippet) {
        captured.assertAnyContains(snippet);
    }

    public void assertNoMessageSent() {
        captured.assertNothingSent();
    }

    /** See {@link CapturedMessages#hoverTextOfMessageContaining(String)}. */
    public @Nullable String hoverTextOfMessageContaining(String visibleTextSnippet) {
        return captured.hoverTextOfMessageContaining(visibleTextSnippet);
    }

    /** See {@link CapturedMessages#clickValueOfMessageContaining(String)}. */
    public @Nullable String clickValueOfMessageContaining(String visibleTextSnippet) {
        return captured.clickValueOfMessageContaining(visibleTextSnippet);
    }
}
