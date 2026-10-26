package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** The message a player gets on leaving the region. */
public final class FarewellFlag extends Flag<String> {

    public FarewellFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "farewell"), FlagTypes.TEXT, Collections.singleton(EventSource.MOVE), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }


    @Override
    public void onLeave(SessionContext context, String lastValue) {
        context.getPlayer().sendMessage(lastValue);
    }

    /** Crossing straight from one farewell to another says the one being left. */
    @Override
    public void onRevalidate(SessionContext context, @Nullable String oldValue, @Nullable String newValue) {
        if (oldValue != null) {
            context.getPlayer().sendMessage(oldValue);
        }
    }

    @Override
    public String getDescription() {
        return "The message a player gets on leaving the region.";
    }
}
