package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A minimal but honest {@link FPlayer} fake: real name/uuid (so {@code isPlayer() == true}),
 * a settable permission set, and captured messages - the same {@link CapturedMessages} a
 * {@link TestCommandSender} keeps. {@link #getLocation()} returns {@code null}; nothing in the
 * FinalCMD dispatch/help/tab paths under test reads it.
 */
public class TestFPlayerSender implements FPlayer {

    private final String name;
    private final UUID uniqueId;
    private final Set<String> permissions = new HashSet<>();
    private final CapturedMessages captured = new CapturedMessages();
    private boolean online = true;

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

    /** Whether this player is still connected - the answer anything that keeps ticking for them reads. */
    public TestFPlayerSender online(boolean online) {
        this.online = online;
        return this;
    }

    @Override
    public boolean isOnline() {
        return online;
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
        captured.record(component);
    }

    @Override
    public Object getDelegate() {
        return this;
    }

    /** What this player was told, for the assertions the shortcuts below do not cover. */
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
