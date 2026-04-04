package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;

public class ArgParserContextualItemStack extends ArgParserContextual<ItemStack> {

    public ArgParserContextualItemStack(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public ItemStack parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {

        HytaleFPlayer player = (HytaleFPlayer) sender;

        ItemStack itemStack = FCScheduler.getHytaleScheduler().getSynchronizedAction().runAndGet(player.getWorld(), () -> {
            return player.getPlayer().getInventory().getItemInHand();
        });

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
