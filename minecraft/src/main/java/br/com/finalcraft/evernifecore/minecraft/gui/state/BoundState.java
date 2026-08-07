package br.com.finalcraft.evernifecore.minecraft.gui.state;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A state whose value lives somewhere else - a {@code PDSection} field, a service, any pair of
 * functions. Every read asks the getter, so a value someone changed behind the screen's back is never
 * stale, and every write goes straight to the setter.
 *
 * <p>The gui knows nothing about storage. The setter is what calls {@code markDirty()}, and the
 * persistence layer flushes on its own schedule - which is what makes a tab, a filter or a sort order
 * still be there the next time the menu opens.</p>
 */
public class BoundState<T> extends MutableState<T> {

    private final Supplier<T> getter;
    private final Consumer<T> setter;

    public BoundState(Supplier<T> getter, Consumer<T> setter) {
        super(null);
        if (getter == null || setter == null) {
            throw new IllegalArgumentException("A bound state needs both a getter and a setter: it holds "
                    + "no value of its own, it only reads and writes someone else's. State.of(...) is the "
                    + "form that owns its value.");
        }
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public T get() {
        return getter.get();
    }

    @Override
    public void set(T value) {
        setter.accept(value);
        invalidate();
    }

    @Override
    public String toString() {
        return "BoundState{" + get() + "}";
    }

}
