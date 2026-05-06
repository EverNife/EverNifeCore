package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

public class ArgParserContextualItemStack extends ArgParserContextual<ItemStack> {

    public ArgParserContextualItemStack(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public ItemStack parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {

        MinecraftFPlayer player = (MinecraftFPlayer) sender;

        ItemStack itemStack = FCBukkitUtil.getPlayersHeldItem(player.getPlayer());

        if (itemStack == null){
            FCMessageUtil.needsToBeHoldingItem(sender);
            throw new ArgParseException();
        }

        return itemStack;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
