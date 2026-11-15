package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.provenance.ProvenanceTracker;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.config.plugin.ProvenanceSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.everydatabase.Storages;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The game's actions for a test, with no Bukkit nor Hytale: one plugin ({@link RegionWorld} - owner, scripted storage,
 * clock), the global {@link EventSourceUsage}, the two steps every native listener takes ({@link #pre}, {@link #post}),
 * contexts to feed them ({@link #contexts()}) and the owner's access to the one {@link ProvenanceTracker} record, which
 * lives in a fresh in-memory storage while a test world is open.
 * Needs a platform with {@code mainThreadInline()} and {@code worldEvents()}.
 *
 * <pre>{@code
 * try (ActionWorld actions = ActionWorld.open("FakeJobs", tempDir)) {
 *     ProvenanceHold recording = actions.provenance().record();
 *     actions.post(actions.contexts().place(alice, "world", 1, 2, 3, "minecraft:stone"));
 * }
 * }</pre>
 */
public final class ActionWorld implements AutoCloseable {

    private final RegionWorld regions;
    private final Contexts contexts = new Contexts();

    private ActionWorld(RegionWorld regions) {
        this.regions = regions;
        recordProvenanceInMemory();
    }

    /** Has the next provenance record open on a fresh in-memory storage instead of EverNifeCore's backend. */
    static void recordProvenanceInMemory() {
        ProvenanceTracker.useStorage(() -> {
            ScriptedStorage backend = ScriptedStorage.wrapping(Storages.createInMemory());
            backend.init().join();
            return backend.asECStorage();
        });
    }

    /** Opens the actions of a fake plugin named {@code ownerName} whose data folder is {@code dataFolder}. */
    public static ActionWorld open(String ownerName, Path dataFolder) {
        return new ActionWorld(RegionWorld.open(ownerName, dataFolder));
    }

    public ECPluginData getOwner() {
        return regions.getOwner();
    }

    /** The owner's regions, which decide every {@link #pre} before its subscribers hear it. */
    public RegionWorld regions() {
        return regions;
    }

    public EventSourceUsage usage() {
        return EventSourceUsage.get();
    }

    /** Whether a native listener of {@code source} would be on the server now. */
    public boolean isNeeded(EventSource source) {
        return usage().isNeeded(source);
    }

    /** What a native listener does before the outcome: the regions decide, then the source's {@code Pre}. True = denied. */
    public boolean pre(ActionContext context) {
        return Actions.denied(context);
    }

    /** What a native listener does after the outcome: the source's {@code Post}, built only with a subscriber. */
    public void post(ActionContext context) {
        Actions.post(context.getSource(), () -> context);
    }

    /** The owner's access to the one record: the one it opened, else one opened on the built-in settings. */
    public ProvenanceTracker provenance() {
        ProvenanceTracker opened = ProvenanceTracker.ofIfPresent(getOwner());
        return opened != null ? opened : provenance(new ProvenanceSettings());
    }

    /** Opens the record for the owner with {@code settings}; an access it already has takes them, as a reload does. */
    public ProvenanceTracker provenance(ProvenanceSettings settings) {
        return ProvenanceTracker.open(getOwner(), settings);
    }

    public Contexts contexts() {
        return contexts;
    }

    /** Lets go of the owner's access to the record (the last one closes it), then closes its regions and storage. */
    @Override
    public void close() {
        ProvenanceTracker.closeOwnedBy(getOwner());
        ProvenanceTracker.useStorage(null);
        regions.close();
    }

    /** The contexts the native listeners build, over {@link Actors}; an entity is named by the uuid its handle answers. */
    public static final class Contexts {

        Contexts() {
        }

        public BlockContext place(FPlayer player, String world, int x, int y, int z, String blockKey) {
            return new BlockContext(EventSource.PLACE, WorldLocPos.of(x, y, z, world), Actors.player(player), null, TypeKey.parse(blockKey), null);
        }

        public BlockContext breakBlock(FPlayer player, String world, int x, int y, int z, String blockKey) {
            return new BlockContext(EventSource.BREAK, WorldLocPos.of(x, y, z, world), Actors.player(player), null, TypeKey.parse(blockKey), null);
        }

        /** A creature spawned for {@code reason} (the platform's word: {@code SPAWNER}, {@code EGG}, {@code NATURAL}...). */
        public SpawnContext spawn(UUID entity, String entityKey, String reason, String world) {
            return new SpawnContext(WorldLocPos.of(0, 64, 0, world), Actors.environment("natural_spawn"), null,
                    creature(entity, entityKey, null), reason);
        }

        public ItemContext drop(FPlayer player, UUID item, String itemKey) {
            return new ItemContext(EventSource.ITEM_DROP, WorldLocPos.of(0, 64, 0, "world"), Actors.player(player), null,
                    TypeKey.parse(itemKey), 1, item, () -> ItemSpec.of(itemKey, 1));
        }

        public ItemContext pickup(FPlayer player, UUID item, String itemKey) {
            return new ItemContext(EventSource.ITEM_PICKUP, WorldLocPos.of(0, 64, 0, "world"), Actors.player(player), null,
                    TypeKey.parse(itemKey), 1, item, () -> ItemSpec.of(itemKey, 1));
        }

        public DamageContext kill(FPlayer killer, UUID victim, String victimKey) {
            return new DamageContext(EventSource.KILL, WorldLocPos.of(0, 64, 0, "world"), Actors.player(killer), null,
                    creature(victim, victimKey, null));
        }

        private static Actor creature(UUID id, String key, FPlayer owner) {
            return Actor.entity(TypeKey.parse(key), ECEntityType.ANIMAL, owner, () -> new NativeEntity(id));
        }
    }
}
