package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;

/**
 * Read-only health snapshot of the PlayerData storage layer: routing (which backend/collection each
 * entity persists on) plus the counters that reveal degradation BEFORE data is at risk - quit-flush
 * retry backlog, adopted optimistic-lock conflicts and the last failed write.
 *
 * <pre>/ecorestoragestatus</pre>
 */
public class CMDStorageStatus {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §6Storage status:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §6Status do armazenamento:")
    private static LocaleMessage STATUS_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7%line%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7%line%")
    private static LocaleMessage STATUS_LINE;

    @FinalCMD(
            aliases = {"ecorestoragestatus", "ecstoragestatus"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_STATUS,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show the PlayerData storage routing and health counters."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra o roteamento e os contadores de saúde do armazenamento de PlayerData.")
            }
    )
    public void onCommand(FCommandSender sender) {
        STATUS_HEADER.send(sender);
        for (String line : PlayerController.storageStatus().split("\n")) {
            //one chat line per routing entry keeps it readable in-game and in console
            for (String piece : line.split(" \\| ")) {
                STATUS_LINE.addPlaceholder("%line%", piece).send(sender);
            }
        }
    }
}
