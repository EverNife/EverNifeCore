package br.com.finalcraft.evernifecore.conformance;

import java.util.Collections;
import java.util.List;

/**
 * A named group of {@link ConformanceCase}s. A suite whose subject has to survive a restart splits into a write
 * half, run before it, and a read half, run after; both report under the suite's own id.
 */
public interface ConformanceSuite {

    /** Bare id ({@code items}, {@code nbt}); the runner adds the {@code ecore-} prefix to the markers. */
    String getId();

    List<ConformanceCase> getCases(ConformanceContext ctx);

    default List<ConformanceCase> getWriteCases(ConformanceContext ctx) {
        return Collections.emptyList();
    }

    default List<ConformanceCase> getReadCases(ConformanceContext ctx) {
        return Collections.emptyList();
    }
}
