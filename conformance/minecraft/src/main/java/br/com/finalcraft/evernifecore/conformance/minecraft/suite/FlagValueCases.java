package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.logic.audit.EffectApplyEvent;
import br.com.finalcraft.evernifecore.logic.audit.GatePaidEvent;
import br.com.finalcraft.evernifecore.logic.audit.LogicEvent;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The flag value model on a live server: an {@code entry-gate} that sends the bot back until it may pass and charges it
 * once a session, {@code effects-on-enter} applying its effect on entering, a {@code damage-multiplier} scaling a real
 * hit, and two {@code heal} layers (the region's and a group's) joined by {@code sum-by-interval} into one heal a tick.
 * Each case builds its region 12 blocks east of the bot, takes the bot's bypass, and removes both when it answers.
 */
final class FlagValueCases {

    private static final long TIMEOUT_MILLIS = 15_000L;
    private static final String GATE_NODE = "ecc.conformance.gate";

    private final RegionManager regions;

    FlagValueCases(RegionManager regions) {
        this.regions = regions;
    }

    List<ConformanceCase> cases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.later("entry-gate-denies-then-charges-once-per-session", ctx, this::entryGate),
                PlayerCases.later("effects-on-enter-runs-message", ctx, this::effectsOnEnter),
                PlayerCases.later("damage-multiplier-scales-event-damage", ctx, this::damageMultiplier),
                PlayerCases.later("heal-layers-sum-by-interval", ctx, this::healLayers));
    }

    /** A region east of the bot, with the bot's bypass of {@code flagName} taken; throws when the manager refuses it. */
    private final class Arena {
        final Player player;
        final Location start;
        final Location inside;
        final Region region;
        final PermissionAttachment noBypass;

        Arena(Player player, String name, String flagName) {
            this.player = player;
            this.start = player.getLocation();
            World world = player.getWorld();
            int tx = start.getBlockX() + 12, z = start.getBlockZ();
            int ty = world.getHighestBlockYAt(tx, z);
            this.inside = new Location(world, tx + 0.5, ty, z + 0.5);
            CreationResult created = regions.create(name, world.getName(),
                    CuboidShape.of(BlockPos.of(tx - 2, ty - 2, z - 2), BlockPos.of(tx + 2, ty + 3, z + 2)));
            this.region = created.getRegion();
            if (region == null) {
                throw new IllegalStateException(name + " refused: " + created.getRefusal());
            }
            this.noBypass = player.addAttachment(plugin());
            noBypass.setPermission("evernifecore.region.bypass.*", false);
            noBypass.setPermission("evernifecore.region.bypass.evernifecore." + flagName, false);
        }

        boolean isInside() {
            Location now = player.getLocation();
            return region.getShape().contains(now.getX(), now.getY(), now.getZ());
        }

        void close() {
            player.teleport(start);
            regions.remove(region);
            player.removeAttachment(noBypass);
        }
    }

    /** Whether {@code event} is about {@code player}, raised by the region {@code region}'s {@code flagName}. */
    private static boolean ofRegion(LogicEvent event, Player player, Region region, String flagName) {
        FPlayer who = event.getPlayer();
        return who != null && who.getUniqueId().equals(player.getUniqueId())
                && event.getCaller().getOrigin().equals("region/" + region.getId() + "#" + flagName);
    }

    private static void later(long ticks, Runnable step) {
        Bukkit.getScheduler().runTaskLater(plugin(), step, ticks);
    }

    /**
     * Without the gate's permission the bot's teleport in is sent back; with it the bot enters twice and the gate is paid
     * once ({@code once-per-session}).
     */
    private CompletableFuture<Verdict> entryGate(Player player) throws Exception {
        Arena arena = new Arena(player, "ecc-gate-" + Long.toHexString(System.nanoTime()), "entry-gate");
        arena.noBypass.setPermission(GATE_NODE, false);   //the bot is OP, and OP holds every undeclared node
        AtomicInteger paid = new AtomicInteger();
        ECEventSubscription<GatePaidEvent> paying = ECEventBus.global().subscribe(GatePaidEvent.class, event -> {
            if (ofRegion(event, player, arena.region, "entry-gate")) {
                paid.incrementAndGet();
            }
        });
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        boolean[] seen = new boolean[3];
        try {
            arena.region.setFlag(CoreFlags.ENTRY_GATE, CoreFlags.ENTRY_GATE.getType().parse("{require: [{permission: " + GATE_NODE + "}]}"));
        } catch (Throwable thrown) {
            paying.unsubscribe();
            arena.close();
            throw thrown;
        }
        later(2, () -> player.teleport(arena.inside));
        later(10, () -> {
            seen[0] = arena.isInside();
            arena.noBypass.setPermission(GATE_NODE, true);
            player.teleport(arena.inside);
        });
        later(18, () -> {
            seen[1] = arena.isInside();
            player.teleport(arena.start);
        });
        later(26, () -> player.teleport(arena.inside));
        later(34, () -> {
            try {
                seen[2] = arena.isInside();
                answer.complete(Verdict.of(!seen[0] && seen[1] && seen[2] && paid.get() == 1, "insideWithoutPermission=" + seen[0]
                        + " firstEntry=" + seen[1] + " secondEntry=" + seen[2] + " gatePaid=" + paid.get() + " (want 1)"));
            } catch (Throwable thrown) {
                answer.complete(Verdict.fail(Verdict.describe(thrown)));
            } finally {
                paying.unsubscribe();
                arena.close();
            }
        });
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    /** A {@code message} effect on {@code effects-on-enter} applies once on entering, and not again on leaving. */
    private CompletableFuture<Verdict> effectsOnEnter(Player player) throws Exception {
        Arena arena = new Arena(player, "ecc-effects", "effects-on-enter");
        List<String> applied = new ArrayList<>();
        ECEventSubscription<EffectApplyEvent> effects = ECEventBus.global().subscribe(EffectApplyEvent.class, event -> {
            if (ofRegion(event, player, arena.region, "effects-on-enter")) {
                applied.add(String.valueOf(event.getEffect()));
            }
        });
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        int[] onEntering = new int[1];
        try {
            arena.region.setFlag(CoreFlags.EFFECTS_ON_ENTER,
                    CoreFlags.EFFECTS_ON_ENTER.getType().parse("[{type: message, text: 'ECC effects-on-enter'}]"));
        } catch (Throwable thrown) {
            effects.unsubscribe();
            arena.close();
            throw thrown;
        }
        later(2, () -> player.teleport(arena.inside));
        later(12, () -> {
            onEntering[0] = applied.size();
            player.teleport(arena.start);
        });
        later(22, () -> {
            try {
                answer.complete(Verdict.of(onEntering[0] == 1 && applied.size() == 1, "appliedOnEntering=" + onEntering[0]
                        + " appliedAfterLeaving=" + applied.size() + " effects=" + applied));
            } finally {
                effects.unsubscribe();
                arena.close();
            }
        });
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    /** A pig hits the bot inside {@code damage-multiplier = 2.0}: the hit's damage reads doubled once the core judged it. */
    private CompletableFuture<Verdict> damageMultiplier(Player player) throws Exception {
        Arena arena = new Arena(player, "ecc-damage-multiplier", "damage-multiplier");
        GameMode mode = player.getGameMode();
        double health = player.getHealth();
        List<Double> seen = new ArrayList<>();
        Listener reader = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onHit(EntityDamageByEntityEvent event) {
                if (event.getEntity().equals(player)) {
                    seen.add(event.getDamage());
                }
            }
        };
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        Pig[] pig = new Pig[1];
        arena.region.setFlag(CoreFlags.DAMAGE_MULTIPLIER, 2.0);
        later(2, () -> player.teleport(arena.inside));
        later(10, () -> {
            try {
                Bukkit.getPluginManager().registerEvents(reader, plugin());
                player.setGameMode(GameMode.SURVIVAL);   //a creative player takes no hit at all
                player.setNoDamageTicks(0);
                pig[0] = player.getWorld().spawn(arena.inside, Pig.class);
                player.damage(2.0, pig[0]);
                answer.complete(Verdict.of(seen.size() == 1 && Math.abs(seen.get(0) - 4.0) < 1e-6,
                        "hit of 2.0 inside damage-multiplier = 2.0 read " + seen + " (want [4.0])"));
            } catch (Throwable thrown) {
                answer.complete(Verdict.fail(Verdict.describe(thrown)));
            } finally {
                HandlerList.unregisterAll(reader);
                if (pig[0] != null) {
                    pig[0].remove();
                }
                player.setHealth(Math.min(health, player.getMaxHealth()));
                player.setGameMode(mode);
                arena.close();
            }
        });
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    /**
     * The region's {@code heal 2 every 1s} and its group's {@code 10 every 1s} heal the bot by 12 in one change a tick.
     * Natural regeneration is cancelled while the case runs, so every change it reads is the flag's.
     */
    private CompletableFuture<Verdict> healLayers(Player player) throws Exception {
        Arena arena = new Arena(player, "ecc-heal-layers", "heal");
        double health = player.getHealth();
        Listener noRegen = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onRegain(EntityRegainHealthEvent event) {
                if (event.getEntity().equals(player)) {
                    event.setCancelled(true);
                }
            }
        };
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        List<Double> changes = new ArrayList<>();
        try {
            arena.region.setFlag(CoreFlags.HEAL, CoreFlags.HEAL.getType().parse("2 every 1s"));
            arena.region.getGroup("ecc-party").addMember(player.getUniqueId());
            arena.region.getGroup("ecc-party").setFlag(CoreFlags.HEAL, CoreFlags.HEAL.getType().parse("10 every 1s"));
        } catch (Throwable thrown) {
            arena.close();
            throw thrown;
        }
        Bukkit.getPluginManager().registerEvents(noRegen, plugin());
        later(2, () -> {
            player.setHealth(2.0);
            player.teleport(arena.inside);
        });
        double[] last = {2.0};
        BukkitTask[] watch = new BukkitTask[1];
        later(3, () -> watch[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            double now = player.getHealth();
            if (now != last[0]) {
                changes.add(now - last[0]);
                last[0] = now;
            }
        }, 0L, 1L));
        later(60, () -> {
            try {
                answer.complete(Verdict.of(!changes.isEmpty() && Math.abs(changes.get(0) - 12.0) < 1e-6,
                        "health changes from 2.0: " + changes + " (want the first +12.0, one change)"));
            } finally {
                watch[0].cancel();
                HandlerList.unregisterAll(noRegen);
                player.setHealth(Math.min(health, player.getMaxHealth()));
                arena.close();
            }
        });
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    private static Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ECoreConformance");
    }
}
