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
        ArgParserManager.addBuiltinParser(World.class, ArgParserWorld.class);
        ArgParserManager.addBuiltinParser(PlayerRef.class, ArgParserPlayerRef.class);
        ArgParserManager.addBuiltinParser(HytaleFPlayer.class, ArgParserFPlayer.class);

        ArgParserManager.addBuiltinContextualParser(CommandSender.class, ArgParserContextualCommandSender.class);
        ArgParserManager.addBuiltinContextualParser(HytaleFCommandSender.class, ArgParserContextualHytaleFCommandSender.class);
        ArgParserManager.addBuiltinContextualParser(HytaleFPlayer.class, ArgParserContextualHytaleFPlayer.class);
        ArgParserManager.addBuiltinContextualParser(ItemStack.class, ArgParserContextualItemStack.class);
        ArgParserManager.addBuiltinContextualParser(Player.class, ArgParserContextualPlayer.class);
    }

}
