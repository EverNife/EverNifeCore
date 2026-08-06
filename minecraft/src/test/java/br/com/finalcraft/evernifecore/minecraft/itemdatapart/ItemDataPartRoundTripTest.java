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
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemLineException;
import org.bukkit.inventory.ItemFlag;
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
 * <p>No server is involved, and none can be: the whole point of the pure half is that a value and
 * its text are a property of the part, not of the machine. Every value below is one that used to be
 * mishandled, or one an admin actually writes.</p>
 */
class ItemDataPartRoundTripTest {

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

        assertEquals("§6Espada", part.parse("&6Espada"), "the file writes '&' and the item holds the section sign");
        assertEquals(Arrays.asList("&6Espada"), part.format("§6Espada"));
    }

    @Test
    void aLoreKeepsAHashInsideItAndBreaksOnlyOnRealLineBreaks() {
        ItemDataPartLore part = new ItemDataPartLore();

        survivesTheRoundTrip(part, Arrays.asList("§7Rank #1 of the season"));
        survivesTheRoundTrip(part, Arrays.asList("§7First", "§7Second", ""));
        survivesTheRoundTrip(part, Arrays.asList("§7Price: §6%product_price%"));

        assertEquals(Arrays.asList("§7Rank #1"), part.parse("&7Rank #1"),
                "'#' is a character an admin writes, not a line break - splitting on it grew a lore "
                        + "line every time the item was saved");
        assertEquals(Arrays.asList("A", "B"), part.parse("A\nB"), "a real line break still breaks");
        assertEquals(Arrays.asList("A", "B", "C"),
                part.merge(part.parse("A"), part.parse("B\nC")), "many lore lines pile into one block");
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
