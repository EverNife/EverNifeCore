package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCCommandUtil;
import br.com.finalcraft.evernifecore.util.FCServerUtil;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FinalCMD(
    aliases = {"eclocale","fclocale"}
)
public class CMDECLocale {

    @FinalCMD.SubCMD(
        subcmd = {"list"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE,
        locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Show all Locales from all plugins."),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mostra as Locales de todos os plugins.")
        }
    )
    public void list(FCommandSender sender, String label) {
        FancyFormatter formatter = FancyFormatter.of(EverNifeCore.getPlatform().getChatAdapter().straightLineOf("§a§m-§r"));

        List<ECPluginData> sortedPlugins = ECPluginManager.getECPluginsMap().values().stream()
            .sorted(Comparator.comparing(ecPlugin -> ecPlugin.getMetaInfo().getName()))
            .collect(Collectors.toList());

        for (ECPluginData ecplugin : sortedPlugins) {
            formatter.appendLine("§d ♦ §b" + ecplugin.getMetaInfo().getName() + " §7");

            //Each language is its own clickable piece, so they cannot be flattened into one string.
            formatter.append(FancyText.join("", LocaleType.values(), localeType -> {
                boolean isThisSelected = ecplugin.getPluginLanguage().equals(localeType);
                return FancyText.of((isThisSelected ? "§a§l" : "") +  "[" + localeType + "]§7")
                    .setHover(isThisSelected ? "§aThis locale is already selected!" : "Click to Change Locale to: " + localeType)
                    .setClickCommand(isThisSelected ? null : FCCommandUtil.dynamicCommand(() -> {
                        FCServerUtil.makeConsoleExecuteCommand(label + " set " + ecplugin.getMetaInfo().getName() + " " + localeType);
                        this.list(sender, label); //Send this command again
                    }));
            }));
            // Only add a button for the active language when it is a custom one; a standard locale is
            // already rendered (and highlighted) by the loop above, so re-adding it would duplicate it.
            if (ecplugin.getCustomLangConfig() != null && !LocaleType.values().contains(ecplugin.getPluginLanguage())){
                formatter.append(
                    FancyText.of("§a§l[" + ecplugin.getPluginLanguage() + "]§7")
                        .setHover("§aThis locale is already selected!")
                );
            }
        }

        formatter.send(sender);
    }

    @FinalCMD.SubCMD(
        subcmd = {"set"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE,
        usage = "%name% <PluginName> <LocaleName>",
        locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Defines a locale to a specific plugin."),
            @FCLocale(lang = LocaleType.PT_BR, text = "Define uma Locale para um plugin específico.")
        }
    )
    public void set(FCommandSender sender, String label, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1,2)){
            helpLine.sendTo(sender);
            return;
        }


        ECPluginData plugin = argumentos.get(1).getECPluginData();
        ECPluginData ecPluginData = plugin == null
            ? null
            : ECPluginManager.getECPluginsMap().get(plugin.getMetaInfo().getName());

        if (ecPluginData == null){
            sender.sendMessage("§e§l ▶ §cThere is no ECPlugin with the name §e[" + argumentos.get(1) + "]§c found on this server.");
            return;
        }

        String localeType = null;
        for (String value : LocaleType.values()) {
            if (argumentos.get(2).equalsIgnoreCase(value)){
                localeType = value;
                break;
            }
        }

        Config localization_config = ConfigFactory.open(plugin, "localization/localization_config.yml");

        String newLocaleValue = "lang_" + (localeType != null ? localeType : argumentos.get(2)) + ".yml";
        String previousLocaleValue = localization_config.getString("Localization.fileName");

        if (!newLocaleValue.equals(previousLocaleValue)){
            localization_config.setValue("Localization.fileName", newLocaleValue);
            localization_config.save();
            ecPluginData.reloadAllCustomLocales();
        }

        sender.sendMessage("§2§l ▶ §b§l" + plugin.getMetaInfo().getName() + "'s §alocalization file name set to [" + localization_config.getString("Localization.fileName") + "]!");
    }

    @FinalCMD.SubCMD(
        subcmd = {"setall"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE,
        usage = "%name% <LocaleName>",
        locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Defines the locale to every single plugin."),
            @FCLocale(lang = LocaleType.PT_BR, text = "Define a Locale de todos os ECPlugin para uma específica.")
        }
    )
    public void setall(FCommandSender sender, String label, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(1)){
            helpLine.sendTo(sender);
            return;
        }

        for (ECPluginData value : ECPluginManager.getECPluginsMap().values()) {
            FCServerUtil.makePlayerExecuteCommand(sender, label + " set " + value.getMetaInfo().getName() + " " + argumentos.get(1));
        }

    }

    @FinalCMD.SubCMD(
        subcmd = {"self"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE_SELF,
        usage = "%name% <LocaleName>",
        validation = {PerPlayerLocaleAccessValidation.class},
        locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Choose the language YOU see messages in."),
            @FCLocale(lang = LocaleType.PT_BR, text = "Escolha o idioma em que VOCÊ vê as mensagens.")
        }
    )
    public void self(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine, LocalePDSection localeSection) {

        if (argumentos.emptyArgs(1)){
            helpLine.sendTo(sender);
            return;
        }

        String localeType = null;
        for (String value : LocaleType.values()) {
            if (argumentos.get(1).equalsIgnoreCase(value)){
                localeType = value;
                break;
            }
        }

        if (localeType == null){
            sender.sendMessage("§e§l ▶ §c[" + argumentos.get(1) + "]§c is not a known locale.");
            return;
        }

        localeSection.setLang(localeType);
        sender.sendMessage("§2§l ▶ §aYour language has been set to §b§l[" + localeType + "]§a!");
    }

    /**
     * Gates a subcommand behind {@code ECSettings.PER_PLAYER_LOCALE}: while the feature is off the
     * subcommand is hidden from tab completion and does nothing if invoked directly. It is the guard
     * that keeps '/eclocale self' invisible and inert until an admin opts into per-player language.
     */
    public static class PerPlayerLocaleAccessValidation extends CMDAccessValidation {

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            return ECSettings.PER_PLAYER_LOCALE;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return ECSettings.PER_PLAYER_LOCALE;
        }
    }
}
