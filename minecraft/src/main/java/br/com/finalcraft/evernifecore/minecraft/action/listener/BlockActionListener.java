package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.PlaceActionEvent;
import br.com.finalcraft.evernifecore.api.platoverride.block.NativeBlock;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Asks the regions about every block broken ({@link EventSource#BREAK}) or placed ({@link EventSource#PLACE}) - by a
 * player, a mob changing a block, fire lit by someone or by lava, or a piston moving it - one instance per source, so each
 * comes and goes with its own need; a denial, or a cancelled {@code Pre}, cancels the event. Each break or place then posts
 * its {@link BreakActionEvent} / {@link PlaceActionEvent} pair; a piston only moves blocks, so it is asked of the regions
 * and posts nothing. Fire spreading on its own is {@link FireSpreadActionListener}'s.
 */
public final class BlockActionListener implements ECListener {

    private final EventSource source;
    private final Predicate<Player> fakePlayers;

    public BlockActionListener(EventSource source) {
        this(source, FCBukkitUtil::isFakePlayer);
    }

    BlockActionListener(EventSource source, Predicate<Player> fakePlayers) {
        if (source != EventSource.BREAK && source != EventSource.PLACE) {
            throw new IllegalArgumentException("A BlockActionListener hears BREAK or PLACE, not " + source + ": pick the one this instance follows.");
        }
        this.source = source;
        this.fakePlayers = fakePlayers;
    }

    @Override
    public boolean silentRegistration() {
        return true;   //comes and goes with the regions that need it; logging each turn would be noise
    }

    //both instances carry every handler; each handler decides only for the source this instance follows

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        decide(event, contextOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreak(BlockBreakEvent event) {
        post(contextOf(event));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        decide(event, contextOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterPlace(BlockPlaceEvent event) {
        post(contextOf(event));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        decide(event, contextOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterEntityChangeBlock(EntityChangeBlockEvent event) {
        post(contextOf(event));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        decide(event, contextOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBurn(BlockBurnEvent event) {
        post(contextOf(event));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        decide(event, contextOf(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterIgnite(BlockIgniteEvent event) {
        post(contextOf(event));
    }

    private @Nullable Supplier<BlockContext> contextOf(BlockBreakEvent event) {
        return source != EventSource.BREAK ? null : () -> context(event.getBlock(), event.getBlock().getType(), playerActor(event.getPlayer()),
                event, McActionContexts.itemInHandKey(event.getPlayer()));
    }

    private @Nullable Supplier<BlockContext> contextOf(BlockPlaceEvent event) {
        return source != EventSource.PLACE ? null : () -> context(event.getBlock(), event.getBlock().getType(), playerActor(event.getPlayer()),
                event, McActionContexts.itemInHandKey(event.getPlayer()));
    }

    /** A mob taking a block (enderman, wither, a sheep eating grass) is a BREAK; one setting a block where there was air (enderman, falling sand) a PLACE. */
    private @Nullable Supplier<BlockContext> contextOf(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        boolean placing = isAir(block.getType()) && !isAir(event.getTo());
        if (source != (placing ? EventSource.PLACE : EventSource.BREAK)) {
            return null;
        }
        Material type = placing ? event.getTo() : block.getType();
        return () -> context(block, type, McActionContexts.actor(event.getEntity()), event, null);
    }

    private @Nullable Supplier<BlockContext> contextOf(BlockBurnEvent event) {
        return source != EventSource.BREAK ? null
                : () -> context(event.getBlock(), event.getBlock().getType(), Actor.environment(McActionContexts.FIRE), event, null);
    }

    /** Fire set on a block: by the player who lit it, else by the environment (lava, lightning). Fire spreading is {@link EventSource#FIRE_SPREAD}'s. */
    private @Nullable Supplier<BlockContext> contextOf(BlockIgniteEvent event) {
        if (source != EventSource.PLACE || event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            return null;
        }
        Player player = event.getPlayer();
        return () -> context(event.getBlock(), Material.FIRE, player != null ? playerActor(player) : Actor.environment(McActionContexts.FIRE),
                event, null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace facing = event.getDirection();
        decidePiston(event, event.getBlocks(), relative(event.getBlock(), facing.getModX(), facing.getModY(), facing.getModZ()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        decidePiston(event, retracted(event), null);
    }

    /** What a retracting piston pulls: the list from 1.8 on; on 1.7.10 the one block a sticky piston holds. */
    private static List<Block> retracted(BlockPistonRetractEvent event) {
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_8_R1)) {
            return event.getBlocks();
        }
        @SuppressWarnings("deprecation")   //the only form 1.7.10 has
        Block pulled = event.getRetractLocation().getBlock();
        return event.isSticky() && !isAir(pulled.getType()) ? Collections.singletonList(pulled) : Collections.<Block>emptyList();
    }

    /**
     * A piston moves each block one step along its line: the block leaves where it is (BREAK) and enters the next spot
     * (PLACE), and the extending head enters {@code head} - null on retract. Only a spot whose regions differ from the piston's is asked -
     * a piston moving blocks inside its own region crosses no border.
     */
    private void decidePiston(BlockPistonEvent event, List<Block> moved, @Nullable Block head) {
        Block piston = event.getBlock();
        if (McActionContexts.isDisabled(piston.getWorld())) {
            return;
        }
        RegionLookup lookup = RegionLookup.get();
        List<Region> pistonRegions = lookup.at(McActionContexts.position(piston.getLocation()));
        Actor actor = Actor.block(McActionContexts.key(piston.getType()), () -> new NativeBlock(piston));
        try (RegionDispatch dispatch = lookup.dispatch()) {
            for (Block block : moved) {
                Block at = source == EventSource.BREAK ? block : step(piston, block, event.getDirection(), head != null);
                if (crosses(lookup, pistonRegions, at) && dispatch.decide(context(at, block.getType(), actor, event, null)) == Decision.DENY) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (head != null && source == EventSource.PLACE && crosses(lookup, pistonRegions, head)
                    && dispatch.decide(context(head, piston.getType(), actor, event, null)) == Decision.DENY) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Where {@code block} lands: one step along the piston's axis, away from the piston on extend and towards it on
     * retract. Only the axis of {@code direction} is read - eras disagree on which way a retract's direction points, and
     * a block a slime block drags sits off the piston's line.
     */
    private static Block step(Block piston, Block block, BlockFace direction, boolean extending) {
        int sign = extending ? 1 : -1;
        return relative(block,
                direction.getModX() == 0 ? 0 : sign * Integer.signum(block.getX() - piston.getX()),
                direction.getModY() == 0 ? 0 : sign * Integer.signum(block.getY() - piston.getY()),
                direction.getModZ() == 0 ? 0 : sign * Integer.signum(block.getZ() - piston.getZ()));
    }

    private static Block relative(Block block, int dx, int dy, int dz) {
        return block.getWorld().getBlockAt(block.getX() + dx, block.getY() + dy, block.getZ() + dz);
    }

    private static boolean crosses(RegionLookup lookup, List<Region> pistonRegions, Block at) {
        return !lookup.at(McActionContexts.position(at.getLocation())).equals(pistonRegions);
    }

    private Actor playerActor(Player player) {
        return McActionContexts.actor(player, fakePlayers.test(player));
    }

    /** Regions, then the Pre; nothing for an event this instance does not follow or in a disabled world. */
    private void decide(Cancellable event, @Nullable Supplier<BlockContext> context) {
        if (context == null) {
            return;
        }
        BlockContext built = context.get();
        if (McActionContexts.isDisabled(built.getWorld())) {
            built.close();
            return;
        }
        boolean denied = source == EventSource.BREAK
                ? Actions.denied(built, BreakActionEvent.Pre.class, BreakActionEvent.Pre::new)
                : Actions.denied(built, PlaceActionEvent.Pre.class, PlaceActionEvent.Pre::new);
        if (denied) {
            event.setCancelled(true);
        }
    }

    /** The Post, built only when somebody listens to it. */
    private void post(@Nullable Supplier<BlockContext> context) {
        if (context == null) {
            return;
        }
        if (source == EventSource.BREAK) {
            Actions.post(BreakActionEvent.Post.class, () -> new BreakActionEvent.Post(context.get()));
        } else {
            Actions.post(PlaceActionEvent.Post.class, () -> new PlaceActionEvent.Post(context.get()));
        }
    }

    private BlockContext context(Block block, Material type, Actor actor, Object event, @Nullable TypeKey itemInHand) {
        return new BlockContext(source, McActionContexts.position(block.getLocation()), actor, event, McActionContexts.key(type), itemInHand);
    }

    /** Air in every era: {@code AIR}, and from 1.13 {@code CAVE_AIR} and {@code VOID_AIR}. */
    static boolean isAir(Material material) {
        return material == Material.AIR || material.name().endsWith("_AIR");
    }
}
