package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.flag.stock.InteractSettings;
import br.com.finalcraft.evernifecore.region.flag.stock.MoveDenySettings;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.annotation.Comment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flag's settings file - when it exists, its shape and header, its reloads - and the trigger throttle that reads it. */
@ECoreTest
class FlagSettingsTest {

    private static final String OWNER = "FlagSettingsOwner";
    private static final String OTHER = "FlagSettingsOther";
    private static final String CORE = "FlagSettingsCore";

    public static class GlowSettings extends FlagSettings {
        private int brightness = 7;

        public int getBrightness() {
            return brightness;
        }

        public void setBrightness(int brightness) {
            this.brightness = brightness;
        }
    }

    private static class GlowFlag extends TristateFlag {
        private final SettingsFilePolicy policy;

        GlowFlag(ECPluginData owner, SettingsFilePolicy policy) {
            super(FlagKey.of(owner, "glow"), EnumSet.of(EventSource.MOVE), Tristate.FALSE, Tristate.TRUE);
            this.policy = policy;
        }

        @Override
        public Class<? extends FlagSettings> getSettingsType() {
            return GlowSettings.class;
        }

        @Override
        public SettingsFilePolicy getSettingsFilePolicy() {
            return policy;
        }

        @Override
        public String getDescription() {
            return "TRUE: the region glows.";
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private ECPluginData other;
    private GlowFlag flag;
    private File file;
    private boolean generateFiles;

    @BeforeEach
    void setUp() {
        generateFiles = ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES;
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = true;
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        other = Plugins.fakePluginData(OTHER, tempDir.resolve("other").toFile());
        flag = new GlowFlag(owner, SettingsFilePolicy.FOLLOW_SERVER);
        file = tempDir.resolve("owner").resolve("regions").resolve("flags").resolve("glow.yml").toFile();
    }

    @AfterEach
    void tearDown() {
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = generateFiles;
        FlagRegistry.get().reset();
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
        Plugins.forget(OTHER);
        Plugins.forget(CORE);
    }

    private String text() throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private void edit(String from, String to) throws Exception {
        String text = text();
        assertTrue(text.contains(from), "the file should hold " + from + ":\n" + text);
        Files.write(file.toPath(), text.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    private static long linesNaming(TestPlatform platform, String... parts) {
        return platform.getLoggedMessages().stream().filter(line -> {
            for (String part : parts) {
                if (!line.contains(part)) {
                    return false;
                }
            }
            return true;
        }).count();
    }

    @Test
    void registeringWritesTheDefaultsReadableUnderTheSettingsRoot() throws Exception {
        assertFalse(file.exists());
        FlagRegistry.get().register(flag);

        assertTrue(file.exists(), "expected " + file);
        String text = text();
        assertFalse(text.contains("PT2S"), text);
        assertTrue(text.contains("\nSettings:\n"), text);
        assertTrue(text.contains("  triggerInterval: 2s"), text);
        assertEquals("2s", ConfigFactory.open(file).getString(FlagRegistry.SETTINGS_ROOT + ".triggerInterval"));
        assertEquals(Integer.valueOf(7), ConfigFactory.open(file).getValue(FlagRegistry.SETTINGS_ROOT + ".brightness"));
        GlowSettings settings = flag.getSettings();
        assertEquals(Duration.ofSeconds(2), settings.getTriggerInterval());
        assertEquals(7, settings.getBrightness());
    }

    @Test
    void theHeaderSaysWhatTheFlagIsBelowTheBanner() throws Exception {
        FlagRegistry.get().register(flag);
        String text = text();

        int banner = text.indexOf("EverNife's Config Manager");
        assertTrue(banner >= 0, text);
        String below = text.substring(banner);
        int key = below.indexOf(flag.getKey().toString());
        int description = below.indexOf("TRUE: the region glows.");
        int type = below.indexOf(flag.getType().getId());
        int times = below.indexOf("500ms");
        assertTrue(key > 0 && description > key && type > description && times > type, text);
        String timesLine = below.substring(below.lastIndexOf('\n', times), below.indexOf('\n', times));
        assertTrue(timesLine.contains("1h30m"), timesLine);
    }

    @Test
    void ownersReloadReadsTheEditedFileAndAnotherPluginsReloadDoesNot() throws Exception {
        FlagRegistry.get().register(flag);
        edit("triggerInterval: 2s", "triggerInterval: 5s");
        edit("brightness: 7", "brightness: 3");

        ECEventBus.global().post(new ECPluginReloadEvent.Post(other));
        assertEquals(Duration.ofSeconds(2), flag.getSettings().getTriggerInterval());

        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));
        GlowSettings settings = flag.getSettings();
        assertEquals(Duration.ofSeconds(5), settings.getTriggerInterval());
        assertEquals(3, settings.getBrightness());
    }

    @Test
    void serverKeyOffAndAFlagThatFollowsItWritesNoFile() {
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;
        FlagRegistry.get().register(flag);

        assertFalse(file.exists());
        assertEquals(Duration.ofSeconds(2), flag.getSettings().getTriggerInterval());
    }

    @Test
    void serverKeyOnWritesAndReadsTheFile() throws Exception {
        FlagRegistry.get().register(flag);
        assertTrue(file.exists());

        edit("brightness: 7", "brightness: 4");
        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));
        assertEquals(4, flag.<GlowSettings>getSettings().getBrightness());
    }

