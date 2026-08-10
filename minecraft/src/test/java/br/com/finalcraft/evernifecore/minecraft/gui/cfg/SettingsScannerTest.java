package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.everyconfig.binding.BindException;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.rule.ConfigRule;
import br.com.finalcraft.everyconfig.rule.RuleContext;
import br.com.finalcraft.everyconfig.rule.RuleHandler;
import br.com.finalcraft.everyconfig.rule.RuleSite;
import br.com.finalcraft.everyconfig.ruleset.Explicit;
import br.com.finalcraft.everyconfig.ruleset.OneOf;
import br.com.finalcraft.everyconfig.ruleset.OneOfSource;
import br.com.finalcraft.everyconfig.ruleset.support.Violations;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    static class ButtonListSettings {

        @ConfigSetting(key = "Settings.buttons")
        public List<Integer> buttons = Arrays.asList(1, 2, 3);
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

    static class WrittenTokenSettings {

        @ConfigSetting(key = "Settings.writtenToken")
        @Explicit
        public String token = "";
    }

    /** The same key again, from a second holder: the scanner warns once per site, and two holders are two
     *  sites - which is what makes the silence after the key is written mean something. */
    static class FilledTokenSettings {

        @ConfigSetting(key = "Settings.writtenToken")
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

    /** Two rules over one value, and neither the file's nor the field's own satisfies both of them. */
    static class UnusableWorldSettings {

        @ConfigSetting(key = "Settings.homeWorld")
        @NotBlank
        @OneOf(provider = LoadedWorlds.class)
        public String homeWorld = "";
    }

    static class DoubleSettings {

        @ConfigSetting(key = "Settings.multiplier")
        public Double multiplier = 1.5D;
    }

    /** A layout is a settings holder like any other; nothing about the scanner asks for the base class. */
    static class ScreenSettings extends LayoutBase {

        @ConfigSetting(key = "Screen.rows")
        public Integer rows = 3;
    }

    static class BilingualSettings {

        @ConfigSetting(key = "Settings.radius", comment = {
                @FCLocale(lang = LocaleType.EN_US, text = "How far the shop reaches"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Ate onde a loja alcanca")
        })
        public Integer radius = 8;
    }

    static class RepeatedlyLoadedSettings {

        @ConfigSetting(key = "Settings.slots")
        @Max(9)
        public Integer slots = 5;
    }

    static class ProviderCountingSettings {

        @ConfigSetting(key = "Settings.region")
        @OneOf(provider = CountingWorlds.class)
        public String region = "spawn";
    }

    /** A provider that also says how often it was asked - the only way to see a cached answer. */
    public static final class CountingWorlds implements OneOfSource {

        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public Collection<String> values() {
            CALLS.incrementAndGet();
            return Arrays.asList("spawn", "arena");
        }
    }

    /** A rule that answers its own refusal instead of leaving the field's default as the only way out. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @ConfigRule(ClampedHandler.class)
    public @interface Clamped {

        int max();
    }

    public static final class ClampedHandler implements RuleHandler {

        @Override
        public void check(RuleContext context) {
            if (!(context.value() instanceof Number)) {
                return;
            }
            int max = ((Clamped) context.site().rule()).max();
            int actual = ((Number) context.value()).intValue();
            if (actual <= max) {
                return;
            }
            context.correct(max);
            Violations.report(context, "must be at most " + max + ", so " + actual + " was trimmed to it");
        }

        @Override
        public List<String> describe(RuleSite site) {
            return Collections.singletonList("At most " + ((Clamped) site.rule()).max() + ", trimmed when larger.");
        }
    }

    static class ClampedSettings {

        @ConfigSetting(key = "Settings.viewDistance")
        @Clamped(max = 16)
        public Integer viewDistance = 8;
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
    void onceTheFilesValueIsGoneTheRulesLeftJudgeTheDefaultAsTheDefaultItNowIs() {
        config.setValue("Settings.homeWorld", "   ");

        BindException failure = assertThrows(BindException.class,
                () -> SettingsScanner.load(ecPluginData, config, new UnusableWorldSettings()),
                "the first refusal put the field's own value in use, and a default nothing accepts is a "
                        + "defect no file can fix");

        assertTrue(failure.getMessage().contains("UnusableWorldSettings.homeWorld"), failure.getMessage());
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

    @Test
    void anEntryTheFileGotWrongCostsThatEntryAndIsReported() {
        config.setValue("Settings.buttons", Arrays.asList(10, "not-a-number", 30));
        ButtonListSettings settings = new ButtonListSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(Arrays.asList(10, 30), settings.buttons, "the entries that do read still open the menu");
        assertTrue(anyContains("1 of the 3 entries at 'Settings.buttons'"),
                "a list that silently lost an entry is a menu nobody knows is missing a button: "
                        + world.platform().getLoggedMessages());
        assertTrue(anyContains("(Settings.buttons[1])"),
                "the count alone leaves the operator hunting for which entry: "
                        + world.platform().getLoggedMessages());
    }

    @Test
    void aClassThatIsNotALayoutCarriesSettingsJustLikeOneThatIs() {
        config.setValue("Gui.rows", 5);
        config.setValue("Screen.rows", 4);

        TypedSettings plain = new TypedSettings();
        ScreenSettings screen = new ScreenSettings();
        SettingsScanner.load(ecPluginData, config, plain);
        SettingsScanner.load(ecPluginData, config, screen);

        assertEquals(5, plain.rows, "a plain class is a settings holder");
        assertEquals(4, screen.rows, "and so is a layout, by the same code path");
    }

    @Test
    void aHandlerThatCorrectsHandsBackTheCorrectedValueAndStillSaysWhatItDid() {
        config.setValue("Settings.viewDistance", 64);
        ClampedSettings settings = new ClampedSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(16, settings.viewDistance,
                "a handler that answered its own refusal wins over the field's default");
        assertTrue(anyContains("rejects the file value '64'"),
                "correcting does not silence: " + world.platform().getLoggedMessages());
        assertTrue(anyContains("The value in use is '16'"),
                "and the operator is told what is running meanwhile: "
                        + world.platform().getLoggedMessages());
    }

    @Test
    void whatARuleDocumentsIncludesWhatACustomHandlerSaysAboutItself() {
        SettingsScanner.load(ecPluginData, config, new ClampedSettings());

        String comment = config.getComment("Settings.viewDistance");
        assertNotNull(comment, "a rule with a describe() has to reach the file");
        assertTrue(comment.contains("At most 16, trimmed when larger."), comment);
    }

    @Test
    void aStringWhereADoubleIsExpectedFallsBackToTheDefaultInsteadOfBreakingTheLoad() {
        config.setValue("Settings.multiplier", "abc");
        DoubleSettings settings = new DoubleSettings();

        SettingsScanner.load(ecPluginData, config, settings);

        assertEquals(1.5D, settings.multiplier);
        assertTrue(anyContains("cannot be read as Double"), world.platform().getLoggedMessages().toString());
    }

    @Test
    void aRuntimeSetIsAskedAgainOnEveryEvaluationRatherThanRemembered() {
        CountingWorlds.CALLS.set(0);
        config.setValue("Settings.region", "arena");

        SettingsScanner.load(ecPluginData, config, new ProviderCountingSettings());
        assertEquals(1, CountingWorlds.CALLS.get(), "the first load asks the provider");

        SettingsScanner.load(ecPluginData, config, new ProviderCountingSettings());
        assertEquals(2, CountingWorlds.CALLS.get(),
                "a set only the running server knows cannot be cached: it is asked again");
    }

    @Test
    void aValueTheOperatorWroteDownSatisfiesTheRuleThatDemandedIt() {
        WrittenTokenSettings missing = new WrittenTokenSettings();
        SettingsScanner.load(ecPluginData, config, missing);
        assertEquals(1, countContaining("Settings.writtenToken"),
                world.platform().getLoggedMessages().toString());

        //the seeded file, once the operator has filled the key in and saved it
        config.setValue("Settings.writtenToken", "a-real-token");
        FilledTokenSettings written = new FilledTokenSettings();
        SettingsScanner.load(ecPluginData, config, written);

        assertEquals("a-real-token", written.token);
        assertEquals(1, countContaining("Settings.writtenToken"),
                "a second holder of the same key would be warned about all over again if the rule still "
                        + "fired; the key is in the file now, so there is nothing left to ask for: "
                        + world.platform().getLoggedMessages());
    }

    @Test
    void theSeededCommentIsTheOneWrittenForThePluginsOwnLanguage() {
        Plugins.setLanguage(ecPluginData, LocaleType.PT_BR);
        assertEquals(LocaleType.PT_BR, ecPluginData.getPluginLanguage());

        SettingsScanner.load(ecPluginData, config, new BilingualSettings());

        String comment = config.getComment("Settings.radius");
        assertTrue(comment.contains("Ate onde a loja alcanca"),
                "the plugin's language picks which comment is written: " + comment);
        assertTrue(!comment.contains("How far the shop reaches"),
                "and the other languages stay out of the file: " + comment);
    }

    @Test
    void aBrokenLineIsReportedOnceNoMatterHowOftenTheFileIsLoaded() {
        config.setValue("Settings.slots", 40);

        for (int load = 0; load < 5; load++) {
            SettingsScanner.load(ecPluginData, config, new RepeatedlyLoadedSettings());
        }

        assertEquals(1, countContaining("Settings.slots"),
                "the same broken line on every reload teaches nothing new: "
                        + world.platform().getLoggedMessages());
    }

    private boolean anyContains(String fragment) {
        return countContaining(fragment) > 0;
    }

    private int countContaining(String fragment) {
        int found = 0;
        for (String line : world.platform().getLoggedMessages()) {
            if (line.contains(fragment)) {
                found++;
            }
        }
        return found;
    }
}
