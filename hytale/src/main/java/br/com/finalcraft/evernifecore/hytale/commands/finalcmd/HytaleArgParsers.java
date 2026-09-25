package br.com.finalcraft.evernifecore.hytale.commands.finalcmd;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserFPlayer;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFCommandSender;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers.*;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.parsers.ArgParserPlayerRef;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.parsers.ArgParserWorld;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public class HytaleArgParsers {

    public static void initialize() {
        ECPluginData core = EverNifeCore.getEcPluginData();
        ArgParserManager.addGlobalParser(core, World.class, ArgParserWorld.class);
        ArgParserManager.addGlobalParser(core, PlayerRef.class, ArgParserPlayerRef.class);
        ArgParserManager.addGlobalParser(core, HytaleFPlayer.class, ArgParserFPlayer.class);

        ArgParserManager.addGlobalContextualParser(core, CommandSender.class, ArgParserContextualCommandSender.class);
        ArgParserManager.addGlobalContextualParser(core, HytaleFCommandSender.class, ArgParserContextualHytaleFCommandSender.class);
        ArgParserManager.addGlobalContextualParser(core, HytaleFPlayer.class, ArgParserContextualHytaleFPlayer.class);
        ArgParserManager.addGlobalContextualParser(core, ItemStack.class, ArgParserContextualItemStack.class);
        ArgParserManager.addGlobalContextualParser(core, Player.class, ArgParserContextualPlayer.class);
    }

}
