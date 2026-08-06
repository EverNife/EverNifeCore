package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.everyconfig.binding.BindException;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.ruleset.Explicit;
import br.com.finalcraft.everyconfig.ruleset.OneOf;
import br.com.finalcraft.everyconfig.ruleset.OneOfSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file decides the value and the field decides what the value has to mean. A file the admin got wrong
 * costs a line in the log; a default the developer got wrong costs the boot.
 *
 * <p>Every scenario that reads the log declares its own settings class: the scanner warns once per site for
 * the whole JVM, so two tests sharing a key would race for the single warning it produces.
 */
class SettingsScannerTest {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    /** The whole vocabulary at once: a bounded number, a set only the running server knows, and a value the
     *  operator has to write down instead of inheriting. */
    static class ShopSettings {

        @ConfigSetting(key = "Settings.taxRate", comment = @FCLocale(text = "Cut kept from every sale"))
        @Min(0)
        @Max(1)
        public Double taxRate = 0.05D;

        @ConfigSetting(key = "Settings.world", comment = @FCLocale(text = "Where the shop stands"))
        @OneOf(provider = LoadedWorlds.class)
        public String world = "world";

        @ConfigSetting(key = "Settings.apiToken")
        @Explicit
        public String apiToken = "";
    }

    /** A set that exists only while the server runs - the case an enum cannot cover. */
    public static final class LoadedWorlds implements OneOfSource {

        @Override
        public Collection<String> values() {
            return Arrays.asList("world", "world_nether");
        }
    }

    static class BaseSettings {

        @ConfigSetting(key = "Settings.title")
        public String title = "Shop";
    }

    static class InheritedSettings extends BaseSettings {

        @ConfigSetting(key = "Settings.rows")
        public Integer rows = 6;
    }

    static class ListSettings {

        @ConfigSetting(key = "Settings.levels")
        public List<Integer> levels = Arrays.asList(1, 2, 3);
    }

    static class TypedSettings {

        @ConfigSetting(key = "Gui.rows")
        public Integer rows = 3;
    }

    static class TokenSettings {

        @ConfigSetting(key = "Settings.token")
        @Explicit
        public String token = "";
    }

    static class BrokenDefault {

        @ConfigSetting(key = "Settings.chance")
        @Max(100)
        public Integer chance = 150;
    }

    /** A value the operator has to write down, seeded with one the rule beside it refuses. */
    static class UnseedableSettings {

        @ConfigSetting(key = "Settings.secret")
        @Explicit
        @NotBlank
        public String secret = "";
    }

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData ecPluginData;
    private Config config;

