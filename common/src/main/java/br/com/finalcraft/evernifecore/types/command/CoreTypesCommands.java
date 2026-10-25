package br.com.finalcraft.evernifecore.types.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.types.TypeReference;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** {@code /ecore types export [kind]}: writes the reference of every registered type, generated from the registries. */
public class CoreTypesCommands {

    @FinalCMD.SubCMD(subcmd = "export", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Write the reference of every registered type (or of one kind) to export/types-<kind>.md"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Escreve a referência de todo tipo registrado (ou de um kind) em export/types-<kind>.md")
    })
    public void export(FCommandSender sender, @Arg("[kind]") String kind) {
        File folder = new File(EverNifeCore.getEcPluginData().getMetaInfo().getDataFolder(), "export");
        export(sender, folder, kind);
    }

    /** Writes into {@code folder} and answers {@code sender} with how many files and where - or which kinds exist. */
    public static void export(FCommandSender sender, File folder, String kind) {
        List<File> written;
        try {
            written = TypeReference.write(folder, kind);
        } catch (IOException failure) {
            sender.sendMessage("§cCould not write into §e" + folder.getAbsolutePath() + "§c: " + failure.getMessage()
                    + "§c. Check that the server may write there, then run it again.");
            return;
        }
        if (written.isEmpty()) {
            sender.sendMessage("§cNo type kind called §e" + kind + "§c. The kinds registered are: §7"
                    + String.join(", ", TypeReference.getKinds().keySet()) + "§c - or give none to write them all.");
            return;
        }
        sender.sendMessage("§aWrote §e" + written.size() + "§a file(s) into §e" + folder.getAbsolutePath() + "§a:");
        for (File file : written) {
            sender.sendMessage(" §7- §f" + file.getName());
        }
    }
}
