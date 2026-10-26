package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagStore;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link FlagScope#ACTOR} holder for a test - a party that follows its members wherever they stand. Made by
 * {@link RegionWorld#actorHolder}; a change of its values or members has its members' session flags looked at again,
 * as a real group does.
 */
public final class TestActorHolder implements FlagHolder {

    private final String id;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final FlagStore flags;
    private volatile int priority;

    TestActorHolder(String id, UUID... members) {
        this.id = id;
        this.members.addAll(Arrays.asList(members));
        this.flags = new FlagStore(this, (flag, oldValue, newValue, cause) -> revalidateMembers());
    }

    @Override
    public String getHolderId() {
        return id;
    }

    @Override
    public FlagScope getHolderScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public TestActorHolder setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public <T> @Nullable FlagValue<T> getFlagValue(Flag<T> flag) {
        return flags.getValue(flag);
    }

    @Override
    public Map<FlagKey, FlagValue<?>> getFlags() {
        return flags.getFlags();
    }

    @Override
    public FlagStore getFlagStore() {
        return flags;
    }

    public <T> TestActorHolder setFlag(Flag<T> flag, T value) {
        flags.set(flag, value);
        return this;
    }

    public <T> TestActorHolder setFlag(Flag<T> flag, FlagValue<T> value) {
        flags.set(flag, value);
        return this;
    }

    public boolean removeFlag(Flag<?> flag) {
        return flags.remove(flag);
    }

    /** Replaces the members; who left and who joined have their session flags looked at again. */
    public TestActorHolder setMembers(UUID... uuids) {
        Set<UUID> before = new HashSet<>(members);
        members.clear();
        members.addAll(Arrays.asList(uuids));
        before.addAll(members);
        before.forEach(RegionLookup.get()::invalidate);
        return this;
    }

    public boolean contains(FPlayer player) {
        return members.contains(player.getUniqueId());
    }

    private void revalidateMembers() {
        members.forEach(RegionLookup.get()::invalidate);
    }

    @Override
    public String toString() {
        return id;
    }
}
