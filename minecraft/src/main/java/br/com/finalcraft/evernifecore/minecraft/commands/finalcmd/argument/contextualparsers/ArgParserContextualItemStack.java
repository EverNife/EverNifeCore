package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The item a command reads off the sender instead of off the line. Declaring no {@code context()} reads
 * whatever the player is holding - the only thing "this player's item" could mean without saying so -
 * and naming a slot reads that piece of equipment instead.
 * <p>
 * An empty slot is not a refusal: not wearing a helmet is a fact the method is free to act on, so the
 * parameter simply arrives null. An empty HAND still refuses, because a command that asks for what you
 * are holding has nothing left to work with.
 */
public class ArgParserContextualItemStack extends ArgParserContextual<ItemStack> {

    /**
     * The equipment a {@code context()} may name. The hand is deliberately absent: it is what declaring
     * no context means, which keeps this list exactly the set a refusal is allowed to offer.
     */
    enum Slot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        OFF_HAND
    }

    /** The equipment this declaration named, or null for whatever the player is holding. */
    private final @Nullable Slot slot;

    public ArgParserContextualItemStack(ArgInfo argInfo) {
        super(argInfo);
        this.slot = slotOf(argInfo.getArgData().getContext());
    }

    @Override
    public ParseResult<ItemStack> parse(@Nonnull ContextualParseCall call) {

        MinecraftFPlayer player = (MinecraftFPlayer) call.getSender();

        if (slot == null){
            ItemStack itemStack = FCBukkitUtil.getPlayersHeldItem(player.getPlayer());

            return itemStack != null
                    ? ParseResult.of(itemStack)
                    : denied(FCMessageUtil.NEEDS_TO_BE_HOLDING_ITEM);
        }

        ItemStack equipped = equipmentOf(player.getPlayer().getInventory(), slot);

        return equipped != null
                ? ParseResult.of(equipped)
                : ParseResult.empty();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

    /** @return the equipment worn on {@code slot}, or null when it is empty or the server has no such slot */
    private static @Nullable ItemStack equipmentOf(PlayerInventory inventory, Slot slot) {
        switch (slot) {
            case HELMET:
                return worn(inventory.getHelmet());
            case CHESTPLATE:
                return worn(inventory.getChestplate());
            case LEGGINGS:
                return worn(inventory.getLeggings());
            case BOOTS:
                return worn(inventory.getBoots());
            case OFF_HAND:
                //The off hand landed in 1.9: on anything older the method is not on the server's
                //PlayerInventory at all, so it stays behind the guard instead of linking into a
                //NoSuchMethodError in the middle of a dispatch
                return MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3) ? null : worn(inventory.getItemInOffHand());
        }
        throw new IllegalStateException("The equipment slot [" + slot + "] has no way to be read");
    }

    /** An empty slot answers null on some versions and AIR on others; both mean nothing is worn. */
    private static @Nullable ItemStack worn(@Nullable ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR ? itemStack : null;
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

        //"a|b" is how the context of a TOKEN offers a choice; a parameter wears one piece of equipment,
        //so saying so beats letting the developer read the list and wonder which half was wrong
        String oneOnly = wanted.indexOf('|') < 0 ? ""
                : " A parameter reads exactly one piece of equipment, so a list of them means nothing here.";

        throw new ArgMountException("The context [" + wanted + "] on a contextual ItemStack names no equipment slot." + oneOnly
                + " Use one of " + accepted() + ", or leave context() empty for whatever the player is holding.");
    }

    private static String accepted() {
        return Arrays.stream(Slot.values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** @return the equipment this declaration resolved to, or null for whatever the player is holding */
    @Nullable Slot getSlot() {
        return slot;
    }
}
