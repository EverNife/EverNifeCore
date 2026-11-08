package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.VehicleContext;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.flag.core.TypeFilterSettings;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The protection flags of the core catalog: parents and their refinements, item and creature filters, mob damage. */
@ECoreTest
class ProtectionCatalogTest {

    private static final String OWNER = "ProtectionCatalogOwner";
    private static final String WORLD = "world";
    private static final WorldLocPos AT = WorldLocPos.of(5, 5, 5, WORLD);

    @TempDir
    Path tempDir;

    private ECPluginData core;
    private ECPluginData owner;
    private RegionLookup lookup;
    private Region region;
    private final TestFPlayerSender player = new TestFPlayerSender("player");

    @BeforeEach
    void setUp() {
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        CoreFlags.registerAll(core);
        lookup = TestRegions.newLookup();
        region = TestRegions.box(owner, "yard", WORLD, 0, 0, 0, 10, 10, 10);
        TestRegions.index(lookup, region);
    }

    @AfterEach
    void tearDown() {
        ActionSupport.declare("an undeclared platform", null, null, true, null);
        TestRegions.unindex(RegionLookup.get(), region);
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(OWNER);
        Plugins.forget(Plugins.CORE);
    }

    private Decision vehicle(VehicleContext.Action action) {
        Actor cart = action == VehicleContext.Action.PLACE ? null : Actors.entity("minecraft:minecart", ECEntityType.OTHER);
        return lookup.decide(new VehicleContext(AT, Actors.player(player), null, action, cart));
    }

    private Decision spawn(String key, ECEntityType type) {
        return lookup.decide(new SpawnContext(AT, Actors.environment("spawn"), null, Actors.entity(key, type), "NATURAL"));
    }

    private Decision item(EventSource source, String key) {
        return lookup.decide(new ItemContext(source, AT, Actors.player(player), null, TypeKey.parse(key), 1, null, null));
    }

    private Decision hit(Actor attacker) {
        return lookup.decide(new DamageContext(EventSource.DAMAGE, AT, attacker, null, Actors.player(new TestFPlayerSender("victim"))));
    }

    @Test
    void denyVehicleDeniesEveryActionAndARefinementSetToFalseLiftsOnlyItsOwn() {
        region.setFlag(CoreFlags.DENY_VEHICLE, Tristate.TRUE);

        assertEquals(Decision.DENY, vehicle(VehicleContext.Action.PLACE), "the refinements are undefined: deny-vehicle decides");
        assertEquals(Decision.DENY, vehicle(VehicleContext.Action.BREAK));
        assertEquals(Decision.DENY, vehicle(VehicleContext.Action.ENTER));

        region.setFlag(CoreFlags.DENY_VEHICLE_ENTER, Tristate.FALSE);

        assertEquals(Decision.PASS, vehicle(VehicleContext.Action.ENTER));
        assertEquals(Decision.DENY, vehicle(VehicleContext.Action.PLACE));
        assertEquals(Decision.DENY, vehicle(VehicleContext.Action.BREAK));
    }

    @Test
    void denySpawnDeniesEveryCreatureAndARefinementSetToFalseLiftsOnlyItsKind() {
        region.setFlag(CoreFlags.DENY_SPAWN, Tristate.TRUE);

        assertEquals(Decision.DENY, spawn("minecraft:zombie", ECEntityType.MONSTER));
        assertEquals(Decision.DENY, spawn("minecraft:cow", ECEntityType.ANIMAL));
        assertEquals(Decision.DENY, spawn("minecraft:bat", ECEntityType.OTHER), "no refinement speaks of it: deny-spawn itself is asked");

        region.setFlag(CoreFlags.DENY_SPAWN_MONSTER, Tristate.FALSE);

        assertEquals(Decision.PASS, spawn("minecraft:zombie", ECEntityType.MONSTER));
        assertEquals(Decision.DENY, spawn("minecraft:cow", ECEntityType.ANIMAL));

        region.removeFlag(CoreFlags.DENY_SPAWN);
        region.removeFlag(CoreFlags.DENY_SPAWN_MONSTER);
        region.setFlag(CoreFlags.DENY_SPAWN_ANIMAL, Tristate.TRUE);

        assertEquals(Decision.DENY, spawn("minecraft:cow", ECEntityType.ANIMAL));
        assertEquals(Decision.PASS, spawn("minecraft:zombie", ECEntityType.MONSTER));
    }

    @Test
    void denySpawnWithATypeFilterLetsACreatureOutsideItSpawn() {
        region.setFlag(CoreFlags.DENY_SPAWN, Tristate.TRUE);
        TypeFilterSettings onlyZombies = new TypeFilterSettings();
        onlyZombies.setOnly(Collections.singletonList("minecraft:zombie"));
        region.setFlagSettings(CoreFlags.DENY_SPAWN, onlyZombies);

        assertEquals(Decision.DENY, spawn("minecraft:zombie", ECEntityType.MONSTER), "the monster refinement reads the parent's value and settings");
        assertEquals(Decision.PASS, spawn("minecraft:skeleton", ECEntityType.MONSTER));
        assertEquals(Decision.PASS, spawn("minecraft:cow", ECEntityType.ANIMAL));
    }

