package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

public class FCMessageUtil {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe player §7[§e%searched_name%§7]§c is not online!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO Jogador §7[§e%searched_name%§7]§c não está online!")
    private static LocaleMessage PLAYER_NOT_ONLINE;
    public static void playerNotOnline(CommandSender sender, String searchedName){
        PLAYER_NOT_ONLINE.addPlaceholder("%searched_name%", searchedName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe player §7[§e%searched_name%§7]§c does not exist or is not online!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO Jogador §7[§e%searched_name%§7]§c não existe ou não está online!")
    private static LocaleMessage PLAYER_NOT_FOUND;
    public static void playerNotFound(CommandSender sender, String searchedName){
        PLAYER_NOT_FOUND.addPlaceholder("%searched_name%", searchedName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe player §7[§e%searched_name%§7]§c does not exist or never joined this server!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO Jogador §7[§e%searched_name%§7]§c não existe ou nunca entrou nesse servidor!")
    private static LocaleMessage PLAYER_DATA_NOT_FOUND;
    public static void playerDataNotFound(CommandSender sender, String searchedName){
        PLAYER_DATA_NOT_FOUND.addPlaceholder("%searched_name%", searchedName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThere is not world called §7[§e%searched_name%§7]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cNão existe nenhum mundo chamado §7[§e%searched_name%§7]§c!")
    private static LocaleMessage WORLD_NOT_FOUND;
    public static void worldNotFound(CommandSender sender, String searchedName){
        WORLD_NOT_FOUND.addPlaceholder("%searched_name%", searchedName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cYou do not have the permission to do that.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cVocê não tem a permissão para fazer isto.")
    private static LocaleMessage YOU_DO_NOT_HAVE_PERMISSION;
    public static void needsThePermission(CommandSender sender){
        YOU_DO_NOT_HAVE_PERMISSION.send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cYou do not have the permission §6[§e%permission%§6] §cto do that.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cVocê não tem a permissão §6[§e%permission%§6] §cpara fazer isto.")
    private static LocaleMessage YOU_DO_NOT_HAVE_SPECIFIC_PERMISSION;
    public static void needsThePermission(CommandSender sender, String permission){
        YOU_DO_NOT_HAVE_SPECIFIC_PERMISSION.addPlaceholder("%permission%", permission).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be holding an item in your hand!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar segurando um item em sua mão!")
    private static LocaleMessage NEEDS_TO_BE_HOLDING_ITEM;
    public static void needsToBeHoldingItem(CommandSender sender){
        NEEDS_TO_BE_HOLDING_ITEM.send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be holding an §7[§2%item_name%§7]§c in your hand!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar segurando um §7[§2%item_name%§7]§c em sua mão!")
    private static LocaleMessage NEEDS_TO_BE_HOLDING_SPECIFIC_ITEM;
    public static void needsToBeHoldingItem(CommandSender sender, String itemName){
        NEEDS_TO_BE_HOLDING_SPECIFIC_ITEM.addPlaceholder("%item_name%", itemName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be looking at a block!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar olhando para um bloco!")
    private static LocaleMessage NEEDS_TO_BE_LOOKING_AT_BLOCK;
    public static void needsToBeLookingAtBlock(CommandSender sender){
        NEEDS_TO_BE_LOOKING_AT_BLOCK.send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be looking at a block §e[%block_name%]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar olhando para um bloco §e[%block_name%]§c!")
    private static LocaleMessage NEEDS_TO_BE_LOOKING_AT_A_SPECIFIC_BLOCK;
    public static void needsToBeLookingAtBlock(CommandSender sender, String blockName){
        NEEDS_TO_BE_LOOKING_AT_A_SPECIFIC_BLOCK
                .addPlaceholder("%block_name%", blockName)
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be looking at an entity!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar olhando para uma entidade!")
    private static LocaleMessage NEEDS_TO_BE_LOOKING_AT_ENTITY;
    public static void needsToBeLookingAtEntity(CommandSender sender){
        NEEDS_TO_BE_LOOKING_AT_BLOCK.send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to be looking at an entity §e[%entity_name%]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa estar olhando para uma entidade §e[%entity_name%]§c!")
    private static LocaleMessage NEEDS_TO_BE_LOOKING_AT_A_SPECIFIC_ENTITY;
    public static void needsToBeLookingAtEntity(CommandSender sender, String entityName){
        NEEDS_TO_BE_LOOKING_AT_A_SPECIFIC_BLOCK
                .addPlaceholder("%entity_name%", entityName)
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need to have an §7[§2%item_name%§7]§c in your inventory!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa ter um §7[§2%item_name%§7]§c em seu inventário!")
    private static LocaleMessage NEEDS_TO_HAVE_ON_INVENTORY;
    public static void needsToHaveOnInventory(CommandSender sender, String itemName){
        NEEDS_TO_HAVE_ON_INVENTORY.addPlaceholder("%item_name%", itemName).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou need more space on the inventory!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê precisa de mais espaço no inventário!")
    private static LocaleMessage NOT_ENOUGHT_SPACE_ON_INVENTORY;
    public static <T extends Number> void needsToHaveMoreInventorySpace(CommandSender sender){
        NOT_ENOUGHT_SPACE_ON_INVENTORY
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be an integer!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser um número inteiro!")
    private static LocaleMessage NEEDS_TO_BE_INTEGER;
    public static void needsToBeInteger(CommandSender sender, String argumento){
        NEEDS_TO_BE_INTEGER.addPlaceholder("%argumento%", argumento).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be a BOLEAN (true|false or yes|no) !")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser um valor BOLEANO (true|false ou sim|nao) !")
    private static LocaleMessage NEEDS_TO_BE_BOOLEAN;
    public static void needsToBeBoolean(CommandSender sender, String argumento){
        NEEDS_TO_BE_BOOLEAN.addPlaceholder("%argumento%", argumento).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be a real number!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser um número real!")
    private static LocaleMessage NEEDS_TO_BE_DOUBLE;
    public static void needsToBeDouble(CommandSender sender, String argumento){
        NEEDS_TO_BE_DOUBLE.addPlaceholder("%argumento%", argumento).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be a valid UUID!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser uma UUID válida!")
    private static LocaleMessage NEEDS_TO_BE_UUID;
    public static void needsToBeUUID(CommandSender sender, String argumento){
        NEEDS_TO_BE_UUID.addPlaceholder("%argumento%", argumento).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be a valid TimeFrame! §eFor Example: '30s' or '1h 30m 10s'")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser uma TimeFrame Valido! §ePor exemplo: '30s' ou '1h 30m 10s'")
    private static LocaleMessage NEEDS_TO_BE_TIME_FRAME;
    public static void needsToBeTimeFrame(CommandSender sender, String argumento){
        NEEDS_TO_BE_TIME_FRAME.addPlaceholder("%argumento%", argumento).send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cYou do not have enough money! §7§o(Money: %current_money%§l/§7§o%needed_money%)")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cVocê não tem money suficiente! §7§o(Money: %current_money%§l/§7§o%needed_money%)")
    private static LocaleMessage ECO_NOT_ENOUGHT;
    public static void ecoNotEnough(Player sender, double amountNeeded){
        NumberWrapper<Double> currentMoney = NumberWrapper.of(FCEcoUtil.ecoGet(sender));
        NumberWrapper<Double> neededMoney = NumberWrapper.of(amountNeeded);
        ECO_NOT_ENOUGHT
                .addPlaceholder("%current_money%", currentMoney)
                .addPlaceholder("%needed_money%", neededMoney)
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe inserted value §e(%number%)§c must be between §6[%min%] §cand §6[%max%]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO valor inserido §e(%number%)§c deve estar entre §6[%min%] §ce §6[%max%]§c!")
    private static LocaleMessage NOT_BOUNDED;
    public static <T extends Number> void notBounded(CommandSender sender, T number, T min, T max){
        NOT_BOUNDED
                .addPlaceholder("%number%", NumberWrapper.of(number))
                .addPlaceholder("%min%", NumberWrapper.of(min))
                .addPlaceholder("%max%", NumberWrapper.of(max))
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe inserted value §e(%number%)§c must be higher than §6[%min%]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO valor inserido §e(%number%)§c deve ser maior que §6[%min%]§c!")
    private static LocaleMessage NOT_BOUNDED_LOWER;
    public static <T extends Number> void notBoundedLower(CommandSender sender, T number, T min){
        NOT_BOUNDED_LOWER
                .addPlaceholder("%number%", NumberWrapper.of(number))
                .addPlaceholder("%min%", NumberWrapper.of(min))
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe inserted value §e(%number%)§c must be lower than §6[%max%]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO valor inserido §e(%number%)§c deve ser menor que §6[%max%]§c!")
    private static LocaleMessage NOT_BOUNDED_UPPER;
    public static <T extends Number> void notBoundedUpper(CommandSender sender, T number, T max){
        NOT_BOUNDED_UPPER
                .addPlaceholder("%number%", NumberWrapper.of(number))
                .addPlaceholder("%max%", NumberWrapper.of(max))
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe inserted value §e(§6%value%§e)§c must be §6[%possibilities%§6]§c!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO valor inserido §e(§6%value%§e)§c deve ser §6[%possibilities%§6]§c!")
    private static LocaleMessage NOT_WITHIN_POSSIBILITIES;
    public static void notWithinPossibilities(CommandSender sender, String argument, Collection<? extends Object> possibilities){
        StringBuilder stringBuilder = new StringBuilder();
        for (Object value : possibilities) {
            stringBuilder.append("§b" + value + "§7, ");
        }
        NOT_WITHIN_POSSIBILITIES
                .addPlaceholder("%value%", argument)
                .addPlaceholder("%possibilities%", stringBuilder.substring(0, stringBuilder.length() - 4))
                .send(sender);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §aThe plugin [§b%plugin_name%§a] has been reloaded!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §aO plugin [§b%plugin_name%§a] foi recarregado com sucesso!")
    private static LocaleMessage PLUGIN_HAS_BEEN_RELOADED;
    public static <T extends Number> void pluginHasBeenReloaded(CommandSender sender, String pluginName){
        PLUGIN_HAS_BEEN_RELOADED
                .addPlaceholder("%plugin_name%", pluginName)
                .send(sender);
    }
}
