package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.HoldOptions;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.QuitPolicy;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import org.bson.BsonDocument;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code INVENTORY} slot held persistently over a real restart. The window of the quit policy makes the stop keep the hold
 * instead of giving it back, so only the player's backup can bring the items back on the join after the boot. The write half
 * leaves on disk what the read half judges: this JVM's start, what the inventory showed during the hold, and the items held.
 */
final class HeldInventoryRestartCases {

    private static final String TOKEN_FILE = "held-inventory-restart.token";
    private static final StateHolder HOLDER = StateHolder.of("evernifecore:conformance-held-inventory-restart");
    private static final String ITEM = "Rock_Stone";
    private static final String ARMOR_ITEM = "Armor_Iron_Chest";
    private static final short ARMOR_SLOT = 1;
    private static final String METADATA = "{\"enc_conformance\": {\"held_over_restart\": 11}}";
    private static final int[] SECTIONS = {InventoryComponent.HOTBAR_SECTION_ID, InventoryComponent.STORAGE_SECTION_ID, InventoryComponent.ARMOR_SECTION_ID,
            InventoryComponent.UTILITY_SECTION_ID, InventoryComponent.TOOLS_SECTION_ID, InventoryComponent.BACKPACK_SECTION_ID};
    /** The give-back runs once the joined player's entity is writable; together well inside the case's own timeout. */
    private static final int GIVE_BACK_POLLS = 100;
    private static final int POLL_TICKS = 5;

    private HeldInventoryRestartCases() {
    }

    static ConformanceCase write(ConformanceContext ctx) {
        return HyRegionsSuite.withPlayer("held-inventory-write", ctx, player -> {
            IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
            HytaleFPlayer body = (HytaleFPlayer) player;
            return HyWorld.on(worldOf(player), () -> {
                List<String> others = slots(body);
                body.getInventorySection(InventoryComponent.HOTBAR_SECTION_ID).addItemStack(new ItemStack(ITEM, 5).withMetadata(BsonDocument.parse(METADATA)));
                body.getInventorySection(InventoryComponent.ARMOR_SECTION_ID).setItemStackForSlot(ARMOR_SLOT, new ItemStack(ARMOR_ITEM, 1));
                List<String> own = slots(body);
                own.removeAll(others);
                HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, HOLDER, "", p -> access.setInventory(p, ""),
                        HoldOptions.persistent().onQuit(QuitPolicy.keepFor(Duration.ofHours(1))), player);
                access.setInventory(player, "");
                List<String> during = slots(body);

                List<String> token = new ArrayList<>();
                token.add(String.valueOf(jvmStart()));
                token.add(String.join(" ", during));
                token.addAll(own);
                Files.write(tokenFile(ctx).toPath(), token, StandardCharsets.UTF_8);
                return Verdict.of(own.size() == 2 && during.isEmpty(), "held " + own + " beside " + others.size() + " other slots, during=" + during);
            });
        });
    }

    static ConformanceCase read(ConformanceContext ctx) {
        return HyRegionsSuite.withPlayer("held-inventory-read", ctx, player -> {
            File token = tokenFile(ctx);
            if (!token.isFile()) {
                return CompletableFuture.completedFuture(Verdict.fail("no " + TOKEN_FILE + " - the write half never ran here"));
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(token.toPath(), StandardCharsets.UTF_8);
            } catch (IOException failed) {
                return CompletableFuture.completedFuture(Verdict.fail(Verdict.describe(failed)));
            }
            long writtenBy = Long.parseLong(lines.get(0));
            String during = lines.get(1);
            List<String> own = lines.subList(2, lines.size());
            return awaitGiveBack((HytaleFPlayer) player, worldOf(player), own, GIVE_BACK_POLLS).thenApply(now -> {
                boolean restarted = writtenBy != jvmStart();
                return Verdict.of(restarted && during.isEmpty() && !own.isEmpty() && now.containsAll(own),
                        "came back=" + own + " during=[" + during + "] restarted=" + restarted + " (server start " + writtenBy + " -> " + jvmStart()
                                + ") after-join=" + now);
            });
        });
    }

    /** What this case held has to be back; the other slots are not its to judge, a login may also claim a delivery into them. */
    private static CompletableFuture<List<String>> awaitGiveBack(HytaleFPlayer player, World world, List<String> own, int left) {
        return HyWorld.on(world, () -> slots(player)).thenCompose(now -> now.containsAll(own) || left == 0
                ? CompletableFuture.completedFuture(now)
                : HyWorld.ticks(world, POLL_TICKS).thenCompose(waited -> awaitGiveBack(player, world, own, left - 1)));
    }

    private static World worldOf(FPlayer player) {
        return HyWorld.worldOf(HyWorld.player(player));
    }

    /** A plugin reload keeps it; only a new process moves it. */
    private static long jvmStart() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private static File tokenFile(ConformanceContext ctx) {
        return new File(ctx.getDataFolder(), TOKEN_FILE);
    }

    private static List<String> slots(HytaleFPlayer player) {
        List<String> slots = new ArrayList<>();
        for (int section : SECTIONS) {
            ItemContainer container = player.getInventorySection(section);
            if (container == null) {
                continue;
            }
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack != null && !stack.isEmpty()) {
                    slots.add(section + "/" + slot + ":" + stack.getItemId() + "x" + stack.getQuantity() + (stack.getMetadata() == null ? "" : ":" + stack.getMetadata().toJson()));
                }
            }
        }
        return slots;
    }
}
