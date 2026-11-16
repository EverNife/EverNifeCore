package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItem;
import br.com.finalcraft.evernifecore.minecraft.inventory.invitem.InvItemManager;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartAmount;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.PartRegistration;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRequirement;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What Bukkit keeps for a plugin outside common - extra inventories, inv items, item parts - is owned and leaves with it. */
class PluginOwnedRegistriesTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** Another plugin's data, leaving the rig's extractor in place for the rig's own plugin. */
    private ECPluginData otherPlugin(String name) {
        IECPluginExtractor previous = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        try {
            return Plugins.fakePluginData(name, tempDir.resolve(name).toFile());
        } finally {
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, previous);
        }
    }

    private static IExtraInvFactory<?> factory(String id) {
        return Doubles.of(IExtraInvFactory.class).returning("getId", id).build();
    }

    private static InvItem invItem(String id, Material material) {
        return Doubles.of(InvItem.class).returning("getId", id).returning("getMaterial", material).build();
    }

    @Test
    void anExtraInventoryLeavesWithThePluginThatRegisteredIt() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData backpacks = otherPlugin("OwnedBackpacks");
            try {
                ExtraInvManager.registerFactory(backpacks, factory("Backpack"));
                assertNotNull(ExtraInvManager.getFactory("backpack"));

                ECPluginManager.removePluginData("OwnedBackpacks");

                assertNull(ExtraInvManager.getFactory("backpack"), "the factory outlived its plugin");
            } finally {
                ExtraInvManager.unregisterAll(backpacks);
                Plugins.forget("OwnedBackpacks");
            }
        }
    }

    @Test
    void aSecondPluginCannotTakeAnExtraInventoryIdAndTheSameOneReplacesItsOwn() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData first = otherPlugin("FirstBackpacks");
            ECPluginData second = otherPlugin("SecondBackpacks");
            try {
                ExtraInvManager.registerFactory(first, factory("baubles"));

                IllegalStateException refused = assertThrows(IllegalStateException.class,
                        () -> ExtraInvManager.registerFactory(second, factory("Baubles")));
                assertTrue(refused.getMessage().contains("FirstBackpacks") && refused.getMessage().contains("SecondBackpacks"),
                        refused.getMessage());

                IExtraInvFactory<?> again = factory("baubles");
                ExtraInvManager.registerFactory(first, again);
                assertSame(again, ExtraInvManager.getFactory("baubles"), "a reload replaces the owner's own factory");
            } finally {
                ExtraInvManager.unregisterAll(first);
                ExtraInvManager.unregisterAll(second);
                Plugins.forget("FirstBackpacks");
                Plugins.forget("SecondBackpacks");
            }
        }
    }

    @Test
    void anInvItemLeavesWithItsPluginAndAnotherPluginCannotTakeItsIdOrMaterial() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData chests = otherPlugin("OwnedChests");
            ECPluginData rival = otherPlugin("RivalChests");
            try {
                InvItemManager.register(chests, invItem("ironchest", Material.CHEST));

                assertThrows(IllegalStateException.class, () -> InvItemManager.register(rival, invItem("IronChest", Material.ENDER_CHEST)));
                IllegalStateException refused = assertThrows(IllegalStateException.class,
                        () -> InvItemManager.register(rival, invItem("other", Material.CHEST)));
                assertTrue(refused.getMessage().contains("OwnedChests") && refused.getMessage().contains("RivalChests"), refused.getMessage());

                InvItem again = invItem("ironchest", Material.CHEST);
                InvItemManager.register(chests, again);
                assertSame(again, InvItemManager.of(Material.CHEST), "a reload replaces the owner's own item");

                ECPluginManager.removePluginData("OwnedChests");

                assertNull(InvItemManager.of("ironchest"), "the item outlived its plugin");
                assertNull(InvItemManager.of(Material.CHEST));
            } finally {
                InvItemManager.unregisterAll(chests);
                InvItemManager.unregisterAll(rival);
                Plugins.forget("OwnedChests");
                Plugins.forget("RivalChests");
            }
        }
    }

    @Test
    void anItemPartLeavesWithItsPluginAndTheShippedPartsStay() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ItemEngine engine = ItemEngine.install(ItemRuntime.of(MCDetailedVersion.v1_21_R1));
            ECPluginData parts = otherPlugin("OwnedParts");
            try {
                int shipped = engine.getParts().size();
                engine.register(parts, PartRegistration.of("ownedpart", ItemRequirement.base(), new String[]{}, ItemDataPartAmount::new));
                assertNotNull(engine.find("ownedpart"));

                ECPluginManager.removePluginData("OwnedParts");
                engine.unregisterAll(world.getPluginData());

                assertNull(engine.find("ownedpart"), "the part outlived its plugin");
                assertEquals(shipped, engine.getParts().size(), "no plugin's release takes a part the engine ships with");
            } finally {
                Plugins.forget("OwnedParts");
                ItemEngine.uninstall();
            }
        }
    }
}
