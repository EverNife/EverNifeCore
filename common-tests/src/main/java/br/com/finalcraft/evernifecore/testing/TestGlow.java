package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.IGroupGlowProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The in-memory glow of {@link Platforms#withGlow()}: who glows for which group, and every call in order. */
public final class TestGlow implements IGroupGlowProvider {

    private final Map<UUID, Set<Group>> glowing = new ConcurrentHashMap<>();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void glow(FPlayer player, Group group) {
        glowing.computeIfAbsent(player.getUniqueId(), uuid -> Collections.synchronizedSet(new LinkedHashSet<>())).add(group);
        calls.add("glow " + player.getName() + " " + group.getId());
    }

    @Override
    public void unglow(FPlayer player, Group group) {
        Set<Group> groups = glowing.get(player.getUniqueId());
        if (groups != null && groups.remove(group) && groups.isEmpty()) {
            glowing.remove(player.getUniqueId());
        }
        calls.add("unglow " + player.getName() + " " + group.getId());
    }

    /** Whether some group glows {@code player} now. */
    public boolean isGlowing(UUID player) {
        return glowing.containsKey(player);
    }

    /** Every call, {@code glow <name> <group id>} or {@code unglow <name> <group id>}, in order. */
    public List<String> getCalls() {
        synchronized (calls) {
            return new ArrayList<>(calls);
        }
    }
}
