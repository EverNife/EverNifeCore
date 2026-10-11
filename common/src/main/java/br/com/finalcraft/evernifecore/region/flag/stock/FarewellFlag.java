package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.SessionContext;
import jakarta.annotation.Nullable;

import java.util.EnumSet;
import java.util.List;

/** The message a player gets on leaving the region. */
public final class FarewellFlag extends RegionFlag<String> {

    public FarewellFlag(ECPluginData owner) {
        super(FlagKey.of(owner, "farewell"), FlagTypes.TEXT, EnumSet.of(EventSource.MOVE), null);
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
