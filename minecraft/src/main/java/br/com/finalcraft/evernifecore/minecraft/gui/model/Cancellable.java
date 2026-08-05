package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * Undoes one registration - a scheduled repetition, a state subscription. Calling {@link #cancel()}
 * more than once is a no-op.
 */
public interface Cancellable {

    Cancellable NONE = new Cancellable() {
        @Override
        public void cancel() {

        }
    };

    void cancel();

}
