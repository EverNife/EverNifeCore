package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.dialogue.command.CoreDialogueCommands;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.group.command.CoreGroupCommands;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.logic.command.CoreLogicCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.CoreDeliveriesCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.DeliveriesCommandNode;
import br.com.finalcraft.evernifecore.quest.screen.QuestOfferScreen;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.screen.Choice;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.DeliveriesScreen;
import br.com.finalcraft.evernifecore.screen.command.CoreScreenCommands;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the real boot loads: the ready-made tools' messages are in its list, in both languages, none left null. */
class CoreLocaleLoadTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void theBootsListReachesEveryMessageOfTheToolsInBothLanguages() throws IllegalAccessException {
        List<Class<?>> tools = Arrays.asList(DeliveriesCommandNode.class, CoreDeliveriesCommands.class, CoreScreenCommands.class,
                CoreLogicCommands.class, AuditCommandNode.class, CoreGroupCommands.class, CoreRegionCommands.class,
                CoreDialogueCommands.class, PromptRegistry.class, Choice.class, DeliveriesScreen.class, ConfirmScreen.class, QuestOfferScreen.class);
        assertTrue(Arrays.asList(ConfigManager.CORE_LOCALE_CLASSES).containsAll(tools), Arrays.toString(ConfigManager.CORE_LOCALE_CLASSES));

        try (FinalCmdTestHarness harness = Commands.harness("CoreLocale", tempDir)) {
            List<String> logged = Logs.capture(() -> FCLocaleManager.loadLocale(harness.ecPluginData, ConfigManager.CORE_LOCALE_CLASSES));
            for (Class<?> tool : tools) {
                assertTrue(logged.stream().noneMatch(line -> line.contains("[" + tool.getSimpleName() + "]")), logged.toString());
            }

            assertTrue(logged.stream().noneMatch(line -> line.contains("has no LocaleMessage fields")), logged.toString());

            int messages = 0;
            for (Class<?> tool : tools) {
                for (Field field : tool.getDeclaredFields()) {
                    if (field.getType() != LocaleMessage.class || !Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    LocaleMessage message = (LocaleMessage) field.get(null);
                    assertNotNull(message, tool.getSimpleName() + "." + field.getName() + " reads null on the command's path");
                    for (String lang : Arrays.asList(LocaleType.EN_US, LocaleType.PT_BR)) {
                        assertNotNull(message.getFancyText(lang), tool.getSimpleName() + "." + field.getName() + " has no " + lang);
                        assertFalse(message.getFancyText(lang).getText().matches(".*%[a-z_]+%.*"), tool.getSimpleName() + "." + field.getName());
                    }
                    messages++;
                }
            }
            assertTrue(messages > 50, "the fields were read: " + messages);
        }
    }
}
