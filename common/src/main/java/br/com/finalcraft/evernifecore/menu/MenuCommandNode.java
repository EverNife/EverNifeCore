package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The menu verbs a plugin mounts under its own command, over the menus IT registered:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "menu", permission = "finalmenus.menu")
 * private final MenuCommandNode menus = new MenuCommandNode(plugin, "finalmenus.menu");
 * }</pre>
 * {@code open <menu> [args...]} opens one for the sender, {@code openfor <player> <menu> [args...]} for someone else
 * ({@code <permissionBase>.others}), {@code list} lists them. A bare menu name means this plugin's. There is no
 * {@code reload}: reading menu files is the plugin's own business. What the verbs answer is locale messages of this
 * class.
 */
public class MenuCommandNode {

    @FCLocale(lang = LocaleType.EN_US, text = "§cOnly a player can open a menu for themselves: use openfor <player> <menu>.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cSó um jogador abre um menu para si mesmo: use openfor <jogador> <menu>.")
    private static LocaleMessage NOT_A_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou need ${permission} to open a menu for another player.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê precisa de ${permission} para abrir um menu para outro jogador.")
    private static LocaleMessage NO_PERMISSION_FOR_OTHERS;

    @FCLocale(lang = LocaleType.EN_US, text = "§c${player} is not online.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c${player} não está online.")
    private static LocaleMessage NOT_ONLINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c${reason}")
    private static LocaleMessage MALFORMED_ID;

    @FCLocale(lang = LocaleType.EN_US, text = "§cNo menu '${menu}' of ${plugin}. Its menus: ${menus}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cNenhum menu '${menu}' de ${plugin}. Os menus dele: ${menus}.")
    private static LocaleMessage NO_SUCH_MENU;

    @FCLocale(lang = LocaleType.EN_US, text = "§e${plugin}§7 menus (${count}):")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Menus de §e${plugin}§7 (${count}):")
    private static LocaleMessage LIST_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 - §e${menu}§7 ${title}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 - §e${menu}§7 ${title}")
    private static LocaleMessage LIST_LINE;

    private final ECPluginData owner;
    private final String permissionBase;

    public MenuCommandNode(ECPluginData owner, String permissionBase) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.permissionBase = Objects.requireNonNull(permissionBase, "permissionBase");
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public String getPermissionBase() {
        return permissionBase;
    }

    @FinalCMD.SubCMD(subcmd = "open", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Opens a menu of this plugin, with its arguments"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Abre um menu deste plugin, com seus argumentos")
    })
    public void open(FCommandSender sender, @Arg("<menu>") String menu, @Arg("[args...]") String args) {
        if (!(sender instanceof FPlayer)) {
            NOT_A_PLAYER.send(sender);
            return;
        }
        openFor(sender, (FPlayer) sender, menu, args);
    }

    @FinalCMD.SubCMD(subcmd = "openfor", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Opens a menu of this plugin for another player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Abre um menu deste plugin para outro jogador")
    })
    public void openFor(FCommandSender sender, @Arg("<player>") String playerName, @Arg("<menu>") String menu, @Arg("[args...]") String args) {
        if (!sender.hasPermission(permissionBase + ".others")) {
            NO_PERMISSION_FOR_OTHERS.addPlaceholder("permission", permissionBase + ".others").send(sender);
            return;
        }
        FPlayer target = EverNifeCore.getPlatform().getPlayer(playerName);
        if (target == null) {
            NOT_ONLINE.addPlaceholder("player", playerName).send(sender);
            return;
        }
        openFor(sender, target, menu, args);
    }

    private void openFor(FCommandSender sender, FPlayer target, String menu, String args) {
        MenuId id;
        try {
            id = MenuId.parse(menu, owner.getMetaInfo().getName());
        } catch (IllegalArgumentException malformed) {
            MALFORMED_ID.addPlaceholder("reason", malformed.getMessage()).send(sender);
            return;
        }
        Menu found = MenuRegistry.get().find(id.toString());
        if (found == null || found.getOwner() != owner) {
            NO_SUCH_MENU.addPlaceholder("menu", id).addPlaceholder("plugin", owner.getMetaInfo().getName())
                    .addPlaceholder("menus", idsOf(owner)).send(sender);
            return;
        }
        MenuRegistry.get().open(target, id.toString(), split(args));
    }

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The menus of this plugin"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Os menus deste plugin")
    })
    public void list(FCommandSender sender) {
        List<Menu> menus = new ArrayList<>(MenuRegistry.get().getOwnedBy(owner));
        menus.sort((left, right) -> left.getId().toString().compareTo(right.getId().toString()));
        LIST_HEADER.addPlaceholder("plugin", owner.getMetaInfo().getName()).addPlaceholder("count", menus.size()).send(sender);
        for (Menu menu : menus) {
            LIST_LINE.addPlaceholder("menu", menu.getId()).addPlaceholder("title", menu.getTitle()).send(sender);
        }
    }

    private static List<String> idsOf(ECPluginData owner) {
        List<String> ids = new ArrayList<>();
        for (Menu menu : MenuRegistry.get().getOwnedBy(owner)) {
            ids.add(menu.getId().toString());
        }
        Collections.sort(ids);
        return ids;
    }

    private static List<String> split(String args) {
        return args == null || args.trim().isEmpty() ? Collections.<String>emptyList() : Arrays.asList(args.trim().split("\\s+"));
    }
}