    @Test
    void aFlagForcingItsFileHasItWithTheServerKeyOff() throws Exception {
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;
        flag = new GlowFlag(owner, SettingsFilePolicy.ALWAYS);
        FlagRegistry.get().register(flag);
        assertTrue(file.exists());

        edit("brightness: 7", "brightness: 2");
        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));
        assertEquals(2, flag.<GlowSettings>getSettings().getBrightness());
    }

    @Test
    void aFlagRefusingItsFileHasNoneWithTheServerKeyOn() {
        flag = new GlowFlag(owner, SettingsFilePolicy.NEVER);
        FlagRegistry.get().register(flag);

        assertFalse(file.exists());
        assertEquals(7, flag.<GlowSettings>getSettings().getBrightness());
    }

    @Test
    void anOldFileIsIgnoredWithOneLogLinePerLoad(TestPlatform platform) throws Exception {
        FlagRegistry.get().register(flag);
        edit("brightness: 7", "brightness: 9");
        FlagRegistry.get().reset();
        ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;
        platform.getLoggedMessages().clear();

        FlagRegistry.get().register(flag);

        assertEquals(7, flag.<GlowSettings>getSettings().getBrightness(), "the file is not read");
        assertEquals(1, linesNaming(platform, file.getPath(), "generate-global-settings-configuration-files"), platform.getLoggedMessages().toString());
        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));
        assertEquals(2, linesNaming(platform, file.getPath(), "generate-global-settings-configuration-files"), "one more for the reload");
        assertEquals(7, flag.<GlowSettings>getSettings().getBrightness());
    }

    @Test
    void theCoresReloadAppliesTheServerKeyToEveryOwnersFlags() throws Exception {
        Field coreData = EverNifeCore.class.getDeclaredField("ecPluginData");
        coreData.setAccessible(true);
        Object previous = coreData.get(EverNifeCore.instance);
        ECPluginData core = Plugins.fakePluginData(CORE, tempDir.resolve("core").toFile());
        coreData.set(EverNifeCore.instance, core);
        try {
            ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;
            FlagRegistry.get().register(flag);
            assertFalse(file.exists());

            ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = true;
            ECEventBus.global().post(new ECPluginReloadEvent.Post(core));
            assertTrue(file.exists(), "written by the core's reload, the owner never reloaded");

            edit("triggerInterval: 2s", "triggerInterval: 5s");
            ECEventBus.global().post(new ECPluginReloadEvent.Post(core));
            assertEquals(Duration.ofSeconds(5), flag.getSettings().getTriggerInterval());

            ECSettings.REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;
            ECEventBus.global().post(new ECPluginReloadEvent.Post(core));
            assertEquals(Duration.ofSeconds(2), flag.getSettings().getTriggerInterval(), "the file is out of use again");
        } finally {
            coreData.set(EverNifeCore.instance, previous);
        }
    }

    @Test
    void everySettingsFieldOfTheCoreIsCommented() throws Exception {
        for (Class<?> type : new Class<?>[]{FlagSettings.class, InteractSettings.class, MoveDenySettings.class}) {
            List<Field> fields = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
            assertFalse(fields.isEmpty(), type.getName());
            for (Field field : fields) {
                Comment comment = field.getAnnotation(Comment.class);
                assertTrue(comment != null && !String.join("", comment.value()).trim().isEmpty(), type.getSimpleName() + "." + field.getName());
            }
        }
        String revalidation = String.join(" ", FlagSettings.class.getDeclaredField("revalidationInterval").getAnnotation(Comment.class).value());
        assertTrue(revalidation.contains("null"), revalidation);
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

        edit("triggerInterval: 2s", "triggerInterval: 0s");
        ECEventBus.global().post(new ECPluginReloadEvent.Post(owner));

        assertTrue(throttle.tryFire(alice, flag));
        assertTrue(throttle.tryFire(alice, flag));
    }
}
