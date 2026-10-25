package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.CoreCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import br.com.finalcraft.evernifecore.logic.command.CoreLogicCommands;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.command.CoreTypesCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /ecore logic trace} toggles a trace for whoever typed it; {@code /ecore types export} writes the generated reference. */
class CoreLogicAndTypesCommandsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand ecore;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private TestCommandSender admin;

    @BeforeEach
    void setUp() {
        harness = Commands.harness("LogicCmd", tempDir.resolve("cmd"));
        ecore = harness.register(CoreCommand.class);
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        alex = shop.player("Alex");
        admin = Senders.console("admin").grant(PermissionNodes.EVERNIFECORE_COMMAND_LOGIC).grant(PermissionNodes.EVERNIFECORE_COMMAND_TYPES);
    }

    @AfterEach
    void tearDown() {
        LogicTrace.stop(CoreLogicCommands.watcherId(admin));
        shop.close();
        harness.close();
    }

    private void vipCheck() {
        Gate.builder().require(ctx -> ConditionResult.pass()).build().check(shop.context(alex).build());
    }

    @Test
    void theTraceCommandTurnsOnForWhoTypedItAndTheSameCommandTurnsItOff() {
        harness.dispatch(ecore, admin, "logic trace Alex FakeShop");
        admin.assertAnyMessageContains("only FakeShop");
        vipCheck();
        admin.assertAnyMessageContains("[fakeshop:test] check ");

        harness.dispatch(ecore, admin, "logic trace Alex FakeShop");
        admin.assertAnyMessageContains("§coff");
        int before = admin.getMessages().size();
        vipCheck();

        assertEquals(before, admin.getMessages().size(), admin.getMessages().toString());
        assertFalse(LogicTrace.get(CoreLogicCommands.watcherId(admin)).isPresent());
    }

    @Test
    void typesExportWritesOneFilePerKindWithEveryStockType() throws IOException {
        File folder = tempDir.resolve("export").toFile();

        CoreTypesCommands.export(admin, folder, null);

        admin.assertAnyMessageContains("file(s) into");
        assertEveryKeyIn(new File(folder, "types-condition.md"), Conditions.registry());
        assertEveryKeyIn(new File(folder, "types-effect.md"), Effects.registry());
        assertEveryKeyIn(new File(folder, "types-variable.md"), Variables.registry());
        String compare = read(new File(folder, "types-condition.md"));
        assertTrue(compare.contains("| `op` |"), compare);
    }

    @Test
    void typesExportOfAnUnknownKindWritesNothingAndListsTheKinds() {
        File folder = tempDir.resolve("export-unknown").toFile();

        CoreTypesCommands.export(admin, folder, "gizmo");

        admin.assertAnyMessageContains("No type kind called §egizmo");
        admin.assertAnyMessageContains("condition");
        String[] written = folder.list();
        assertTrue(written == null || written.length == 0);
    }

    private static void assertEveryKeyIn(File file, TypeRegistry<?> registry) throws IOException {
        String page = read(file);
        assertFalse(registry.getAll().isEmpty(), "the stock is registered");
        for (TypeDescriptor<?> descriptor : registry.getAll()) {
            assertTrue(page.contains("## `" + descriptor.getKey() + "`"), descriptor.getKey() + " missing from " + file);
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
