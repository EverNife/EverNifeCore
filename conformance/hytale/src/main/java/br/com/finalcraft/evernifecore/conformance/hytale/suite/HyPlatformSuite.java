package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.hytale.itemdatapart.datapart.ItemDataPartMetadata;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.hytale.region.HyRegionSystems;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which platform the core detected on this server, that its region Systems went on it once, at setup, and that the
 * core's item metadata links against the server's own {@code org.bson}.
 */
public final class HyPlatformSuite implements ConformanceSuite {

    private static final String ITEM = "Rock_Stone";
    private static final String METADATA = "{\"enc_conformance\": {\"mark\": 7}}";

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
                }),
                //metadata goes through the server's BsonDocument; a relocated copy would die on NoSuchMethodError here
                ConformanceCase.of("metadata-datapart-roundtrip", () -> {
                    ItemDataPartMetadata part = new ItemDataPartMetadata();
                    ItemStack tagged = part.transform(new ItemStack(ITEM), "metadata", METADATA);
                    String read = part.read(tagged, new ArrayList<>()).get(0);
                    boolean similar = part.isSimilar(tagged, part.transform(new ItemStack(ITEM), "metadata", METADATA));
                    return Verdict.of(read.equals("metadata:" + BsonDocument.parse(METADATA).toJson()) && similar,
                            "read=" + read + " similar=" + similar);
                }),
                ConformanceCase.of("metadata-comparable-complex", () -> {
                    ItemStack tagged = new ItemStack(ITEM).withMetadata(BsonDocument.parse(METADATA));
                    ComparableItemComplex comparable = new ComparableItemComplex(tagged);
                    boolean matchesTagged = comparable.match(tagged);
                    boolean matchesPlain = comparable.match(new ItemStack(ITEM));
                    return Verdict.of(matchesTagged && !matchesPlain && comparable.getMetadata().containsKey("enc_conformance"),
                            "matchesTagged=" + matchesTagged + " matchesPlain=" + matchesPlain + " metadata=" + comparable.getMetadata().toJson());
                }));
    }
}
