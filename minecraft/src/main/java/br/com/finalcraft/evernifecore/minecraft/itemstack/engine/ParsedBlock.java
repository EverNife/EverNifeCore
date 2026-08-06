package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineProblem;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A block of item-data lines after reading: what it asks for, and what could not be read. */
public final class ParsedBlock {

    private final List<ItemEdit> edits;
    private final List<ItemLineProblem> problems;

    ParsedBlock(@Nonnull List<ItemEdit> edits, @Nonnull List<ItemLineProblem> problems) {
        this.edits = Collections.unmodifiableList(new ArrayList<>(edits));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    @Nonnull
    public List<ItemEdit> getEdits() {
        return edits;
    }

    /** Lines that said nothing usable. Each cost itself and left the rest of the block standing. */
    @Nonnull
    public List<ItemLineProblem> getProblems() {
        return problems;
    }

}
