package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /ecore i18n export <plugin> <locale>} writes the overlay file of a plugin's definitions. */
class CoreI18nCommandsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand ecore;
    private TestCommandSender admin;

    @BeforeEach
    void setUp() {
        harness = Commands.harness("I18nCmd", tempDir.resolve("cmd"));
        ecore = harness.register(CoreCommand.class);
        admin = Senders.console("admin").grant(PermissionNodes.EVERNIFECORE_COMMAND_I18N);
    }

    @AfterEach
    void tearDown() {
        LocaleOverlay.closeOwnedBy(harness.ecPluginData);
        harness.close();
    }

    @Test
    void exportWritesTheFileAndReportsWhatWasAddedAndKept() throws IOException {
        File definition = tempDir.resolve("quest.yml").toFile();
        Files.write(definition.toPath(), "name: \"Minerar\"\ndescription: \"Minere tudo\"\n".getBytes(StandardCharsets.UTF_8));
        LocaleOverlay overlay = LocaleOverlay.of(harness.ecPluginData);
        overlay.register("quest", new ConfigSection(ConfigFactory.open(definition), ""));
        File file = overlay.getFile("EN_US");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "quest.name: \"Mine\"\n".getBytes(StandardCharsets.UTF_8));

        harness.dispatch(ecore, admin, "i18n export " + harness.pluginName + " en_us");

        admin.assertAnyMessageContains("Exported §e1§a texts of §e" + harness.pluginName);
        admin.assertAnyMessageContains("§e1§a already translated, kept");
        String written = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(written.contains("Minere tudo"), written);
        assertTrue(written.contains("Mine"), written);
    }

    @Test
    void anUnknownPluginTeachesTheInstalledOnes() {
        harness.dispatch(ecore, admin, "i18n export NoSuchPlugin EN_US");

        admin.assertAnyMessageContains("No ECPlugin named §e[NoSuchPlugin]");
        admin.assertAnyMessageContains(harness.pluginName);
    }

    @Test
    void aPluginWithNoDefinitionSaysHowToRegisterOneAndWritesNothing() {
        harness.dispatch(ecore, admin, "i18n export " + harness.pluginName + " EN_US");

        admin.assertAnyMessageContains("registered no definition in LocaleOverlay");
        assertFalse(LocaleOverlay.of(harness.ecPluginData).getFile("EN_US").exists());
    }
}
