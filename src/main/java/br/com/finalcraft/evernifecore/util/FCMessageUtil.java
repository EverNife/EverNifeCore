package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FCMessageUtil {

    public static void playerNotOnline(CommandSender sender, String searchedName){
        sender.sendMessage("§e§l ▶ §cO Jogador §7[§e" + searchedName + "§7]§c não esta online!");
    }

    public static void playerNotFound(CommandSender sender, String searchedName){
        sender.sendMessage("§e§l ▶ §cO Jogador §7[§e" + searchedName + "§7]§c não existe ou não esta online!");
    }

    public static void playerDataNotFound(CommandSender sender, String searchedName){
        sender.sendMessage("§e§l ▶ §cO Jogador §7[§e" + searchedName + "§7]§c não existe ou nunca entrou nesse servidor!");
    }

    public static void worldNotFound(CommandSender sender, String searchedName){
        sender.sendMessage("§e§l ▶ §cNão existe nenhum mundo chamado §7[§e" + searchedName + "§7]§c!");
    }

    public static void needsToBeHoldingItem(CommandSender sender){
        sender.sendMessage("§e§l ▶ §cVocê precisa estar segurando um item em sua mão!");
    }

    public static void needsToBeHoldingItem(CommandSender sender, String item){
        sender.sendMessage("§e§l ▶ §cVocê precisa estar segurando um §7[§2" + item + "§7] em sua mão!");
    }

    public static void needsToBeInteger(CommandSender sender, String argumento){
        sender.sendMessage("§e§l ▶ §7[" + argumento + "§7]§c precisa ser um número inteiro!");
    }

    public static void needsToBeBoolean(CommandSender sender, String argumento){
        sender.sendMessage("§e§l ▶ §7[" + argumento + "§7]§c precisa ser um valor BOLEANO (true|false ou sim|nao) !");
    }

    public static void needsToBeDouble(CommandSender sender, String argumento){
        sender.sendMessage("§e§l ▶ §7[" + argumento + "§7]§c precisa ser um número real!");
    }

    public static void ecoNotEnough(Player sender, double amountNeeded){
        double havingAmout = FCBukkitUtil.normalizeDouble(FCBukkitUtil.getMoney(sender));
        sender.sendMessage("§e§l ▶ §cVocê não tem money suficiente! §7§o(Money: " + (havingAmout % 1 == 0 ? (int)(havingAmout) : havingAmout) +"/" + (amountNeeded % 1 == 0 ? (int)amountNeeded : amountNeeded) + ")");
    }

    public static <T extends Number> void notBounded(CommandSender sender, T value, T min, T max){
        sender.sendMessage(String.format("§e§l ▶ §cO valor inserido (§e%s§c) deve estar entre §6[%s] §ce §6[%s]!", NumberWrapper.of(value), NumberWrapper.of(min), NumberWrapper.of(max)));
    }

    public static <T extends Number> void notBoundedLower(CommandSender sender, T number, T min){
        sender.sendMessage(String.format("§e§l ▶ §cO valor inserido (§e%s§c) deve ser maior que §6[%s]!", NumberWrapper.of(number), NumberWrapper.of(min)));
    }

    public static <T extends Number> void notBoundedUpper(CommandSender sender, T number, T max){
        sender.sendMessage(String.format("§e§l ▶ §cO valor inserido (§e%s§c) deve ser menor que §6[%s]!", NumberWrapper.of(number), NumberWrapper.of(max)));
    }

    public static void printPage(CommandSender sender, List<String> content, int page){
        int PAGE_SIZE = 10;
        int totalPages = (int) Math.ceil(content.size() / (double)PAGE_SIZE);
        page = Math.min(0, Math.max(page, totalPages));
        for (int i = page * PAGE_SIZE; i < content.size(); i++) {
            sender.sendMessage(content.get(i));
        }
    }

}
