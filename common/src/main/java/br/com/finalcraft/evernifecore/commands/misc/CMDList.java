package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import jakarta.annotation.Nonnull;

public class CMDList implements ICustomFinalCMD {

    @FCLocale(lang = LocaleType.EN_US, text = "§7# ${number}: §e§l- §a ${value}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7# ${number}: §e§l- §a ${value}")
    private static LocaleMessage LINE;

    /**
     * Built on the first {@code /list} and never again. It cannot be a field of the command itself:
     * the executor is instantiated before its locale fields are filled, so a page built there would
     * carry a message that is still null.
     */
    private static final class Page {

        static final PageViewer<FPlayer> ONLINE_PLAYERS = PageViewer.of(FPlayer.class)
                .id("evernifecore:list")
                .source(() -> EverNifeCore.getPlatform().getOnlinePlayers())
                .unlimitedEntries()                                 //the online list fits whole, by definition
                .orderBy(FPlayer::getName).ascending()
                .setFormatLine(LINE)
                .theme(PageTheme.classic().withTotalCount())
                .build();
    }

    @FinalCMD(
            aliases = {"playerlist", "list"}
    )
    public void onCommand(FCommandSender sender, @Arg("[page]") PageVisualization page) {
        Page.ONLINE_PLAYERS.send(page, sender);
    }

    @Override
    public void customize(@Nonnull CustomizeContext context) {
        if (!ECSettings.COMMAND_LIST_ALIAS) {
            context.getFinalCMDData().setLabels("playerlist");   //the server's own /list stays in place
        }
    }
}
