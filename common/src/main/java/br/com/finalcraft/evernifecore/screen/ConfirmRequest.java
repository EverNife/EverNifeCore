package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Gate;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What a {@link ConfirmScreen} asks: the question (also the window title), what it is about, whether confirming takes a
 * shift-click, and - for a confirmation that charges - the {@link Gate} the confirm button pays and the effects it runs.
 */
public final class ConfirmRequest {

    private final String question;
    private final Gate gate;
    private final List<Effect> onConfirm;
    private ECPluginData owner;
    private ItemSpec display;
    private boolean dangerous;

    private ConfirmRequest(String question, @Nullable Gate gate, List<Effect> onConfirm) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("A confirmation has to say what it is asking about: the question is the title "
                    + "of the window, and without it the player reads two buttons and no reason.");
        }
        this.question = question;
        this.gate = gate;
        this.onConfirm = onConfirm;
    }

    /** Asks {@code question}; the answer is the whole effect. */
    @Nonnull
    public static ConfirmRequest of(@Nonnull String question) {
        return new ConfirmRequest(question, null, Collections.<Effect>emptyList());
    }

    /**
     * Asks {@code question}; confirming pays {@code gate} and runs {@code onConfirm}, and a gate that is not met keeps
     * the screen open and tells the viewer what is missing. The confirm button's lore reads {@code {cost}}.
     */
    @Nonnull
    public static ConfirmRequest of(@Nonnull String question, @Nonnull Gate gate, @Nonnull Effect... onConfirm) {
        return new ConfirmRequest(question, Objects.requireNonNull(gate, "gate"),
                Collections.unmodifiableList(Arrays.asList(onConfirm.clone())));
    }

    /** The plugin asking - the content owner an override of {@link ScreenKey#CONFIRM} matches on, and the gate's caller. */
    @Nonnull
    public ConfirmRequest owner(@Nullable ECPluginData owner) {
        this.owner = owner;
        return this;
    }

    /** Shows {@code item} in the middle of the screen - what the question is about. */
    @Nonnull
    public ConfirmRequest display(@Nullable ItemSpec item) {
        this.display = item;
        return this;
    }

    /** Makes confirming take a shift-click, for the answer that cannot be taken back. */
    @Nonnull
    public ConfirmRequest dangerous() {
        this.dangerous = true;
        return this;
    }

    @Nonnull
    public String getQuestion() {
        return question;
    }

    @Nullable
    public Gate getGate() {
        return gate;
    }

    @Nonnull
    public List<Effect> getOnConfirm() {
        return onConfirm;
    }

    @Nullable
    public ECPluginData getOwner() {
        return owner;
    }

    @Nullable
    public ItemSpec getDisplay() {
        return display;
    }

    public boolean isDangerous() {
        return dangerous;
    }

    @Override
    public String toString() {
        return "ConfirmRequest{'" + question + "'" + (gate == null ? "" : ", " + gate) + (dangerous ? ", dangerous" : "") + "}";
    }

}
