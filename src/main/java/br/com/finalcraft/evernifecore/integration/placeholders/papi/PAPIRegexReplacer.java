package br.com.finalcraft.evernifecore.integration.placeholders.papi;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;

public class PAPIRegexReplacer<P extends IPlayerData> {

    private final Class<P> referClass;
    private RegexReplacer<P> regexReplacer;

    public PAPIRegexReplacer(Class<P> referClass) {
        this.referClass = referClass;
        this.regexReplacer = new RegexReplacer<>();
    }

    public Class<P> getReferClass() {
        return referClass;
    }

    public RegexReplacer<P> getRegexReplacer() {
        return regexReplacer;
    }
}
