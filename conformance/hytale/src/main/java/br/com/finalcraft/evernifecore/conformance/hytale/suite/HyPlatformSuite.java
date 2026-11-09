package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.UnsupportedSourceException;
import br.com.finalcraft.evernifecore.action.event.CommandActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.hytale.action.HyActionSystems;
import br.com.finalcraft.evernifecore.hytale.loader.EverNifeCoreHytalePlugin;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import br.com.finalcraft.evernifecore.hytale.itemdatapart.datapart.ItemDataPartMetadata;
import br.com.finalcraft.evernifecore.hytale.itemstack.ComparableItemComplex;
import br.com.finalcraft.evernifecore.hytale.region.HyPlayerStateAccess;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.HoldOptions;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.testing.PlatformConformance;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Which platform the core detected on this server, that its region Systems went on it once, at setup, and that the
 * core's item metadata links against the server's own {@code org.bson}, and that a player's command reaches the core
 * as that player - and, Hytale having no command event, as a {@code CommandActionEvent} raised by {@code FinalCMD}.
 */
public final class HyPlatformSuite implements ConformanceSuite {

    private static final String ITEM = "Rock_Stone";
    private static final String METADATA = "{\"enc_conformance\": {\"mark\": 7}}";
    private static final String ARMOR_ITEM = "Armor_Iron_Chest";
    private static final short ARMOR_SLOT = 1;

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
                //what the headless suite holds a double to, held against this server's HyPlatform
                ConformanceCase.of("platform", () -> {
                    List<String> violations = PlatformConformance.check(EverNifeCore.getPlatform());
                    return Verdict.of(violations.isEmpty(), violations.isEmpty() ? "platform conformance: OK"
                            : violations.size() + " violation(s): " + String.join(" | ", violations));
                }),
                ConformanceCase.later("world-thread-next-tick", HyPlatformSuite::worldThreadNextTick),
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
                //Hytale has title, toast, tracker and sound; no action bar, no sidebar, no progress boss bar, no compass seam
                ConformanceCase.of("hud-capabilities", () -> {
                    Set<IHudAccess.Capability> expected = EnumSet.of(IHudAccess.Capability.TITLE, IHudAccess.Capability.TOAST,
                            IHudAccess.Capability.TRACKER, IHudAccess.Capability.SOUND);
                    Set<IHudAccess.Capability> answered = EnumSet.noneOf(IHudAccess.Capability.class);
                    for (IHudAccess.Capability capability : IHudAccess.Capability.values()) {
                        if (EverNifeCore.getPlatform().getHud().supports(capability)) {
                            answered.add(capability);
                        }
                    }
                    return Verdict.of(answered.equals(expected), "supports=" + answered + " expected=" + expected);
                }),
                //the core registered them in its setup; a second registration has to be refused, never stacked
                ConformanceCase.of("systems-registered-once", () -> {
                    boolean again = HyActionSystems.register(plugin);
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
                HyRegionsSuite.withPlayer("player-verb-by-sudo", ctx, this::playerVerbBySudo),
                HyRegionsSuite.withPlayer("held-inventory-comes-back-on-disconnect", ctx, HyPlatformSuite::heldInventoryComesBackOnDisconnect),
                ConformanceCase.later("held-inventory-real-disconnect", () -> realDisconnect != null ? realDisconnect
                        : CompletableFuture.completedFuture(Verdict.fail("the player never left: /ecc hold-quit arm, player_leave, player_join,"
                        + " /ecc hold-quit read, as the matrix driver does"))),
                //no command event on this server: the core posts COMMAND from its own executor, for its own commands
                HyRegionsSuite.withPlayer("command-source-via-finalcmd", ctx, this::commandSourceViaFinalCmd),
                ConformanceCase.of("fish-source-not-served", () -> {
                    boolean served = ActionSupport.isServed(EventSource.FISH);
                    String refusal = "";
                    try {
                        ActionSupport.requireServed(EventSource.FISH, false, "the conformance probe");
                    } catch (UnsupportedSourceException expected) {
                        refusal = expected.getMessage();
                    }
                    return Verdict.of(!served && refusal.contains("Hytale"), "served=" + served + " refusal=" + refusal);
                }),
                //money over VaultUnlocked when an economy is installed; without one a take answers UNAVAILABLE, never throws
                ConformanceCase.of("economy", () -> {
                    Currency money = Currencies.money();
                    CurrencyOwner unknown = CurrencyOwner.player(UUID.randomUUID());
                    if (money.isAvailable()) {
                        return Verdict.of(money.getBalance(unknown) != null && money.format(BigDecimal.TEN) != null,
                                "money over " + EverNifeCore.getProviders().getEconomyOrNull().getClass().getName());
                    }
                    EcoResponse take = money.take(unknown, BigDecimal.ONE, Caller.of(EverNifeCore.getEcPluginData(), "conformance/economy"));
                    return Verdict.of(take.getReason() == EcoResponse.Reason.UNAVAILABLE, "no economy: take=" + take);
                }));
    }

