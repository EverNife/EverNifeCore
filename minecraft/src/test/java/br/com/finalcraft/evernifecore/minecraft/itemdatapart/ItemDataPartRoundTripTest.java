package br.com.finalcraft.evernifecore.minecraft.itemdatapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartAmount;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartComponents;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartCustomModelData;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartDurability;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartEnchantment;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartItemflags;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartLore;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartMaterial;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartNBT;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartName;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ParsedBlock;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.itemstack.testkit.ItemWorld;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import org.bukkit.inventory.ItemFlag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The law every part obeys: writing a value out and reading it back answers the same value.
 *
 * <p>It is what stops the two directions from drifting. Reading and writing used to be separate
 * pieces of code that happened to agree, and each place they stopped agreeing was a silent loss on
 * disk - a lore line that split in two, a name that lost its colour, a number that came back as
 * something else. Here the two are the same description, and this file is the proof.</p>
 *
 * <p>The law is checked twice for every value: once over the part alone, and once over the whole
 * line the engine writes and reads, {@code key:argument} included. The second one is what makes the
 * proved law the law that runs - splitting a line apart has rules of its own, and a value that only
 * survives {@code format} into {@code parse} can still lose something on the way through a file.</p>
 *
 * <p>No real server is involved. The engine stands on a runtime described by hand, because whether
 * a value survives its own text is a property of the part, not of the machine.</p>
 */
class ItemDataPartRoundTripTest {

    private static ItemWorld world;

    @BeforeAll
    static void aServerThatCanDoEverythingAPartAsksFor() {
        world = ItemWorld.install(ItemRuntime.of(MCDetailedVersion.v1_21_R1, ItemProbe.ITEM_META,
                ItemProbe.NBT, ItemProbe.SNBT_IO, ItemProbe.COMPONENTS, ItemProbe.ENCHANT_REGISTRY));
    }

    @AfterAll
    static void handTheJvmBack() {
        world.close();
    }

    /** {@code parse} of everything {@code format} wrote, folded back the way a block of lines is. */
    private static <V> void survivesTheRoundTrip(ItemDataPart<V> part, V value) {
        List<String> arguments = part.format(value);
        assertTrue(!arguments.isEmpty(),
                part.getCanonicalKey() + " wrote nothing at all for [" + value + "]");

        V rebuilt = null;
        for (String argument : arguments) {
            V parsed = part.parse(argument);
            rebuilt = rebuilt == null ? parsed : part.merge(rebuilt, parsed);
        }
        assertEquals(value, rebuilt, part.getCanonicalKey() + " wrote " + arguments
                + " and read back something else");

        survivesTheTripThroughItsOwnLines(part, value);
    }

    /** The same law over the pipeline that runs: lines out, lines in, value back. */
    private static <V> void survivesTheTripThroughItsOwnLines(ItemDataPart<V> part, V value) {
        List<String> lines = new ArrayList<>();
        for (String argument : part.format(value)) {
            lines.add(part.getCanonicalKey() + ":" + argument);
        }

        ParsedBlock block = world.getEngine().parse(lines);
        assertTrue(block.getProblems().isEmpty(),
                part.getCanonicalKey() + " wrote " + lines + " and could not read it back: "
                        + block.getProblems());
        assertEquals(value, world.getEngine().staged(block.getEdits(), part.getCanonicalKey()),
                part.getCanonicalKey() + " wrote " + lines + " and the block read something else");
    }

