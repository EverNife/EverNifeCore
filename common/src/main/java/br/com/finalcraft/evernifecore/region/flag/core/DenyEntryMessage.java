package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.Tristate;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** The value of {@link DenyEntryWithMessageFlag}: whether entry is denied, and what the player is told. */
public final class DenyEntryMessage {

    private Tristate denied = Tristate.UNDEFINED;
    private @Nullable String message;

    public DenyEntryMessage() {
    }

    public DenyEntryMessage(Tristate denied, @Nullable String message) {
        this.denied = denied;
        this.message = message;
    }

    public Tristate getDenied() {
        return denied;
    }

    public void setDenied(Tristate denied) {
        this.denied = denied;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DenyEntryMessage && denied == ((DenyEntryMessage) other).denied
                && Objects.equals(message, ((DenyEntryMessage) other).message);
    }

    @Override
    public int hashCode() {
        return 31 * denied.hashCode() + Objects.hashCode(message);
    }

    @Override
    public String toString() {
        return "denied=" + denied + " message=" + message;
    }
}
