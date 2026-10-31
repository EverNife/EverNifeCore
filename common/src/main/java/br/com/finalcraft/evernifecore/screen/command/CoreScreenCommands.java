package br.com.finalcraft.evernifecore.screen.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.LayoutDiff;
import br.com.finalcraft.evernifecore.screen.LayoutScanner;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.Screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /ecore screen reload|diff|which}: operating a screen without restarting the server - reload a layout's file,
 * confront what the plugin declares against what the file says, and name the override layer that answers a key.
 *
 * <p>Reload and diff work for ANY plugin's layout, because layouts register themselves as they load. A consuming plugin gets this
 * for free and writes nothing.</p>
 */
public class CoreScreenCommands {

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Re-read a layout's yml and redraw whoever has it open!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Rele o yml de um layout e redesenha quem esta com ele aberto!")
            }
    )
    public void reload(FCommandSender sender, @Arg("<plugin>") String pluginName, @Arg("[layout]") String layoutName) {
        ECPluginData plugin = pluginOf(sender, pluginName);
        if (plugin == null) {
            return;
        }

        if (layoutName == null) {
            int refreshed = Layouts.reloadAll(plugin);
            sender.sendMessage("§e§l ▶ §a[" + plugin.getMetaInfo().getName() + "] "
                    + Layouts.getRegistered(plugin).size() + " layout(s) reloaded. "
                    + refreshed + " open screen(s) redrawn.");
            return;
        }

        Class<? extends Layout> type = layoutOf(sender, plugin, layoutName);
        if (type == null) {
            return;
        }
        int refreshed = Layouts.reload(type);
        sender.sendMessage("§e§l ▶ §a[" + plugin.getMetaInfo().getName() + "] " + type.getSimpleName()
                + " reloaded. " + refreshed + " open screen(s) redrawn.");
    }

    @FinalCMD.SubCMD(
            subcmd = "diff",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show what the plugin declares against what the yml says!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra o que o plugin declara contra o que o yml diz!")
            }
    )
    public void diff(FCommandSender sender, @Arg("<plugin>") String pluginName,
                     @Arg("<layout>") String layoutName, @Arg("[language]") String language) {
        ECPluginData plugin = pluginOf(sender, pluginName);
        if (plugin == null) {
            return;
        }
        Class<? extends Layout> type = layoutOf(sender, plugin, layoutName);
        if (type == null) {
            return;
        }

        LayoutDiff diff = LayoutDiff.of(plugin, type, language);
        sender.sendMessage("§f" + diff.getLayoutName() + "  §7->  §fplugins/" + plugin.getMetaInfo().getName()
                + "/" + diff.getFileName() + (diff.hasOverlay() ? " §7+ overlay " + language : ""));
        if (language != null && !diff.hasOverlay()) {
            sender.sendMessage("§7  no overlay for " + LocaleType.normalize(language) + ": every key below "
                    + "is the base file's. Write plugins/" + plugin.getMetaInfo().getName() + "/"
                    + LayoutScanner.overlayFileNameOf(type, LocaleType.normalize(language)) + " by hand to "
                    + "change this screen for that language alone - the framework never creates it.");
        }

        report(sender, diff, LayoutDiff.Verdict.MATCHED, "§aMATCHED", "");
        report(sender, diff, LayoutDiff.Verdict.NEW, "§eNEW",
                "§7 - declared by the plugin, will be seeded on the next save");
        report(sender, diff, LayoutDiff.Verdict.ORPHAN, "§6ORPHAN",
                "§7 - in the file, the plugin does not use it anymore");
        report(sender, diff, LayoutDiff.Verdict.SILENCED, "§cSILENCED",
                "§7 - in the file, but nowhere on the screen");

        if (!diff.getWarnings().isEmpty()) {
            sender.sendMessage("§c  WARNINGS (" + diff.getWarnings().size() + ")");
            for (String warning : diff.getWarnings()) {
                sender.sendMessage("§7    " + warning);
            }
        }
    }

    @FinalCMD.SubCMD(
            subcmd = "which",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show which override answers a screen key!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra qual override responde a uma chave de tela!")
            }
    )
    public void which(FCommandSender sender, @Arg("<screen>") String screenKey, @Arg("[plugin]") String pluginName) {
        ScreenKey key;
        try {
            key = ScreenKey.parse(screenKey);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§e§l ▶ §c" + invalid.getMessage());
            return;
        }
        ECPluginData owner = null;
        if (pluginName != null) {
            owner = pluginOf(sender, pluginName);
            if (owner == null) {
                return;
            }
        }
        String answer;
        try {
            answer = Screens.which(key, ScreenRequest.of(owner));
        } catch (IllegalStateException refused) {
            answer = null;
        }
        sender.sendMessage(answer == null
                ? "§e§l ▶ §cNothing answers " + key + (owner == null ? "" : " for the content of " + owner.getMetaInfo().getName())
                + ": no override registered it and the core has no default under that key."
                : "§e§l ▶ §a" + answer + (owner == null ? "" : " §7(content of " + owner.getMetaInfo().getName() + ")"));
    }

    private void report(FCommandSender sender, LayoutDiff diff, LayoutDiff.Verdict verdict, String title,
                        String explanation) {
        List<LayoutDiff.Entry> entries = diff.getEntries(verdict);
        if (entries.isEmpty()) {
            return;
        }
        sender.sendMessage("§7  " + title + " (" + entries.size() + ")" + explanation);
        for (LayoutDiff.Entry entry : entries) {
            List<String> columns = new ArrayList<>();
            columns.add(entry.getKey());
            if (entry.getSlots() != null) {
                columns.add("Slot " + entry.getSlots().serialize());
            }
            if (!entry.getGroup().isEmpty()) {
                //three icons over one slot read as a defect until the report says they share it on purpose
                columns.add("group " + entry.getGroup());
            }
            if (!entry.getDetail().isEmpty()) {
                columns.add(entry.getDetail());
            }
            if (entry.isFromOverlay()) {
                columns.add("from overlay");
            }
            sender.sendMessage("§7    §f" + String.join("   §7", columns));
        }
    }

    private ECPluginData pluginOf(FCommandSender sender, String pluginName) {
        for (Map.Entry<String, ECPluginData> entry : ECPluginManager.getECPluginsMap().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(pluginName)) {
                return entry.getValue();
            }
        }
        sender.sendMessage("§e§l ▶ §cNo plugin named '" + pluginName + "' is registered on EverNifeCore. "
                + "Known: " + String.join(", ", ECPluginManager.getECPluginsMap().keySet()));
        return null;
    }

    private Class<? extends Layout> layoutOf(FCommandSender sender, ECPluginData plugin, String layoutName) {
        Class<? extends Layout> type = Layouts.findRegistered(plugin, layoutName).orElse(null);
        if (type != null) {
            return type;
        }
        List<String> known = new ArrayList<>();
        for (Class<? extends Layout> registered : Layouts.getRegistered(plugin)) {
            known.add(registered.getSimpleName());
        }
        sender.sendMessage("§e§l ▶ §c" + plugin.getMetaInfo().getName() + " has no layout named '"
                + layoutName + "' loaded. A layout registers itself the first time a screen asks for it, so "
                + "one nobody opened yet is not listed here. Loaded: "
                + (known.isEmpty() ? "none" : String.join(", ", known)));
        return null;
    }

}
