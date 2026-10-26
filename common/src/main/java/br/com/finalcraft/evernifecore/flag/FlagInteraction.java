package br.com.finalcraft.evernifecore.flag;

import java.util.Objects;

/**
 * Two flags that act on the same thing ({@link Flag#getChannels()}), and the rule between them - declared by one of
 * them in {@link Flag#getInteractions()}, required by {@link FlagRegistry#register} for every shared channel. The rule
 * itself lives in the flags' code; this is the declaration that it was thought about.
 */
public final class FlagInteraction {

    private final FlagKey other;
    private final String rule;

    private FlagInteraction(FlagKey other, String rule) {
        this.other = Objects.requireNonNull(other, "other");
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    /** @param rule one sentence for {@code region info} and the docs */
    public static FlagInteraction with(FlagKey other, String rule) {
        return new FlagInteraction(other, rule);
    }

    public FlagKey getOther() {
        return other;
    }

    public String getRule() {
        return rule;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FlagInteraction && other.equals(((FlagInteraction) o).other) && rule.equals(((FlagInteraction) o).rule);
    }

    @Override
    public int hashCode() {
        return 31 * other.hashCode() + rule.hashCode();
    }

    @Override
    public String toString() {
        return other + ": " + rule;
    }
}
