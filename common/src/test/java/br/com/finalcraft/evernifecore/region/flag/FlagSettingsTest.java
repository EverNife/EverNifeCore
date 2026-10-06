package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flag's settings file, its reload with the owner, and the trigger throttle that reads it. */
@ECoreTest
class FlagSettingsTest {

    private static final String OWNER = "FlagSettingsOwner";
    private static final String OTHER = "FlagSettingsOther";

    public static class GlowSettings extends FlagSettings {
        private int brightness = 7;

        public int getBrightness() {
            return brightness;
        }

        public void setBrightness(int brightness) {
            this.brightness = brightness;
        }
    }

    private static final class GlowFlag extends TristateFlag {
        GlowFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "glow"), EnumSet.of(EventSource.MOVE), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Class<? extends FlagSettings> getSettingsType() {
            return GlowSettings.class;
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private ECPluginData other;
    private GlowFlag flag;
    private File file;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        other = Plugins.fakePluginData(OTHER, tempDir.resolve("other").toFile());
        flag = new GlowFlag(owner);
        file = tempDir.resolve("owner").resolve("regions").resolve("flags").resolve("glow.yml").toFile();
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().reset();
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
        Plugins.forget(OTHER);
    }

    private void edit(String from, String to) throws Exception {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains(from), "the file should hold " + from + ":\n" + text);
        Files.write(file.toPath(), text.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void registeringWritesTheDefaultsToTheOwnersFlagFolder() throws Exception {
        assertFalse(file.exists());
        FlagRegistry.get().register(flag);

        assertTrue(file.exists(), "expected " + file);
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("PT2S"), text);
        assertTrue(text.contains("brightness: 7"), text);
        GlowSettings settings = flag.getSettings();
        assertEquals(Duration.ofSeconds(2), settings.getTriggerInterval());
        assertEquals(7, settings.getBrightness());
    }

    @Test
    void ownersReloadReadsTheEditedFileAndAnotherPluginsReloadDoesNot() throws Exception {
        FlagRegistry.get().register(flag);
        edit("PT2S", "PT5S");
        edit("brightness: 7", "brightness: 3");

        ECEventBus.global().post(new ECPluginReloadEvent.Post(other));
        assertEquals(Duration.ofSeconds(2), flag.getSettings().getTriggerInterval());

        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));
        GlowSettings settings = flag.getSettings();
        assertEquals(Duration.ofSeconds(5), settings.getTriggerInterval());
        assertEquals(3, settings.getBrightness());
    }

    @Test
    void throttleLetsOneFiringPerPlayerAndFlagThroughEachInterval() {
        ManualClock clock = TestRegions.manualClock();
        FlagRegistry.get().register(flag);
        TristateFlag otherFlag = new TristateFlag(FlagKey.of(owner, "other-flag"), EnumSet.of(EventSource.MOVE), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(otherFlag);
        TriggerThrottle throttle = new TriggerThrottle();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertTrue(throttle.tryFire(alice, flag));
        assertFalse(throttle.tryFire(alice, flag));
        assertTrue(throttle.tryFire(bob, flag), "another player has a window of his own");
        assertTrue(throttle.tryFire(alice, otherFlag), "another flag has a window of its own");

        clock.advance(Duration.ofMillis(1999));
        assertFalse(throttle.tryFire(alice, flag));
        clock.advance(Duration.ofMillis(1));
        assertTrue(throttle.tryFire(alice, flag));

        throttle.forget(alice);
        assertTrue(throttle.tryFire(alice, flag));
    }

    @Test
    void throttleReadsTheIntervalOnEveryCallAndZeroTurnsItOff() throws Exception {
        TestRegions.manualClock();
        FlagRegistry.get().register(flag);
        TriggerThrottle throttle = new TriggerThrottle();
        UUID alice = UUID.randomUUID();
        assertTrue(throttle.tryFire(alice, flag));
        assertFalse(throttle.tryFire(alice, flag));

        edit("PT2S", "PT0S");
        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));

        assertTrue(throttle.tryFire(alice, flag));
        assertTrue(throttle.tryFire(alice, flag));
    }
}
