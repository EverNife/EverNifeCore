package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal but honest {@link FPlayer} fake: real name/uuid (so {@code isPlayer() == true}),
 * a settable permission set, and captured messages - the same {@link CapturedMessages} a
 * {@link TestCommandSender} keeps. {@link #getLocation()} returns {@code null} until {@link #at} places the
 * player - the FinalCMD paths never read it, region code does.
 */
public class TestFPlayerSender implements FPlayer {

    private final String name;
    private final UUID uniqueId;
    private final Set<String> permissions = new HashSet<>();
    private final CapturedMessages captured = new CapturedMessages();
    private boolean online = true;
    private boolean playedBefore = false;
    private volatile @Nullable FLocation location;
    private final List<FLocation> teleports = new CopyOnWriteArrayList<>();

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

    /** Whether the platform would say it saw this player in an earlier session; false until set. */
    public TestFPlayerSender playedBefore(boolean playedBefore) {
        this.playedBefore = playedBefore;
        return this;
    }

    @Override
    public boolean hasPlayedBefore() {
        return playedBefore;
    }

    /** Stands the player at a point; {@link #getLocation()} answers it from then on. */
    public TestFPlayerSender at(String world, double x, double y, double z) {
        this.location = new FLocation(WorldLocPos.of(x, y, z, world), null);
        return this;
    }

    @Override
    public @Nullable FLocation getLocation() {
        return location;
    }

    /** Moves the player there, as a real teleport would, and keeps the target for {@link #getTeleports()}. */
    @Override
    public boolean teleportTo(FLocation targetLocation) {
        teleports.add(targetLocation);
        this.location = targetLocation;
        return true;
    }

    /** Every location this player was teleported to, in order. */
    public List<FLocation> getTeleports() {
        return teleports;
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
