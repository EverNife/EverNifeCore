package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import jakarta.annotation.Nullable;

import java.util.EnumSet;
import java.util.List;

/** The message a player gets on entering the region. */
public final class GreetingFlag extends RegionFlag<String> {

    public GreetingFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "greeting"), FlagTypes.TEXT, EnumSet.of(EventSource.MOVE), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.SESSION;
    }

    /** The first. */
    @Override
    public String resolveTie(List<String> values) {
        return values.get(0);
    }

    @Override
    public void onEnter(SessionContext context, String value) {
        context.getPlayer().sendMessage(value);
    }

    /** Crossing straight from one greeting to another greets again, with the new one. */
    @Override
    public void onRevalidate(SessionContext context, @Nullable String oldValue, @Nullable String newValue) {
        if (newValue != null) {
            context.getPlayer().sendMessage(newValue);
        }
    }

    @Override
    public String getDescription() {
        return "The message a player gets on entering the region.";
    }
}
