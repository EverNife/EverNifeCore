package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.PartFailure;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.PartRefusal;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an item said about itself, plus everything that went unanswered - the reading border.
 *
 * <p>The lines alone cannot tell a complete description from a truncated one, which is how a config
 * used to be re-saved with data silently missing. Here the two live side by side: whatever could be
 * read, and what could not, named.</p>
 */
public final class ItemDescription {

    private final List<String> lines;
    private final List<PartRefusal> refusals;
    private final List<PartFailure> failures;

    ItemDescription(@Nonnull List<String> lines, @Nonnull List<PartRefusal> refusals,
                    @Nonnull List<PartFailure> failures) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.refusals = Collections.unmodifiableList(new ArrayList<>(refusals));
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }

    /** The item-data block, in the canonical spelling - the exact text that rebuilds the item. */
    @Nonnull
    public List<String> getLines() {
        return lines;
    }

    /** Parts this runtime cannot answer for. Their concepts may be on the item and unreported. */
    @Nonnull
    public List<PartRefusal> getRefusals() {
        return refusals;
    }

    /** Parts that broke while reading. Each is a defect to report, not a gap to live with. */
    @Nonnull
    public List<PartFailure> getFailures() {
        return failures;
    }

    /** Whether every part answered, which is the only case where these lines are the whole item. */
    public boolean isComplete() {
        return refusals.isEmpty() && failures.isEmpty();
    }

    /** Everything missing from {@link #getLines()}, as one sentence, for a log or a message. */
    @Nonnull
    public String describeGaps() {
        List<String> gaps = new ArrayList<>();
        for (PartRefusal refusal : refusals) {
            gaps.add(refusal.toString());
        }
        for (PartFailure failure : failures) {
            gaps.add(failure.getKey() + " (broke: " + failure.getDefect() + ")");
        }
        return String.join(", ", gaps);
    }

    @Override
    public String toString() {
        return "ItemDescription" + lines + (isComplete() ? "" : " missing[" + describeGaps() + "]");
    }

}
