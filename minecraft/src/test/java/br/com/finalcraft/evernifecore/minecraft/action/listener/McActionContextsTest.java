package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
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
class McActionContextsTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void eachEntityPlaysItsRole() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Cow cow = RegionServer.entity(Cow.class, EntityType.COW, server.at(0, 0, 0)).build();
            Creeper creeper = RegionServer.entity(Creeper.class, EntityType.CREEPER, server.at(0, 0, 0)).build();
            Villager citizen = RegionServer.entity(Villager.class, EntityType.VILLAGER, server.at(0, 0, 0))
                    .on("hasMetadata", args -> McActionContexts.CITIZENS_NPC_METADATA.equals(args[0])).build();
            Player machine = server.player("[Quarry]", 0, 0, 0);

            assertEquals(ECEntityType.ANIMAL, McActionContexts.actor(cow).getEntityType());
            assertEquals(ECEntityType.MONSTER, McActionContexts.actor(creeper).getEntityType());
            assertEquals(ECEntityType.NPC, McActionContexts.actor(citizen).getEntityType());
            assertNull(McActionContexts.actor(citizen).getRootPlayer(), "an NPC acts for nobody");
            assertEquals(ECEntityType.FAKE_PLAYER, McActionContexts.actor(machine, true).getEntityType());
            assertEquals(ECEntityType.PLAYER, McActionContexts.actor(machine, false).getEntityType());
        }
    }

    @Test
    void aPlayerShapedNpcIsAnNpcNotAPlayer() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Player npc = server.player("Shopkeeper", 0, 0, 0);
            Player marked = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> "hasMetadata".equals(method.getName())
                            ? McActionContexts.CITIZENS_NPC_METADATA.equals(args[0])
                            : method.invoke(npc, args));

            Actor actor = McActionContexts.actor(marked);
            assertEquals(ECEntityType.NPC, actor.getEntityType());
            assertNull(actor.getRootPlayer());
        }
    }

    @Test
    void lightningIsTheEnvironment() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            LightningStrike bolt = RegionServer.entity(LightningStrike.class, EntityType.LIGHTNING_BOLT, server.at(0, 0, 0)).build();

            assertEquals(Actor.Type.ENVIRONMENT, McActionContexts.actor(bolt).getType());
            assertEquals(McActionContexts.LIGHTNING, McActionContexts.actor(bolt).getKey());
        }
    }

    @Test
    void vanillaKeysAreTheRegistrysOwn() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            Creeper creeper = RegionServer.entity(Creeper.class, EntityType.CREEPER, server.at(0, 0, 0)).build();

            assertEquals("minecraft:stone", McActionContexts.key(Material.STONE).toString());
            assertEquals("minecraft:redstone_wire", McActionContexts.key(Material.REDSTONE_WIRE).toString());
            assertEquals("minecraft:creeper", McActionContexts.key(creeper).toString());
            assertEquals("minecraft:player", McActionContexts.key(server.player("steve", 0, 0, 0)).toString());
        }
    }

    @Test
    void aBareRegistryNameIsThePlatformsAndADottedOneTheMods() {
        assertEquals(TypeKey.of("minecraft", "Pig"), McActionContexts.entityKey("Pig"));
        assertEquals(TypeKey.of("pixelmon", "Pixelmon"), McActionContexts.entityKey("pixelmon.Pixelmon"));
        assertEquals(TypeKey.of("minecraft", "pig"), McActionContexts.entityKey("minecraft:pig"));
    }
}
