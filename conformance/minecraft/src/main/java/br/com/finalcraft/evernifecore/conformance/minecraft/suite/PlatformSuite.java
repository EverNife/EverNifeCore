package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionGroupChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLoadFailedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRedefinedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import br.com.finalcraft.evernifecore.testing.EconomyConformance;
import br.com.finalcraft.evernifecore.testing.PlatformConformance;

import java.util.ArrayList;
import java.util.List;

/**
 * The conformance checks the headless suite runs against a double, run against the platform this server
 * registered - the only place a classpath probe that lies about the platform can be caught. One case per
 * violation line, so a report names each one.
 */
public final class PlatformSuite implements ConformanceSuite {

    @Override
    public String getId() {
        return "platform";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(ConformanceCase.of("platform", () -> fromViolations(PlatformConformance.check(EverNifeCore.getPlatform()),
                PlatformConformance.summarize(EverNifeCore.getPlatform()))));
        cases.add(ConformanceCase.of("region-events", () -> fromViolations(ECEventConformance.checkAll(
                RegionEnterEvent.class, RegionLeaveEvent.class, RegionCreatedEvent.class, RegionRemovedEvent.class,
                RegionRedefinedEvent.class, RegionFlagChangedEvent.class, RegionGroupChangedEvent.class,
                RegionChangedEvent.class, RegionLoadFailedEvent.class), "9 region events")));
        cases.add(ConformanceCase.of("economy", () -> {
            IEconomyProvider economy = EverNifeCore.getProviders().getEconomyOrNull();
            if (economy == null) {
                return Verdict.skipped("no economy provider on this server");
            }
            return fromViolations(EconomyConformance.check(economy), economy.getClass().getName());
        }));
        return cases;
    }

    private static Verdict fromViolations(List<String> violations, String subject) {
        return violations.isEmpty() ? Verdict.pass(subject) : Verdict.fail(violations.size() + " violation(s) on " + subject + ": "
                + String.join(" | ", violations));
    }
}
