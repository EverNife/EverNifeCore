package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.ICancellableEcsEvent;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The region protection on Hytale: one ECS System per kind of game event, registered once while the core sets up -
 * the server takes Systems only then - and always on the server. What switches a System on and off is
 * {@link EventSourceUsage}: every handle first asks whether some region needs its source, and while none does it
 * returns before building anything. A denial cancels the event; the deny message is the flag's to send.
 *
 * <p>0.7.1 fires no explosion event: an explosion reaches the regions only block by block, as a
 * {@link DamageBlockEvent} with no actor, decided as a BREAK by the environment. {@code deny-explosion} has no effect
 * here.</p>
 */
public final class HyRegionSystems {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private HyRegionSystems() {
    }

    /** Puts every region System on {@code registry}; true the first time, false after - the server refuses a System class twice. */
    public static boolean register(ComponentRegistryProxy<EntityStore> registry) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return false;
        }
        registry.registerSystem(new BreakSystem());
        registry.registerSystem(new DamageBlockSystem());
        registry.registerSystem(new ExplosionBlockSystem());
        registry.registerSystem(new PlaceSystem());
        registry.registerSystem(new UseBlockSystem());
        registry.registerSystem(new UseEntitySystem());
        registry.registerSystem(new InteractionStartSystem());
        registry.registerSystem(new DamageSystem());
        return true;
    }

    /**
     * What every System does with its event: nothing while no region needs {@code source}, in a disabled world or on an
     * event already cancelled; else it asks the regions and cancels a denial. {@code context} is built only past the gate.
     *
     * @return whether the event was denied here
     */
    static boolean judge(EventSource source, String world, ICancellableEcsEvent event, Supplier<? extends ActionContext> context) {
        if (!EventSourceUsage.get().isNeeded(source) || event.isCancelled() || HyRegionContexts.isDisabled(world)) {
            return false;
        }
        if (RegionLookup.get().decide(context.get()) == Decision.DENY) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    /** One block an explosion is about to damage, in {@code world}: a BREAK by the environment. */
    static boolean explode(String world, DamageBlockEvent event) {
        return judge(EventSource.BREAK, world, event, () -> new BlockContext(EventSource.BREAK,
                HyRegionContexts.position(world, event.getTargetBlock()), Actor.environment(HyRegionContexts.EXPLOSION), event,
                HyRegionContexts.key(event.getBlockType()), null));
    }

    private static String worldOf(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getName();
    }

    private static boolean onBlock(EventSource source, Ref<EntityStore> actor, Store<EntityStore> store, ICancellableEcsEvent event,
                                   Vector3i target, TypeKey blockKey, @Nullable ItemStack hand) {
        String world = worldOf(store);
        return judge(source, world, event, () -> new BlockContext(source, HyRegionContexts.position(world, target),
                HyRegionContexts.actor(actor, store), event, blockKey, HyRegionContexts.itemKey(hand)));
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
            onBlock(EventSource.BREAK, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyRegionContexts.key(event.getBlockType()), event.getItemInHand());
        }
    }

    /** Each hit on a block is a BREAK too: a denied one leaves no crack, and the block never reaches the break. */
    private static final class DamageBlockSystem extends ByActor<DamageBlockEvent> {
        DamageBlockSystem() {
            super(DamageBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           DamageBlockEvent event) {
            onBlock(EventSource.BREAK, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyRegionContexts.key(event.getBlockType()), event.getItemInHand());
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

    /** The block placed is what the hand holds: the event names no block type. */
    private static final class PlaceSystem extends ByActor<PlaceBlockEvent> {
        PlaceSystem() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                           PlaceBlockEvent event) {
            TypeKey placed = HyRegionContexts.itemKey(event.getItemInHand());
            onBlock(EventSource.PLACE, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    placed != null ? placed : HyRegionContexts.key((BlockType) null), event.getItemInHand());
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
            if (onBlock(EventSource.INTERACT, chunk.getReferenceTo(index), store, event, event.getTargetBlock(),
                    HyRegionContexts.key(event.getBlockType()), hand)) {
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
            decideOnEntity(chunk.getReferenceTo(index), event.getTargetEntity(), store, event);
        }
    }

    private static boolean decideOnEntity(Ref<EntityStore> user, @Nullable Ref<EntityStore> target, Store<EntityStore> store,
                                          ICancellableEcsEvent event) {
        Vector3d at = target == null ? null : positionOf(target, store);
        if (at == null) {
            return false;
        }
        String world = worldOf(store);
        return judge(EventSource.INTERACT, world, event, () -> new EntityContext(HyRegionContexts.position(world, at),
                HyRegionContexts.actor(user, store), event, HyRegionContexts.actor(target, store)));
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
            Ref<EntityStore> target = context.getTargetEntity();
            if (target != null) {
                decideOnEntity(user, target, store, event);
                return;
            }
            BlockPosition block = context.getTargetBlock();
            if (block != null) {
                onBlock(EventSource.INTERACT, user, store, event, new Vector3i(block.x, block.y, block.z),
                        HyRegionContexts.key((BlockType) null), context.getHeldItem());
            }
        }
    }

    /**
     * Damage to an entity, asked where the victim stands, in the filter group where the game's own protection runs; a
     * denial cancels it - the amount is never zeroed, which would still knock back and play the hit.
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
            judge(EventSource.DAMAGE, world, damage, () -> new DamageContext(HyRegionContexts.position(world, at),
                    attacker(damage, store), damage, HyRegionContexts.actor(victim, store)));
        }

        private static Actor attacker(Damage damage, Store<EntityStore> store) {
            Damage.Source source = damage.getSource();
            if (source instanceof Damage.ProjectileSource projectile && projectile.getProjectile() != null
                    && projectile.getProjectile().isValid()) {
                Ref<EntityStore> shooter = projectile.getRef();
                FPlayer player = shooter != null && shooter.isValid() ? HyRegionContexts.player(shooter, store) : null;
                return HyRegionContexts.projectile(projectile.getProjectile(), player, store);
            }
            if (source instanceof Damage.EntitySource entity && entity.getRef() != null && entity.getRef().isValid()) {
                return HyRegionContexts.actor(entity.getRef(), store);
            }
            DamageCause cause = damage.getCause();
            return Actor.environment(TypeKey.of("environment", cause == null ? "damage" : cause.getId()));
        }
    }
}