    @BeforeEach
    void setup() {
        world = Platforms.lenient().install().withPluginExtractor(
                Plugins.fake("SettingsScanner_" + UNIQUE_SUFFIX.incrementAndGet(), tempDir.toFile()));
        ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
        config = ConfigFactory.open((ECPluginData) null, tempDir.resolve("settings.yml").toFile());
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    @Test
    void aFreshFileIsSeededWithTheDefaultAndWithWhatEachRuleDocuments() {
        ShopSettings settings = new ShopSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(0.05D, settings.taxRate);
        assertEquals(0.05D, config.getDouble("Settings.taxRate"));

        String comment = config.getComment("Settings.taxRate");
        assertTrue(comment.contains("Cut kept from every sale"), comment);
        assertTrue(comment.contains("At least 0."), "a rule documents itself in the file: " + comment);
        assertTrue(comment.contains("At most 1."), comment);
    }

    @Test
    void whatTheFileSaysWinsOverTheDefault() {
        config.setValue("Settings.taxRate", 0.2D);
        ShopSettings settings = new ShopSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(0.2D, settings.taxRate);
    }

    @Test
    void aFileValueARuleRefusesIsLoggedAtItsOwnKeyAndTheDefaultIsKept() {
        config.setValue("Settings.taxRate", 1.5D);
        ShopSettings settings = new ShopSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(0.05D, settings.taxRate, "the refused value never reaches the field");
        assertTrue(anyContains("at 'Settings.taxRate' rejects the file value '1.5'"),
                "the message names the key the admin will look for: " + world.platform().getLoggedMessages());
    }

    @Test
    void aRuntimeSetIsAskedWhileTheServerRuns() {
        ShopSettings accepted = new ShopSettings();
        config.setValue("Settings.world", "world_nether");
        SettingsScanner.load(ecPluginData, config, accepted);
        assertEquals("world_nether", accepted.world);

        ShopSettings refused = new ShopSettings();
        config.setValue("Settings.world", "the_end");
        SettingsScanner.load(ecPluginData, config, refused);

        assertEquals("world", refused.world);
        assertTrue(anyContains("at 'Settings.world' rejects the file value 'the_end'"),
                world.platform().getLoggedMessages().toString());
    }

    @Test
    void aValueTheDeclaredTypeCannotReadFallsBackToTheDefault() {
        config.setValue("Gui.rows", "notanumber");
        TypedSettings settings = new TypedSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(3, settings.rows);
        assertTrue(anyContains("cannot be read as Integer"), world.platform().getLoggedMessages().toString());
    }

    @Test
    void aValueThatHasToBeWrittenDownIsAskedForWithoutStoppingTheBoot() {
        TokenSettings settings = new TokenSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals("", settings.token);
        assertTrue(anyContains("must be set in the config file"),
                world.platform().getLoggedMessages().toString());
    }

    @Test
    void aDefaultThatBreaksItsOwnRuleIsACodeDefectAndThrows() {
        BindException failure = assertThrows(BindException.class,
                () -> SettingsScanner.load(ecPluginData, config, new BrokenDefault()));

        assertTrue(failure.getMessage().contains("code defect"), failure.getMessage());
        assertTrue(failure.getMessage().contains("BrokenDefault.chance"), failure.getMessage());
    }

    @Test
    void aValueTheOperatorMustWriteDownCannotBeSeededWithOneItsOwnRuleRefuses() {
        BindException failure = assertThrows(BindException.class,
                () -> SettingsScanner.load(ecPluginData, config, new UnseedableSettings()));

        String message = failure.getMessage();
        assertTrue(message.contains("UnseedableSettings.secret"), message);
        assertTrue(message.contains("@Explicit"), message);
        assertTrue(message.contains("@NotBlank"), message);
        assertTrue(message.contains("drop one of the two annotations"), "the message has to name the way "
                + "out, and the way out is in the code: " + message);
    }

    @Test
    void aFileThatAlreadyCarriesTheValueDoesNotHideTheContradiction() {
        config.setValue("Settings.secret", "a-real-token");

        assertThrows(BindException.class,
                () -> SettingsScanner.load(ecPluginData, config, new UnseedableSettings()),
                "the defect is in the code, so the one boot whose file happens to be filled in already "
                        + "must not be the boot that lets it through");
    }

    @Test
    void settingsAreInheritedFromTheBaseClass() {
        config.setValue("Settings.title", "Bazaar");
        InheritedSettings settings = new InheritedSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals("Bazaar", settings.title);
        assertEquals(6, settings.rows);
    }

    @Test
    void aListIsReadBackElementTypedAndEmptyingItIsAnAnswer() {
        ListSettings seeded = new ListSettings();
        SettingsScanner.load(ecPluginData, config, seeded);
        assertEquals(Arrays.asList(1, 2, 3), seeded.levels);

        config.setValue("Settings.levels", Arrays.asList(4, 5));
        ListSettings edited = new ListSettings();
        SettingsScanner.load(ecPluginData, config, edited);
        assertEquals(Arrays.asList(4, 5), edited.levels);

        config.setValue("Settings.levels", Collections.emptyList());
        ListSettings emptied = new ListSettings();
        SettingsScanner.load(ecPluginData, config, emptied);
        assertEquals(Collections.emptyList(), emptied.levels, "erasing every entry means none, not the default");
    }

    private boolean anyContains(String fragment) {
        for (String line : world.platform().getLoggedMessages()) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
