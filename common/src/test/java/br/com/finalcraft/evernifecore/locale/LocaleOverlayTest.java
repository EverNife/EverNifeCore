package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text of a definition leaf for a locale: overlay file, then inline entry, then the plugin's language, then the author. */
@ECoreTest
class LocaleOverlayTest {

    private static final String PLUGIN = "OverlayQuests";
    private static final String QUEST = "mine-ten-ores";

    private static final String DEFINITION = String.join("\n",
            "name: \"Minerar dez minerios\"",
            "stages:",
            "  mine:",
            "    title:",
            "      EN_US: \"Mine\"",
            "      PT_BR: \"Minerar\"",
            "    subtitle:",
            "      ES_ES: \"Excavar\"",
            "      ZH_CN: \"Wa\"",
            "    objectives:",
            "      ore:",
            "        description: \"Minere ${amount} minerios\"",
            "        amount: 10",
            "        label: \"Rotulo\"",
            "lore:",
            "  color: red",
            "");

    @TempDir
    Path tempDir;

    private ECPluginData plugin;
    private LocaleOverlay overlay;

    @BeforeEach
    void setUp() throws IOException {
        plugin = Plugins.fakePluginData(PLUGIN, tempDir.resolve("plugin").toFile());
        Plugins.setLanguage(plugin, LocaleType.PT_BR);
        overlay = LocaleOverlay.of(plugin);
        overlay.register(QUEST, definition(DEFINITION));
    }

    @AfterEach
    void tearDown() {
        LocaleOverlay.closeOwnedBy(plugin);
        Plugins.forget(PLUGIN);
    }

    @Test
    void aTranslationInTheOverlayFileWinsOverTheInlineEntryOfThatLocale() throws IOException {
        writeOverlay("EN_US", QUEST + ".stages.mine.title: \"Mine it\"");
        overlay.reload();

        assertEquals("Mine it", overlay.text(QUEST, "stages.mine.title", "EN_US").getText());
        assertEquals("Mine it", overlay.translated(QUEST, "stages.mine.title", "en_us").getText());
    }

    @Test
    void inlineEntryOfTheLocaleAnswersWithoutATranslation() {
        assertEquals("Mine", overlay.text(QUEST, "stages.mine.title", "EN_US").getText());
        assertNull(overlay.translated(QUEST, "stages.mine.title", "EN_US"));
    }

    @Test
    void anInlineMapWithoutTheLocaleFallsBackToThePluginLanguage() {
        assertEquals("Minerar", overlay.text(QUEST, "stages.mine.title", "ES_ES").getText());
        assertEquals("Minerar", overlay.text(QUEST, "stages.mine.title", null).getText());
    }

    @Test
    void anInlineMapWithNeitherLocaleAnswersTheAuthorsFirstLanguage() {
        assertEquals("Excavar", overlay.text(QUEST, "stages.mine.subtitle", "EN_US").getText());
    }

    @Test
    void aPlainLeafWithoutATranslationFallsBackToTheAuthorText() {
        assertEquals("Minerar dez minerios", overlay.text(QUEST, "name", "EN_US").getText());
    }

    @Test
    void theStablePathOfANestedLeafIsTheIdThenItsPathInTheDefinition() throws IOException {
        writeOverlay("EN_US", QUEST + ".stages.mine.objectives.ore.description: \"Mine ${amount} ores\"");
        overlay.reload();

        assertEquals("Mine ${amount} ores", overlay.text(QUEST, "stages.mine.objectives.ore.description", "EN_US").getText());
    }

    @Test
    void anUnknownIdOrAPathThatIsNotATextLeafThrowsNamingTheOptions() {
        IllegalArgumentException unknownId = assertThrows(IllegalArgumentException.class,
                () -> overlay.text("mine-ten-ore", "name", "EN_US"));
        assertTrue(unknownId.getMessage().contains(QUEST), unknownId.getMessage());

        IllegalArgumentException notALeaf = assertThrows(IllegalArgumentException.class,
                () -> overlay.text(QUEST, "stages.mine.objectives.ore.amount", "EN_US"));
        assertTrue(notALeaf.getMessage().contains("stages.mine.objectives.ore.description"), notALeaf.getMessage());
    }