    @Test
    void denyItemPickupNarrowedByOnlyAndExcept() {
        region.setFlag(CoreFlags.DENY_ITEM_PICKUP, Tristate.TRUE);
        TypeFilterSettings onlyDiamonds = new TypeFilterSettings();
        onlyDiamonds.setOnly(Collections.singletonList("minecraft:diamond"));
        region.setFlagSettings(CoreFlags.DENY_ITEM_PICKUP, onlyDiamonds);

        assertEquals(Decision.DENY, item(EventSource.ITEM_PICKUP, "minecraft:diamond"));
        assertEquals(Decision.PASS, item(EventSource.ITEM_PICKUP, "minecraft:dirt"), "dirt is outside 'only'");
        assertEquals(Decision.PASS, item(EventSource.ITEM_DROP, "minecraft:diamond"), "deny-item-pickup says nothing about a drop");

        TypeFilterSettings butDiamonds = new TypeFilterSettings();
        butDiamonds.setExcept(Collections.singletonList("minecraft:diamond"));
        region.setFlagSettings(CoreFlags.DENY_ITEM_PICKUP, butDiamonds);

        assertEquals(Decision.PASS, item(EventSource.ITEM_PICKUP, "minecraft:diamond"), "diamonds are in 'except'");
        assertEquals(Decision.DENY, item(EventSource.ITEM_PICKUP, "minecraft:dirt"));
    }

    @Test
    void denyItemDropDeniesADrop() {
        region.setFlag(CoreFlags.DENY_ITEM_DROP, Tristate.TRUE);

        assertEquals(Decision.DENY, item(EventSource.ITEM_DROP, "minecraft:dirt"));
        assertEquals(Decision.PASS, item(EventSource.ITEM_PICKUP, "minecraft:dirt"));
    }

    private ItemContext dropAt(WorldLocPos at, String key) {
        return new ItemContext(EventSource.ITEM_DROP, at, Actors.player(player), null, TypeKey.parse(key), 1, null, null);
    }

    @Test
    void destroyDroppedItemsLetsTheDropThroughAndMarksItsItem() {
        region.setFlag(CoreFlags.DESTROY_DROPPED_ITEMS, Tristate.TRUE);
        ItemContext inside = dropAt(AT, "minecraft:dirt");
        ItemContext outside = dropAt(WorldLocPos.of(50, 5, 50, WORLD), "minecraft:dirt");
        ItemContext pickup = new ItemContext(EventSource.ITEM_PICKUP, AT, Actors.player(player), null, TypeKey.parse("minecraft:dirt"), 1,
                null, null);

        assertEquals(Decision.PASS, lookup.decide(inside), "the drop itself is allowed");
        assertTrue(inside.isItemDestroyed());
        assertEquals(Decision.PASS, lookup.decide(outside));
        assertFalse(outside.isItemDestroyed(), "outside the region the item stays");
        assertEquals(Decision.PASS, lookup.decide(pickup));
        assertFalse(pickup.isItemDestroyed(), "a pickup is no drop");
    }

    @Test
    void destroyDroppedItemsNarrowedByOnlyLeavesTheOtherItems() {
        region.setFlag(CoreFlags.DESTROY_DROPPED_ITEMS, Tristate.TRUE);
        TypeFilterSettings onlyDirt = new TypeFilterSettings();
        onlyDirt.setOnly(Collections.singletonList("minecraft:dirt"));
        region.setFlagSettings(CoreFlags.DESTROY_DROPPED_ITEMS, onlyDirt);
        ItemContext dirt = dropAt(AT, "minecraft:dirt");
        ItemContext diamond = dropAt(AT, "minecraft:diamond");

        lookup.decide(dirt);
        lookup.decide(diamond);

        assertTrue(dirt.isItemDestroyed());
        assertFalse(diamond.isItemDestroyed(), "a diamond is outside 'only'");
    }

    @Test
    void denyItemDropBeatsDestroyDroppedItemsTheItemStaysWithTheDropper() {
        region.setFlag(CoreFlags.DESTROY_DROPPED_ITEMS, Tristate.TRUE);
        region.setFlag(CoreFlags.DENY_ITEM_DROP, Tristate.TRUE);

        assertEquals(Decision.DENY, lookup.decide(dropAt(AT, "minecraft:dirt")), "a denied drop never reaches the ground to vanish");
    }

