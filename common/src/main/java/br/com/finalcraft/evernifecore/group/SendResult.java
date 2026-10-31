package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** What a {@link GroupNetwork#send} did: the members it moved, and the ones it left behind with why. */
public final class SendResult {

    private final Set<UUID> moved;
    private final Map<UUID, FancyText> left;

    private SendResult(Set<UUID> moved, Map<UUID, FancyText> left) {
        this.moved = Collections.unmodifiableSet(new LinkedHashSet<>(moved));
        this.left = Collections.unmodifiableMap(new LinkedHashMap<>(left));
    }

    /** @param left each member not moved, with the reason to show them or the leader */
    public static SendResult of(Set<UUID> moved, Map<UUID, FancyText> left) {
        return new SendResult(Objects.requireNonNull(moved, "moved"), Objects.requireNonNull(left, "left"));
    }

    public Set<UUID> getMoved() {
        return moved;
    }

    public Map<UUID, FancyText> getLeft() {
        return left;
    }
}
