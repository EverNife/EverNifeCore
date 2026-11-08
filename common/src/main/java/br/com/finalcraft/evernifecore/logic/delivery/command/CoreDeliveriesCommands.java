package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /ecore deliveries list|purge <plugin>}: what an admin sees and drops of the pending deliveries a plugin left. */
public class CoreDeliveriesCommands {

    @FCLocale(lang = LocaleType.EN_US, text = "§cThe deliveries could not be read: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cAs entregas não puderam ser lidas: ${reason}")
    private static LocaleMessage READ_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§7No player has a pending delivery from §e${plugin}§7.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Nenhum jogador tem entrega pendente de §e${plugin}§7.")
    private static LocaleMessage NONE_FROM;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Pending deliveries from §e${plugin}§6, ${players} players:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Entregas pendentes de §e${plugin}§6, ${players} jogadores:")
    private static LocaleMessage LIST_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7- §f${player}§7: §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7- §f${player}§7: §e${count}")
    private static LocaleMessage LIST_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§cThe purge did not finish: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cO expurgo não terminou: ${reason}")
    private static LocaleMessage PURGE_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§aPurged §e${count}§a pending deliveries from §e${plugin}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§aDescartadas §e${count}§a entregas pendentes de §e${plugin}§a.")
    private static LocaleMessage PURGED;

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "How many pending deliveries a plugin left, per player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Quantas entregas pendentes um plugin deixou, por jogador")
    })
    public void list(FCommandSender sender, @Arg("<plugin>") String plugin) {
        Deliveries.get().listFrom(plugin).whenComplete((found, failure) -> {
            if (failure != null) {
                READ_FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
                return;
            }
            if (found.isEmpty()) {
                NONE_FROM.addPlaceholder("plugin", plugin).send(sender);
                return;
            }
            LIST_HEADER.custom().addPlaceholder("plugin", plugin).addPlaceholder("players", found.size()).send(sender);
            for (Map.Entry<UUID, List<Delivery>> entry : found.entrySet()) {
                String name = UUIDsController.getNameFromUUID(entry.getKey());
                LIST_LINE.custom().addPlaceholder("player", name == null ? entry.getKey().toString() : name)
                        .addPlaceholder("count", entry.getValue().size()).send(sender);
            }
        });
    }

    @FinalCMD.SubCMD(subcmd = "purge", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Drop every pending delivery a plugin left, of every player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Descarta toda entrega pendente que um plugin deixou, de todo jogador")
    })
    public void purge(FCommandSender sender, @Arg("<plugin>") String plugin) {
        Caller by = Caller.of(EverNifeCore.getEcPluginData(), "command:" + sender.getName());
        Deliveries.get().purge(plugin, by).whenComplete((count, failure) -> {
            if (failure != null) {
                PURGE_FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
                return;
            }
            PURGED.custom().addPlaceholder("count", count).addPlaceholder("plugin", plugin).send(sender);
        });
    }
}
