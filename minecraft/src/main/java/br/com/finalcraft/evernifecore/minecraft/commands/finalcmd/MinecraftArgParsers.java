package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers.*;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserFCWorldGuardRegion;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserOreDict;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserPlayer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers.ArgParserWorld;
import br.com.finalcraft.evernifecore.minecraft.nms.data.oredict.OreDictEntry;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.FCWorldGuardRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MinecraftArgParsers {

    public static void initialize() {
        ArgParserManager.addGlobalParser(Player.class, ArgParserPlayer.class);
        ArgParserManager.addGlobalParser(World.class, ArgParserWorld.class);

        if (FCBukkitUtil.isForge()){
            ArgParserManager.addGlobalParser(OreDictEntry.class, ArgParserOreDict.class);
        }

        //External Plugins
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")){
            ArgParserManager.addGlobalParser(FCWorldGuardRegion.class, ArgParserFCWorldGuardRegion.class);
        }

        ArgParserManager.addGlobalContextualParser(CommandSender.class, ArgParserContextualCommandSender.class);
        ArgParserManager.addGlobalContextualParser(MinecraftFCommandSender.class, ArgParserContextualMinecraftFCommandSender.class);
        ArgParserManager.addGlobalContextualParser(MinecraftFPlayer.class, ArgParserContextualMinecraftFPlayer.class);
        ArgParserManager.addGlobalContextualParser(ItemStack.class, ArgParserContextualItemStack.class);
        ArgParserManager.addGlobalContextualParser(Player.class, ArgParserContextualPlayer.class);
    }

}
