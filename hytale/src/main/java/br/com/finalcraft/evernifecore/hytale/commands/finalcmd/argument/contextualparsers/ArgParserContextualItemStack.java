package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The item a command reads off the sender instead of off the line. Declaring no {@code context()} reads
 * whatever the player is holding - the only thing "this player's item" could mean without saying so -
 * and naming a slot reads that piece of armor instead.
 * <p>
 * An empty slot is not a refusal: not wearing a helmet is a fact the method is free to act on, so the
 * parameter simply arrives null. An empty HAND still refuses, because a command that asks for what you
 * are holding has nothing left to work with.
 */
public class ArgParserContextualItemStack extends ArgParserContextual<ItemStack> {

    /**
     * The armor a {@code context()} may name, each at its own place in the player's armor container. The
     * hand is deliberately absent: it is what declaring no context means, which keeps this list exactly
     * the set a refusal is allowed to offer.
     */
    enum Slot {
        HEAD(0),
        CHEST(1),
        HANDS(2),
        LEGS(3);

        private final short index;

        Slot(int index) {
            this.index = (short) index;
        }
    }

    /** The armor this declaration named, or null for whatever the player is holding. */
    private final @Nullable Slot slot;

    public ArgParserContextualItemStack(ArgInfo argInfo) {
        super(argInfo);
        this.slot = slotOf(argInfo.getArgData().getContext());
    }

    @Override
    public ParseResult<ItemStack> parse(@Nonnull ContextualParseCall call) {

        HytaleFPlayer player = (HytaleFPlayer) call.getSender();

        //An inventory is only readable from its own world's thread, so each branch pays for exactly one
        //hop and reads everything it needs while it is there
        if (slot == null){
            ItemStack itemStack = FCScheduler.getHytaleScheduler().getSynchronizedAction().runAndGet(player.getWorld(), () -> {
                return player.getPlayer().getInventory().getItemInHand();
            });

            return itemStack != null
                    ? ParseResult.of(itemStack)
                    : denied(FCMessageUtil.NEEDS_TO_BE_HOLDING_ITEM);
        }

        ItemStack equipped = FCScheduler.getHytaleScheduler().getSynchronizedAction().runAndGet(player.getWorld(), () -> {
            return player.getPlayer().getInventory().getArmor().getItemStack(slot.index);
        });

        return equipped != null && equipped.isValid()
                ? ParseResult.of(equipped)
                : ParseResult.empty();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

    /**
     * The slot a declaration names, or null when it names none.
     *
     * @throws ArgMountException when the text names no slot at all - a mistake in the command rather
     * than in anything a player could type, so the boot is where it has to be told
     */
    private static @Nullable Slot slotOf(String context) {
        String wanted = context.trim();
        if (wanted.isEmpty()){
            return null;
        }

        for (Slot slot : Slot.values()) {
            if (slot.name().equalsIgnoreCase(wanted)){
                return slot;
            }
        }

        //"a|b" is how the context of a TOKEN offers a choice; a parameter wears one piece of armor, so
        //saying so beats letting the developer read the list and wonder which half was wrong
        String oneOnly = wanted.indexOf('|') < 0 ? ""
                : " A parameter reads exactly one piece of armor, so a list of them means nothing here.";

        throw new ArgMountException("The context [" + wanted + "] on a contextual ItemStack names no armor slot." + oneOnly
                + " Use one of " + accepted() + ", or leave context() empty for whatever the player is holding.");
    }

    private static String accepted() {
        return Arrays.stream(Slot.values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** @return the armor this declaration resolved to, or null for whatever the player is holding */
    @Nullable Slot getSlot() {
        return slot;
    }
}
