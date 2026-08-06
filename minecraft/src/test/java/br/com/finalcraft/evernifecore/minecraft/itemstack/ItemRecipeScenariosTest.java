package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.BuiltItem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemDescription;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.PartRegistration;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.IncompleteItemException;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.PartFailure;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.RefusedEdit;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRequirement;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.itemstack.testkit.ItemWorld;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item api used the way a plugin actually uses it: long chains, servers that cannot do
 * everything, and files written by hand.
 *
 * <p>Each test below is a story with a beginning and an end, not a single call with an assertion
 * after it. That is deliberate - the thing worth proving about this api is not that one method
 * works, it is that a recipe of eight steps comes out as the item it described, that a server which
 * is missing a capability says which one, and that a mistake in a file costs one line.</p>
 */
class ItemRecipeScenariosTest {

    // -----------------------------------------------------------------------------------------------------------------
    //  1. A long chain, built and read back, with nothing lost on the way
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aLongRecipeComesBackAsTheSameTextItWasBuiltFrom() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {

            ItemStack sword = FCItemFactory.from(Material.DIAMOND_SWORD)
                    .displayName("&6Espada do Rei")
                    .lore("&7Forjada no fogo.", "&7Rank #1 da temporada.")
                    .addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                    .setCustomModelData(1042)
                    .amount(1)
                    .build();

            List<String> written = world.getEngine().read(sword).getLines();

            assertEquals(Arrays.asList(
                    "type:DIAMOND_SWORD",
                    "amount:1",
                    "CustomModelData:1042",
                    "hideflags:HIDE_ENCHANTS",
                    "hideflags:HIDE_ATTRIBUTES",
                    "name:&6Espada do Rei",
                    "lore:&7Forjada no fogo.",
                    "lore:&7Rank #1 da temporada."), written,
                    "the whole chain has to come out as text an admin could have typed");

            //and the text alone is enough to build the very same item again
            ItemStack rebuilt = FCItemFactory.from(written).build();
            assertEquals(written, world.getEngine().read(rebuilt).getLines(),
                    "reading and writing are the same description, so a second trip changes nothing");
            assertTrue(world.getEngine().isSimilar(sword, rebuilt),
                    "the rebuilt item is the item, not a lookalike");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  2. Changing the material half way through a chain
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void changingTheMaterialLateKeepsEverythingTheRecipeAskedForBefore() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {

            ItemStack glowing = FCItemFactory.from(Material.STONE)
                    .displayName("&bBotão")
                    .setGlow()
                    .addItemFlags(ItemFlag.HIDE_ENCHANTS)
                    .material(Material.DIAMOND)
                    .build();

            assertEquals(Material.DIAMOND, glowing.getType(), "the last material asked for is the one");

            List<String> written = world.getEngine().read(glowing).getLines();
            assertTrue(written.contains("hideflags:HIDE_ENCHANTS"),
                    "a flag asked for before the material change survives it: " + written);
            assertTrue(written.contains("name:&bBotão"),
                    "so does the name: " + written);
            assertTrue(glowing.getItemMeta().hasEnchants(),
                    "and so does the shimmer, which is what the flag was hiding");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  3. A server that cannot do everything the recipe asks
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aServerWithNoMetadataBuildsWhatItCanAndNamesWhatItCannot() {
        try (ItemWorld world = ItemWorld.install(ItemRuntime.of(MCDetailedVersion.v1_21_R1))) {

            BuiltItem built = FCItemFactory.from(Material.CHEST)
                    .amount(16)
                    .displayName("&aCaixa")
                    .lore("&7Guarda coisas.")
                    .materialize();

            assertEquals(Material.CHEST, built.getItemStack().getType(), "an item still comes out");
            assertEquals(16, built.getItemStack().getAmount(), "and what needs no metadata is applied");

            assertFalse(built.isComplete(), "and it does not pretend to be the item that was asked for");
            assertEquals(Arrays.asList("name", "lore"), namesOf(built.getRefused()),
                    "every refusal names the key that was dropped");
            assertTrue(built.getRefused().get(0).getReason().contains("no item metadata"),
                    "and says what the runtime is missing: " + built.getRefused().get(0).getReason());

            IncompleteItemException strict = assertThrows(IncompleteItemException.class,
                    built::requireComplete);
            assertTrue(strict.getMessage().contains("not a mistake in your config")
                            && strict.getMessage().contains("getItemStack()"),
                    "the strict door explains whose limit it is and what to do: " + strict.getMessage());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  4. A line the server is too old for
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anEnchantOnAnOldServerIsAnsweredWithTheVersionAndNotBlamedOnTheFile() {
        try (ItemWorld world = ItemWorld.install(
                ItemRuntime.of(MCDetailedVersion.v1_12_R1, ItemProbe.ITEM_META))) {

            BuiltItem built = FCItemFactory.from(Arrays.asList(
                    "type:DIAMOND_SWORD",
                    "name:&6Espada",
                    "enchant:minecraft:sharpness:5")).materialize();

            assertTrue(built.getProblems().isEmpty(),
                    "nothing is wrong with the file, so nothing is reported against it");
            assertEquals(Arrays.asList("enchant"), namesOf(built.getRefused()),
                    "the one line this server cannot honour is the one that is answered");

            String reason = built.getRefused().get(0).getReason();
            assertTrue(reason.contains("1.13") && reason.contains("v1_12_R1"),
                    "the answer names the version needed and the version running: " + reason);
            assertTrue(reason.contains("nbt:"),
                    "and offers the way to write it anyway on this server: " + reason);

            assertEquals("§6Espada", built.getItemStack().getItemMeta().getDisplayName(),
                    "the rest of the block was applied as usual");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  5. A part with a bug in it
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aPartThatBreaksIsReportedAsADefectWhileTheOthersStillDescribeTheItem() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            world.getEngine().register(PartRegistration.of("brokenpart", ItemRequirement.base(),
                    new String[]{}, PartThatBreaksWhenRead::new));

            ItemStack named = FCItemFactory.from(Material.PAPER).displayName("&fCarta").build();
            ItemDescription description = world.getEngine().read(named);

            assertEquals(Arrays.asList("type:PAPER", "amount:1", "name:&fCarta"), description.getLines(),
                    "a broken part costs its own line and nothing else");

            assertEquals(1, description.getFailures().size(), "the break is reported, not swallowed");
            PartFailure failure = description.getFailures().get(0);
            assertEquals("brokenpart", failure.getKey(), "and it names which part broke");
            assertTrue(failure.describe().contains("defect in the part, not in your config"),
                    "and says whose fault it is not: " + failure.describe());
            assertFalse(description.isComplete(),
                    "so nothing downstream can mistake this reading for the whole item");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  6. A file with a mistake in it
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aLineThatCannotBeReadCostsItselfAndLeavesTheRestOfTheBlockStanding() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {

            BuiltItem built = FCItemFactory.from(Arrays.asList(
                    "type:DIAMOND_SWORD",
                    "amount:muitos",
                    "colour:blue",
                    "name:&6Espada")).materialize();

            assertEquals(Material.DIAMOND_SWORD, built.getItemStack().getType());
            assertEquals("§6Espada", built.getItemStack().getItemMeta().getDisplayName(),
                    "the good lines were applied");

            assertEquals(2, built.getProblems().size(), "both bad lines were reported: "
                    + built.getProblems());
            assertTrue(built.getProblems().get(0).getReason().contains("16"),
                    "the complaint about a number shows a number: " + built.getProblems().get(0));
            assertTrue(built.getProblems().get(1).getReason().contains("amount"),
                    "the complaint about an unknown key lists the keys that exist: "
                            + built.getProblems().get(1));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  7. Two items that differ only where the old comparison could not look
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoItemsThatDifferOnlyInTheirCustomModelAreNotTheSameItem() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {

            ItemStack plain = FCItemFactory.from(Material.DIAMOND_SWORD).build();
            ItemStack modelled = FCItemFactory.from(Material.DIAMOND_SWORD)
                    .setCustomModelData(1042).build();
            ItemStack otherModel = FCItemFactory.from(Material.DIAMOND_SWORD)
                    .setCustomModelData(7).build();

            assertFalse(world.getEngine().isSimilar(plain, modelled),
                    "an item with a custom model is not the same as one without");
            assertFalse(world.getEngine().isSimilar(modelled, otherModel),
                    "and two different models are two different items");
            assertTrue(world.getEngine().isSimilar(modelled, FCItemFactory.from(Material.DIAMOND_SWORD)
                            .setCustomModelData(1042).build()),
                    "while the same model is the same item");

            ItemDescription reading = world.getEngine().read(plain);
            assertTrue(reading.getFailures().isEmpty(),
                    "reading an item that has no custom model is an ordinary reading: asking for one "
                            + "that is not there used to throw, and the throw used to be swallowed whole");
            assertFalse(reading.getLines().contains("CustomModelData:-1"),
                    "and absence is absence, not a number nobody chose");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static List<String> namesOf(List<RefusedEdit> refused) {
        List<String> names = new java.util.ArrayList<>();
        for (RefusedEdit edit : refused) {
            names.add(edit.getName());
        }
        Collections.sort(names, Collections.reverseOrder());
        return names;
    }

    /** A part whose reading half is wrong, which is what a blanket catch used to look like from outside. */
    private static final class PartThatBreaksWhenRead extends ItemDataPart<String> {

        @Nonnull
        @Override
        public String getCanonicalKey() {
            return "brokenpart";
        }

        @Nonnull
        @Override
        public String parse(@Nonnull String argument) {
            return argument;
        }

        @Nonnull
        @Override
        public List<String> format(@Nonnull String value) {
            return Collections.singletonList(value);
        }

        @Nonnull
        @Override
        public ItemStack apply(@Nonnull String value, @Nonnull ItemStack item) {
            return item;
        }

        @Nullable
        @Override
        public String extract(@Nonnull ItemStack item) {
            throw new IllegalStateException("this part reads a field that is not there");
        }

        @Override
        public int getPriority() {
            return PRIORITY_VERY_LATE;
        }
    }

}
