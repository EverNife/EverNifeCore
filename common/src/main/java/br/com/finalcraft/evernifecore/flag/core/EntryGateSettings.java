package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.everyconfig.annotation.Comment;
import jakarta.annotation.Nullable;

/** The settings of {@code entry-gate}: those of {@code deny-entry}, plus when the gate charges its costs. */
public class EntryGateSettings extends EntryDenySettings {

    /** When the gate charges its costs. */
    public enum Charge {
        /** Once per login, while the server runs. */
        ONCE_PER_SESSION,
        /** On every crossing in. */
        EVERY_ENTRY,
        /** Once per {@link #getWindow() window}. */
        PER_WINDOW
    }

    @Comment({"When the gate charges its costs: ONCE_PER_SESSION (once per login while the server runs), EVERY_ENTRY (every",
            "crossing in), PER_WINDOW (once per window below). The conditions are checked on every entry either way."})
    private Charge charge = Charge.ONCE_PER_SESSION;

    @Comment("The window of PER_WINDOW, e.g. 'daily at 00:00' or 'weekly on monday at 00:00'; ignored otherwise.")
    private @Nullable String window;

    public Charge getCharge() {
        return charge;
    }

    public void setCharge(Charge charge) {
        this.charge = charge;
    }

    public @Nullable String getWindow() {
        return window;
    }

    public void setWindow(@Nullable String window) {
        this.window = window;
    }
}
