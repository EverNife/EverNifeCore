package br.com.finalcraft.evernifecore.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.everyconfig.config.Config;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One plugin's command registry file - {@code plugins/EverNifeCore/commands/<PluginName>.yml} - where
 * an admin may disable a command or hand it extra aliases.
 * <p>
 * It only exists while {@code Settings.Commands.REGISTRY_FILES_ENABLED} is on: with the feature off
 * nothing here is ever instantiated, so no file is opened, seeded or saved and the annotations are the
 * only source of truth.
 */
final class CommandRegistryFile {

    /** Sub-folder of EverNifeCore's own data folder that holds one file per plugin. */
    static final String FOLDER = "commands";

    /** The single, server-wide file of the 2.x era - never read, never migrated, never deleted. */
    private static final String LEGACY_FILE = "commands.yml";

    private static final String ROOT_KEY = "commands";

    /**
     * The core's plugin data the legacy notice was already given for. Keyed by instance rather than a
     * plain flag so the line comes out once per boot and not once per JVM, which is also what makes it
     * observable to a test that boots its own core.
     */
    private static ECPluginData legacyNoticeGivenFor;

    private final Config config;
    private final String fileName;

    /** How many nodes this file changed on the tree it was applied to - what the summary line reports. */
    private int changes;

    private CommandRegistryFile(Config config, String fileName) {
        this.config = config;
        this.fileName = fileName;
    }

    /** Opens (creating on first use) the registry file of {@code owningPlugin}. */
    static CommandRegistryFile of(@Nonnull ECPluginData owningPlugin) {
        ECPluginData coreData = EverNifeCore.getEcPluginData();
        noticeTheLegacyFileOnce(coreData);

        String fileName = FOLDER + "/" + owningPlugin.getMetaInfo().getName() + ".yml";
        return new CommandRegistryFile(ConfigFactory.open(coreData, fileName), fileName);
    }

    /**
     * Applies this file to a whole tree, at registration time, seeding whatever is missing on the way -
     * leaves included, so an admin finds every path the plugin offers by opening the file instead of
     * reading its code.
     * <p>
     * A disabled node is REMOVED from its parent rather than filtered later: dispatch, tab-complete and
     * help then agree for free, because there is nothing left for them to disagree about.
     *
     * @return false when the ROOT itself is disabled, meaning the caller must not register at all
     */
    boolean applyTo(@Nonnull CommandNode root, @Nonnull ECPluginData owningPlugin) {
        changes = 0;
        boolean rootSurvived = applyToNode(root, root.getPrimaryLabel());
        saveIfSeeded();

        //Whoever edited the file knows they did; this line exists for the OTHER question - "why did
        //that command vanish" - and the full answer is one debug flag away.
        if (changes > 0) {
            owningPlugin.getLog().info("Command registry: " + changes + " command(s)/branch(es) of "
                    + owningPlugin.getMetaInfo().getName() + " changed by " + fileName + " - enable the "
                    + ECDebugModule.COMMAND_REGISTRY.getName() + " debug module to see which");
        }
        return rootSurvived;
    }

    /** @return whether {@code node} survived; a pruned node's children are neither read nor seeded */
    private boolean applyToNode(CommandNode node, String rootLabel) {
        NodeOverride override = read(node.toUsagePath(rootLabel, false), node.getExtraLabels());

        if (!override.isEnabled()) {
            changes++;
            detail("Removed '{}' and everything under it - disabled in {}", typedPathOf(node, rootLabel), fileName);
            reportEntriesOverruledBy(node, rootLabel);
            if (node.getParent() != null) {
                node.getParent().removeChild(node);
            }
            return false;
        }

        if (!Arrays.equals(override.getExtraLabels(), node.getExtraLabels())) {
            changes++;
            detail("Aliases of '{}' are now {} instead of {} - {}", typedPathOf(node, rootLabel),
                    Arrays.toString(override.getExtraLabels()), Arrays.toString(node.getExtraLabels()), fileName);
            node.setExtraLabels(override.getExtraLabels());
        }

        for (CommandNode child : new ArrayList<>(node.getChildren())) {
            applyToNode(child, rootLabel);
        }
        return true;
    }

