package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What an actor is - its role and its key - as the region listeners build it. */
class RegionContextsTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void eachEntityPlaysItsRole() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Cow cow = RegionServer.entity(Cow.class, EntityType.COW, server.at(0, 0, 0)).build();
            Creeper creeper = RegionServer.entity(Creeper.class, EntityType.CREEPER, server.at(0, 0, 0)).build();
            Villager citizen = RegionServer.entity(Villager.class, EntityType.VILLAGER, server.at(0, 0, 0))
                    .on("hasMetadata", args -> RegionContexts.CITIZENS_NPC_METADATA.equals(args[0])).build();
            Player machine = server.player("[Quarry]", 0, 0, 0);

            assertEquals(ECEntityType.ANIMAL, RegionContexts.actor(cow).getEntityType());
            assertEquals(ECEntityType.MONSTER, RegionContexts.actor(creeper).getEntityType());
            assertEquals(ECEntityType.NPC, RegionContexts.actor(citizen).getEntityType());
            assertNull(RegionContexts.actor(citizen).getRootPlayer(), "an NPC acts for nobody");
            assertEquals(ECEntityType.FAKE_PLAYER, RegionContexts.actor(machine, true).getEntityType());
            assertEquals(ECEntityType.PLAYER, RegionContexts.actor(machine, false).getEntityType());
        }
    }

    @Test
    void aPlayerShapedNpcIsAnNpcNotAPlayer() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player npc = server.player("Shopkeeper", 0, 0, 0);
            Player marked = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> "hasMetadata".equals(method.getName())
                            ? RegionContexts.CITIZENS_NPC_METADATA.equals(args[0])
                            : method.invoke(npc, args));

            Actor actor = RegionContexts.actor(marked);
            assertEquals(ECEntityType.NPC, actor.getEntityType());
            assertNull(actor.getRootPlayer());
        }
    }

    @Test
    void lightningIsTheEnvironment() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            LightningStrike bolt = RegionServer.entity(LightningStrike.class, EntityType.LIGHTNING_BOLT, server.at(0, 0, 0)).build();

            assertEquals(Actor.Type.ENVIRONMENT, RegionContexts.actor(bolt).getType());
            assertEquals(RegionContexts.LIGHTNING, RegionContexts.actor(bolt).getKey());
        }
    }

    @Test
    void vanillaKeysAreTheRegistrysOwn() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Creeper creeper = RegionServer.entity(Creeper.class, EntityType.CREEPER, server.at(0, 0, 0)).build();

            assertEquals("minecraft:stone", RegionContexts.key(Material.STONE).toString());
            assertEquals("minecraft:redstone_wire", RegionContexts.key(Material.REDSTONE_WIRE).toString());
            assertEquals("minecraft:creeper", RegionContexts.key(creeper).toString());
            assertEquals("minecraft:player", RegionContexts.key(server.player("steve", 0, 0, 0)).toString());
        }
    }

    @Test
    void aBareRegistryNameIsThePlatformsAndADottedOneTheMods() {
        assertEquals(TypeKey.of("minecraft", "Pig"), RegionContexts.entityKey("Pig"));
        assertEquals(TypeKey.of("pixelmon", "Pixelmon"), RegionContexts.entityKey("pixelmon.Pixelmon"));
        assertEquals(TypeKey.of("minecraft", "pig"), RegionContexts.entityKey("minecraft:pig"));
    }
}
