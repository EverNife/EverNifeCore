package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.PartRefusal;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A part as an engine holds it: the registration, whether this runtime lets it work, and - only if
 * it does - the single instance that answers for it.
 *
 * <p>A refused registration is kept, never dropped. That is what turns a line naming it into a
 * capability answer instead of the old "Mistake in Config", which blamed the file for a limit of
 * the server.</p>
 */
public final class RegisteredPart {

    private final PartRegistration registration;
    private final ItemDataPart<?> part;
    private final String refusal;

    RegisteredPart(@Nonnull PartRegistration registration, @Nonnull ItemRuntime runtime) {
        this.registration = registration;
        String gap = registration.getRequirement().explain(runtime);
        if (gap == null) {
            this.part = registration.newPart();
            this.refusal = null;
        } else {
            //never instantiated: the class may name api this runtime does not have
            this.part = null;
            this.refusal = registration.getHint() == null ? gap : gap + ". " + registration.getHint();
        }
    }

    @Nonnull
    public String getKey() {
        return registration.getKey();
    }

    @Nonnull
    public String[] getSpellings() {
        return registration.getSpellings();
    }

    /** Whether {@code spelling} is the canonical key or one of the aliases, ignoring case. */
    public boolean answersTo(@Nullable String spelling) {
        if (spelling == null) {
            return false;
        }
        for (String known : registration.getSpellings()) {
            if (known.equalsIgnoreCase(spelling)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActive() {
        return part != null;
    }

    /** Why this runtime refused it, or {@code null} when it did not. */
    @Nullable
    public PartRefusal getRefusal() {
        return refusal == null ? null : new PartRefusal(getKey(), refusal);
    }

    /**
     * The one instance answering for this concept.
     *
     * @throws IllegalStateException when this runtime refused it - ask {@link #isActive()} first
     */
    @Nonnull
    public ItemDataPart<?> getPart() {
        if (part == null) {
            throw new IllegalStateException("The part '" + getKey() + "' is not active on this runtime: "
                    + refusal + ". Check isActive() before asking for it, or read getRefusal() to say why.");
        }
        return part;
    }

    /** Where this part sits in the application order, or last when it is refused. */
    public int getPriority() {
        return part == null ? ItemDataPart.PRIORITY_VERY_LATE : part.getPriority();
    }

    @Override
    public String toString() {
        return "RegisteredPart{" + getKey() + (isActive() ? ", active" : ", refused: " + refusal) + "}";
    }

}