    @Test
    void aMaterialSurvivesWithItsDataValueAndItsNamespace() {
        ItemDataPartMaterial part = new ItemDataPartMaterial();

        survivesTheRoundTrip(part, "STONE");
        survivesTheRoundTrip(part, "STONE:2");
        survivesTheRoundTrip(part, "minecraft:diamond_sword");
        survivesTheRoundTrip(part, "pixelmon:poke_ball");

        assertEquals("STONE:2", part.parse("  STONE : 2 "),
                "spaces around a material are how a person types, not part of the name");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse(""))
                .getMessage().contains("DIAMOND_SWORD"), "the complaint shows a name that works");
    }

    @Test
    void anAmountIsWrittenEvenWhenItIsTheOnlyOneAnItemCanHave() {
        ItemDataPartAmount part = new ItemDataPartAmount();

        survivesTheRoundTrip(part, 1);
        survivesTheRoundTrip(part, 64);

        assertEquals(Arrays.asList("1"), part.format(1),
                "a single item still writes its amount: the file is a surface to edit, and this is "
                        + "the field an admin edits most");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("a lot"))
                .getMessage().contains("16"), "the complaint shows a number that works");
    }

    @Test
    void damageSurvivesAndUndamagedIsNotWorthALine() {
        ItemDataPartDurability part = new ItemDataPartDurability();

        survivesTheRoundTrip(part, 200);
        survivesTheRoundTrip(part, 14);
        assertEquals(Integer.valueOf(1561), part.parse("1561"));
    }

    @Test
    void aNameKeepsItsColoursThroughTheAmpersandForm() {
        ItemDataPartName part = new ItemDataPartName();

        survivesTheRoundTrip(part, "§6Espada do Rei");
        survivesTheRoundTrip(part, "§x§f§f§a§a§00Neon");
        survivesTheRoundTrip(part, "Rank #1");
        survivesTheRoundTrip(part, "%player_name%'s pickaxe");
        survivesTheRoundTrip(part, "§6   Espada do Rei   ");

        assertEquals("§6Espada", part.parse("&6Espada"), "the file writes '&' and the item holds the section sign");
        assertEquals(Arrays.asList("&6Espada"), part.format("§6Espada"));
        assertTrue(!part.trimsArgument(),
                "a name centred with spaces has to come back with them, so the line keeps its ends");
    }

    @Test
    void aLoreKeepsAHashInsideItAndBreaksOnlyOnRealLineBreaks() {
        ItemDataPartLore part = new ItemDataPartLore();

        survivesTheRoundTrip(part, Arrays.asList("§7Rank #1 of the season"));
        survivesTheRoundTrip(part, Arrays.asList("§7First", "§7Second", ""));
        survivesTheRoundTrip(part, Arrays.asList("§7Price: §6%product_price%"));
        survivesTheRoundTrip(part, Arrays.asList("§7   Centralizado   ", "§7Beside it"));

        assertEquals(Arrays.asList("§7Rank #1"), part.parse("&7Rank #1"),
                "'#' is a character an admin writes, not a line break - splitting on it grew a lore "
                        + "line every time the item was saved");
        assertEquals(Arrays.asList("A", "B"), part.parse("A\nB"), "a real line break still breaks");
        assertEquals(Arrays.asList("A", "B", "C"),
                part.merge(part.parse("A"), part.parse("B\nC")), "many lore lines pile into one block");
        assertTrue(!part.trimsArgument(),
                "padding is how a lore line is centred, so the line hands it over as it was written");
    }

    @Test
    void anAmountBelowOneAndDamageBelowZeroAreRefusedNamingTheFloor() {
        assertTrue(assertThrows(ItemLineException.class, () -> new ItemDataPartAmount().parse("0"))
                        .getMessage().contains("1 or more"),
                "a stack of zero is an item that vanishes, and the complaint says where counting starts");
        assertTrue(assertThrows(ItemLineException.class, () -> new ItemDataPartAmount().parse("-5"))
                .getMessage().contains("1 or more"));

        assertTrue(assertThrows(ItemLineException.class, () -> new ItemDataPartDurability().parse("-1"))
                        .getMessage().contains("0 or more"),
                "damage below zero wraps into a nearly destroyed item, so the complaint names the floor");
        assertEquals(Integer.valueOf(0), new ItemDataPartDurability().parse("0"),
                "undamaged is a value a file may state, even though a reading never writes it");
    }

    @Test
    void theTagHatchLeavesOutWhatAnotherKeyAlreadyWrites() {
        Set<String> owned = ItemDataPartNBT.getKeysOwnedElsewhere();

        assertTrue(owned.contains("CustomModelData"),
                "the model has a key of its own, and a tag emitting it too writes it twice: " + owned);
        assertTrue(owned.containsAll(Arrays.asList("display", "Damage", "HideFlags", "ench", "Enchantments")),
                "the name, the lore, the damage, the flags and the enchants are keys of their own too: "
                        + owned);
    }

    @Test
    void hiddenTooltipPartsSurviveOneByOneAndAllAtOnce() {
        ItemDataPartItemflags part = new ItemDataPartItemflags();

        Set<ItemFlag> some = EnumSet.of(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        survivesTheRoundTrip(part, some);
        survivesTheRoundTrip(part, EnumSet.allOf(ItemFlag.class));

        assertEquals(Arrays.asList("all"), part.format(EnumSet.allOf(ItemFlag.class)),
                "everything hidden is written as the word an admin would have written");
        assertEquals(some, part.parse("ENCHANTS#ATTRIBUTES"),
                "the HIDE_ prefix is optional, and '#' still joins here because no flag name has one");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("EVERYTHING"))
                .getMessage().contains("HIDE_ENCHANTS"), "the complaint lists the flags that exist");
        assertEquals(EnumSet.of(ItemFlag.HIDE_ENCHANTS), part.parse("EVERYTHING#ENCHANTS"),
                "a flag this server has no name for costs itself, not the flags written beside it");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("EVERYTHING#NOTHING"))
                        .getMessage().contains("HIDE_ENCHANTS"),
                "a line this server understands nothing of would hide nothing, which no file meant");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse(""))
                        .getMessage().contains("HIDE_ENCHANTS"),
                "an empty value never says whether hiding nothing was meant, so it is refused out loud");
    }

    @Test
    void aCustomModelSurvivesAsTheNumberTheResourcePackGaveIt() {
        ItemDataPartCustomModelData part = new ItemDataPartCustomModelData();

        survivesTheRoundTrip(part, 1042);
        survivesTheRoundTrip(part, 1);

        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("swordmodel"))
                .getMessage().contains("1042"), "the complaint shows a value that works");
    }

    @Test
    void anEnchantKeepsItsNamespaceAndItsLevelIsWhatFollowsTheLastColon() {
        ItemDataPartEnchantment part = new ItemDataPartEnchantment();

        survivesTheRoundTrip(part, levels("minecraft:sharpness", 5));
        survivesTheRoundTrip(part, levels("mymod:soul_reaping", 3));

        SortedMap<String, Integer> two = levels("minecraft:sharpness", 5);
        two.put("minecraft:unbreaking", 3);
        survivesTheRoundTrip(part, two);

        assertEquals(levels("minecraft:sharpness", 5), part.parse("minecraft:sharpness:5"),
                "the key keeps its own colon; only the last one separates the level");
        assertEquals(Arrays.asList("minecraft:sharpness:5", "minecraft:unbreaking:3"), part.format(two),
                "two enchants are two lines, in an order that does not depend on the item");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("minecraft:sharpness"))
                .getMessage().contains("<enchantment>:<level>"), "the complaint teaches the shape");
    }

    @Test
    void aRawTagSurvivesAsTheTextItWasWrittenAs() {
        ItemDataPartNBT part = new ItemDataPartNBT();

        survivesTheRoundTrip(part, Arrays.asList("{CustomModelData:1042}"));
        survivesTheRoundTrip(part, Arrays.asList("{display:{Name:\"x\"}}", "{Unbreakable:1b}"));

        assertEquals(Arrays.asList("{a:1}"), part.parse("  {a:1}  "),
                "the tag is what is between the braces, wherever the person put spaces around it");
        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("CustomModelData:1042"))
                .getMessage().contains("{CustomModelData:1042}"), "the complaint shows the braces");
    }

    @Test
    void aComponentBlockSurvivesAsTheTextItWasWrittenAs() {
        ItemDataPartComponents part = new ItemDataPartComponents();

        survivesTheRoundTrip(part, Arrays.asList("{\"minecraft:max_stack_size\":1}"));
        survivesTheRoundTrip(part,
                Arrays.asList("{\"minecraft:unbreakable\":{}}", "{\"minecraft:rarity\":\"epic\"}"));

        assertTrue(assertThrows(ItemLineException.class, () -> part.parse("minecraft:rarity=epic"))
                .getMessage().contains("SNBT"), "the complaint names the shape a component block takes");
    }

    @Test
    void everyPartAnswersToOneSpellingAndOnlyWritesThatOne() {
        List<ItemDataPart<?>> parts = new ArrayList<>(Arrays.asList(
                new ItemDataPartMaterial(), new ItemDataPartAmount(), new ItemDataPartDurability(),
                new ItemDataPartName(), new ItemDataPartLore(), new ItemDataPartItemflags(),
                new ItemDataPartCustomModelData(), new ItemDataPartEnchantment(),
                new ItemDataPartNBT(), new ItemDataPartComponents()));

        List<String> keys = new ArrayList<>();
        for (ItemDataPart<?> part : parts) {
            String key = part.getCanonicalKey();
            assertTrue(!key.isEmpty() && key.indexOf(':') < 0,
                    "a key is what comes before the ':' of a line, so it cannot contain one: " + key);
            assertTrue(!keys.contains(key), "two parts answer to '" + key + "'");
            keys.add(key);
        }
        assertEquals(10, keys.size(), "every part this library ships with is proved above: " + keys);
    }

    private static SortedMap<String, Integer> levels(String key, int level) {
        SortedMap<String, Integer> enchants = new TreeMap<>();
        enchants.put(key, level);
        return enchants;
    }

}
