package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.hytale.region.HyRegionSystems;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import com.hypixel.hytale.server.core.plugin.PluginBase;

import java.util.Arrays;
import java.util.List;

/** Which platform the core detected on this server, and that its region Systems went on it once, at setup. */
public final class HyPlatformSuite implements ConformanceSuite {

    private final PluginBase plugin;

    public HyPlatformSuite(PluginBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "platform";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("platform-is-hytale", () -> {
                    String platform = EverNifeCore.getPlatform().getClass().getSimpleName();
                    return Verdict.of(FCPlatformType.isHytale() && "HyPlatform".equals(platform),
                            "type=" + FCPlatformType.getCurrent() + " platform=" + platform);
                }),
                ConformanceCase.of("region-spis-are-real", () -> {
                    ISelectionItems items = EverNifeCore.getPlatform().getSelectionItems();
                    IOutlineRenderer outlines = EverNifeCore.getPlatform().getOutlineRenderer();
                    return Verdict.of(items != ISelectionItems.NONE && outlines != IOutlineRenderer.NONE,
                            "selectionItems=" + items.getClass().getSimpleName() + " outlineRenderer=" + outlines.getClass().getSimpleName());
                }),
                //the core registered them in its setup; a second registration has to be refused, never stacked
                ConformanceCase.of("systems-registered-once", () -> {
                    boolean again = HyRegionSystems.register(plugin);
                    return Verdict.of(!again, "secondRegisterAccepted=" + again);
                }));
    }
}
