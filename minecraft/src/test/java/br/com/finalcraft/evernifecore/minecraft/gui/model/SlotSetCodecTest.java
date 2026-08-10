package br.com.finalcraft.evernifecore.minecraft.gui.model;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McConfigTypes;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A slot list has been written three ways on disk and there is now one codec that reads all three
 * and writes one.
 *
 * <p>The bare scalar is the reason this exists. It used to be parsed somewhere that expected a list,
 * came back empty, and left an icon missing from a menu with nothing to say why - so it gets a case of
 * its own here, and so does the text that is none of the three: an empty list is how an admin switches
 * an icon off, so text nothing can read must never arrive as one.</p>
 */
class SlotSetCodecTest {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();
    private static boolean typesRegistered = false;

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;

    @BeforeEach
    void setup() {
        world = Platforms.lenient().install().withPluginExtractor(
                Plugins.fake("SlotSetCodec_" + UNIQUE_SUFFIX.incrementAndGet(), tempDir.toFile()));
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);

        if (!typesRegistered) {
            //the registry is process-wide; registering the platform types a second time would be the
            //bootstrap running twice, which the platform's own guard is what prevents in production
            McConfigTypes.register();
            typesRegistered = true;
        }
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private Config open() {
        return ConfigFactory.open(tempDir.resolve("gui.yml"));
    }

    private SlotSet readAfterWriting(Object onDisk) {
        Config config = open();
        config.setValue("Icon.slot", onDisk);
        config.save();
        return open().getValue("Icon.slot", SlotSet.class);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The three forms a file may hold
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theBracketedStringReads() {
        assertArrayEquals(new int[]{1, 2, 3}, readAfterWriting("[1,2,3]").toArray());
        assertArrayEquals(new int[]{1, 2, 3}, readAfterWriting("[ 1, 2 , 3 ]").toArray(),
                "whitespace inside the brackets is an admin's formatting, not an error");
    }

    @Test
    void theYamlListReads() {
        assertArrayEquals(new int[]{1, 2, 3}, readAfterWriting(Arrays.asList(1, 2, 3)).toArray());
    }

    @Test
    void theBareScalarReads() {
        assertArrayEquals(new int[]{45}, readAfterWriting(45).toArray(),
                "one number means one slot - this is the form that used to vanish");
        assertArrayEquals(new int[]{45}, readAfterWriting("45").toArray());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The one form it writes
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everySetIsWrittenBackAsTheBracketedString() {
        Config config = open();
        config.setValue("Icon.slot", SlotSet.of(1, 2, 3));
        config.setValue("Icon.none", SlotSet.EMPTY);
        config.save();

        Config reopened = open();
        assertEquals("[1,2,3]", reopened.getString("Icon.slot"));
        assertEquals("[]", reopened.getString("Icon.none"));
        assertArrayEquals(new int[]{1, 2, 3}, reopened.getValue("Icon.slot", SlotSet.class).toArray());
    }

    @Test
    void aSetReadFromAnyFormIsWrittenBackInTheSameOne() {
        Config config = open();
        config.setValue("Icon.fromList", Arrays.asList(4, 5));
        config.setValue("Icon.fromScalar", 45);
        config.save();

        Config reopened = open();
        reopened.setValue("Icon.fromList", reopened.getValue("Icon.fromList", SlotSet.class));
        reopened.setValue("Icon.fromScalar", reopened.getValue("Icon.fromScalar", SlotSet.class));
        reopened.save();

        Config settled = open();
        assertEquals("[4,5]", settled.getString("Icon.fromList"));
        assertEquals("[45]", settled.getString("Icon.fromScalar"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Nowhere is a decision; nonsense is a refusal
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anEmptyListMeansSwitchedOffAndIsNeverAnError() {
        assertEquals(0, readAfterWriting(Collections.emptyList()).size());
        assertEquals(0, readAfterWriting("[]").size());
        assertEquals(0, readAfterWriting("").size());
    }

    @Test
    void unreadableTextIsRefusedNamingItself() {
        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> readAfterWriting("second row, third column"),
                "reading it as the empty list would make a broken key look like a deliberate one");

        String message = rootCauseOf(refused).getMessage();
        assertTrue(message.contains("second row"), "the file's own text travels with the refusal: " + message);
        assertTrue(message.contains("A slot list is written as"), message);
    }

    /** The failure that actually knows: a read that fails inside a codec is wrapped on its way out. */
    private static Throwable rootCauseOf(Throwable failure) {
        Throwable deepest = failure;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        return deepest;
    }

}
