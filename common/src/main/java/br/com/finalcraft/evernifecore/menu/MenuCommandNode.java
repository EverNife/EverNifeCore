package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
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
 * {@code reload}: reading menu files is the plugin's own business.
 */
public class MenuCommandNode {

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
            sender.sendMessage("§cOnly a player can open a menu for themselves: use openfor <player> <menu>.");
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
            sender.sendMessage("§cYou need " + permissionBase + ".others to open a menu for another player.");
            return;
        }
        FPlayer target = EverNifeCore.getPlatform().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§c" + playerName + " is not online.");
            return;
        }
        openFor(sender, target, menu, args);
    }

    private void openFor(FCommandSender sender, FPlayer target, String menu, String args) {
        MenuId id;
        try {
            id = MenuId.parse(menu, owner.getMetaInfo().getName());
        } catch (IllegalArgumentException malformed) {
            sender.sendMessage("§c" + malformed.getMessage());
            return;
        }
        Menu found = MenuRegistry.get().find(id.toString());
        if (found == null || found.getOwner() != owner) {
            sender.sendMessage("§cNo menu '" + id + "' of " + owner.getMetaInfo().getName() + ". Its menus: " + idsOf(owner) + ".");
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
        sender.sendMessage("§e" + owner.getMetaInfo().getName() + "§7 menus (" + menus.size() + "):");
        for (Menu menu : menus) {
            sender.sendMessage("§7 - §e" + menu.getId() + (menu.getTitle().isEmpty() ? "" : "§7 " + menu.getTitle()));
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
