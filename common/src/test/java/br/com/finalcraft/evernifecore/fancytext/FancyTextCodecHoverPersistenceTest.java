package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverRegistry;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHoverType;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import br.com.finalcraft.everyconfig.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hover value whose type never registered a codec cannot be reconstructed from a file - but it must
 * not disappear as if it had never existed. Saving it writes the type name with no payload and says so
 * once; loading that back reports the loss instead of quietly yielding a hover-less segment.
 *
 * <p>Every type id here is unique to its own test, because the codec's "warn once" bookkeeping is
 * static and outlives a test class in a shared JVM.</p>
 */
public class FancyTextCodecHoverPersistenceTest {

    private static final String UNPERSISTABLE_TYPE = "fx1_unpersistable_hover";
    private static final String ORPHAN_TYPE = "fx1_orphan_hover_type";

    @BeforeAll
    static void setUp() {
        TestPlatformFixture.ensureInstalled();
        registerCodecless(UNPERSISTABLE_TYPE);
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
}
