package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;

import java.util.Collection;

/**
 * A supplier of the boosters that count for a context: a permission, a region flag, a plugin's {@link BoosterManager}.
 * Added with {@link Boosters#addSource}. Any thread; it must not block.
 */
public interface BoosterSource {

    /** {@code evernifecore:permission}, {@code evernifecore:region}, {@code <plugin>:manager}. */
    String getId();

    /**
     * The boosters of {@code variableId} (a full {@code owner:name} id) that count for {@code ctx}; inactive ones may be
     * included, {@link Boosters#resolve} drops them.
     */
    Collection<Booster> boostersFor(ExecutionContext ctx, String variableId);
}
