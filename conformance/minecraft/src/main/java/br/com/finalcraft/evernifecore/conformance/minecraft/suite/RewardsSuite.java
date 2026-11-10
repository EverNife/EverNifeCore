package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.reward.PhasedRewardTable;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardOccasion;
import br.com.finalcraft.evernifecore.logic.reward.RewardReceipt;
import br.com.finalcraft.evernifecore.logic.reward.RewardScene;
import br.com.finalcraft.evernifecore.logic.reward.command.RewardCommandNode;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rewards on a Bukkit server, with the bot as the one participant of stage 5 of {@value #OCCASION}: {@code /ecc rewards
 * simulate} shows the receipt of the table in {@code rewards/ecc-stage.yml} and applies nothing; paying it gives the bot
 * the stone the receipt promises and runs the {@code once} command one time. No money: no matrix server has an economy.
 */
public final class RewardsSuite implements ConformanceSuite {

    public static final String OCCASION = "ecc-stage";
    private static final int STAGE = 5;
    private static final int STONE = 3;
    private static final String PROBED_NAME = "stage-" + STAGE;
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final long SETTLE_TICKS = 10;
    private static final List<String> PROBED = new CopyOnWriteArrayList<>();

    private final ECPluginData plugin;
    private final RewardCommandNode node;
    private final JsonNode rewards = readTable();
    private RewardOccasion occasion;

    public RewardsSuite(ECPluginData plugin) {
        this.plugin = plugin;
        this.node = new RewardCommandNode(plugin);
    }

    /** What {@code /ecc rewards} mounts. */
    public RewardCommandNode getNode() {
        return node;
    }

    /** What {@code /ecc reward-probe} calls: the once step's command reached the server under {@code name}. */
    public static void answerProbe(String name) {
        PROBED.add(name);
    }

    @Override
    public String getId() {
        return "rewards";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.later("simulate-applies-nothing", ctx, player -> PlayerCases.within(simulate(player), TIMEOUT_MILLIS)),
                PlayerCases.later("stage-pays-the-receipt", ctx, player -> PlayerCases.within(pay(player), TIMEOUT_MILLIS)));
    }

    private CompletableFuture<Verdict> simulate(Player player) {
        RewardOccasion stage = occasionFor(player);
        PROBED.clear();
        int before = stoneOf(player);
        Capture sender = new Capture();
        node.simulate(sender, stage.getId(), STAGE);
        List<String> lines = sender.getPlainLines();
        long botLines = lines.stream().filter(line -> line.contains(player.getName() + ":") && line.endsWith("online")).count();
        boolean onceShown = lines.stream().anyMatch(line -> line.contains("#2 broadcast"));
        boolean header = !lines.isEmpty() && lines.get(0).contains(OCCASION);
        return PlayerCases.afterTicks(SETTLE_TICKS, () -> {
            int after = stoneOf(player);
            return Verdict.of(header && botLines == 2 && onceShown && after == before && PROBED.isEmpty(),
                    "bot-lines=" + botLines + " once-shown=" + onceShown + " stone " + before + "->" + after + " probed=" + PROBED
                            + " said=" + lines);
        });
    }

    private CompletableFuture<Verdict> pay(Player player) {
        RewardOccasion stage = occasionFor(player);
        PROBED.clear();
        int before = stoneOf(player);
        RewardScene scene = RewardScene.builder(stage).participant(player.getUniqueId(), player.getName(), BigDecimal.ONE, "alive")
                .variable("stage", STAGE).build();
        RewardContext ctx = RewardContext.builder(Caller.of(plugin, "conformance/rewards"), ProgressOwner.global()).scene(scene).build();
        UUID bot = player.getUniqueId();
        return PhasedRewardTable.parse(plugin, stage, rewards).getTable(STAGE).pay(ctx)
                .thenCompose(receipt -> PlayerCases.afterTicks(SETTLE_TICKS, () -> {
                    int after = stoneOf(player);
                    if (after > before) player.getInventory().removeItem(new ItemStack(Material.STONE, after - before));
                    boolean receiptOk = status(receipt, 0, bot) == RewardReceipt.Status.ONLINE
                            && status(receipt, 1, bot) == RewardReceipt.Status.ONLINE
                            && receipt.getStep(2).isOnce() && receipt.getStep(2).isRun();
                    return Verdict.of(receiptOk && after - before == STONE && PROBED.equals(Arrays.asList(PROBED_NAME)),
                            "stone " + before + "->" + after + " probed=" + PROBED + " receipt=" + receipt);
                }));
    }

    /** The occasion, mounted on the node the first time a case runs: its sample is the bot, known only then. */
    private synchronized RewardOccasion occasionFor(Player player) {
        if (occasion == null) {
            occasion = RewardOccasion.builder(plugin, OCCASION).phase("stage").marks("alive").variables("stage")
                    .sample(scene -> scene.participant(player.getUniqueId(), player.getName(), BigDecimal.ONE, "alive").variable("stage", STAGE))
                    .build();
            node.occasion(occasion, () -> rewards);
        }
        return occasion;
    }

    private static RewardReceipt.Status status(RewardReceipt receipt, int step, UUID member) {
        RewardReceipt.Line line = receipt.getStep(step).getLine(member);
        return line == null ? null : line.getStatus();
    }

    private static int stoneOf(Player player) {
        int held = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.STONE) {
                held += stack.getAmount();
            }
        }
        return held;
    }

    private static JsonNode readTable() {
        try (InputStream in = RewardsSuite.class.getResourceAsStream("/rewards/ecc-stage.yml")) {
            return new YAMLMapper().readTree(in);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** A console that keeps what the verb said, colours stripped. */
    private static final class Capture implements FCommandSender {
        private final List<String> lines = new ArrayList<>();

        List<String> getPlainLines() {
            List<String> plain = new ArrayList<>();
            for (String line : lines) plain.add(line.replaceAll("§.", "").trim());
            return plain;
        }

        @Override
        public String getName() {
            return "ecc-rewards";
        }

        @Override
        public UUID getUniqueId() {
            return null;
        }

        @Override
        public boolean hasPermission(@Nonnull String permission) {
            return true;
        }

        @Override
        public void sendMessage(@Nonnull Component component) {
            lines.add(FCColorUtil.componentToString(component));
        }

        @Override
        public Object getDelegate() {
            return this;
        }
    }
}
