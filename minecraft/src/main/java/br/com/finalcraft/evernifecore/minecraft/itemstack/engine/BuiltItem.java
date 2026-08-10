package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.IncompleteItemException;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineProblem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.RefusedEdit;
import jakarta.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The item a recipe produced here, plus everything this runtime would not do - the building border.
 *
 * <p>{@code build()} hands back the stack and nothing else, because most callers want an item and
 * would only drop the rest. Whoever needs to know that the item came out reduced asks this instead,
 * and whoever cannot accept a reduced item calls {@link #requireComplete()}.</p>
 */
public final class BuiltItem {

    private final ItemStack itemStack;
    private final List<RefusedEdit> refused;
    private final List<ItemLineProblem> problems;

    BuiltItem(@Nonnull ItemStack itemStack, @Nonnull List<RefusedEdit> refused,
              @Nonnull List<ItemLineProblem> problems) {
        this.itemStack = itemStack;
        this.refused = Collections.unmodifiableList(new ArrayList<>(refused));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    /** What this runtime could build, always an item - never null, never an exception. */
    @Nonnull
    public ItemStack getItemStack() {
        return itemStack;
    }

    /** Edits this runtime refused, each naming the part and the gap. */
    @Nonnull
    public List<RefusedEdit> getRefused() {
        return refused;
    }

    /** Lines of the source block that could not be read - empty for a recipe built in code. */
    @Nonnull
    public List<ItemLineProblem> getProblems() {
        return problems;
    }

    /** Whether the item is everything the recipe asked for. */
    public boolean isComplete() {
        return refused.isEmpty() && problems.isEmpty();
    }

    /**
     * The strict door: the item, or an exception naming everything that is missing from it.
     *
     * @throws IncompleteItemException when this runtime, or the source block, left something out
     */
    @Nonnull
    public ItemStack requireComplete() {
        if (isComplete()) {
            return itemStack;
        }
        StringBuilder message = new StringBuilder();
        if (!refused.isEmpty()) {
            //each refusal carries its own reason: one reason quoted for all of them sends whoever
            //reads it to fix a gap the other edits never had
            List<String> named = new ArrayList<>();
            for (RefusedEdit edit : refused) {
                named.add(edit.toString());
            }
            message.append("This runtime could not apply ").append(String.join("; ", named))
                    .append(". It is not a mistake in your config. Run on a server that satisfies it, ")
                    .append("or take the reduced item from getItemStack().");
        }
        if (!problems.isEmpty()) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append("These lines could not be read: ").append(problems)
                    .append(". Fix them in the file, or take the item built from the rest via getItemStack().");
        }
        throw new IncompleteItemException(message.toString());
    }

    @Override
    public String toString() {
        return "BuiltItem{" + itemStack.getType() + (isComplete() ? "" : ", refused=" + refused
                + (problems.isEmpty() ? "" : ", problems=" + problems)) + "}";
    }

}
