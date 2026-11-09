package br.com.finalcraft.evernifecore.screen.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.locale.SendCustom;
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

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §a[${plugin}] ${layouts} layout(s) reloaded. ${redrawn} open screen(s) redrawn.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §a[${plugin}] ${layouts} layout(s) recarregado(s). ${redrawn} tela(s) aberta(s) redesenhada(s).")
    private static LocaleMessage RELOADED_ALL;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §a[${plugin}] ${layout} reloaded. ${redrawn} open screen(s) redrawn.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §a[${plugin}] ${layout} recarregado. ${redrawn} tela(s) aberta(s) redesenhada(s).")
    private static LocaleMessage RELOADED_ONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§f${layout}  §7->  §fplugins/${plugin}/${file}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§f${layout}  §7->  §fplugins/${plugin}/${file}")
    private static LocaleMessage DIFF_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§f${layout}  §7->  §fplugins/${plugin}/${file} §7+ overlay ${language}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§f${layout}  §7->  §fplugins/${plugin}/${file} §7+ overlay ${language}")
    private static LocaleMessage DIFF_HEADER_OVERLAY;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  no overlay for ${language}: every key below is the base file's. Write plugins/${plugin}/${overlay} by hand to change this screen for that language alone - the framework never creates it.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  sem overlay para ${language}: toda chave abaixo é do arquivo base. Escreva plugins/${plugin}/${overlay} à mão para mudar esta tela só nesse idioma - o framework nunca o cria.")
    private static LocaleMessage NO_OVERLAY;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  §aMATCHED (${count})")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  §aIGUAIS (${count})")
    private static LocaleMessage MATCHED;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  §eNEW (${count})§7 - declared by the plugin, will be seeded on the next save")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  §eNOVAS (${count})§7 - declaradas pelo plugin, entram no arquivo no próximo save")
    private static LocaleMessage NEW;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  §6ORPHAN (${count})§7 - in the file, the plugin does not use it anymore")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  §6ÓRFÃS (${count})§7 - no arquivo, o plugin não as usa mais")
    private static LocaleMessage ORPHAN;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  §cSILENCED (${count})§7 - in the file, but nowhere on the screen")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  §cSILENCIADAS (${count})§7 - no arquivo, mas em lugar nenhum da tela")
    private static LocaleMessage SILENCED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c  WARNINGS (${count})")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c  AVISOS (${count})")
    private static LocaleMessage WARNINGS;

    @FCLocale(lang = LocaleType.EN_US, text = "§7    ${key} has an unreadable slot list, so where it goes is unknown: ${reason} Fix ${path} in ${file}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7    ${key} tem uma lista de slots ilegível, então não se sabe onde ele vai: ${reason} Corrija ${path} em ${file}.")
    private static LocaleMessage WARNING_UNREADABLE_SLOTS;

    @FCLocale(lang = LocaleType.EN_US, text = "§7    ${winner} and ${loser} both claim slot ${slot}. ${winner} wins.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7    ${winner} e ${loser} disputam o slot ${slot}. ${winner} vence.")
    private static LocaleMessage WARNING_SLOT_DISPUTE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7    §f${key}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7    §f${key}")
    private static LocaleMessage ENTRY_KEY;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7Slot ${slots}")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7Slot ${slots}")
    private static LocaleMessage ENTRY_SLOT;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7group ${group}")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7grupo ${group}")
    private static LocaleMessage ENTRY_GROUP;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7will be seeded on the next save")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7será criada no próximo save")
    private static LocaleMessage ENTRY_TO_BE_SEEDED;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7the plugin no longer declares it")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7o plugin não a declara mais")
    private static LocaleMessage ENTRY_NO_LONGER_DECLARED;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7declared by the file only")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7declarada só pelo arquivo")
    private static LocaleMessage ENTRY_FILE_ONLY;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7states: ${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7estados: ${count}")
    private static LocaleMessage ENTRY_STATES;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7languages: ${languages}")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7idiomas: ${languages}")
    private static LocaleMessage ENTRY_LANGUAGES;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7permission ${permission}")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7permissão ${permission}")
    private static LocaleMessage ENTRY_PERMISSION;

    @FCLocale(lang = LocaleType.EN_US, text = "   §7from overlay")
    @FCLocale(lang = LocaleType.PT_BR, text = "   §7do overlay")
    private static LocaleMessage ENTRY_FROM_OVERLAY;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §c${reason}")
    private static LocaleMessage INVALID_KEY;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cNothing answers ${key}: no override registered it and the core has no default under that key.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNada responde a ${key}: nenhum override a registrou e o core não tem padrão nessa chave.")
    private static LocaleMessage NOTHING_ANSWERS;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cNothing answers ${key} for the content of ${plugin}: no override registered it and the core has no default under that key.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNada responde a ${key} para o conteúdo de ${plugin}: nenhum override a registrou e o core não tem padrão nessa chave.")
    private static LocaleMessage NOTHING_ANSWERS_CONTENT;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §a${answer}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §a${answer}")
    private static LocaleMessage ANSWERED_BY;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §a${answer} §7(content of ${plugin})")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §a${answer} §7(conteúdo de ${plugin})")
    private static LocaleMessage ANSWERED_BY_CONTENT;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cNo plugin named '${plugin}' is registered on EverNifeCore. Known: ${known}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNenhum plugin chamado '${plugin}' está registrado no EverNifeCore. Conhecidos: ${known}")
    private static LocaleMessage NO_PLUGIN;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §c${plugin} has no layout named '${layout}' loaded. A layout registers itself the first time a screen asks for it, so one nobody opened yet is not listed here. Loaded: ${loaded}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §c${plugin} não tem layout chamado '${layout}' carregado. Um layout se registra na primeira vez que uma tela o pede, então um que ninguém abriu ainda não aparece aqui. Carregados: ${loaded}")
    private static LocaleMessage NO_LAYOUT;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §c${plugin} has no layout named '${layout}' loaded. A layout registers itself the first time a screen asks for it, so one nobody opened yet is not listed here. None is loaded yet.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §c${plugin} não tem layout chamado '${layout}' carregado. Um layout se registra na primeira vez que uma tela o pede, então um que ninguém abriu ainda não aparece aqui. Nenhum carregado ainda.")
    private static LocaleMessage NO_LAYOUT_NONE_LOADED;

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
            RELOADED_ALL.custom().addPlaceholder("plugin", plugin.getMetaInfo().getName())
                    .addPlaceholder("layouts", Layouts.getRegistered(plugin).size())
                    .addPlaceholder("redrawn", refreshed).send(sender);
            return;
        }

        Class<? extends Layout> type = layoutOf(sender, plugin, layoutName);
        if (type == null) {
            return;
        }
        int refreshed = Layouts.reload(type);
        RELOADED_ONE.custom().addPlaceholder("plugin", plugin.getMetaInfo().getName())
                .addPlaceholder("layout", type.getSimpleName())
                .addPlaceholder("redrawn", refreshed).send(sender);
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

        String name = plugin.getMetaInfo().getName();
        LayoutDiff diff = LayoutDiff.of(plugin, type, language);
        (diff.hasOverlay() ? DIFF_HEADER_OVERLAY : DIFF_HEADER).custom().addPlaceholder("layout", diff.getLayoutName())
                .addPlaceholder("plugin", name).addPlaceholder("file", diff.getFileName())
                .addPlaceholder("language", String.valueOf(language)).send(sender);
        if (language != null && !diff.hasOverlay()) {
            String normalized = LocaleType.normalize(language);
            NO_OVERLAY.custom().addPlaceholder("language", normalized).addPlaceholder("plugin", name)
                    .addPlaceholder("overlay", LayoutScanner.overlayFileNameOf(type, normalized)).send(sender);
        }

        report(sender, diff, LayoutDiff.Verdict.MATCHED, MATCHED);
        report(sender, diff, LayoutDiff.Verdict.NEW, NEW);
        report(sender, diff, LayoutDiff.Verdict.ORPHAN, ORPHAN);
        report(sender, diff, LayoutDiff.Verdict.SILENCED, SILENCED);

        int warnings = diff.getUnreadableSlots().size() + diff.getSlotDisputes().size();
        if (warnings > 0) {
            WARNINGS.addPlaceholder("count", warnings).send(sender);
            for (LayoutDiff.UnreadableSlots unreadable : diff.getUnreadableSlots()) {
                WARNING_UNREADABLE_SLOTS.custom().addPlaceholder("key", unreadable.getKey()).addPlaceholder("reason", unreadable.getReason())
                        .addPlaceholder("path", unreadable.getPath()).addPlaceholder("file", diff.getFileName()).send(sender);
            }
            for (LayoutDiff.SlotDispute dispute : diff.getSlotDisputes()) {
                WARNING_SLOT_DISPUTE.custom().addPlaceholder("winner", dispute.getWinner()).addPlaceholder("loser", dispute.getLoser())
                        .addPlaceholder("slot", dispute.getSlot()).send(sender);
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
            INVALID_KEY.addPlaceholder("reason", String.valueOf(invalid.getMessage())).send(sender);
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
        String content = owner == null ? "" : owner.getMetaInfo().getName();
        LocaleMessage message = answer == null
                ? (owner == null ? NOTHING_ANSWERS : NOTHING_ANSWERS_CONTENT)
                : (owner == null ? ANSWERED_BY : ANSWERED_BY_CONTENT);
        message.custom().addPlaceholder("key", key.toString()).addPlaceholder("answer", String.valueOf(answer))
                .addPlaceholder("plugin", content).send(sender);
    }

    private void report(FCommandSender sender, LayoutDiff diff, LayoutDiff.Verdict verdict, LocaleMessage title) {
        List<LayoutDiff.Entry> entries = diff.getEntries(verdict);
        if (entries.isEmpty()) {
            return;
        }
        title.addPlaceholder("count", entries.size()).send(sender);
        for (LayoutDiff.Entry entry : entries) {
            SendCustom line = ENTRY_KEY.custom().addPlaceholder("key", entry.getKey());
            if (entry.getSlots() != null) {
                line = line.append(ENTRY_SLOT.custom().addPlaceholder("slots", entry.getSlots().serialize()));
            }
            if (!entry.getGroup().isEmpty()) {
                //three icons over one slot read as a defect until the report says they share it on purpose
                line = line.append(ENTRY_GROUP.custom().addPlaceholder("group", entry.getGroup()));
            }
            if (entry.getOrigin() == LayoutDiff.Origin.TO_BE_SEEDED) {
                line = line.append(ENTRY_TO_BE_SEEDED);
            } else if (entry.getOrigin() == LayoutDiff.Origin.NO_LONGER_DECLARED) {
                line = line.append(ENTRY_NO_LONGER_DECLARED);
            } else if (entry.getOrigin() == LayoutDiff.Origin.FILE_ONLY) {
                line = line.append(ENTRY_FILE_ONLY);
            }
            if (entry.getStateCount() > 0) {
                line = line.append(ENTRY_STATES.custom().addPlaceholder("count", entry.getStateCount()));
            }
            if (!entry.getLanguages().isEmpty()) {
                line = line.append(ENTRY_LANGUAGES.custom().addPlaceholder("languages", String.join("/", entry.getLanguages())));
            }
            if (entry.getPermission() != null) {
                line = line.append(ENTRY_PERMISSION.custom().addPlaceholder("permission", entry.getPermission()));
            }
            if (entry.isFromOverlay()) {
                line = line.append(ENTRY_FROM_OVERLAY);
            }
            line.send(sender);
        }
    }

    private ECPluginData pluginOf(FCommandSender sender, String pluginName) {
        for (Map.Entry<String, ECPluginData> entry : ECPluginManager.getECPluginsMap().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(pluginName)) {
                return entry.getValue();
            }
        }
        NO_PLUGIN.custom().addPlaceholder("plugin", pluginName)
                .addPlaceholder("known", String.join(", ", ECPluginManager.getECPluginsMap().keySet())).send(sender);
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
        (known.isEmpty() ? NO_LAYOUT_NONE_LOADED : NO_LAYOUT).custom().addPlaceholder("plugin", plugin.getMetaInfo().getName())
                .addPlaceholder("layout", layoutName).addPlaceholder("loaded", String.join(", ", known)).send(sender);
        return null;
    }

}
