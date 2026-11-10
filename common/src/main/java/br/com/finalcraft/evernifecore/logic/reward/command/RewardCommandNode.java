package br.com.finalcraft.evernifecore.logic.reward.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.NodeVerbAccess;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.VerbPermissionBase;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserString;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser.TabContext;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.EffectModifiers;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.logic.reward.PhasedRewardTable;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardOccasion;
import br.com.finalcraft.evernifecore.logic.reward.RewardPiece;
import br.com.finalcraft.evernifecore.logic.reward.RewardReceipt;
import br.com.finalcraft.evernifecore.logic.reward.RewardScene;
import br.com.finalcraft.evernifecore.logic.reward.Rewards;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeReference;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The reward verbs a plugin mounts under its own command, over the occasions it declares and the reward config it reads:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = {"rewards"}, permission = "raid.rewards")
 * private final RewardCommandNode rewards = new RewardCommandNode(ecPluginData, "raid.rewards")
 *         .occasion(STAGE_WON, () -> config.getNode("Rewards.stage-won"));
 * }</pre>
 * {@code export} writes {@code <data folder>/export/rewards.json} - every type a reward can use, this node's occasions
 * and their current config - for the reward editor; {@code simulate <occasion> <phase>} previews the table of that
 * phase on the occasion's sample scene and applies nothing. The node sees only what the plugin handed it. The mount's
 * permission is the door; a {@code permissionBase} adds {@code <base>.<verb>} per verb. Runs on the dispatching thread.
 */
public final class RewardCommandNode implements VerbPermissionBase {

