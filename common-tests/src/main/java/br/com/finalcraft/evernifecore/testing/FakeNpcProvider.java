package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcProvider;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;
import jakarta.annotation.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "An NPC" without an NPC plugin: a provider that knows the entities, places and names a test gives it, and clicks the
 * way a real provider does - {@link #click} (the right click) and {@link #leftClick} (the punch) raise {@code interact-npc}
 * only while {@link Npcs} started it, that is while somebody needs the source. Register it with {@link Npcs#register} and unregister its owner after the test.
 */
public final class FakeNpcProvider implements NpcProvider {

    private static final WorldLocPos NOWHERE = WorldLocPos.of(0, 64, 0, "world");

    private final String id;
    private volatile boolean available = true;
    private volatile boolean started;
    private final Map<NativeEntity, NpcRef> entities = new IdentityHashMap<>();
    private final Map<NpcRef, WorldLocPos> locations = new ConcurrentHashMap<>();
    private final Map<NpcRef, String> names = new ConcurrentHashMap<>();

    public FakeNpcProvider(String id) {
        this.id = id;
    }

    /** Whether the fake plugin is enabled; true by default. */
    public FakeNpcProvider available(boolean available) {
        this.available = available;
        return this;
    }

    /** {@code entity} is the NPC {@code ref}: {@link #resolve} answers it and {@link #find} hands it back. */
    public synchronized FakeNpcProvider knows(NativeEntity entity, NpcRef ref) {
        entities.put(entity, ref);
        return this;
    }

    public FakeNpcProvider at(NpcRef ref, WorldLocPos location) {
        locations.put(ref, location);
        return this;
    }

    public FakeNpcProvider named(NpcRef ref, String name) {
        names.put(ref, name);
        return this;
    }

    /** Whether {@link Npcs} has this provider raising clicks now. */
    public boolean isStarted() {
        return started;
    }

    /** A {@code player} right-clicking {@code ref} with an empty hand - see {@link #click(FPlayer, NpcRef, String)}. */
    public boolean click(FPlayer player, NpcRef ref) {
        return click(player, ref, null);
    }

    /**
     * {@code player} right-clicking {@code ref} holding {@code itemInHandKey}: the regions decide and the {@code Pre} is
     * posted, then the {@code Post} if nothing denied - nothing at all while the provider is not started.
     *
     * @return whether the click was denied
     */
    public boolean click(FPlayer player, NpcRef ref, @Nullable String itemInHandKey) {
        return click(player, ref, itemInHandKey, EntityContext.ClickType.RIGHT);
    }

    /** {@code player} punching {@code ref} with an empty hand: the same decide-then-post as {@link #click}, with the left click. */
    public boolean leftClick(FPlayer player, NpcRef ref) {
        return click(player, ref, null, EntityContext.ClickType.LEFT);
    }

    private boolean click(FPlayer player, NpcRef ref, @Nullable String itemInHandKey, EntityContext.ClickType clickType) {
        if (!started) {
            return false;
        }
        if (Actions.denied(context(player, ref, itemInHandKey, clickType))) {
            return true;
        }
        Actions.post(EventSource.INTERACT_NPC, () -> context(player, ref, itemInHandKey, clickType));
        return false;
    }

    private EntityContext context(FPlayer player, NpcRef ref, @Nullable String itemInHandKey, EntityContext.ClickType clickType) {
        return new EntityContext(EventSource.INTERACT_NPC, locations.getOrDefault(ref, NOWHERE), Actors.player(player), null,
                Actors.npc(id + ":npc", ref), itemInHandKey == null ? null : TypeKey.parse(itemInHandKey), clickType);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public synchronized Optional<NpcRef> resolve(NativeEntity entity) {
        return Optional.ofNullable(entities.get(entity));
    }

    @Override
    public synchronized Optional<NativeEntity> find(NpcRef ref) {
        for (Map.Entry<NativeEntity, NpcRef> entry : entities.entrySet()) {
            if (entry.getValue().equals(ref)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<WorldLocPos> getLocation(NpcRef ref) {
        return Optional.ofNullable(locations.get(ref));
    }

    @Override
    public Optional<String> getName(NpcRef ref) {
        return Optional.ofNullable(names.get(ref));
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        started = false;
    }
}