    @Test
    void theDefaultTextKeysAreUsedUnlessTheRegisterNamesItsOwn() throws IOException {
        assertEquals(new LinkedHashSet<>(Arrays.asList("name", "stages.mine.title", "stages.mine.subtitle",
                "stages.mine.objectives.ore.description")), overlay.textPaths(QUEST),
                "label is not a default text key, and lore here is a structure, not a text");

        overlay.register("labels", definition(DEFINITION), "label");

        assertEquals(new LinkedHashSet<>(Arrays.asList("stages.mine.objectives.ore.label")), overlay.textPaths("labels"));
    }

    @Test
    void exportWritesOnlyTheMissingPathsWithTheTextTheLocaleReadsToday() throws IOException {
        writeOverlay("EN_US", QUEST + ".name: \"Mine ten ores\"");
        overlay.reload();

        OverlayExporter.Report report = OverlayExporter.export(plugin, "en_us");

        assertEquals(3, report.getAdded());
        assertEquals(1, report.getKept());
        assertEquals(overlay.getFile("EN_US"), report.getFile());
        Config written = ConfigFactory.open(report.getFile());
        assertEquals("Mine ten ores", written.getRoot().get(QUEST + ".name").asText());
        assertEquals("Minere ${amount} minerios", written.getRoot().get(QUEST + ".stages.mine.objectives.ore.description").asText());
        assertEquals("Mine", written.getRoot().get(QUEST + ".stages.mine.title").asText());
        assertEquals("Excavar", written.getRoot().get(QUEST + ".stages.mine.subtitle").asText());
    }

    @Test
    void neverOverwritesATranslatedValueOnASecondExport() throws IOException {
        OverlayExporter.export(plugin, "EN_US");
        String translated = read(overlay.getFile("EN_US")).replace("Minerar dez minerios", "Mine ten ores");
        Files.write(overlay.getFile("EN_US").toPath(), translated.getBytes(StandardCharsets.UTF_8));

        OverlayExporter.Report again = OverlayExporter.export(plugin, "EN_US");

        assertEquals(0, again.getAdded());
        assertEquals(4, again.getKept());
        assertTrue(read(overlay.getFile("EN_US")).contains("Mine ten ores"));
        assertEquals("Mine ten ores", overlay.text(QUEST, "name", "EN_US").getText());
    }

    @Test
    void exportOfAPluginWithNoDefinitionRefusesAndSaysHowToRegister() {
        overlay.unregister(QUEST);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> OverlayExporter.export(plugin, "EN_US"));

        assertTrue(refused.getMessage().contains("LocaleOverlay.of(plugin).register(id, root)"), refused.getMessage());
        assertFalse(overlay.getFile("EN_US").exists());
    }

    @Test
    void reloadReadsAnEditedFile() throws IOException {
        writeOverlay("EN_US", QUEST + ".name: \"Mine ten ores\"");
        overlay.reload();
        writeOverlay("EN_US", QUEST + ".name: \"Mine ten rocks\"");

        assertEquals("Mine ten ores", overlay.text(QUEST, "name", "EN_US").getText(), "nothing re-reads before reload");
        overlay.reload();
        assertEquals("Mine ten rocks", overlay.text(QUEST, "name", "EN_US").getText());
    }

    @Test
    void everyCallHandsOutAFreshTextSoAReplaceNeverLeaks() {
        FancyText first = overlay.text(QUEST, "stages.mine.objectives.ore.description", null);
        first.replace("${amount}", "10");

        FancyText second = overlay.text(QUEST, "stages.mine.objectives.ore.description", null);

        assertNotSame(first, second);
        assertEquals("Minere ${amount} minerios", second.getText());
    }

    @Test
    void closeOwnedByDropsTheOverlayOfThePlugin() {
        assertSame(overlay, LocaleOverlay.ofIfPresent(plugin));

        LocaleOverlay.closeOwnedBy(plugin);

        assertNull(LocaleOverlay.ofIfPresent(plugin));
    }

    private ConfigSection definition(String yaml) throws IOException {
        File file = tempDir.resolve("definitions-" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        return new ConfigSection(ConfigFactory.open(file), "");
    }

    private void writeOverlay(String locale, String... lines) throws IOException {
        File file = overlay.getFile(locale);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
