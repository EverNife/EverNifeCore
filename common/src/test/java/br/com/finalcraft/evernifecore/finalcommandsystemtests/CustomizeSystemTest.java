package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDAlias;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ICustomFinalCMD}/{@link CustomizeContext}/{@code CMDAlias}: customization
 * runs before registration, and its effects (labels, {@code replace(...)}) are per-instance.
 */
class CustomizeSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Customize", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // ICustomFinalCMD.customize runs before registration; setLabels changes the registered
    // alias
    // ------------------------------------------------------------------

    public static class SetLabels_Cmd implements ICustomFinalCMD {
        @FinalCMD(aliases = "originalalias")
        public void run(FCommandSender sender) {}

        @Override
        public void customize(@Nonnull CustomizeContext context) {
            context.getFinalCMDData().setLabels("customizedlabel");
        }
    }

    @Test
    void customizeRunsBeforeRegistrationAndSetLabelsChangesTheRegisteredAlias() {
        FinalCMDPluginCommand command = newHarness().register(new SetLabels_Cmd());

        assertEquals("customizedlabel", command.getPrimaryLabel());
        assertNull(harness.platform.getCaptured("originalalias"), "the annotation's own alias was never registered");
        assertEquals(command, harness.platform.getCaptured("customizedlabel"));
    }

    // ------------------------------------------------------------------
    // CustomizeContext.replace("%x%", v) affects labels/usage/permission/locales AND ArgData
    // (name/context/locales). descriptionOverride is a runtime LocaleMessageImp, not a String, and
    // is deliberately NOT touched by replace(...) (see CMDAlias, which resolves its own placeholder
    // via LocaleMessageImp#derivePlaceholderResolved before ever handing the override to CMDData).
    // ------------------------------------------------------------------

    public static class ReplacePlaceholder_Cmd implements ICustomFinalCMD {
        @FinalCMD(aliases = "cmd%suffix%", permission = "perm%suffix%")
        public void run(FCommandSender sender,
                         @Arg(value = "<val%suffix%>", context = "ctx%suffix%",
                                 locales = {@FCLocale(lang = LocaleType.EN_US, text = "loc%suffix%")}) String value) {}

        @Override
        public void customize(@Nonnull CustomizeContext context) {
            context.replace("%suffix%", "REPLACED");
        }
    }

    //usage() lives on its own command: it is only legal on a method with no @Arg/@Arg.Flag at all
    public static class ReplacePlaceholderUsage_Cmd implements ICustomFinalCMD {
        @FinalCMD(aliases = "usagecmd%suffix%", usage = "usage%suffix%")
        public void run(FCommandSender sender) {}

        @Override
        public void customize(@Nonnull CustomizeContext context) {
            context.replace("%suffix%", "REPLACED");
        }
    }

    @Test
    void replacePlaceholderAffectsCmdDataAndArgData() {
        FinalCMDPluginCommand command = newHarness().register(new ReplacePlaceholder_Cmd());

        assertEquals("cmdREPLACED", command.getPrimaryLabel());
        assertEquals("permREPLACED", command.getFinalCMD().getPermission());

        ArgParser<?> argParser = command.getMainInterpreter().getCustomArguments().get(1);
        ArgData argData = argParser.getArgInfo().getArgData();
        assertEquals("<valREPLACED>", argData.getName());
        assertEquals("ctxREPLACED", argData.getContext());
        assertEquals("locREPLACED", argData.getLocales()[0].text());

        assertEquals("usageREPLACED", harness.register(new ReplacePlaceholderUsage_Cmd()).getFinalCMD().getUsage());
    }

    // ------------------------------------------------------------------
    // two CMDAlias instances (different aliases, different target commands) each register
    // with a hover carrying THEIR OWN %the_command% - proof the derived descriptionOverride is
    // per-instance, not shared through the class-level @FCLocale template
    // ------------------------------------------------------------------

    @Test
    void twoCMDAliasInstancesEachKeepTheirOwnTheCommandInTheHover() {
        newHarness();
        FinalCMDPluginCommand cmd1 = harness.register(new CMDAlias("hoverone", "target1"));
        FinalCMDPluginCommand cmd2 = harness.register(new CMDAlias("hovertwo", "target2"));

        assertNotNull(cmd1.getMainInterpreter());
        assertNotNull(cmd2.getMainInterpreter());

        FancyText hover1 = cmd1.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("EN_US");
        FancyText hover2 = cmd2.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("EN_US");

        assertNotNull(hover1);
        assertNotNull(hover2);
        assertTrue(hover1.getHoverText().contains("target1"));
        assertFalse(hover1.getHoverText().contains("target2"));
        assertTrue(hover2.getHoverText().contains("target2"));
        assertFalse(hover2.getHoverText().contains("target1"));
    }

    // ------------------------------------------------------------------
    // a plain custom executor calling setDescriptionOverride() directly inside customize()
    // (no placeholder to resolve, unlike CMDAlias) gets that message as the help line's hover -
    // there is no runtime-only description String left on the annotation, so this is the only way
    // left to give a command a per-instance description without being a CMDAlias
    // ------------------------------------------------------------------

    public static class DescriptionOverride_Cmd implements ICustomFinalCMD {
        @FCLocale(lang = LocaleType.EN_US, text = "Set directly via customize")
        static LocaleMessageImp DESCRIPTION;

        @FinalCMD(aliases = "descriptionoverride")
        public void run(FCommandSender sender) {}

        @Override
        public void customize(@Nonnull CustomizeContext context) {
            context.getFinalCMDData().setDescriptionOverride(DESCRIPTION);
        }
    }

    @Test
    void setDescriptionOverrideInsideCustomizeBecomesTheHelpLineHover() {
        FinalCMDPluginCommand command = newHarness().register(new DescriptionOverride_Cmd());

        FancyText hover = command.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("EN_US");

        assertNotNull(hover);
        assertTrue(hover.getHoverText().contains("Set directly via customize"));
    }

    // ------------------------------------------------------------------
    // two CMDAlias instances each keep their own %the_command% in BOTH EN_US and PT_BR
    // hovers - proof the derived override carries every locale of the class-level @FCLocale
    // template, not just the plugin's default language
    // ------------------------------------------------------------------

    @Test
    void twoCMDAliasInstancesKeepTheirOwnTheCommandInBothEnUsAndPtBrHovers() {
        newHarness();
        FinalCMDPluginCommand cmd1 = harness.register(new CMDAlias("multilocaleone", "target1"));
        FinalCMDPluginCommand cmd2 = harness.register(new CMDAlias("multilocaletwo", "target2"));

        FancyText hover1EnUs = cmd1.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("EN_US");
        FancyText hover1PtBr = cmd1.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("PT_BR");
        FancyText hover2EnUs = cmd2.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("EN_US");
        FancyText hover2PtBr = cmd2.getMainInterpreter().getHelpLineTemplate().getLocaleMessage().getFancyText("PT_BR");

        assertNotNull(hover1EnUs);
        assertNotNull(hover1PtBr);
        assertNotNull(hover2EnUs);
        assertNotNull(hover2PtBr);

        assertTrue(hover1EnUs.getHoverText().contains("target1"));
        assertTrue(hover1PtBr.getHoverText().contains("target1"));
        assertTrue(hover2EnUs.getHoverText().contains("target2"));
        assertTrue(hover2PtBr.getHoverText().contains("target2"));
    }
}
