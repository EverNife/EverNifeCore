package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * A supplier of the boosters that count for a context: a permission, a region flag, a plugin's {@link BoosterManager}.
 * Added with {@link Boosters#addSource}. Any thread; it must not block.
 */
public interface BoosterSource {

    /** {@code evernifecore:permission}, {@code evernifecore:region}, {@code <plugin>:manager}. */
    String getId();

    /**
     * The boosters of {@code domain} that count for {@code ctx}; inactive ones, or ones of another domain, may be
     * included, {@link BoosterDomain#resolve} drops them.
     */
    Collection<Booster> boostersFor(ExecutionContext ctx, BoosterDomain domain);

    /**
     * Why this source could not read what it needs for {@code ctx} - an offline player's permissions not loaded - so
     * {@link BoosterResult#explain()} can say a booster may be missing; null when it read everything. Must not block.
     */
    default @Nullable String getUnreadReason(ExecutionContext ctx, BoosterDomain domain) {
        return null;
    }
}
