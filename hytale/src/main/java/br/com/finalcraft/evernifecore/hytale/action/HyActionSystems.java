package br.com.finalcraft.evernifecore.hytale.action;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.Actions;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import br.com.finalcraft.evernifecore.hytale.region.HyRegionMoveSystem;
import br.com.finalcraft.evernifecore.hytale.region.HySelectionItems;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.selection.SelectionClick;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.ICancellableEcsEvent;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.event.events.ecs.EnvironmentBreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.RespawnEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import jakarta.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The actions on Hytale: one ECS System per kind of game event, plus the tick that follows region borders
 * ({@link HyRegionMoveSystem}), registered once while the core sets up - the server takes Systems only then - and
 * always on the server. What switches a System on and off is {@link EventSourceUsage}: every handle first asks whether
 * its source is needed - by a region, or by a listener of its action event - and while it is not, returns before building
 * anything. Past the gate the regions decide and the {@code Pre} action event is posted; a denial or a cancelled
 * {@code Pre} cancels the event (the deny message is the flag's to send), and an event left uncancelled posts its
 * {@code Post}.
 *
 * <p>0.7.2 fires no explosion event: an explosion reaches the regions only block by block, as a
 * {@link DamageBlockEvent} with no actor, decided as a BREAK by the environment. {@code deny-explosion} has no effect
 * here. {@link #SERVED_SOURCES} are the core sources a System here raises; every flag that needs another one says in
 * the boot log that it has no effect on Hytale.</p>
 */
public final class HyActionSystems {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    /** The core sources raised here - by a System, or TICK by the core's own region tick: the ones a flag needs to have an effect on Hytale. */
    public static final Set<EventSource> SERVED_SOURCES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(EventSource.MOVE,
            EventSource.BREAK, EventSource.PLACE, EventSource.INTERACT, EventSource.DAMAGE, EventSource.SPAWN, EventSource.ITEM_DROP,
            EventSource.ITEM_PICKUP, EventSource.TICK, EventSource.KILL, EventSource.CRAFT, EventSource.RESPAWN, EventSource.EXPLORE,
            EventSource.COMMAND, EventSource.INTERACT_NPC)));
    /**
     * The served sources whose System posts a cancellable {@code Pre}: the ones the game asks before the outcome. COMMAND's
     * is posted by the command framework, before one of its commands runs: the server has no command event.
     */
    public static final Set<EventSource> PRE_SOURCES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(EventSource.BREAK,
            EventSource.PLACE, EventSource.INTERACT, EventSource.DAMAGE, EventSource.SPAWN, EventSource.ITEM_DROP, EventSource.ITEM_PICKUP,
            EventSource.CRAFT, EventSource.RESPAWN, EventSource.COMMAND, EventSource.INTERACT_NPC)));
    /** The killer of a death the server recorded no damage for. */
    public static final TypeKey UNKNOWN_DEATH = TypeKey.of("environment", "death");
    /** The actor kinds no context built here ever carries: Hytale has no fake players. */
    public static final Set<ECEntityType> NEVER_CLASSIFIED = Collections.singleton(ECEntityType.FAKE_PLAYER);
    static final HyRegionMoveSystem MOVE = new HyRegionMoveSystem(RegionMovementTracker::get);
    /** The spawn-point of each player the server is respawning, applied when its respawn ends ({@link RespawnedSystem}). */
    static final Map<UUID, WorldLocPos> RESPAWN_POINTS = new ConcurrentHashMap<>();

    private HyActionSystems() {
    }

    /** How a message names this server: {@code Hytale 0.7.2}. */
    static String platformName() {
        String version = ManifestUtil.getImplementationVersion();
        return version == null ? "Hytale" : "Hytale " + version;
    }

    /**
     * Puts every region System on {@code plugin}'s entity store, and the disconnect that ends a player's movement
     * session on its events; true the first time, false after - the server refuses a System class twice.
     */
    public static boolean register(PluginBase plugin) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return false;
        }
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> {
                    MOVE.quit(event.getPlayerRef().getUuid(), () -> FCHytaleUtil.adapt(event.getPlayerRef()));
                    HyPlatform.PLAYER_STATE.forget(event.getPlayerRef().getUuid()); //after the quit restored what the regions changed
                    RESPAWN_POINTS.remove(event.getPlayerRef().getUuid());
                });
        ComponentRegistryProxy<EntityStore> registry = plugin.getEntityStoreRegistry();
        registry.registerSystem(MOVE);
        registry.registerSystem(new BreakSystem());
        registry.registerSystem(new DamageBlockSystem());
        registry.registerSystem(new ExplosionBlockSystem());
        registry.registerSystem(new PlaceSystem());
        registry.registerSystem(new UseBlockSystem());
        registry.registerSystem(new UseEntitySystem());
        registry.registerSystem(new InteractionStartSystem());
        registry.registerSystem(new DamageSystem());
        registry.registerSystem(new SpawnSystem());
        registry.registerSystem(new ItemDropSystem());
        registry.registerSystem(new ItemPickupSystem());
        registry.registerSystem(new TerrainBreakSystem());
        registry.registerSystem(new TerrainDamageSystem());
        registry.registerSystem(new TerrainPlaceSystem());
        registry.registerSystem(new TerrainExplosionSystem());
        registry.registerSystem(new CraftSystem());
        registry.registerSystem(new CraftedSystem());
        registry.registerSystem(new KillSystem());
        registry.registerSystem(new RespawnSystem());
        registry.registerSystem(new RespawnedSystem());
        registry.registerSystem(new EnvironmentBreakSystem());
        declareSupport(platformName());
        return true;
    }

    /** Tells the core what this platform raises and lacks; each flag that cannot act in full here then says so in the boot log. */
    static void declareSupport(String platform) {
        ActionSupport.declare(platform, SERVED_SOURCES, PRE_SOURCES, false, NEVER_CLASSIFIED);
        FlagRegistry.get().setMissingCapabilities(HyPlatform.PLAYER_STATE.getUnsupported());
    }

    /** {@link #judge(EventSource, String, ICancellableEcsEvent, Supplier, boolean)} for an event that is the outcome itself. */
    static boolean judge(EventSource source, String world, ICancellableEcsEvent event, Supplier<? extends ActionContext> context) {
        return judge(source, world, event, context, true);
    }

    /**
     * What every System does with its event: nothing while {@code source} is not needed - by a region or a listener of its
     * action event -, in a disabled world or on an event already cancelled; else the regions decide, the {@code Pre} is
     * posted, and a denial or a cancelled {@code Pre} cancels the event. When {@code postsOutcome}, an event this System
     * leaves uncancelled then posts its {@code Post} - Hytale has no last-word priority, so a System of another plugin
     * that runs later may still cancel it. {@code context} is built only past the gate, and again only for a listened Post.
     *
     * @param postsOutcome false for a step on the way to the outcome (a hit on a block is not its break)
     * @return whether the event was denied here
     */
    static boolean judge(EventSource source, String world, ICancellableEcsEvent event, Supplier<? extends ActionContext> context,
                         boolean postsOutcome) {
        if (!EventSourceUsage.get().isNeeded(source) || event.isCancelled() || HyActionContexts.isDisabled(world)) {
            return false;
        }
        if (Actions.denied(context.get())) {
            event.setCancelled(true);
            return true;
        }
        if (postsOutcome) {
            Actions.post(source, context);
        }
        return false;
    }

    /** Judges a hit like {@link #judge}; one let through is scaled by the factor a flag wrote on its context (damage-multiplier). */
    static boolean judgeDamage(String world, Damage damage, Supplier<DamageContext> context) {
        DamageContext[] asked = new DamageContext[1];
        boolean denied = judge(EventSource.DAMAGE, world, damage, () -> {
            DamageContext built = context.get();
            if (asked[0] == null) {
                asked[0] = built; //the one the regions decided on; a listened Post builds its own
            }
            return built;
        });
        if (!denied && asked[0] != null && asked[0].getDamageMultiplier() != 1.0) {
            damage.setAmount((float) (damage.getAmount() * asked[0].getDamageMultiplier()));
        }
        return denied;
    }

    /** An outcome with no point before it (a death): its {@code Post} only, built only past the source gate. */
    static void posted(EventSource source, String world, Supplier<? extends ActionContext> context) {
        if (EventSourceUsage.get().isNeeded(source) && !HyActionContexts.isDisabled(world)) {
            Actions.post(source, context);
        }
    }

    /** A craft of {@code crafts} times the recipe whose primary output is {@code output}: the amount is every item it gives. */
    static ItemContext crafted(String world, Vector3d at, Actor crafter, @Nullable Object event, MaterialQuantity output, int crafts) {
        return new ItemContext(EventSource.CRAFT, HyActionContexts.position(world, at), crafter, event, HyActionContexts.key(output.getItemId()),
                output.getQuantity() * crafts, null, null);
    }

    /** One block an explosion is about to damage, in {@code world}: a BREAK by the environment - a step, never posted as broken. */
    static boolean explode(String world, DamageBlockEvent event) {
        return judge(EventSource.BREAK, world, event, () -> new BlockContext(EventSource.BREAK,
                HyActionContexts.position(world, event.getTargetBlock()), Actor.environment(HyActionContexts.EXPLOSION), event,
                HyActionContexts.key(event.getBlockType()), null), false);
    }

    /**
     * A block the world itself broke - fire, a harvest nobody did - in {@code world}: kept by the {@code track-changes}
     * journals and posted as a BREAK by {@link HyActionContexts#ENVIRONMENT_BREAK}. The server cannot be told no: the event
     * is not cancellable, so no flag denies it and no {@code Pre} is posted.
     */
    static void environmentBreak(String world, EnvironmentBreakBlockEvent event) {
        if (!EventSourceUsage.get().isNeeded(EventSource.BREAK) || HyActionContexts.isDisabled(world)) {
            return;
        }
        Vector3i block = event.getTargetBlock();
        RegionTerrain.recordChange(world, block.x, block.y, block.z,
                content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(world, block.x, block.y, block.z, content));
        Actions.post(EventSource.BREAK, () -> new BlockContext(EventSource.BREAK, HyActionContexts.position(world, block),
                Actor.environment(HyActionContexts.ENVIRONMENT_BREAK), event, HyActionContexts.key(event.getBlockType()), null));
    }

    /** Where the regions send {@code player}, dead at {@code diedAt}, back to life: the {@code spawn-point} resolved there; null for none. */
    static @Nullable WorldLocPos spawnPointOf(String world, Vector3d diedAt, @Nullable FPlayer player) {
        return HyActionContexts.isDisabled(world) ? null : RegionLookup.get().resolve(CoreFlags.SPAWN_POINT, world, diedAt.x, diedAt.y, diedAt.z, player);
    }

    /** The click an entity interaction of {@code type} is on an NPC: Use is the right click, Primary the left; null for any other. */
    static @Nullable EntityContext.ClickType npcClickOf(InteractionType type) {
        return type == InteractionType.Use ? EntityContext.ClickType.RIGHT : type == InteractionType.Primary ? EntityContext.ClickType.LEFT : null;
    }

    /**
     * A hit a player lands with his own hand - not a projectile - on an NPC is the left click on it: {@code interact-npc}
     * with {@link EntityContext.ClickType#LEFT}. A denial cancels the hit.
     */
    static boolean punchNpc(String world, Damage damage, Supplier<DamageContext> context) {
        if (!EventSourceUsage.get().isNeeded(EventSource.INTERACT_NPC)) {
            return false;
        }
        DamageContext probe = context.get();
        if (probe.getActor().getEntityType() != ECEntityType.PLAYER || probe.getVictim().getNpc() == null) {
            return false;   //a projectile acts as itself, with its shooter only behind it
        }
        return judge(EventSource.INTERACT_NPC, world, damage, () -> {
            DamageContext hit = context.get();   //fresh actors for every dispatch
            return new EntityContext(EventSource.INTERACT_NPC, hit.getPosition(), hit.getActor(), damage, hit.getVictim(), null,
                    EntityContext.ClickType.LEFT);
        });
    }

    /**
     * An item the entity at {@code actor} drops or picks up, asked where that entity stands. An event naming no item, or
     * an entity with no position, is left alone.
     */
    static boolean onItem(EventSource source, Ref<EntityStore> actor, Store<EntityStore> store, ICancellableEcsEvent event,
                          @Nullable ItemStack item) {
        if (!EventSourceUsage.get().isNeeded(source)) {
            return false;
        }
        TypeKey key = HyActionContexts.itemKey(item);
        Vector3d at = key == null ? null : positionOf(actor, store);
        if (at == null) {
            return false;
        }
        String world = worldOf(store);
        return judge(source, world, event, () -> new ItemContext(source, HyActionContexts.position(world, at),
                HyActionContexts.actor(actor, store), event, key, item.getQuantity(), null,
                () -> ItemSpec.of(key.toString(), item.getQuantity()).withNativeItem(new NativeItem(item))));
    }

    /** What spawns a creature nobody spawned on purpose: the world itself. */
    public static final TypeKey NATURAL_SPAWN = TypeKey.of("environment", "spawn");

    /**
     * Whether the regions or a subscriber of the {@code Pre} deny the spawn {@code context} describes; one allowed posts
     * its {@code Post}. Nothing is built while SPAWN is not needed.
     */
    public static boolean spawnDenied(String world, Supplier<SpawnContext> context) {
        if (!EventSourceUsage.get().isNeeded(EventSource.SPAWN) || HyActionContexts.isDisabled(world)) {
            return false;
        }
        if (Actions.denied(context.get())) {
            return true;
        }
        Actions.post(EventSource.SPAWN, context);
        return false;
    }

    /**
     * A click with the selection tool belongs to the selection, never to the regions: true when it was one, and the
     * event is then cancelled, so the tool never breaks, tills or uses. An open selection holds INTERACT, so with that
     * gate closed there is none to click for. A held left click hits its block again and again: the last point
     * clicked again adds nothing.
     */
    public static boolean select(@Nullable ItemStack hand, ICancellableEcsEvent event, Supplier<FPlayer> player, String world, Vector3i target,
                          Supplier<SelectionClick> click) {
        if (!EventSourceUsage.get().isNeeded(EventSource.INTERACT) || event.isCancelled() || !HySelectionItems.isSelectionTool(hand)) {
            return false;
        }
        FPlayer clicker = player.get();
        if (clicker == null) {
            return false;
        }
        SelectionClick kind = click.get();
        if (kind == SelectionClick.LEFT && isLastPoint(SelectionTool.getSession(clicker), world, target)) {
            event.setCancelled(true);
            return true;
        }
        if (SelectionTool.handleClick(clicker, WorldBlockPos.of(target.x, target.y, target.z, world), kind)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private static boolean isLastPoint(@Nullable SelectionSession session, String world, Vector3i target) {
        if (session == null || !session.getWorld().equals(world) || session.getPoints().isEmpty()) {
            return false;
        }
        List<BlockPos> points = session.getPoints();
        BlockPos last = points.get(points.size() - 1);
        return last.getX() == target.x && last.getY() == target.y && last.getZ() == target.z;
    }

    private static boolean clicked(Ref<EntityStore> actor, Store<EntityStore> store, ICancellableEcsEvent event, Vector3i target,
                                   @Nullable ItemStack hand, boolean left) {
        return select(hand, event, () -> HyActionContexts.player(actor, store), worldOf(store), target,
                () -> left ? SelectionClick.LEFT : isCrouching(actor, store) ? SelectionClick.SNEAK_RIGHT : SelectionClick.RIGHT);
    }

    private static boolean isCrouching(Ref<EntityStore> ref, Store<EntityStore> store) {
        MovementStatesComponent states = store.getComponent(ref, MovementStatesComponent.getComponentType());
        return states != null && states.getMovementStates() != null && states.getMovementStates().crouching;
    }

    private static String worldOf(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getName();
    }

    private static boolean onBlock(EventSource source, Ref<EntityStore> actor, Store<EntityStore> store, ICancellableEcsEvent event,
                                   Vector3i target, TypeKey blockKey, @Nullable ItemStack hand) {
        return onBlock(source, actor, store, event, target, blockKey, hand, true);
    }

    private static boolean onBlock(EventSource source, Ref<EntityStore> actor, Store<EntityStore> store, ICancellableEcsEvent event,
                                   Vector3i target, TypeKey blockKey, @Nullable ItemStack hand, boolean postsOutcome) {
        String world = worldOf(store);
        return judge(source, world, event, () -> new BlockContext(source, HyActionContexts.position(world, target),
                HyActionContexts.actor(actor, store), event, blockKey, HyActionContexts.itemKey(hand)), postsOutcome);
    }

    private static @Nullable Vector3d positionOf(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = ref.isValid() ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
        return transform == null ? null : transform.getPosition();
    }

    /** A block event the game fires at whoever acts - a player, an NPC - on any entity. */
    private abstract static class ByActor<E extends EcsEvent> extends EntityEventSystem<EntityStore, E> {

        ByActor(Class<E> type) {
            super(type);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    private static final class BreakSystem extends ByActor<BreakBlockEvent> {
        BreakSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           BreakBlockEvent event) {
            if (clicked(chunk.getReferenceTo(index), store, event, event.getTargetBlock(), event.getItemInHand(), true)) {
                return;
            }
            onBlock(EventSource.BREAK, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyActionContexts.key(event.getBlockType()), event.getItemInHand());
        }
    }

    /**
     * Each hit on a block is a BREAK too: a denied one - or one whose {@code Pre} a subscriber cancelled - leaves no crack,
     * and the block never reaches the break. A hit is not the break: only {@link BreakSystem} posts the {@code Post}.
     */
    private static final class DamageBlockSystem extends ByActor<DamageBlockEvent> {
        DamageBlockSystem() {
            super(DamageBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           DamageBlockEvent event) {
            if (clicked(chunk.getReferenceTo(index), store, event, event.getTargetBlock(), event.getItemInHand(), true)) {
                return;
            }
            onBlock(EventSource.BREAK, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyActionContexts.key(event.getBlockType()), event.getItemInHand(), false);
        }
    }

    /** The same block damage fired at the whole store, with nobody acting: an explosion, block by block. */
    private static final class ExplosionBlockSystem extends WorldEventSystem<EntityStore, DamageBlockEvent> {
        ExplosionBlockSystem() {
            super(DamageBlockEvent.class);
        }

        @Override
        public void handle(Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            explode(worldOf(store), event);
        }
    }

    /**
     * Feeds the {@code track-changes} journals ({@link RegionTerrain}) after the System that decides {@code type} ran, and
     * only while nobody cancelled the event: the block is still the original here, the change comes after the event.
     * One subclass per event: the server takes each System class once.
     */
    private abstract static class TerrainSystem<E extends EcsEvent & ICancellableEcsEvent> extends ByActor<E> {
        private final Class<? extends ISystem<EntityStore>> decider;
        private final EventSource source;
        private final Function<E, Vector3i> target;

        TerrainSystem(Class<E> type, Class<? extends ISystem<EntityStore>> decider, EventSource source, Function<E, Vector3i> target) {
            super(type);
            this.decider = decider;
            this.source = source;
            this.target = target;
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(new SystemDependency<>(Order.AFTER, decider));
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, E event) {
            record(source, worldOf(store), event, target.apply(event));
        }
    }

    private static final class TerrainBreakSystem extends TerrainSystem<BreakBlockEvent> {
        TerrainBreakSystem() {
            super(BreakBlockEvent.class, BreakSystem.class, EventSource.BREAK, BreakBlockEvent::getTargetBlock);
        }
    }

    private static final class TerrainDamageSystem extends TerrainSystem<DamageBlockEvent> {
        TerrainDamageSystem() {
            super(DamageBlockEvent.class, DamageBlockSystem.class, EventSource.BREAK, DamageBlockEvent::getTargetBlock);
        }
    }

    private static final class TerrainPlaceSystem extends TerrainSystem<PlaceBlockEvent> {
        TerrainPlaceSystem() {
            super(PlaceBlockEvent.class, PlaceSystem.class, EventSource.PLACE, PlaceBlockEvent::getTargetBlock);
        }
    }

    /** {@link TerrainSystem} for the blocks an explosion damages, after {@link ExplosionBlockSystem}. */
    private static final class TerrainExplosionSystem extends WorldEventSystem<EntityStore, DamageBlockEvent> {
        TerrainExplosionSystem() {
            super(DamageBlockEvent.class);
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(new SystemDependency<>(Order.AFTER, ExplosionBlockSystem.class));
        }

        @Override
        public void handle(Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            record(EventSource.BREAK, worldOf(store), event, event.getTargetBlock());
        }
    }

    private static void record(EventSource source, String world, ICancellableEcsEvent event, Vector3i block) {
        if (!EventSourceUsage.get().isNeeded(source) || event.isCancelled() || HyActionContexts.isDisabled(world)) {
            return;
        }
        RegionTerrain.recordChange(world, block.x, block.y, block.z,
                content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(world, block.x, block.y, block.z, content));
    }

    /** The block placed is what the hand holds: the event names no block type. */
    private static final class PlaceSystem extends ByActor<PlaceBlockEvent> {
        PlaceSystem() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           PlaceBlockEvent event) {
            TypeKey placed = HyActionContexts.itemKey(event.getItemInHand());
            onBlock(EventSource.PLACE, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    placed != null ? placed : HyActionContexts.key((BlockType) null), event.getItemInHand());
        }
    }

    /** A denied use also fails the interaction chain that carries it, or the chain would play the use out anyway. */
    private static final class UseBlockSystem extends ByActor<UseBlockEvent.Pre> {
        UseBlockSystem() {
            super(UseBlockEvent.Pre.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           UseBlockEvent.Pre event) {
            InteractionContext context = event.getContext();
            ItemStack hand = context == null ? null : context.getHeldItem();
            if (clicked(chunk.getReferenceTo(index), store, event, event.getTargetBlock(), hand, false)) {
                return;
            }
            if (onBlock(EventSource.INTERACT, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyActionContexts.key(event.getBlockType()), hand)) {
                InteractionChain chain = context == null ? null : context.getChain();
                if (chain != null) {
                    chain.setServerState(InteractionState.Failed);
                }
            }
        }
    }

    private static final class UseEntitySystem extends ByActor<UseEntityEvent.Pre> {
        UseEntitySystem() {
            super(UseEntityEvent.Pre.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           UseEntityEvent.Pre event) {
            Ref<EntityStore> user = chunk.getReferenceTo(index);
            if (decideOnEntity(EventSource.INTERACT, user, event.getTargetEntity(), store, event, null)) {
                return;
            }
            //a use of an NPC is also an interact-npc, the source the NPC providers raise on every platform
            EntityContext.ClickType click = npcClickOf(event.getInteractionType());
            if (click != null && EventSourceUsage.get().isNeeded(EventSource.INTERACT_NPC)
                    && event.getTargetEntity() != null && HyActionContexts.actor(event.getTargetEntity(), store).getNpc() != null) {
                InteractionContext context = event.getContext();
                decideOnEntity(EventSource.INTERACT_NPC, user, event.getTargetEntity(), store, event,
                        context == null ? null : HyActionContexts.itemKey(context.getHeldItem()), click);
            }
        }
    }

    private static boolean decideOnEntity(EventSource source, Ref<EntityStore> user, @Nullable Ref<EntityStore> target, Store<EntityStore> store,
                                          ICancellableEcsEvent event, @Nullable TypeKey itemInHand) {
        return decideOnEntity(source, user, target, store, event, itemInHand, EntityContext.ClickType.RIGHT);
    }

    private static boolean decideOnEntity(EventSource source, Ref<EntityStore> user, @Nullable Ref<EntityStore> target, Store<EntityStore> store,
                                          ICancellableEcsEvent event, @Nullable TypeKey itemInHand, EntityContext.ClickType click) {
        Vector3d at = target == null ? null : positionOf(target, store);
        if (at == null) {
            return false;
        }
        String world = worldOf(store);
        return judge(source, world, event, () -> new EntityContext(source, HyActionContexts.position(world, at),
                HyActionContexts.actor(user, store), event, HyActionContexts.actor(target, store), itemInHand, click));
    }

    /**
     * The interactions no use event reports - a hammer, a bucket, a hoe, a light - as they start. Primary (hitting,
     * mining) belongs to BREAK and DAMAGE, and Use to the use events, which name the real block; the rest of the types
     * are not a player's own hand. The block a chain starts at has no type the public API reads, so it is the server's
     * {@code Unknown}.
     */
    private static final class InteractionStartSystem extends ByActor<InteractionChainStartEvent> {
        InteractionStartSystem() {
            super(InteractionChainStartEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           InteractionChainStartEvent event) {
            InteractionType type = event.getType();
            if (type != InteractionType.Secondary && type != InteractionType.Ability1 && type != InteractionType.Ability2
                    && type != InteractionType.Ability3 && type != InteractionType.Ability4) {
                return;
            }
            InteractionContext context = event.getContext();
            if (context == null) {
                return;
            }
            Ref<EntityStore> user = chunk.getReferenceTo(index);
            Ref<EntityStore> target = chainTargetEntity(event, store.getExternalData());
            if (target != null) {
                decideOnEntity(EventSource.INTERACT, user, target, store, event, HyActionContexts.itemKey(context.getHeldItem()));
                return;
            }
            Vector3i at = chainTargetBlock(event);
            if (at != null) {
                if (type == InteractionType.Secondary && clicked(user, store, event, at, context.getHeldItem(), false)) {
                    return;
                }
                onBlock(EventSource.INTERACT, user, store, event, at, HyActionContexts.key((BlockType) null), context.getHeldItem());
            }
        }
    }

    /**
     * The entity the client aimed a starting chain at, by the network id in the chain's packet data: the server puts
     * the context's target meta only after {@link InteractionChainStartEvent}. Null for no entity (a negative id, as the
     * server reads it) or one the world no longer knows.
     */
    static @Nullable Ref<EntityStore> chainTargetEntity(InteractionChainStartEvent event, EntityStore entities) {
        InteractionChainData data = event.getChain().getChainData();
        Ref<EntityStore> target = data == null || data.entityId < 0 ? null : entities.getRefFromNetworkId(data.entityId);
        return target != null && target.isValid() ? target : null;
    }

    /**
     * The block the client aimed a starting chain at, from the chain's packet data; null for none. It is the block
     * clicked, not the root of a multiblock the server maps it to later: the region judges where the player acted.
     */
    static @Nullable Vector3i chainTargetBlock(InteractionChainStartEvent event) {
        InteractionChainData data = event.getChain().getChainData();
        BlockPosition block = data == null ? null : data.blockPosition;
        return block == null ? null : new Vector3i(block.x, block.y, block.z);
    }

    private static final class ItemDropSystem extends ByActor<DropItemEvent.Drop> {
        ItemDropSystem() {
            super(DropItemEvent.Drop.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           DropItemEvent.Drop event) {
            onItem(EventSource.ITEM_DROP, chunk.getReferenceTo(index), store, event, event.getItemStack());
        }
    }

    /** The pickup the player asks for; 0.7.2 raises no event for an item picked up by walking over it (PlayerItemEntityPickupSystem). */
    private static final class ItemPickupSystem extends ByActor<InteractivelyPickupItemEvent> {
        ItemPickupSystem() {
            super(InteractivelyPickupItemEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           InteractivelyPickupItemEvent event) {
            onItem(EventSource.ITEM_PICKUP, chunk.getReferenceTo(index), store, event, event.getItemStack());
        }
    }

    /**
     * A creature the world spawns, asked where it appears; a denied one is removed before it ever ticks. One added on
     * LOAD - out of a chunk or a prefab - already existed, and is never asked.
     */
    private static final class SpawnSystem extends RefSystem<EntityStore> {

        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            if (reason != AddReason.SPAWN) {
                return;
            }
            Vector3d at = positionOf(ref, store);
            if (at == null) {
                return;
            }
            String world = worldOf(store);
            if (spawnDenied(world, () -> new SpawnContext(HyActionContexts.position(world, at), Actor.environment(NATURAL_SPAWN), null,
                    HyActionContexts.actor(ref, store), reason.name()))) {
                buffer.removeEntity(ref, RemoveReason.REMOVE);
            }
        }

        @Override
        public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        }
    }

    /** The recipe's primary output, when it names an item; null for a recipe that gives none. */
    private static @Nullable MaterialQuantity outputOf(@Nullable CraftingRecipe recipe) {
        MaterialQuantity output = recipe == null ? null : recipe.getPrimaryOutput();
        return output == null || output.getItemId() == null ? null : output;
    }

    /**
     * A craft about to happen, asked where the crafter stands: a denial, or a cancelled {@code Pre}, cancels it - the
     * game checks before it takes the inputs. The {@code Post} waits for the game's own after-craft event
     * ({@link CraftedSystem}), when the items were given.
     */
    private static final class CraftSystem extends ByActor<CraftRecipeEvent.Pre> {
        CraftSystem() {
            super(CraftRecipeEvent.Pre.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           CraftRecipeEvent.Pre event) {
            MaterialQuantity output = outputOf(event.getCraftedRecipe());
            Ref<EntityStore> crafter = chunk.getReferenceTo(index);
            Vector3d at = output == null ? null : positionOf(crafter, store);
            if (at == null) {
                return;
            }
            String world = worldOf(store);
            judge(EventSource.CRAFT, world, event, () -> crafted(world, at, HyActionContexts.actor(crafter, store), event, output,
                    event.getQuantity()), false);
        }
    }

    /** The {@code Post} of a craft the game finished and left uncancelled. */
    private static final class CraftedSystem extends ByActor<CraftRecipeEvent.Post> {
        CraftedSystem() {
            super(CraftRecipeEvent.Post.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           CraftRecipeEvent.Post event) {
            MaterialQuantity output = event.isCancelled() ? null : outputOf(event.getCraftedRecipe());
            Ref<EntityStore> crafter = chunk.getReferenceTo(index);
            Vector3d at = output == null ? null : positionOf(crafter, store);
            if (at == null) {
                return;
            }
            String world = worldOf(store);
            posted(EventSource.CRAFT, world, () -> crafted(world, at, HyActionContexts.actor(crafter, store), event, output, event.getQuantity()));
        }
    }

    /**
     * A death, on the server's own death hook - the {@link DeathComponent} added to the dying entity, where the game builds
     * its kill feed - posted where the victim fell, with the killer the death's damage names. A death cannot be
     * cancelled: no {@code Pre}, and the regions are not asked.
     */
    private static final class KillSystem extends DeathSystems.OnDeathSystem {

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }

        @Override
        public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            if (!EventSourceUsage.get().isNeeded(EventSource.KILL)) {
                return;
            }
            Vector3d at = positionOf(ref, store);
            if (at == null) {
                return;
            }
            String world = worldOf(store);
            Damage damage = death.getDeathInfo();
            posted(EventSource.KILL, world, () -> new DamageContext(EventSource.KILL, HyActionContexts.position(world, at),
                    damage == null ? Actor.environment(UNKNOWN_DEATH) : DamageSystem.attacker(damage, store), damage,
                    HyActionContexts.actor(ref, store)));
        }
    }

    /**
     * A player about to respawn, asked where he died: a denial, or a cancelled {@code Pre}, keeps him dead - the game
     * checks the event before it respawns him; one left uncancelled posts its {@code Post} and goes to the
     * {@code spawn-point} resolved where he died. The server's own respawn teleports him later, off this event (its
     * respawn controller adds a {@link Teleport} once the point it picked is ready), so the point waits for the end of
     * that respawn ({@link RespawnedSystem}); a respawn with no death behind it - a plugin firing the event - goes now.
     */
    private static final class RespawnSystem extends ByActor<RespawnEvent> {
        RespawnSystem() {
            super(RespawnEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           RespawnEvent event) {
            if (!EventSourceUsage.get().isNeeded(EventSource.RESPAWN)) {
                return;   //no region holds a spawn-point, and nobody listens
            }
            Ref<EntityStore> player = chunk.getReferenceTo(index);
            TransformComponent transform = store.getComponent(player, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            Vector3d at = transform.getPosition();
            String world = worldOf(store);
            if (judge(EventSource.RESPAWN, world, event, () -> new SimpleContext(EventSource.RESPAWN, HyActionContexts.position(world, at),
                    HyActionContexts.actor(player, store), event)) || event.isCancelled()) {
                return;
            }
            PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
            WorldLocPos point = playerRef == null ? null : spawnPointOf(world, at, HyActionContexts.player(player, store));
            if (point == null) {
                return;
            }
            if (store.getComponent(player, DeathComponent.getComponentType()) == null) {
                buffer.putComponent(player, Teleport.getComponentType(),
                        HyRegionMoveSystem.teleport(point, store.getExternalData().getWorld(), transform.getRotation()));
            } else {
                RESPAWN_POINTS.put(playerRef.getUuid(), point);
            }
        }
    }

    /**
     * The end of a respawn - the server removes the {@link DeathComponent} once its own respawn teleport is on the player:
     * a spawn-point {@link RespawnSystem} held for him goes on once that teleport has left him
     * ({@link #teleportWhenFree}).
     */
    private static final class RespawnedSystem extends DeathSystems.OnDeathSystem {

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        }

        @Override
        public void onComponentRemoved(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            WorldLocPos point = player == null ? null : RESPAWN_POINTS.remove(player.getUuid());
            if (point != null) {
                teleportWhenFree(store.getExternalData().getWorld(), ref, point, SPAWN_POINT_WAIT_TICKS);
            }
        }
    }

    /** How long a spawn-point waits for the server's respawn teleport to leave the player: two seconds at 30 TPS. */
    static final int SPAWN_POINT_WAIT_TICKS = 60;

    /**
     * Puts the spawn-point teleport on the first tick the player carries no {@link Teleport} nor {@link PendingTeleport}.
     * The server's respawn teleport is removed by its teleport system's own buffer, and a Teleport set while it is still
     * there is a replacement that system ignores. Stops when the player leaves; past the wait it gives up with a warning.
     */
    private static void teleportWhenFree(World world, Ref<EntityStore> ref, WorldLocPos point, int ticksLeft) {
        EverNifeCore.getPlatform().runOnWorldThreadNextTick(world.getName(), () -> {
            if (!ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store.getComponent(ref, Teleport.getComponentType()) != null
                    || store.getComponent(ref, PendingTeleport.getComponentType()) != null) {
                if (ticksLeft > 0) {
                    teleportWhenFree(world, ref, point, ticksLeft - 1);
                } else {
                    EverNifeCore.getLog().warning("The spawn-point " + point + " was not applied: the player still carried a teleport "
                            + SPAWN_POINT_WAIT_TICKS + " ticks after his respawn, and the region's point never overrides one"
                            + " in flight. If something teleports players on respawn, that teleport is where they land.");
                }
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                store.addComponent(ref, Teleport.getComponentType(), HyRegionMoveSystem.teleport(point, world, transform.getRotation()));
            }
        });
    }

    /** {@link #environmentBreak} for every block the world breaks by itself; the event comes at the whole store. */
    private static final class EnvironmentBreakSystem extends WorldEventSystem<EntityStore, EnvironmentBreakBlockEvent> {
        EnvironmentBreakSystem() {
            super(EnvironmentBreakBlockEvent.class);
        }

        @Override
        public void handle(Store<EntityStore> store, CommandBuffer<EntityStore> buffer, EnvironmentBreakBlockEvent event) {
            environmentBreak(worldOf(store), event);
        }
    }

    /**
     * Damage to an entity, asked where the victim stands, in the filter group where the game's own protection runs; a
     * denial cancels it - the amount is never zeroed, which would still knock back and play the hit - and one let through
     * is scaled by the factor the regions wrote on it. A hit a player lands on an NPC is also its left click ({@link #punchNpc}).
     */
    private static final class DamageSystem extends DamageEventSystem {

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Override
        public Query<EntityStore> getQuery() {
            return TransformComponent.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           Damage damage) {
            Vector3d at = chunk.getComponent(index, TransformComponent.getComponentType()).getPosition();
            Ref<EntityStore> victim = chunk.getReferenceTo(index);
            String world = worldOf(store);
            Supplier<DamageContext> hit = () -> new DamageContext(EventSource.DAMAGE, HyActionContexts.position(world, at),
                    attacker(damage, store), damage, HyActionContexts.actor(victim, store));
            if (!judgeDamage(world, damage, hit)) {
                punchNpc(world, damage, hit);
            }
        }

        private static Actor attacker(Damage damage, Store<EntityStore> store) {
            Damage.Source source = damage.getSource();
            if (source instanceof Damage.ProjectileSource projectile && projectile.getProjectile() != null
                    && projectile.getProjectile().isValid()) {
                Ref<EntityStore> shooter = projectile.getRef();
                FPlayer player = shooter != null && shooter.isValid() ? HyActionContexts.player(shooter, store) : null;
                return HyActionContexts.projectile(projectile.getProjectile(), player, store);
            }
            if (source instanceof Damage.EntitySource entity && entity.getRef() != null && entity.getRef().isValid()) {
                return HyActionContexts.actor(entity.getRef(), store);
            }
            DamageCause cause = damage.getCause();
            return Actor.environment(TypeKey.of("environment", cause == null ? "damage" : cause.getId()));
        }
    }
}
