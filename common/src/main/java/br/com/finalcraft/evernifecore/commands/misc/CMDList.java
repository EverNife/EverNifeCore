package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.pageviwer.PageViewer;
import br.com.finalcraft.evernifecore.pageviwer.PageVizualization;

import java.util.ArrayList;

public class CMDList {

    @FinalCMD(
            aliases = {"list","playerlist"}
    )
    public void onCommand(FCommandSender sender, @Arg(name = "[page]") PageVizualization page) {
        PageViewer.targeting(FPlayer.class)
                .withSuplier(() -> new ArrayList<>(EverNifeCore.getPlatform().getOnlinePlayers()))
                .extracting(player -> player.getName())
                .setFormatLine(
                        FancyText.of("§7# %number%: §e§l- §a %value%")
                )
                .setIncludeTotalCount(true)
                .build()
                .send(page, sender);
    }
}
