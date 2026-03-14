package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.pageviwer.PageViewer;
import br.com.finalcraft.evernifecore.pageviwer.PageVizualization;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.ArrayList;

public class CMDList {

    @FinalCMD(
            aliases = {"list","playerlist"}
    )
    public void onCommand(FCommandSender sender, @Arg(name = "[page]") PageVizualization page) {
        PageViewer.targeting(PlayerRef.class)
                .withSuplier(() -> new ArrayList<>(Universe.get().getPlayers()))
                .extracting(plaeyrRef -> plaeyrRef.getUsername())
                .setFormatLine(
                        FancyText.of("§7# %number%: §e§l- §a %value%")
                )
                .setIncludeTotalCount(true)
                .build()
                .send(page, sender);
    }
}
