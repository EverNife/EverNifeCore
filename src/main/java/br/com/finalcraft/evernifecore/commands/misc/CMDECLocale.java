package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPlugin;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FinalCMD(
        aliases = {"eclocale"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE
)
public class CMDECLocale {

    @FinalCMD.SubCMD(
            subcmd = {"list"},
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show all Locales from all plugins."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra as Locales de todos os plugins.")
            }
    )
    public void list(CommandSender sender, String label) {
        FancyFormatter formatter = FancyFormatter.of(FCTextUtil.straightLineOf("§a§m-§r"));

        List<ECPlugin> sortedPlugins = ECPluginManager.getECPluginsMap().values().stream()
                .sorted(Comparator.comparing(ecPlugin -> ecPlugin.getPlugin().getName()))
                .collect(Collectors.toList());

        for (ECPlugin ecplugin : sortedPlugins) {
            formatter.append("\n§d ♦ §b" + ecplugin.getPlugin().getName() + " §7");

            for (LocaleType localeType : LocaleType.values()) {
                boolean isThisSelected = ecplugin.getPluginLanguage().equals(localeType.name());
                formatter.append(
                        FancyText.of((isThisSelected ? "§a§l" : "") +  "[" + localeType.name() + "]§7")
                                .setHoverText(isThisSelected ? "§aThis locale is already selected!" : "Click to Change Locale to: " + localeType.name())
                                .setRunCommandAction(isThisSelected ? null : "/" + label + " set " + ecplugin.getPlugin().getName() + " " + localeType.name())
                );
            }
            if (ecplugin.getCustomLangConfig() != null){
                formatter.append(
                        FancyText.of("§a§l[" + ecplugin.getPluginLanguage() + "]§7")
                                .setHoverText("§aThis locale is already selected!")
                );
            }
        }

        formatter.send(sender);
    }

    @FinalCMD.SubCMD(
            subcmd = {"set"},
            usage = "%name% <PluginName> <LocaleName>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Defines a locale to a specific plugin."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Define uma Locale para um plugin específico.")
            }
    )
    public void set(CommandSender sender, String label, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1,2)){
            helpLine.sendTo(sender);
            return;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin(argumentos.getStringArg(1));
        ECPlugin ecPlugin = plugin == null ? null : ECPluginManager.getECPluginsMap().get(plugin.getName());

        if (ecPlugin == null){
            sender.sendMessage("§e§l ▶ §cThere is no ECPlugin with the name §e[" + argumentos.get(1) + "]§c found on this server.");
            return;
        }

        LocaleType localeType = null;
        for (LocaleType value : LocaleType.values()) {
            if (argumentos.get(2).equalsIgnoreCase(value.name())){
                localeType = value;
                break;
            }
        }

        Config localization_config = new Config(plugin, "localization/localization_config.yml");

        String newLocaleValue = "lang_" + (localeType != null ? localeType.name() : argumentos.get(2)) + ".yml";
        String previousLocaleValue = localization_config.getString("Localization.fileName");

        if (!newLocaleValue.equals(previousLocaleValue)){
            localization_config.setValue("Localization.fileName", newLocaleValue);
            localization_config.save();
            ecPlugin.reloadAllCustomLocales();
        }

        sender.sendMessage("§2§l ▶ §b§l" + plugin.getName() + "'s §alocalization file name set to [" + localization_config.getString("Localization.fileName") + "]!");
    }

    @FinalCMD.SubCMD(
            subcmd = {"setall"},
            usage = "%name% <LocaleName>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Defines the locale to every single plugin."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Define a Locale de todos os ECPlugin para uma específica.")
            }
    )
    public void setall(CommandSender sender, String label, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1)){
            helpLine.sendTo(sender);
            return;
        }

        for (ECPlugin value : ECPluginManager.getECPluginsMap().values()) {
            FCBukkitUtil.makePlayerExecuteCommand(sender, label + " set " + value.getPlugin().getName() + " " + argumentos.get(1));
        }

    }
}