    /** The version of the export's shape; it grows when a field changes meaning or goes away. */
    public static final int FORMAT = 1;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aRewards of §e${plugin}§a exported to §e${file}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aPrêmios de §e${plugin}§a exportados para §e${file}")
    public static LocaleMessage EXPORTED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cCould not export the rewards of §e${plugin}§c: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão foi possível exportar os prêmios de §e${plugin}§c: ${reason}")
    public static LocaleMessage EXPORT_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §e${plugin}§c has no occasion §e${occasion}§c - it has: §e${occasions}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §e${plugin}§c não tem a ocasião §e${occasion}§c - ela tem: §e${occasions}")
    public static LocaleMessage NO_SUCH_OCCASION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe rewards of §e${occasion}§c do not read: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cOs prêmios de §e${occasion}§c não leem: ${reason}")
    public static LocaleMessage UNREADABLE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eNo key of the rewards of §6${occasion}§e takes ${phase_name} §6${phase}§e: nothing would pay.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eNenhuma chave dos prêmios de §6${occasion}§e pega ${phase_name} §6${phase}§e: nada pagaria.")
    public static LocaleMessage NO_TABLE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eThe occasion §6${occasion}§e declares no sample scene, so only the once steps show - the plugin fills one with RewardOccasion.Builder.sample(...).")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eA ocasião §6${occasion}§e não declara cena de amostra, então só os passos once aparecem - o plugin preenche uma com RewardOccasion.Builder.sample(...).")
    public static LocaleMessage NO_SAMPLE;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Simulation of §e${occasion}§6, ${phase_name} §e${phase}§6 (key §e${key}§6), on the sample of §e${participants}§6 participant(s) - nothing applied:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Simulação de §e${occasion}§6, ${phase_name} §e${phase}§6 (chave §e${key}§6), sobre a amostra de §e${participants}§6 participante(s) - nada aplicado:")
    public static LocaleMessage SIMULATION_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 #${index} §f${step}§7: ${members} member(s)")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 #${index} §f${step}§7: ${members} membro(s)")
    public static LocaleMessage STEP;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 #${index} §f${step}§7: once, from the console")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 #${index} §f${step}§7: uma vez, pelo console")
    public static LocaleMessage STEP_ONCE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 #${index} §f${step}§7: does not pay at this moment")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 #${index} §f${step}§7: não paga neste momento")
    public static LocaleMessage STEP_NOT_NOW;

    @FCLocale(lang = LocaleType.EN_US, text = "§7    ${player}: share §f${share}§7, amount §f${amount}§7, ${status}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7    ${player}: parte §f${share}§7, quantia §f${amount}§7, ${status}")
    public static LocaleMessage LINE;

    private final ECPluginData plugin;
    private final @Nullable String permissionBase;
    private final Map<String, Mounted> occasions = new LinkedHashMap<>();

    public RewardCommandNode(ECPluginData plugin) {
        this(plugin, null);
    }

    /** @param permissionBase when not null, each verb also needs {@code <permissionBase>.<verb>} */
    public RewardCommandNode(ECPluginData plugin, @Nullable String permissionBase) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionBase = permissionBase;
    }

    /**
     * Adds an occasion of this node's plugin and where its rewards are read from: a map of phase keys, as
     * {@link PhasedRewardTable#parse} reads it, asked again on every verb so an edited and reloaded file is what shows.
     *
     * @throws IllegalArgumentException when the occasion is another plugin's, or its id is already added
     */
    public synchronized RewardCommandNode occasion(RewardOccasion occasion, Supplier<JsonNode> rewards) {
        if (occasion.getPlugin() != plugin) {
            throw new IllegalArgumentException("The occasion '" + occasion.getId() + "' belongs to " + occasion.getPlugin().getMetaInfo().getName()
                    + ", and this node to " + pluginName() + ": a node shows only its own plugin's occasions. Mount a node in "
                    + occasion.getPlugin().getMetaInfo().getName() + " for it.");
        }
        if (occasions.containsKey(occasion.getId())) {
            throw new IllegalArgumentException("The occasion '" + occasion.getId() + "' is already on this node - add each occasion once.");
        }
        occasions.put(occasion.getId(), new Mounted(occasion, Objects.requireNonNull(rewards, "rewards")));
        return this;
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    /** In the order they were added. Read-only. */
    public synchronized List<RewardOccasion> getOccasions() {
        return occasions.values().stream().map(mounted -> mounted.occasion).collect(Collectors.toList());
    }

    @Override
    public @Nullable String getPermissionBase() {
        return permissionBase;
    }

    /** Where {@code export} writes: {@code <data folder of the plugin>/export/rewards.json}. */
    public File getExportFile() {
        return new File(new File(plugin.getMetaInfo().getDataFolder(), "export"), "rewards.json");
    }

    // ------------------------------------------------------------------
    //  Verbs
    // ------------------------------------------------------------------

    @FinalCMD.SubCMD(subcmd = "export", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Write the reward types, occasions and config of this plugin to export/rewards.json"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Escreve os tipos, ocasiões e a config de prêmios deste plugin em export/rewards.json")
    })
    public void export(FCommandSender sender) {
        File file = getExportFile();
        try {
            write(file);
        } catch (IOException | RuntimeException failure) {
            EXPORT_FAILED.addPlaceholder("plugin", pluginName()).addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
            return;
        }
        EXPORTED.addPlaceholder("plugin", pluginName()).addPlaceholder("file", file.getAbsolutePath()).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "simulate", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Show what the rewards of a phase would pay on the occasion's sample, applying nothing"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mostra o que os prêmios de uma fase pagariam na amostra da ocasião, sem aplicar nada")
    })
    public void simulate(FCommandSender sender, @Arg(value = "<occasion>", parser = OccasionName.class) String occasionId,
                         @Arg("<phase>") Integer phase) {
        Mounted mounted;
        synchronized (this) {
            mounted = occasions.get(occasionId.trim().toLowerCase(Locale.ROOT));
        }
        if (mounted == null) {
            NO_SUCH_OCCASION.addPlaceholder("plugin", pluginName()).addPlaceholder("occasion", occasionId)
                    .addPlaceholder("occasions", occasionIds()).send(sender);
            return;
        }
        RewardOccasion occasion = mounted.occasion;
        PhasedRewardTable table;
        RewardReceipt receipt;
        RewardScene sample = occasion.getSample();
        try {
            table = PhasedRewardTable.parse(plugin, occasion, mounted.rewards.get());
            if (table.getKey(phase) == null) {
                NO_TABLE.addPlaceholder("occasion", occasion.getId()).addPlaceholder("phase_name", occasion.getPhase())
                        .addPlaceholder("phase", phase).send(sender);
                return;
            }
            receipt = table.getTable(phase).preview(RewardContext.builder(Caller.of(plugin, "rewards simulate " + occasion.getId()),
                    ProgressOwner.global()).scene(sample).build());
        } catch (TypeRegistryException | IllegalArgumentException refused) {
            UNREADABLE.addPlaceholder("occasion", occasion.getId()).addPlaceholder("reason", refused.getMessage()).send(sender);
            return;
        }
        if (sample.getParticipants().isEmpty()) {
            NO_SAMPLE.addPlaceholder("occasion", occasion.getId()).send(sender);
        }
        SIMULATION_HEADER.addPlaceholder("occasion", occasion.getId()).addPlaceholder("phase_name", occasion.getPhase())
                .addPlaceholder("phase", phase).addPlaceholder("key", table.getKey(phase))
                .addPlaceholder("participants", sample.getParticipants().size()).send(sender);
        for (RewardReceipt.Step step : receipt.getSteps()) {
            String name = step.getName() == null ? "-" : step.getName();
            if (!step.isRun()) {
                STEP_NOT_NOW.addPlaceholder("index", step.getIndex()).addPlaceholder("step", name).send(sender);
            } else if (step.isOnce()) {
                STEP_ONCE.addPlaceholder("index", step.getIndex()).addPlaceholder("step", name).send(sender);
            } else {
                STEP.addPlaceholder("index", step.getIndex()).addPlaceholder("step", name).addPlaceholder("members", step.getLines().size()).send(sender);
                for (RewardReceipt.Line line : step.getLines()) {
                    RewardScene.Participant participant = sample.getParticipant(line.getMember());
                    LINE.addPlaceholder("player", participant == null ? line.getMember().toString() : participant.getName())
                            .addPlaceholder("share", plain(line.getShare())).addPlaceholder("amount", plain(line.getAmount()))
                            .addPlaceholder("status", line.getStatus().name().toLowerCase(Locale.ROOT)).send(sender);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  The export
    // ------------------------------------------------------------------

    /**
     * The export: {@code format} ({@link #FORMAT}), {@code plugin}, {@code platform}, {@code types} (by kind: every
     * registered {@code effect}, {@code condition} and {@code variable}, and each reward piece under its role, as
     * {@link TypeReference#toJson(TypeRegistry, TypeDescriptor)} writes one), {@code effect-modifiers} (the keys every
     * effect takes, as {@link TypeReference#toJson(ParamSpec)}), {@code always-accepted} (the {@code {variables}} every
     * reward text reads) and {@code occasions} (this node's, each with its marks, variables, phase, sample scene and
     * current {@code rewards} config). The wiki's Rewards page is the contract, field by field.
     *
     * @throws RuntimeException as an occasion's rewards supplier does
     */
    public ObjectNode toJson() {
        ObjectNode export = JsonNodeFactory.instance.objectNode();
        export.put("format", FORMAT);
        export.put("plugin", pluginName());
        export.put("platform", EverNifeCore.getPlatform().getPlatformProviderId());
        ObjectNode types = export.putObject("types");
        addTypes(types, "effect", Effects.registry());
        addTypes(types, "condition", Conditions.registry());
        addTypes(types, "variable", Variables.registry());
        for (RewardPiece.Role role : RewardPiece.Role.values()) {
            types.putArray(role.name().toLowerCase(Locale.ROOT));
        }
        for (TypeDescriptor<RewardPiece> piece : Rewards.registry().getAll()) {
            String role = piece.getCategory() == null ? "reward-piece" : piece.getCategory();
            ArrayNode ofRole = types.has(role) ? (ArrayNode) types.get(role) : types.putArray(role);
            ofRole.add(TypeReference.toJson(Rewards.registry(), piece));
        }
        ArrayNode modifiers = export.putArray("effect-modifiers");
        for (ParamSpec modifier : EffectModifiers.getParams()) {
            modifiers.add(TypeReference.toJson(modifier));
        }
        ArrayNode always = export.putArray("always-accepted");
        RewardOccasion.ALWAYS_ACCEPTED.forEach(always::add);
        ArrayNode list = export.putArray("occasions");
        List<Mounted> mounted;
        synchronized (this) {
            mounted = new ArrayList<>(occasions.values());
        }
        for (Mounted each : mounted) {
            list.add(occasionJson(each));
        }
        return export;
    }

    /** Writes {@link #toJson()} into {@code file}, pretty-printed, creating its folder. */
    public void write(File file) throws IOException {
        ObjectNode export = toJson();
        Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, export);
    }

    private static void addTypes(ObjectNode types, String kind, TypeRegistry<?> registry) {
        ArrayNode ofKind = types.putArray(kind);
        for (TypeDescriptor<?> descriptor : registry.getAll()) {
            ofKind.add(TypeReference.toJson(registry, descriptor));
        }
    }

    private static ObjectNode occasionJson(Mounted mounted) {
        RewardOccasion occasion = mounted.occasion;
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("id", occasion.getId());
        json.put("phase", occasion.getPhase());
        ArrayNode marks = json.putArray("marks");
        occasion.getMarks().forEach(marks::add);
        ArrayNode variables = json.putArray("variables");
        occasion.getVariables().forEach(variables::add);
        ObjectNode sample = json.putObject("sample");
        ArrayNode participants = sample.putArray("participants");
        for (RewardScene.Participant participant : occasion.getSample().getParticipants()) {
            ObjectNode one = participants.addObject();
            one.put("uuid", participant.getUniqueId().toString());
            one.put("name", participant.getName());
            one.put("contribution", participant.getContribution());
            ArrayNode itsMarks = one.putArray("marks");
            participant.getMarks().forEach(itsMarks::add);
        }
        ObjectNode sampleVariables = sample.putObject("variables");
        occasion.getSample().getVariables().forEach(sampleVariables::put);
        JsonNode rewards = mounted.rewards.get();
        json.set("rewards", rewards == null ? JsonNodeFactory.instance.nullNode() : rewards.deepCopy());
        return json;
    }

    private String occasionIds() {
        List<RewardOccasion> all = getOccasions();
        return all.isEmpty() ? "-" : all.stream().map(RewardOccasion::getId).collect(Collectors.joining(", "));
    }

    private String pluginName() {
        return plugin.getMetaInfo().getName();
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Mounted {
        private final RewardOccasion occasion;
        private final Supplier<JsonNode> rewards;

        Mounted(RewardOccasion occasion, Supplier<JsonNode> rewards) {
            this.occasion = occasion;
            this.rewards = rewards;
        }
    }

    /** An occasion id of the node that runs the verb, completed with its occasions. */
    public static final class OccasionName extends ArgParserString {

        public OccasionName(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            if (!(tabContext.getExecutor() instanceof RewardCommandNode)) {
                return Collections.emptyList();
            }
            return ((RewardCommandNode) tabContext.getExecutor()).getOccasions().stream()
                    .map(RewardOccasion::getId)
                    .filter(id -> FCStringUtil.startsWithIgnoreCase(id, tabContext.getLastWord()))
                    .collect(Collectors.toList());
        }
    }
}