    private ItemContext pickupBy(TestFPlayerSender who, UUID itemOwner, String item) {
        ItemContext pickup = new ItemContext(EventSource.ITEM_PICKUP, AT, Actors.player(who), null, TypeKey.parse(item), 1, null, null);
        pickup.setItemOwner(itemOwner);
        return pickup;
    }

    @Test
    void ownerOnlyPickupMarksTheDropAndDeniesEveryoneButTheOwner() {
        region.setFlag(CoreFlags.OWNER_ONLY_PICKUP, Tristate.TRUE);
        ItemContext drop = dropAt(AT, "minecraft:dirt");
        ItemContext dropOutside = dropAt(WorldLocPos.of(50, 5, 50, WORLD), "minecraft:dirt");

        assertEquals(Decision.PASS, lookup.decide(drop));
        assertEquals(Decision.PASS, lookup.decide(dropOutside));

        UUID owner = player.getUniqueId();
        assertEquals(owner, drop.getItemOwner());
        assertNull(dropOutside.getItemOwner(), "outside the region the item stays nobody's");
        assertEquals(Decision.PASS, lookup.decide(pickupBy(player, owner, "minecraft:dirt")));
        assertEquals(Decision.DENY, lookup.decide(pickupBy(new TestFPlayerSender("other"), owner, "minecraft:dirt")));
        assertEquals(Decision.PASS, lookup.decide(pickupBy(new TestFPlayerSender("other"), null, "minecraft:dirt")), "an item nobody dropped here");
        assertEquals(Decision.DENY, lookup.decide(hopperPull(owner)));
        assertEquals(Decision.PASS, lookup.decide(hopperPull(null)));
    }

    private ItemContext hopperPull(UUID itemOwner) {
        ItemContext pull = new ItemContext(EventSource.ITEM_HOPPER_PICKUP, AT, Actors.environment("hopper"), null, TypeKey.parse("minecraft:dirt"), 1, null, null);
        pull.setItemOwner(itemOwner);
        return pull;
    }

    @Test
    void ownerOnlyPickupNarrowedByExceptLeavesTheOtherItems() {
        region.setFlag(CoreFlags.OWNER_ONLY_PICKUP, Tristate.TRUE);
        TypeFilterSettings notDirt = new TypeFilterSettings();
        notDirt.setExcept(Collections.singletonList("minecraft:dirt"));
        region.setFlagSettings(CoreFlags.OWNER_ONLY_PICKUP, notDirt);
        ItemContext dirt = dropAt(AT, "minecraft:dirt");

        lookup.decide(dirt);

        assertNull(dirt.getItemOwner());
        assertEquals(Decision.PASS, lookup.decide(pickupBy(new TestFPlayerSender("other"), player.getUniqueId(), "minecraft:dirt")));
    }

    @Test
    void denyDamageFromMobsStopsAMobAndNothingElse() {
        region.setFlag(CoreFlags.DENY_DAMAGE_FROM_MOBS, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.entity("minecraft:zombie", ECEntityType.MONSTER)));
        assertEquals(Decision.DENY, hit(Actors.entity("minecraft:wolf", ECEntityType.ANIMAL)));
        assertEquals(Decision.PASS, hit(Actors.environment("fall")), "a fall still hurts");
        assertEquals(Decision.PASS, hit(Actors.environment("lava")), "lava still hurts");
        assertEquals(Decision.PASS, hit(Actors.player(new TestFPlayerSender("attacker"))), "a player is no mob");
        assertEquals(Decision.PASS, lookup.decide(new DamageContext(EventSource.DAMAGE, AT, Actors.entity("minecraft:zombie", ECEntityType.MONSTER), null,
                Actors.entity("minecraft:cow", ECEntityType.ANIMAL))), "only a player's damage is about this flag");
    }

    @Test
    void aFlagWhoseCoreSourceThePlatformNeverRaisesSaysSoOneLineEach() {
        assertNull(FlagRegistry.get().describeWithoutEffect(CoreFlags.DENY_FLUID_FLOW), "nothing declared: every source is raised");

        ActionSupport.declare("Hytale 0.7.2", Arrays.asList(EventSource.BREAK, EventSource.PLACE), null, false, null);

        String fluid = FlagRegistry.get().describeWithoutEffect(CoreFlags.DENY_FLUID_FLOW);
        assertNotNull(fluid);
        assertTrue(fluid.contains("evernifecore:deny-fluid-flow") && fluid.contains("evernifecore:fluid"), fluid);
        assertNull(FlagRegistry.get().describeWithoutEffect(CoreFlags.DENY_BLOCK_BREAK));
        assertNull(FlagRegistry.get().describeWithoutEffect(CoreFlags.TELEPORT_POINT), "a value-only flag needs no event");
        assertFalse(FlagRegistry.get().getUnservedSources(CoreFlags.DENY_VEHICLE_ENTER).isEmpty());
    }
}