    /**
     * Names every entry under {@code disabledNode} that still says {@code enabled: true}. The parent
     * wins - a child reviving its parent would leave a path nobody can type - so those entries do
     * nothing, and silently doing nothing is exactly what an admin cannot debug.
     */
    private void reportEntriesOverruledBy(CommandNode disabledNode, String rootLabel) {
        for (CommandNode child : disabledNode.getChildren()) {
            String entry = entryPathOf(child.toUsagePath(rootLabel, false));
            if (config.contains(entry + ".enabled") && config.getBoolean(entry + ".enabled")) {
                detail("Entry of '{}' says enabled, but '{}' above it does not - the parent wins",
                        typedPathOf(child, rootLabel), typedPathOf(disabledNode, rootLabel));
            }
            reportEntriesOverruledBy(child, rootLabel);
        }
    }

    /** One line per path removed and per alias overridden - the detail nobody needs until they do. */
    private static void detail(String message, Object... params) {
        ECDebugModule.COMMAND_REGISTRY.debug(message, params);
    }

    /** The node as a sender types it, ancestors' capture names included: {@code /lp user <user> permission}. */
    private static String typedPathOf(CommandNode node, String rootLabel) {
        return node.toUsagePath(rootLabel, false).full();
    }

    /**
     * Reads one node's entry, seeding it from the annotation on first sight. An absent entry is not an
     * error: it simply means the annotation still decides, which is why the file never has to be
     * complete to be valid.
     *
     * @param annotatedExtraLabels what the annotation declares, so a fresh entry seeds the truth
     */
    NodeOverride read(@Nonnull CommandPath path, @Nonnull String[] annotatedExtraLabels) {
        String entry = entryPathOf(path);
        boolean enabled = config.getOrSetValueIfAbsent(entry + ".enabled", true);
        List<String> extraLabels = config.getOrSetValueIfAbsent(entry + ".aliases", Arrays.asList(annotatedExtraLabels));
        return new NodeOverride(enabled, extraLabels.toArray(new String[0]));
    }

    /** Writes the file back only if reading it seeded something new - a complete file costs no save. */
    void saveIfSeeded() {
        if (config.hasNewSeededDefaults()) {
            //after the seeding, never before: a comment on a key that does not exist yet is dropped
            config.setComment(ROOT_KEY, HEADER);
            config.save();
            config.clearNewSeededDefaults();
        }
    }

    /**
     * Where a node's entry lives: the command's primary label at the top, then one {@code nodes.} step
     * per literal below it, so the file's shape is the tree's shape.
     */
    private static String entryPathOf(CommandPath path) {
        StringBuilder entry = new StringBuilder(ROOT_KEY).append('.').append(path.getLabel());
        for (String literal : path.getLiterals()) {
            entry.append(".nodes.").append(literal);
        }
        return entry.toString();
    }

    /**
     * Tells the admin, once, that the old server-wide file stopped being read. Translating it is a
     * hand job on purpose: it may name plugins that are no longer installed, and writing entries for a
     * plugin that never registers would leave ghosts nobody can trace back.
     */
    private static void noticeTheLegacyFileOnce(ECPluginData coreData) {
        if (legacyNoticeGivenFor == coreData) {
            return;
        }
        legacyNoticeGivenFor = coreData;

        File legacyFile = new File(coreData.getMetaInfo().getDataFolder(), LEGACY_FILE);
        if (legacyFile.exists()) {
            EverNifeCore.getLog().info("The old '" + LEGACY_FILE + "' is no longer read - every command is now configured in '"
                    + FOLDER + "/<PluginName>.yml'. The old file was left untouched; move what you still want and delete it.");
        }
    }

    /** What the file says about one node: whether it stays, and which extra labels it answers to. */
    static final class NodeOverride {

        private final boolean enabled;
        private final String[] extraLabels;

        NodeOverride(boolean enabled, String[] extraLabels) {
            this.enabled = enabled;
            this.extraLabels = extraLabels;
        }

        boolean isEnabled() {
            return enabled;
        }

        String[] getExtraLabels() {
            return extraLabels;
        }
    }

    private static final String HEADER = String.join("\n",
            "============================================================",
            " EverNifeCore - command registry of this plugin",
            "",
            " One entry per command this plugin registers, nested exactly",
            " like the command tree: every sub-command and branch lives",
            " under its parent's 'nodes:' section.",
            "",
            " enabled: false removes that command (or branch) entirely -",
            "          it disappears from dispatch, tab-complete and help,",
            "          along with everything below it.",
            " aliases: overrides the EXTRA labels only - the key of the",
            "          entry is the primary label, it is the entry's",
            "          identity and cannot be changed here.",
            "",
            " An entry you delete simply goes back to what the code says.",
            " Changes only apply on the owning plugin's next boot/reload -",
            " there is no hot rebind.",
            "============================================================");
}
