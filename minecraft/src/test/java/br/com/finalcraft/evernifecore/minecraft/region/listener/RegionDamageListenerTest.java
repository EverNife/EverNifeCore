package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.core.DenyPvpFlag;
import br.com.finalcraft.evernifecore.region.flag.core.InvincibleFlag;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hit is asked of the regions where the victim stands, with the player behind it as the attacker. Each root-cause
 * test proves its path through {@code deny-pvp}, which only denies when there is a player behind the hit.
 */
class RegionDamageListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private static EntityDamageByEntityEvent hit(Entity damager, Entity victim) {
        return new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                Doubles.of(DamageSource.class).build(), 1.0);
    }

    /** An arena where players cannot hurt each other, and the player standing in it. */
    private static Player arenaWithVictim(RegionServer server) {
        server.box("arena", 0, 0, 0, 10, 10, 10, server.flag(new DenyPvpFlag()));
        server.listen(new RegionDamageListener());
        return server.player("victim", 5, 5, 5);
    }

    @Test
    void anArrowIsItsShooter() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player victim = arenaWithVictim(server);
            Player archer = server.player("archer", 30, 5, 30);
            Arrow shot = RegionServer.entity(Arrow.class, EntityType.ARROW, server.at(5, 5, 5)).returning("getShooter", archer).build();
            Arrow stray = RegionServer.entity(Arrow.class, EntityType.ARROW, server.at(5, 5, 5)).build();

            assertTrue(server.call(hit(shot, victim)).isCancelled(), "the archer shot from outside the region");
            assertFalse(server.call(hit(stray, victim)).isCancelled(), "an arrow nobody shot has no player behind it");
        }
    }

    @Test
    void aPetIsItsOwner() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player victim = arenaWithVictim(server);
            Player owner = server.player("owner", 30, 5, 30);
            AnimalTamer tamer = Doubles.of(AnimalTamer.class).returning("getUniqueId", owner.getUniqueId()).build();
            Wolf pet = RegionServer.entity(Wolf.class, EntityType.WOLF, server.at(5, 5, 5))
                    .returning("isTamed", true).returning("getOwner", tamer).build();
            Wolf wild = RegionServer.entity(Wolf.class, EntityType.WOLF, server.at(5, 5, 5)).build();

            assertTrue(server.call(hit(pet, victim)).isCancelled(), "the pet bites for its online owner");
            assertFalse(server.call(hit(wild, victim)).isCancelled(), "a wild wolf is nobody's");
        }
    }

    @Test
    void aPrimedTntIsItsIgniter() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player victim = arenaWithVictim(server);
            Player igniter = server.player("igniter", 30, 5, 30);
            TNTPrimed lit = RegionServer.entity(TNTPrimed.class, EntityType.TNT, server.at(5, 5, 5)).returning("getSource", igniter).build();
            TNTPrimed natural = RegionServer.entity(TNTPrimed.class, EntityType.TNT, server.at(5, 5, 5)).build();

            assertTrue(server.call(hit(lit, victim)).isCancelled());
            assertFalse(server.call(hit(natural, victim)).isCancelled(), "TNT nobody lit has no player behind it");
        }
    }

    @Test
    void pvpInsideDenyPvpIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("lobby", 0, 0, 0, 10, 10, 10, server.flag(new DenyPvpFlag()));
            server.listen(new RegionDamageListener());
            Player attacker = server.player("attacker", 30, 5, 30);
            Player inside = server.player("inside", 5, 5, 5);
            Player outside = server.player("outside", 20, 5, 20);

            assertTrue(server.call(hit(attacker, inside)).isCancelled(), "the victim stands in the region, the attacker need not");
            assertFalse(server.call(hit(attacker, outside)).isCancelled());
        }
    }

    @Test
    void aMonsterHittingAnInvinciblePlayerIsCancelled() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            server.box("spawn", 0, 0, 0, 10, 10, 10, server.flag(new InvincibleFlag()));
            server.listen(new RegionDamageListener());
            Zombie zombie = RegionServer.entity(Zombie.class, EntityType.ZOMBIE, server.at(5, 5, 5)).build();
            Player inside = server.player("inside", 5, 5, 5);

            assertTrue(server.call(hit(zombie, inside)).isCancelled(), "no player behind the hit, and the flag still decides");
            assertFalse(server.call(hit(zombie, server.player("outside", 20, 5, 20))).isCancelled());
        }
    }

    /** Never denies; keeps the attacker's handle it read while deciding. */
    static final class HandleKeepingFlag extends TristateFlag {
        NativeEntity kept;
        Entity readDuring;

        HandleKeepingFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "handle-keeping"), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            kept = context.getActor().getNativeEntity();
            readDuring = kept.getEntity();
            return Decision.PASS;
        }
    }

    @Test
    void aHandleKeptPastTheEventThrowsNamingTheUuid() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            HandleKeepingFlag keeping = server.flag(new HandleKeepingFlag(server.regions.getOwner()));
            server.box("arena", 0, 0, 0, 10, 10, 10, keeping);
            server.listen(new RegionDamageListener());
            Player victim = server.player("victim", 5, 5, 5);
            Player attacker = server.player("attacker", 6, 5, 5);

            server.call(hit(attacker, victim));

            assertSame(attacker, keeping.readDuring, "during the event the handle reads the entity");
            IllegalStateException late = assertThrows(IllegalStateException.class, keeping.kept::getEntity);
            assertTrue(late.getMessage().contains(attacker.getUniqueId().toString()), late.getMessage());
            assertTrue(late.getMessage().contains("not the handle"), late.getMessage());
            assertEquals(attacker.getUniqueId(), keeping.kept.getUniqueId(), "the UUID stays readable");
        }
    }
}
