package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;

import java.util.EnumSet;

/** The shape of every stock {@code deny-*} flag: default FALSE, TRUE wins a tie, TRUE denies. */
abstract class StockDenyFlag extends TristateFlag {

    StockDenyFlag(ECPluginData owner, String name, EventSource source) {
        super(FlagKey.of(owner, name), EnumSet.of(source), Tristate.FALSE, Tristate.TRUE);
    }

    @Override
    public Decision decide(ActionContext context, Tristate value) {
        return value == Tristate.TRUE ? Decision.DENY : Decision.PASS;
    }
}
