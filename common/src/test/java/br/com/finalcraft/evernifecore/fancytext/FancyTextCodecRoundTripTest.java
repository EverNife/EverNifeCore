package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the confirmed codec bug: {@code readFancyText} rebuilds a formatter through
 * {@link FancyFormatter#of()}, which itself seeds an empty leaf (see
 * {@link FancyTextModelContractTest#formatterFactoryAndConstructorAgreeOnChildCount()}), so every
 * read->write cycle grows the persisted segment count by one - the bug that makes a lang file grow
 * on every reload.
 */
public class FancyTextCodecRoundTripTest {

    @BeforeAll
    static void setUp() {
        // ConfigFactory's static init calls getPlatform().registerConfigTypes(); seed a no-op platform first.
        TestPlatformFixture.ensureInstalled();
    }

    private Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("data.yml"));
    }

    @Test
    @Tag("known-bug")
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
}
