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
        ArgParserManager.addBuiltinParser(Player.class, ArgParserPlayer.class);
        ArgParserManager.addBuiltinParser(World.class, ArgParserWorld.class);

        if (FCBukkitUtil.isModded()){
            ArgParserManager.addBuiltinParser(OreDictEntry.class, ArgParserOreDict.class);
        }

        //External Plugins
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")){
            ArgParserManager.addBuiltinParser(FCWorldGuardRegion.class, ArgParserFCWorldGuardRegion.class);
        }

        ArgParserManager.addBuiltinContextualParser(CommandSender.class, ArgParserContextualCommandSender.class);
        ArgParserManager.addBuiltinContextualParser(MinecraftFCommandSender.class, ArgParserContextualMinecraftFCommandSender.class);
        ArgParserManager.addBuiltinContextualParser(MinecraftFPlayer.class, ArgParserContextualMinecraftFPlayer.class);
        ArgParserManager.addBuiltinContextualParser(ItemStack.class, ArgParserContextualItemStack.class);
        ArgParserManager.addBuiltinContextualParser(Player.class, ArgParserContextualPlayer.class);
    }

}
