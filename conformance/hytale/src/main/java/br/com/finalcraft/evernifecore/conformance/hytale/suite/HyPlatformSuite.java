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
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which platform the core detected on this server, that its region Systems went on it once, at setup, and that the
 * core's item metadata links against the server's own {@code org.bson}, and that a player's command reaches the core
 * as that player.
 */
public final class HyPlatformSuite implements ConformanceSuite {

    private static final String ITEM = "Rock_Stone";
    private static final String METADATA = "{\"enc_conformance\": {\"mark\": 7}}";

    /** The callers of {@code /ecc whoami <token>}, by token, for the case that runs it through {@code sudo}. */
    private static final Map<String, CompletableFuture<FPlayer>> WHOAMI = new ConcurrentHashMap<>();

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
                }),
                //the serialized line has to bring the metadata back: ItemStack is immutable, withMetadata returns a copy
                ConformanceCase.of("metadata-comparable-deserialize", () -> {
                    ItemStack tagged = new ItemStack(ITEM).withMetadata(BsonDocument.parse(METADATA));
                    String serialized = new ComparableItemComplex(tagged).serialize();
                    ComparableItemComplex back = ComparableItemComplex.deserialize(serialized);
                    boolean matchesTagged = back.match(tagged);
                    String plain = new ComparableItemComplex(new ItemStack(ITEM)).serialize();
                    boolean plainBack = ComparableItemComplex.deserialize(plain).match(new ItemStack(ITEM));
                    return Verdict.of(matchesTagged && plainBack, "serialized=" + serialized + " matchesTagged=" + matchesTagged
                            + " metadata=" + back.getMetadata() + " plain=" + plain + " plainBack=" + plainBack);
                }),
                //the server dispatches a player's command (chat or sudo) with the PlayerRef as its sender
                HyRegionsSuite.withPlayer("player-verb-by-sudo", ctx, this::playerVerbBySudo));
    }

    /** Completes the {@code whoami} case waiting on {@code token}; a token nobody waits on is ignored. */
    public static void answerWhoami(String token, FPlayer caller) {
        CompletableFuture<FPlayer> waiting = WHOAMI.remove(token);
        if (waiting != null) {
            waiting.complete(caller);
        }
    }

    /**
     * Runs the player-only {@code /ecc whoami} as {@code player} through the server's {@code sudo}. The player gets the
     * command's permissions (never op: op bypasses the regions the next suite measures) only while the case runs.
     */
    private CompletableFuture<Verdict> playerVerbBySudo(FPlayer player) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<FPlayer> answered = new CompletableFuture<>();
        WHOAMI.put(token, answered);
        Set<String> grant = new HashSet<>();
        grant.add("evernifecore.conformance");
        AbstractCommand ecc = CommandManager.get().getCommandRegistration().get("ecc");
        if (ecc != null && ecc.getPermission() != null) {
            grant.add(ecc.getPermission());
        }
        PermissionsModule.get().addUserPermission(player.getUniqueId(), grant);
        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, "sudo " + player.getName() + " ecc whoami " + token);
        return answered.thenApply(caller -> Verdict.of(caller.getUniqueId().equals(player.getUniqueId()),
                        "caller=" + caller.getName() + " as=" + caller.getClass().getSimpleName() + " granted=" + grant))
                .whenComplete((verdict, failed) -> {
                    WHOAMI.remove(token);
                    PermissionsModule.get().removeUserPermission(player.getUniqueId(), grant);
                });
    }
}
