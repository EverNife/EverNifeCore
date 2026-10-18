package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.api.platoverride.block.NativeBlock;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionDispatch;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
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

/**
 * Asks the regions about every block broken ({@link EventSource#BREAK}) or placed ({@link EventSource#PLACE}) - by a
 * player, a mob changing a block, fire lit by someone or by lava, or a piston moving it - one instance per source, so each
 * comes and goes with its own need; a denial cancels the event. Fire spreading on its own is
 * {@link RegionFireSpreadListener}'s.
 */
public final class RegionBlockListener implements ECListener {

    private final EventSource source;
    private final Predicate<Player> fakePlayers;

    public RegionBlockListener(EventSource source) {
        this(source, FCBukkitUtil::isFakePlayer);
    }

    RegionBlockListener(EventSource source, Predicate<Player> fakePlayers) {
        if (source != EventSource.BREAK && source != EventSource.PLACE) {
            throw new IllegalArgumentException("A RegionBlockListener hears BREAK or PLACE, not " + source + ": pick the one this instance follows.");
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
        if (source == EventSource.BREAK) {
            decide(event.getBlock(), playerActor(event.getPlayer()), event, RegionContexts.itemInHandKey(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (source == EventSource.PLACE) {
            decide(event.getBlock(), playerActor(event.getPlayer()), event, RegionContexts.itemInHandKey(event.getPlayer()));
        }
    }

    /** A mob taking a block (enderman, wither, a sheep eating grass) is a BREAK; one setting a block where there was air (enderman, falling sand) a PLACE. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        boolean placing = isAir(block.getType()) && !isAir(event.getTo());
        if (source == (placing ? EventSource.PLACE : EventSource.BREAK)) {
            decide(block, placing ? event.getTo() : block.getType(), RegionContexts.actor(event.getEntity()), event, null);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (source == EventSource.BREAK) {
            decide(event.getBlock(), Actor.environment(RegionContexts.FIRE), event, null);
        }
    }

    /** Fire set on a block: by the player who lit it, else by the environment (lava, lightning). Fire spreading is {@link EventSource#FIRE_SPREAD}'s. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (source == EventSource.PLACE && event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) {
            Player player = event.getPlayer();
            decide(event.getBlock(), Material.FIRE, player != null ? playerActor(player) : Actor.environment(RegionContexts.FIRE), event, null);
        }
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
        if (RegionContexts.isDisabled(piston.getWorld())) {
            return;
        }
        RegionLookup lookup = RegionLookup.get();
        List<Region> pistonRegions = lookup.at(RegionContexts.position(piston.getLocation()));
        Actor actor = Actor.block(RegionContexts.key(piston.getType()), () -> new NativeBlock(piston));
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
        return !lookup.at(RegionContexts.position(at.getLocation())).equals(pistonRegions);
    }

    private Actor playerActor(Player player) {
        return RegionContexts.actor(player, fakePlayers.test(player));
    }

    private void decide(Block block, Actor actor, Cancellable event, @Nullable TypeKey itemInHand) {
        decide(block, block.getType(), actor, event, itemInHand);
    }

    private void decide(Block block, Material type, Actor actor, Cancellable event, @Nullable TypeKey itemInHand) {
        if (RegionContexts.isDisabled(block.getWorld())) {
            return;
        }
        if (RegionLookup.get().decide(context(block, type, actor, event, itemInHand)) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    private BlockContext context(Block block, Material type, Actor actor, Object event, @Nullable TypeKey itemInHand) {
        return new BlockContext(source, RegionContexts.position(block.getLocation()), actor, event, RegionContexts.key(type), itemInHand);
    }

    /** Air in every era: {@code AIR}, and from 1.13 {@code CAVE_AIR} and {@code VOID_AIR}. */
    static boolean isAir(Material material) {
        return material == Material.AIR || material.name().endsWith("_AIR");
    }
}
