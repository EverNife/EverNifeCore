package br.com.finalcraft.evernifecore.locale.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.locale.OverlayExporter;

/** {@code /ecore i18n export <plugin> <locale>}: writes the overlay file a translator fills for a plugin's definitions. */
public class CoreI18nCommands {

    @FinalCMD.SubCMD(subcmd = "export", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Write the texts of a plugin's definitions to localization/overlay/<locale>.yml, for a translator"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Escreve os textos das definições de um plugin em localization/overlay/<locale>.yml, para um tradutor")
    })
    public void export(FCommandSender sender, @Arg("<plugin>") String pluginName, @Arg("<locale>") String locale) {
        ECPluginData plugin = null;
        for (ECPluginData candidate : ECPluginManager.getECPluginsMap().values()) {
            if (candidate.getMetaInfo().getName().equalsIgnoreCase(pluginName)) {
                plugin = candidate;
            }
        }
        if (plugin == null) {
            sender.sendMessage("§4§l ▶ §cNo ECPlugin named §e[" + pluginName + "]§c. Installed: §e"
                    + String.join(", ", ECPluginManager.getECPluginsMap().keySet()));
            return;
        }
        OverlayExporter.Report report;
        try {
            report = OverlayExporter.export(plugin, locale);
        } catch (IllegalStateException nothingRegistered) {
            sender.sendMessage("§4§l ▶ §c" + nothingRegistered.getMessage());
            return;
        }
        String name = plugin.getMetaInfo().getName();
        sender.sendMessage("§a§l ▶ §aExported §e" + report.getAdded() + "§a texts of §e" + name + "§a to §e"
                + report.getFile().getPath() + "§a (§e" + report.getKept() + "§a already translated, kept).");
        sender.sendMessage("   §7Translate the values, then reload the plugin (or its definitions) - a text left untranslated falls back to "
                + FCLocaleManager.getLangOf(plugin) + ".");
    }
}
