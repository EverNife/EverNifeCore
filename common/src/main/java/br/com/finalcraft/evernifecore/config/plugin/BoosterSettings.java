package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The booster block a plugin keeps as the {@value #SECTION} section of its own {@code config.yml}: the permission
 * factors of each of its {@link BoosterDomain}s, keyed by domain name. The plugin seeds its domains with
 * {@link #addDomain} and declares each one from the block with
 * {@link BoosterDomain.Builder#permission(String, BoosterSettings)}; it may extend the block with keys of its own.
 */
public class BoosterSettings {

    public static final String SECTION = "boosters";

    // the binder writes no comment inside a map entry, so the entry's key is explained here
    @Comment({"Each booster domain of this plugin, by name. permission-factors: the multipliers a permission grants - a",
            "player holding <prefix>.<factor> (the plugin names the prefix) is boosted by that factor, the largest held",
            "counts; empty, no permission boosts the domain. Read while the plugin enables."})
    @JsonProperty
    private Map<String, Domain> domains = new LinkedHashMap<>();

    /** Seeds the domain {@code name} with {@code factors}: what a file without it gets. */
    public BoosterSettings addDomain(String name, double... factors) {
        Domain domain = new Domain();
        for (double factor : factors) {
            domain.permissionFactors.add(factor);
        }
        domains.put(name, domain);
        return this;
    }

    /** The domain {@code name}; null when the block has none. */
    @JsonIgnore
    public @Nullable Domain getDomain(String name) {
        return domains.get(name);
    }

    @JsonIgnore
    public Map<String, Domain> getDomains() {
        return domains;
    }

    @JsonIgnore
    public void setDomains(Map<String, Domain> domains) {
        this.domains = domains;
    }

    /** One domain of the block. */
    public static class Domain {

        @Key("permission-factors")
        @JsonProperty
        private List<Double> permissionFactors = new ArrayList<>();

        @JsonIgnore
        public List<Double> getPermissionFactors() {
            return permissionFactors;
        }

        @JsonIgnore
        public void setPermissionFactors(List<Double> permissionFactors) {
            this.permissionFactors = permissionFactors;
        }
    }
}
