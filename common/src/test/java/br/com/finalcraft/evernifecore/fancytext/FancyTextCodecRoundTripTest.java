package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverRegistry;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The codec's round-trip contract for {@link FancyText}: content survives, not just segment count.
 */
@ECoreTest
public class FancyTextCodecRoundTripTest {


    private Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("data.yml"));
    }

    @Test
    void codecRoundTripKeepsSegmentCountStable(@TempDir Path dir) {
        FancyFormatter original = FancyFormatter.of("&aone").append("&btwo").append("&cthree");
        int expected = original.getFancyTextList().size();

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        // first re-read
        Config reopened = open(dir);
        FancyText firstRead = reopened.getValue("msg", FancyText.class);
        assertEquals(expected, ((FancyFormatter) firstRead).getFancyTextList().size(),
                "segment count must survive the very first read");

        // write back what was just read, then read again: the count must NOT grow
        reopened.setValue("msg", firstRead);
        reopened.save();
        Config thirdOpen = open(dir);
        FancyText secondRead = thirdOpen.getValue("msg", FancyText.class);
        assertEquals(expected, ((FancyFormatter) secondRead).getFancyTextList().size(),
                "segment count grew across a read->write->read cycle");
    }

    /**
     * The full field-by-field contract for a formatter of 10+ children mixing every shape the codec
     * has to carry: plain text, a plain hover, the {@code $show_item$} hover sentinel, every non-NONE
     * {@link ClickActionType} with its action text, and a child whose click type is set but has no
     * action text at all (the shape a locale entry with no click text produces). Reads through
     * {@code FancyText.class} - the interface, not a concrete class - so a silent fallback to an
     * empty default (the EveryConfig trap the codec's concrete-class registrations work around) would
     * fail loudly here instead of passing by accident.
     */
    @Test
    void codecRoundTripPreservesClickAndHoverTypeForEveryChildInOrder(@TempDir Path dir) {
        List<FancySegment> expected = new ArrayList<>();
        expected.add(new FancySegment("§aChild One"));                                                        // (a) plain
        expected.add(new FancySegment("§bChild Two", "§7Simple hover text"));                                  // (b) plain hover
        FancySegment childThree = new FancySegment("§cChild Three");
        childThree.setHoverItem("minecraft:diamond_sword");                                                 // (c) hover sentinel
        expected.add(childThree);
        expected.add(new FancySegment("§dChild Four", null, "/say run four", ClickActionType.RUN_COMMAND));    // (d) RUN_COMMAND
        expected.add(new FancySegment("§eChild Five", null, "/suggest five", ClickActionType.SUGGEST_COMMAND));// (d) SUGGEST_COMMAND
        expected.add(new FancySegment("§fChild Six", null, "https://example.com/six", ClickActionType.OPEN_URL)); // (d) OPEN_URL
        expected.add(new FancySegment("§9Child Seven", "§7hover seven", "/say seven", ClickActionType.RUN_COMMAND)); // hover + click together
        FancySegment childEight = new FancySegment("§0Child Eight");
        childEight.setClickType(ClickActionType.RUN_COMMAND);                                                // (e) type set, no action text
        expected.add(childEight);
        expected.add(new FancySegment("§1Child Nine", "§2hover nine"));                                        // (b) plain hover again
        FancySegment childTen = new FancySegment("§3Child Ten");
        childTen.setHoverItem("minecraft:diamond");                                                            // (c) hover sentinel again
        expected.add(childTen);
        assertTrue(expected.size() >= 10, "the fixture must cover at least 10 children");

        FancyFormatter original = new FancyFormatter();
        for (FancySegment child : expected) {
            original.append(child);
        }

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        Config reopened = open(dir);
        // reads through the FancyText interface - the path that goes through EveryConfig's
        // EntityBinder.constructDefault()/readerForUpdating seam, not a concrete-class shortcut.
        FancyText read = reopened.getValue("msg", FancyText.class);
        assertTrue(read instanceof FancyFormatter, "a formatter node must load back as a FancyFormatter");
        List<FancyText> actual = ((FancyFormatter) read).getFancyTextList();

        assertEquals(expected.size(), actual.size(), "every child must survive, none dropped or added");
        for (int i = 0; i < expected.size(); i++) {
            FancySegment want = expected.get(i);
            FancyText got = actual.get(i);
            String at = " (child #" + i + ")";
            assertEquals(want.getText(), got.getText(), "text" + at);
            assertEquals(want.getHoverText(), got.getHoverText(), "hoverText" + at);
            assertEquals(want.getClickActionText(), got.getClickActionText(), "clickActionText" + at);
            assertEquals(want.getClickActionType(), got.getClickActionType(), "clickActionType" + at);
        }
    }

    /**
     * The churn this fixes: a locale entry built the way {@code FCLocaleScanner} builds one - a child
     * with no click text at all still carries the annotation's default {@code RUN_COMMAND} type. Before
     * this fix, the codec dropped a type with no action text, so a save->load cycle came back as
     * {@code NONE} and {@code equals()} never matched the live value - which is exactly what made
     * {@code ECPluginData} think the locale file was out of date on every reload.
     */
    @Test
    void formatterWithClickTypeButNoActionTextSurvivesEqualsAfterRoundTrip(@TempDir Path dir) {
        // Mirrors FCLocaleScanner: a root segment plus a child whose click() was never declared -
        // append(text, hover, runCommand) with a null runCommand still forces clickActionType = RUN_COMMAND,
        // then click(...) re-applies the annotation's own default, exactly like the scanner does.
        FancyText root = new FancySegment("§aRoot text", null, null, ClickActionType.RUN_COMMAND);
        FancyText combined = root.append("§bChild text", null, null);
        combined.setClickType(ClickActionType.RUN_COMMAND);
        assertTrue(combined instanceof FancyFormatter, "append() on a leaf must produce a formatter");
        FancyFormatter original = (FancyFormatter) combined;

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        Config reopened = open(dir);
        FancyText read = reopened.getValue("msg", FancyText.class);

        assertEquals(original, read,
                "a click type with no action text must round-trip identically, or the locale file churns forever");
    }

    /**
     * A file written before this fix has no {@code hoverType} key and never wrote {@code clickActionType}
     * without an accompanying {@code clickActionText} - both additive keys are simply absent. Reading it
     * must not throw, and must fall back to exactly what it meant before this fix: hover is plain text
     * (no sentinel unwrapping) and a missing click type is {@code NONE}.
     */
    @Test
    void legacyFileWithoutTheNewKeysDecodesWithTheOldImplicitDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.yml");
        String legacyYaml = ""
                + "plain: '&aplain text'\n"
                + "withHoverAndClick:\n"
                + "  text: '&aClick me'\n"
                + "  hoverText: '&7A helpful tooltip'\n"
                + "  clickActionText: /say hi\n"
                + "  clickActionType: RUN_COMMAND\n"
                + "withItemHoverSentinel:\n"
                + "  text: '&ahover item'\n"
                + "  hoverText: $show_item$minecraft:diamond_sword\n";
        Files.write(file, legacyYaml.getBytes(StandardCharsets.UTF_8));

        Config cfg = ConfigFactory.open(file);

        FancyText plain = cfg.getValue("plain", FancyText.class);
        assertEquals("§aplain text", plain.getText());
        assertNull(plain.getHoverText());
        assertNull(plain.getClickActionText());
        assertEquals(ClickActionType.NONE, plain.getClickActionType());

        FancyText withHoverAndClick = cfg.getValue("withHoverAndClick", FancyText.class);
        assertEquals("§aClick me", withHoverAndClick.getText());
        assertEquals("§7A helpful tooltip", withHoverAndClick.getHoverText());
        assertEquals("/say hi", withHoverAndClick.getClickActionText());
        assertEquals(ClickActionType.RUN_COMMAND, withHoverAndClick.getClickActionType());

        // no hoverType key present: the sentinel-carrying string stays exactly as stored, unwrapped -
        // identical to how it read before this fix existed.
        FancyText withItemHoverSentinel = cfg.getValue("withItemHoverSentinel", FancyText.class);
        assertEquals("§ahover item", withItemHoverSentinel.getText());
        assertEquals("$show_item$minecraft:diamond_sword", withItemHoverSentinel.getHoverText());
        assertNull(withItemHoverSentinel.getClickActionText());
        assertEquals(ClickActionType.NONE, withItemHoverSentinel.getClickActionType());
    }

    // ------------------------------------------------------------------
    //  hover persistence: a type with no codec warns once and survives
    // ------------------------------------------------------------------

    private static final String UNPERSISTABLE_TYPE = "fx1_unpersistable_hover";
    private static final String ORPHAN_TYPE = "fx1_orphan_hover_type";


    @BeforeAll
    static void registerTheHoverTypesTheseCasesPersist() {
        registerCodecless(UNPERSISTABLE_TYPE);
        if (!FancyHoverRegistry.registeredIds().contains(TYPE_ID)) {
            FancyHoverRegistry.register(FancyHoverType.<CustomHover>of(TYPE_ID,
                            custom -> HoverEvent.showText(Component.text(custom.payload())))
                    .withCodec(CustomHover::payload, CustomHover::new));
        }
    }

    private static void registerCodecless(String typeId) {
        if (!FancyHoverRegistry.registeredIds().contains(typeId)) {
            FancyHoverRegistry.register(FancyHoverType.<CodeclessHover>of(typeId,
                    hover -> HoverEvent.showText(Component.text(hover.payload))));
        }
    }

    private Config open(Path dir, String fileName) {
        return ConfigFactory.open(dir.resolve(fileName));
    }

    @Test
    void savingACodeclessHoverWarnsOnceAndStillRecordsItsType(@TempDir Path dir) throws IOException {
        List<String> warnings = captureCoreWarnings();
        try {
            FancySegment first = new FancySegment("§aone");
            first.setHover(new CodeclessHover(UNPERSISTABLE_TYPE, "payload-one"));
            Config firstCfg = open(dir, "first.yml");
            firstCfg.setValue("msg", first);
            firstCfg.save();

            FancySegment second = new FancySegment("§btwo");
            second.setHover(new CodeclessHover(UNPERSISTABLE_TYPE, "payload-two"));
            Config secondCfg = open(dir, "second.yml");
            secondCfg.setValue("msg", second);
            secondCfg.save();

            List<String> aboutThisType = new ArrayList<>();
            for (String warning : warnings) {
                if (warning.contains(UNPERSISTABLE_TYPE)) {
                    aboutThisType.add(warning);
                }
            }
            assertEquals(1, aboutThisType.size(),
                    "a lang file with hundreds of entries of the same type must warn once, not once per entry: "
                            + aboutThisType);

            String written = new String(Files.readAllBytes(dir.resolve("first.yml")), StandardCharsets.UTF_8);
            assertTrue(written.contains("hoverType"),
                    "the type must be recorded even with no payload, or the file is indistinguishable "
                            + "from one that never had a hover: " + written);
            assertTrue(written.contains(UNPERSISTABLE_TYPE),
                    "the recorded type must be the value's own type id: " + written);
        } finally {
            warnings.clear();
        }
    }

    @Test
    void readingATypeWithNoPayloadYieldsNoHoverAndReportsTheLoss(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("orphan.yml");
        Files.write(file, ("msg:\n"
                + "  text: '&aSome text'\n"
                + "  hoverType: " + ORPHAN_TYPE + "\n").getBytes(StandardCharsets.UTF_8));

        List<String> warnings = captureCoreWarnings();
        FancyText read = ConfigFactory.open(file).getValue("msg", FancyText.class);

        assertEquals("§aSome text", read.getText(), "the rest of the node must still read normally");
        assertNull(read.getHover(), "a type with no payload cannot be rebuilt, so there is no hover");

        List<String> aboutThisType = new ArrayList<>();
        for (String warning : warnings) {
            if (warning.contains(ORPHAN_TYPE)) {
                aboutThisType.add(warning);
            }
        }
        assertEquals(1, aboutThisType.size(), "the load must denounce the missing payload exactly once: " + warnings);
    }

    /**
     * Captures what the codec logs for the duration of the current test. The handler stays attached to
     * the shared logger - harmless, since every assertion filters by this test's own unique type id.
     */
    private static List<String> captureCoreWarnings() {
        List<String> captured = new ArrayList<>();
        Logger.getLogger("EverNifeCore").addHandler(new Handler() {
            @Override public void publish(LogRecord record) { captured.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return captured;
    }

    /** A plugin-owned hover value whose type was registered without {@code withCodec}. */
    static final class CodeclessHover implements FancyHover {
        private final String typeId;
        private final String payload;

        CodeclessHover(String typeId, String payload) {
            this.typeId = typeId;
            this.payload = payload;
        }

        @Override
        public String typeId() {
            return typeId;
        }
    }

    // ------------------------------------------------------------------
    //  literal payloads the codec must not reinterpret
    // ------------------------------------------------------------------

    @Test
    void aClickUrlWithQueryParametersSurvivesTheRoundTripUntouched(@TempDir Path dir) {
        String url = "https://x.com/a?b=1&c=2";
        FancySegment original = new FancySegment("§aOpen it");
        original.setClickLink(url);

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText firstRead = open(dir).getValue("msg", FancyText.class);
        assertEquals(url, firstRead.getClickActionText(), "the '&' of a query string is not a colour code");
        assertEquals(ClickActionType.OPEN_URL, firstRead.getClickActionType());
        assertEquals("§aOpen it", firstRead.getText(), "visible text still carries its colour codes");

        // a second save->load cycle must be a fixed point: the value that came back is the value that
        // goes out, or the lang file would keep changing shape on every boot.
        Config reopened = open(dir);
        reopened.setValue("msg", firstRead);
        reopened.save();
        assertEquals(url, open(dir).getValue("msg", FancyText.class).getClickActionText(),
                "the click text must survive a read->write->read cycle unchanged");
    }

    @Test
    void anItemHoverPayloadWithAnAmpersandSurvivesTheRoundTripUntouched(@TempDir Path dir) {
        String rawItem = "{id:\"minecraft:diamond\",tag:{display:{Name:\"Salt & Pepper\"}}}";
        FancySegment original = new FancySegment("§bHover me");
        original.setHoverItem(rawItem);

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText firstRead = open(dir).getValue("msg", FancyText.class);
        FancyHover hover = firstRead.getHover();
        assertNotNull(hover, "the item hover must survive the read");
        assertInstanceOf(ItemHover.class, hover, "an 'item' hoverType must decode back to an ItemHover");
        assertEquals(rawItem, ((ItemHover) hover).rawItem(), "an item id/SNBT payload is not colour-coded text");
        assertEquals(ItemHover.LEGACY_SENTINEL + rawItem, firstRead.getHoverText());

        Config reopened = open(dir);
        reopened.setValue("msg", firstRead);
        reopened.save();
        FancyHover secondHover = open(dir).getValue("msg", FancyText.class).getHover();
        assertEquals(rawItem, ((ItemHover) secondHover).rawItem(),
                "the item payload must survive a read->write->read cycle unchanged");
    }

    @Test
    void aPlainTooltipStillCarriesItsColourCodesBothWays(@TempDir Path dir) {
        FancySegment original = new FancySegment("§cDanger", "§7line one\n§7line two");

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText read = open(dir).getValue("msg", FancyText.class);
        assertEquals("§7line one\n§7line two", read.getHoverText(),
                "a text tooltip is the one hover payload that IS text, so it keeps the colour translation");
    }

    // ------------------------------------------------------------------
    //  a custom hover type with a codec, end to end
    // ------------------------------------------------------------------

    private static final String TYPE_ID = "custom_roundtrip";


    @Test
    void customHoverTypeRoundTripsThroughTheCodec(@TempDir Path dir) {
        FancySegment original = new FancySegment("§aHover me");
        original.setHover(new CustomHover("secret-payload-42"));

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        Config reopened = open(dir);
        FancyText firstRead = reopened.getValue("msg", FancyText.class);
        FancyHover firstHover = firstRead.getHover();
        assertNotNull(firstHover, "the custom hover must survive the first read, not be dropped");
        assertEquals(TYPE_ID, firstHover.typeId(), "the hover type id must survive");
        assertInstanceOf(CustomHover.class, firstHover, "the hover must decode back to its own type");
        assertEquals("secret-payload-42", ((CustomHover) firstHover).payload(), "the payload must survive");

        // write back what was read, read again: a second cycle must keep the same value.
        reopened.setValue("msg", firstRead);
        reopened.save();
        FancyHover secondHover = open(dir).getValue("msg", FancyText.class).getHover();
        assertEquals(TYPE_ID, secondHover.typeId());
        assertEquals("secret-payload-42", ((CustomHover) secondHover).payload(),
                "the payload must survive a second save->load cycle");
    }

    /** A plugin-owned hover value: opaque payload, its own type id, reconstructed by the codec. */
    static final class CustomHover implements FancyHover {
        private final String payload;

        CustomHover(String payload) {
            this.payload = payload;
        }

        String payload() {
            return payload;
        }

        @Override
        public String typeId() {
            return TYPE_ID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomHover)) return false;
            return Objects.equals(payload, ((CustomHover) o).payload);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(payload);
        }
    }
}