    /**
     * The disconnect's path with the player kept for the other cases: from a thread that is not the world's, where the
     * server dispatches {@code PlayerDisconnectEvent}, the give-back hops and is done on the entity before a task queued right
     * after it - the place of {@code Player.remove} in {@code Universe.removePlayer}. It lets go by {@code release} and not
     * {@code forget}, which would orphan the region session of the player who stays.
     */
    private static CompletableFuture<Verdict> heldInventoryComesBackOnDisconnect(FPlayer player) {
        PlayerRef ref = HyWorld.player(player);
        World world = HyWorld.worldOf(ref);
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        StateHolder holder = StateHolder.of("evernifecore:conformance-held-inventory");
        HeldPlayerState held = HeldPlayerState.of(player.getUniqueId());
        return HyWorld.on(world, () -> {
            String before = access.getInventory(player);
            Ref<EntityStore> entity = ref.getReference();
            giveRealItems(player);
            String own = access.getInventory(player);
            held.hold(HeldPlayerState.INVENTORY, holder, "", p -> access.setInventory(p, ""), HoldOptions.memory(), player);
            access.setInventory(player, "");
            return new String[]{before, own};
        }).thenCompose(inventories -> {
            CompletableFuture<Verdict> answer = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                HyPlayerStateAccess.runBeforeRemoval(ref, () -> held.release(HeldPlayerState.INVENTORY, holder, player));
                world.execute(() -> {
                    String after = access.getInventory(player);
                    access.setInventory(player, inventories[0]);
                    answer.complete(Verdict.of(after.equals(inventories[1]) && !inventories[1].equals(inventories[0]),
                            "own came back before the removal's place in the queue: " + after.equals(inventories[1])));
                });
            });
            return answer;
        });
    }

    /** A stack with metadata in the hotbar and a piece of armor: what a held inventory has to bring back whole. */
    private static void giveRealItems(FPlayer player) {
        HytaleFPlayer body = (HytaleFPlayer) player;
        body.getInventorySection(InventoryComponent.HOTBAR_SECTION_ID).addItemStack(new ItemStack(ITEM, 3).withMetadata(BsonDocument.parse(METADATA)));
        body.getInventorySection(InventoryComponent.ARMOR_SECTION_ID).setItemStackForSlot(ARMOR_SLOT, new ItemStack(ARMOR_ITEM, 1));
    }

    /** What the real disconnect of the player left to read when they are back; null until {@code /ecc hold-quit read}. */
    private static volatile CompletableFuture<Verdict> realDisconnect;
    private static volatile HeldQuit heldQuit;

    /** The inventories around one real disconnect, and the threads the two quit events came in. */
    private static final class HeldQuit {
        private String before;
        private String own;
        private volatile String disconnectThread;
        private volatile String quitThread;
        private volatile boolean quitOnWorld;
        private ECEventSubscription<ECPlayerQuitEvent> quit;
    }

    /** The player holds their inventory on a quit policy of giving it back, and the quit is watched: the next thing they do is really leave. */
    public static CompletableFuture<Void> armRealDisconnect(FPlayer player) {
        PlayerRef ref = HyWorld.player(player);
        World world = HyWorld.worldOf(ref);
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        HeldQuit armed = new HeldQuit();
        heldQuit = armed;
        EverNifeCoreHytalePlugin.instance.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (heldQuit == armed && armed.disconnectThread == null) {
                armed.disconnectThread = Thread.currentThread().getName();
            }
        });
        armed.quit = ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> {
            armed.quitThread = Thread.currentThread().getName();
            armed.quitOnWorld = world.isInThread();
        });
        return HyWorld.on(world, () -> {
            armed.before = access.getInventory(player);
            giveRealItems(player);
            armed.own = access.getInventory(player);
            HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, StateHolder.of("evernifecore:conformance-real-disconnect"), "",
                    p -> access.setInventory(p, ""), HoldOptions.memory(), player);
            access.setInventory(player, "");
            return null;
        });
    }

    /** The player is back: what they hold is what they had before the hold, and the quit took the world thread. */
    public static void readRealDisconnect(FPlayer player) {
        HeldQuit armed = heldQuit;
        if (armed == null) {
            realDisconnect = CompletableFuture.completedFuture(Verdict.fail("hold-quit read with nothing armed"));
            return;
        }
        armed.quit.unsubscribe();
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        World world = HyWorld.worldOf(HyWorld.player(player));
        realDisconnect = HyWorld.on(world, () -> {
            String after = access.getInventory(player);
            access.setInventory(player, armed.before);
            return Verdict.of(armed.disconnectThread != null && armed.quitOnWorld && after.equals(armed.own),
                    "PlayerDisconnectEvent on " + armed.disconnectThread + ", ECPlayerQuitEvent on " + armed.quitThread + " onWorld=" + armed.quitOnWorld
                            + " own-came-back=" + after.equals(armed.own) + (after.equals(armed.own) ? "" : " own=" + armed.own + " after=" + after));
        });
    }

    /**
     * From the default world's own thread, {@code runOnWorldThreadNextTick} must run its task on that thread in a later
     * tick - the world thread is where a same-tick run would be possible at all.
     */
    private static CompletableFuture<Verdict> worldThreadNextTick() {
        World world = HyWorld.getDefault();
        return HyWorld.on(world, () -> {
            long before = world.getTick();
            CompletableFuture<Verdict> answer = new CompletableFuture<>();
            EverNifeCore.getPlatform().runOnWorldThreadNextTick(world.getName(), () -> {
                long after = world.getTick();
                boolean onWorldThread = world.isInThread();
                answer.complete(Verdict.of(after > before && onWorldThread,
                        "world=" + world.getName() + " before=" + before + " after=" + after + " onWorldThread=" + onWorldThread));
            }).whenComplete((done, failed) -> {
                if (failed != null) {
                    answer.complete(Verdict.fail(Verdict.describe(failed)));
                }
            });
            return answer;
        }).thenCompose(answer -> answer)
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(failed -> Verdict.fail(Verdict.describe(failed instanceof CompletionException && failed.getCause() != null
                        ? failed.getCause() : failed)));
    }

    /** Runs {@code /ecc whoami} as {@code player} while subscribed to {@code CommandActionEvent.Post}: the post names /ecc. */
    private CompletableFuture<Verdict> commandSourceViaFinalCmd(FPlayer player) {
        CompletableFuture<String> heard = new CompletableFuture<>();
        ECEventSubscription<CommandActionEvent.Post> subscription = ECEventBus.global().subscribe(CommandActionEvent.Post.class, event -> {
            FPlayer who = event.getContext().getPlayer();
            if (who != null && who.getUniqueId().equals(player.getUniqueId()) && event.getContext().getNames().contains("ecc")) {
                heard.complete(event.getContext().getCommandLine());
            }
        });
        //the verdict of the run completes inside the command's body, and the Post follows the body: wait for it
        return playerVerbBySudo(player)
                .thenCompose(ran -> heard.completeOnTimeout("none", 5, TimeUnit.SECONDS)
                        .thenApply(line -> Verdict.of(ran.getKind() == Verdict.Kind.PASS && !line.equals("none"),
                                "command ran: " + ran.getDetail() + " | CommandActionEvent.Post=" + line)))
                .whenComplete((verdict, failed) -> subscription.unsubscribe());
    }

    /** Completes the {@code whoami} case waiting on {@code token}; a token nobody waits on is ignored. */
    public static void answerWhoami(String token, FPlayer caller) {
        CompletableFuture<FPlayer> waiting = WHOAMI.remove(token);
        if (waiting != null) {
            waiting.complete(caller);
        }
    }

    /**
     * Runs the player-only {@code /ecc whoami} as {@code player} through the server's {@code sudo}. The player gets only
     * the {@code @FinalCMD} permission (never op: op bypasses the regions the next suite measures) while the case runs;
     * the node the server would generate for the command is never granted, so it must not be asked.
     */
    private CompletableFuture<Verdict> playerVerbBySudo(FPlayer player) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<FPlayer> answered = new CompletableFuture<>();
        WHOAMI.put(token, answered);
        Set<String> grant = new HashSet<>();
        grant.add("evernifecore.conformance");
        AbstractCommand ecc = CommandManager.get().getCommandRegistration().get("ecc");
        if (ecc == null || ecc.getPermission() != null) {
            return CompletableFuture.completedFuture(Verdict.of(false, "the server asks its own node of /ecc too ("
                    + (ecc == null ? "no /ecc registered" : ecc.getPermission()) + "), which nobody is ever granted"));
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
