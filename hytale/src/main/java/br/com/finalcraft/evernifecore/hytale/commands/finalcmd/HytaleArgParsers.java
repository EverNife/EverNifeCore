package br.com.finalcraft.evernifecore.hytale.commands.finalcmd;

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
        ArgParserManager.addGlobalParser(World.class, ArgParserWorld.class);
        ArgParserManager.addGlobalParser(PlayerRef.class, ArgParserPlayerRef.class);
        ArgParserManager.addGlobalParser(HytaleFPlayer.class, ArgParserFPlayer.class);

        ArgParserManager.addGlobalContextualParser(CommandSender.class, ArgParserContextualCommandSender.class);
        ArgParserManager.addGlobalContextualParser(HytaleFCommandSender.class, ArgParserContextualHytaleFCommandSender.class);
        ArgParserManager.addGlobalContextualParser(HytaleFPlayer.class, ArgParserContextualHytaleFPlayer.class);
        ArgParserManager.addGlobalContextualParser(ItemStack.class, ArgParserContextualItemStack.class);
        ArgParserManager.addGlobalContextualParser(Player.class, ArgParserContextualPlayer.class);
    }

}
