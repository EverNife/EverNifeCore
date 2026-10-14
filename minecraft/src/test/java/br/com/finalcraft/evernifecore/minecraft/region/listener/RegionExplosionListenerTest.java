package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An explosion is asked as a whole, then block by block, with one actor; whom it hurts is asked with that actor too. */
class RegionExplosionListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** The blocks of a row along x, at y=5 z=5. */
    static List<Block> row(RegionServer server, int fromX, int toX) {
        List<Block> blocks = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            blocks.add(server.world.getBlockAt(x, 5, 5));
        }
        return blocks;
    }

    static EntityExplodeEvent explosion(RegionServer server, Entity exploding, List<Block> blocks) {
        return new EntityExplodeEvent(exploding, exploding.getLocation(), new ArrayList<>(blocks), 1.0f, ExplosionResult.DESTROY);
    }

    static Creeper creeper(RegionServer server, double x) {
        return RegionServer.entity(Creeper.class, EntityType.CREEPER, server.at(x, 5, 5)).build();
    }

    @Test
    void denyExplosionCancelsTheWholeExplosion() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_EXPLOSION));
            server.listen(new RegionExplosionListener());

            assertTrue(server.call(explosion(server, creeper(server, 5), row(server, 4, 6))).isCancelled());
            assertFalse(server.call(explosion(server, creeper(server, 30), row(server, 29, 31))).isCancelled(), "outside the region it goes off");
        }
    }

    @Test
    void withoutDenyExplosionTheExplosionGoesOff() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_EXPLOSION);
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_INTERACT));
            server.listen(new RegionExplosionListener());

            EntityExplodeEvent event = server.call(explosion(server, creeper(server, 5), row(server, 4, 6)));
            assertFalse(event.isCancelled());
            assertEquals(3, event.blockList().size());
        }
    }

    @Test
    void onABorderDenyBlockBreakKeepsTheInsideAndBreaksTheOutside() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("vault", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
            server.listen(new RegionExplosionListener());

            EntityExplodeEvent event = server.call(explosion(server, creeper(server, 10), row(server, 6, 15)));

            assertFalse(event.isCancelled(), "the explosion itself is not what deny-block-break is about");
            assertEquals(5, event.blockList().size(), "x=11..15 are outside and break");
            for (Block block : event.blockList()) {
                assertTrue(block.getX() > 10, "a block inside the region left the list: x=" + block.getX());
            }
        }
    }

    /** A region whose members may break: the region denies, its "members" group lifts it. */
    private static Region home(RegionServer server, Player member) {
        Region home = server.box("home", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.DENY_BLOCK_BREAK));
        home.getGroup("members").setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.FALSE);
        home.getGroup("members").addMember(member.getUniqueId());
        return home;
    }

    @Test
    void tntLitByAMemberBreaksInsideItsRegionAndTntOfAStrangerDoesNot() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player member = server.player("owner", 30, 5, 30);
            Player stranger = server.player("griefer", 30, 5, 30);
            home(server, member);
            server.listen(new RegionExplosionListener());
            TNTPrimed byMember = RegionServer.entity(TNTPrimed.class, EntityType.TNT, server.at(5, 5, 5)).returning("getSource", member).build();
            TNTPrimed byStranger = RegionServer.entity(TNTPrimed.class, EntityType.TNT, server.at(5, 5, 5)).returning("getSource", stranger).build();

            assertEquals(3, server.call(explosion(server, byMember, row(server, 4, 6))).blockList().size(),
                    "the igniter is the root of the TNT, and the region lets its members break");
            assertEquals(0, server.call(explosion(server, byStranger, row(server, 4, 6))).blockList().size());
        }
    }

    /** Never denies; keeps the actor of the last context it was asked about. */
    static final class ActorProbe extends TristateFlag {
        Actor seen;

        ActorProbe(RegionServer server, EventSource source) {
            super(FlagKey.of(server.regions.getOwner(), "probe-" + source.getName()), Collections.singleton(source), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            seen = context.getActor();
            return Decision.PASS;
        }
    }

    @Test
    void aCreepersBlastOnAnInvinciblePlayerIsCancelledWithTheCreeperAsTheAttacker() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActorProbe probe = server.flag(new ActorProbe(server, EventSource.DAMAGE));
            Region spawn = server.box("spawn", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.INVINCIBLE), probe);
            server.listen(new RegionDamageListener());
            Player inside = server.player("inside", 5, 5, 5);

            assertTrue(server.call(blast(server, inside)).isCancelled());
            spawn.removeFlag(CoreFlags.INVINCIBLE);   //the probe is asked once no other flag answers first
            assertFalse(server.call(blast(server, inside)).isCancelled());
            assertNotNull(probe.seen, "the attacker the context carried");
            assertEquals(ECEntityType.MONSTER, probe.seen.getEntityType());
            assertEquals("minecraft:creeper", probe.seen.getKey().toString());
        }
    }

    private static EntityDamageByEntityEvent blast(RegionServer server, Player victim) {
        return new EntityDamageByEntityEvent(creeper(server, 6), victim, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                Doubles.of(DamageSource.class).build(), 4.0);
    }

    @Test
    void aBlockExplosionsDamageIsTheEnvironments() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActorProbe probe = server.flag(new ActorProbe(server, EventSource.DAMAGE));
            Region spawn = server.box("spawn", 0, 0, 0, 10, 10, 10, server.flag(CoreFlags.INVINCIBLE), probe);
            server.listen(new RegionDamageListener());
            Player inside = server.player("inside", 5, 5, 5);

            assertTrue(server.call(bed(server, inside)).isCancelled());
            spawn.removeFlag(CoreFlags.INVINCIBLE);
            server.call(bed(server, inside));
            assertEquals(Actor.Type.ENVIRONMENT, probe.seen.getType());
            assertEquals(RegionContexts.EXPLOSION, probe.seen.getKey());
        }
    }

    private static EntityDamageByBlockEvent bed(RegionServer server, Player victim) {
        return new EntityDamageByBlockEvent(server.world.getBlockAt(6, 5, 5), null, victim, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                Doubles.of(DamageSource.class).build(), 4.0);
    }

    @Test
    void theWholeExplosionAndEveryBlockShareOneActor() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            ActorProbe onExplosion = server.flag(new ActorProbe(server, EventSource.EXPLOSION));
            ActorProbe onBreak = server.flag(new ActorProbe(server, EventSource.BREAK));
            server.box("vault", 0, 0, 0, 10, 10, 10, onExplosion, onBreak);
            server.listen(new RegionExplosionListener());

            server.call(explosion(server, creeper(server, 5), Arrays.asList(server.world.getBlockAt(5, 5, 5))));

            assertNotNull(onExplosion.seen);
            assertTrue(onExplosion.seen == onBreak.seen, "one actor for the explosion and for what it breaks");
        }
    }

    /**
     * The block stage costs at most a millisecond a block: a 216-block blast among 64 regions, the median of 15 runs after
     * a warm-up. Prints the measure, the number a change of the stage is compared against.
     */
    @Test
    void theBlockStageOfABigExplosionAmongManyRegionsStaysUnderAMillisecondABlock() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.flag(CoreFlags.DENY_BLOCK_BREAK);
            for (int gx = 0; gx < 8; gx++) {
                for (int gz = 0; gz < 8; gz++) {   //64 boxes of 8x8, every other one denying, the blast over several of them
                    server.box("plot_" + gx + "_" + gz, gx * 8, 0, gz * 8, gx * 8 + 7, 20, gz * 8 + 7,
                            (gx + gz) % 2 == 0 ? new TristateFlag[]{CoreFlags.DENY_BLOCK_BREAK} : new TristateFlag[0]);
                }
            }
            server.listen(new RegionExplosionListener());
            List<Block> blast = new ArrayList<>();
            for (int x = 20; x < 26; x++) {
                for (int y = 5; y < 11; y++) {
                    for (int z = 20; z < 26; z++) {
                        blast.add(server.world.getBlockAt(x, y, z));
                    }
                }
            }
            Creeper creeper = creeper(server, 22);
            for (int warmUp = 0; warmUp < 5; warmUp++) {
                server.call(explosion(server, creeper, blast));
            }

            long[] nanos = new long[15];
            for (int run = 0; run < nanos.length; run++) {
                long start = System.nanoTime();
                server.call(explosion(server, creeper, blast));
                nanos[run] = System.nanoTime() - start;
            }
            Arrays.sort(nanos);
            double msPerBlock = nanos[nanos.length / 2] / 1_000_000.0 / blast.size();
            System.out.printf("explosion block stage: %d blocks, 64 regions, median of %d runs = %.4f ms per block%n", blast.size(), nanos.length, msPerBlock);
            assertTrue(msPerBlock <= 1.0, "the block stage costs " + msPerBlock + " ms per block");
        }
    }
}
