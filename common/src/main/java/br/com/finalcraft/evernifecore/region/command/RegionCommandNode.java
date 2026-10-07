package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Overlap;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RemoveStrategy;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.Membership;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldShape;
import br.com.finalcraft.evernifecore.region.visual.RegionVisualizer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The region verbs a plugin mounts under its own command - {@code define}, {@code flag}, {@code group ... add} and the
 * rest - acting on the regions of one {@link RegionManager}:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "region", permission = "myplugin.region")
 * private final RegionCommandNode regions = new RegionCommandNode(manager, SelectionSpec.cuboid(), "myplugin.region");
 * }</pre>
 * The mount's permission is the door; a {@code permissionBase} adds {@code <base>.<verb>} per verb, checked before any
 * argument is read and hiding the verb from tab and help. A bare region name means this node's owner; another owner's
 * region is reached only by {@code owner:name}, and only read unless {@link #allowForeign} lets the node edit it AND
 * that owner accepts edits from other plugins. Every verb runs on the thread that dispatched it.
 */
public class RegionCommandNode implements RegionScope {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cSelect the area first: §e${select_command}§c, mark the points and confirm.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cSelecione a área primeiro: §e${select_command}§c, marque os pontos e confirme.")
    public static LocaleMessage NEEDS_A_SELECTION;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aSelection ready - §e${define_command} <name>§a creates the region.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aSeleção pronta - §e${define_command} <nome>§a cria a região.")
    public static LocaleMessage SELECTION_READY;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe area overlaps §e${overlaps}§c - add §e-f§c to go ahead anyway.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA área sobrepõe §e${overlaps}§c - use §e-f§c para seguir mesmo assim.")
    public static LocaleMessage AREA_OVERLAPS;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c${reason}")
    public static LocaleMessage REFUSED;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aRegion §e${region}§a ${action}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aRegião §e${region}§a ${action}.")
    public static LocaleMessage REGION_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cRegion §e${region}§c has §e${children}§c children - add §e--cascade§c to remove them too, or §e--orphan§c to keep them without a parent.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA região §e${region}§c tem §e${children}§c filhas - use §e--cascade§c para removê-las junto, ou §e--orphan§c para mantê-las sem pai.")
    public static LocaleMessage HAS_CHILDREN;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aFlag §e${flag}§a of §e${target}§a: §e${value}§a${ttl}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aFlag §e${flag}§a de §e${target}§a: §e${value}§a${ttl}.")
    public static LocaleMessage FLAG_SET;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eFlag §6${flag}§e of §6${target}§e cleared.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eFlag §6${flag}§e de §6${target}§e removida.")
    public static LocaleMessage FLAG_CLEARED;

    @FCLocale(lang = LocaleType.EN_US, text = "§7${flag} of ${target}: §e${value}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7${flag} de ${target}: §e${value}")
    public static LocaleMessage FLAG_VALUE;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Settings of §e${flag}§6 in §e${region}§6:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Settings de §e${flag}§6 em §e${region}§6:")
    public static LocaleMessage SETTINGS_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aSettings of §e${flag}§a in §e${region}§a ${action}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aSettings de §e${flag}§a em §e${region}§a ${action}.")
    public static LocaleMessage SETTINGS_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eNote: §6${region}§e has no value for §6${flag}§e, so these settings wait for one.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eNota: §6${region}§e não tem valor para §6${flag}§e, então estas settings esperam por um.")
    public static LocaleMessage SETTINGS_WITHOUT_VALUE;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aGroup §e${group}§a of §e${region}§a: ${change}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aGrupo §e${group}§a de §e${region}§a: ${change}.")
    public static LocaleMessage GROUP_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cNo online player is called §e${player}§c - name an online player or give the UUID.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNenhum jogador online se chama §e${player}§c - informe um jogador online ou o UUID.")
    public static LocaleMessage PLAYER_UNKNOWN;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cRegion §e${region}§c belongs to §e${owner}§c; this command only edits its own regions.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA região §e${region}§c pertence a §e${owner}§c; este comando só edita as próprias regiões.")
    public static LocaleMessage FOREIGN_NOT_ALLOWED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cRegion §e${region}§c belongs to §e${owner}§c, and ${owner} does not accept changes from other plugins.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA região §e${region}§c pertence a §e${owner}§c, e ${owner} não aceita mudanças de outros plugins.")
    public static LocaleMessage OWNER_REFUSES_FOREIGN;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe visualizer is off - set §eRegions.visualizer.enabled: true§c in EverNifeCore/config.yml.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cO visualizador está desligado - ponha §eRegions.visualizer.enabled: true§c no EverNifeCore/config.yml.")
    public static LocaleMessage VISUALIZER_OFF;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Regions of §e${owner}§6 here: §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Regiões de §e${owner}§6 aqui: §e${count}")
    public static LocaleMessage HERE_HEADER;

    private final RegionManager manager;
    private final SelectionSpec selectionSpec;
    private final @Nullable String permissionBase;
    private final Map<UUID, Selected> confirmedSelections = new ConcurrentHashMap<>();
    private volatile ForeignAccess foreignAccess = ForeignAccess.none();

    public RegionCommandNode(RegionManager manager, SelectionSpec selectionSpec) {
        this(manager, selectionSpec, null);
    }

    /** @param permissionBase when not null, each verb also needs {@code <permissionBase>.<verb>} */
    public RegionCommandNode(RegionManager manager, SelectionSpec selectionSpec, @Nullable String permissionBase) {
        this.manager = manager;
        this.selectionSpec = selectionSpec;
        this.permissionBase = permissionBase;
    }

    /** The node behind {@code executor}: the node itself, or the one a {@code group} branch belongs to; null otherwise. */
    public static @Nullable RegionCommandNode of(@Nullable Object executor) {
        if (executor instanceof RegionCommandNode) {
            return (RegionCommandNode) executor;
        }
        if (executor instanceof GroupBranch) {
            return ((GroupBranch) executor).getNode();
        }
        return null;
    }

    @Override
    public RegionManager getManager() {
        return manager;
    }

    public SelectionSpec getSelectionSpec() {
        return selectionSpec;
    }

    /** {@code <permissionBase>.<verb>}, or null when the node has no base and the mount's permission is all there is. */
    public @Nullable String getVerbPermission(String verb) {
        return permissionBase == null ? null : permissionBase + "." + verb;
    }

    /** Lets this node edit the regions of the owners {@code access} names; their owners may still refuse. */
    public RegionCommandNode allowForeign(ForeignAccess access) {
        this.foreignAccess = access;
        return this;
    }

    public ForeignAccess getForeignAccess() {
        return foreignAccess;
    }

    // ------------------------------------------------------------------
    //  Verbs
    // ------------------------------------------------------------------

    @FinalCMD.SubCMD(subcmd = "select", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Start selecting an area with the selection tool"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Começa a selecionar uma área com a ferramenta de seleção")
    })
    public void select(FPlayer player, CommandPath path, @Arg(value = "[shape]", context = "cuboid|polygon") String shape) {
        SelectionSpec spec = shape == null ? selectionSpec
                : shape.equalsIgnoreCase("polygon") ? copyShape(SelectionSpec.polygon()) : copyShape(SelectionSpec.cuboid());
        String defineCommand = nodeCommand(path) + " define";
        confirmedSelections.remove(player.getUniqueId());
        SelectionSession[] started = new SelectionSession[1];
        started[0] = SelectionTool.start(player, spec, confirmed -> {
            confirmedSelections.put(player.getUniqueId(), new Selected(started[0].getWorld(), confirmed));
            SELECTION_READY.addPlaceholder("define_command", defineCommand).send(player);
        });
    }

    @FinalCMD.SubCMD(subcmd = "define", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Create a region over your selection; -f accepts an overlap"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Cria uma região sobre a sua seleção; -f aceita sobreposição")
    })
    public void define(FPlayer player, CommandPath path, @Arg("<name>") String name,
                       @Arg.Flag(value = "--force", aliases = {"-f"}) Boolean force) {
        Selected selected = getSelection(player, path);
        if (selected == null) {
            return;
        }
        List<Overlap> overlaps = manager.dryRun(selected.world, selected.shape);
        if (!overlaps.isEmpty() && !Boolean.TRUE.equals(force)) {
            AREA_OVERLAPS.addPlaceholder("overlaps", describe(overlaps)).send(player);
            return;
        }
        CreationResult result = manager.create(name, selected.world, selected.shape);
        if (!result.isCreated()) {
            REFUSED.addPlaceholder("reason", result.getRefusal()
                    + (result.getOverlaps().isEmpty() ? "" : " (" + describe(result.getOverlaps()) + ")")).send(player);
            return;
        }
        consume(player, selected);
        REGION_DONE.addPlaceholder("region", result.getRegion().getId()).addPlaceholder("action", "created").send(player);
    }

    @FinalCMD.SubCMD(subcmd = "redefine", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Give a region the shape of your selection; -f accepts an overlap"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Dá à região a forma da sua seleção; -f aceita sobreposição")
    })
    public void redefine(FPlayer player, CommandPath path, @Arg("<region>") Region region,
                         @Arg.Flag(value = "--force", aliases = {"-f"}) Boolean force) {
        if (!requireWritable(player, region)) {
            return;
        }
        Selected selected = getSelection(player, path);
        if (selected == null) {
            return;
        }
        if (!region.getWorld().equals(selected.world)) {
            REFUSED.addPlaceholder("reason", "Region " + region.getId() + " is in " + region.getWorld() + " and the selection in "
                    + selected.world + " - select in the region's world.").send(player);
            return;
        }
        List<Overlap> overlaps = new ArrayList<>();
        for (Overlap overlap : region.getManager().dryRun(selected.world, selected.shape)) {
            if (!overlap.getId().equals(region.getId().toString())) {   //its own old shape is no overlap
                overlaps.add(overlap);
            }
        }
        if (!overlaps.isEmpty() && !Boolean.TRUE.equals(force)) {
            AREA_OVERLAPS.addPlaceholder("overlaps", describe(overlaps)).send(player);
            return;
        }
        region.getManager().redefine(region, selected.shape);
        consume(player, selected);
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "redefined").send(player);
    }

    @FinalCMD.SubCMD(subcmd = "remove", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Remove a region; --cascade takes its children too, --orphan keeps them"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Remove uma região; --cascade leva as filhas junto, --orphan as mantém")
    })
    public void remove(FCommandSender sender, @Arg("<region>") Region region,
                       @Arg.Flag("--cascade") Boolean cascade, @Arg.Flag("--orphan") Boolean orphan) {
        if (!requireWritable(sender, region)) {
            return;
        }
        RegionManager owner = region.getManager();
        if (Boolean.TRUE.equals(cascade)) {
            owner.remove(region, RemoveStrategy.CASCADE);
        } else if (Boolean.TRUE.equals(orphan)) {
            owner.remove(region, RemoveStrategy.ORPHAN_CHILDREN);
        } else {
            long children = owner.getAll().stream().filter(other -> other.getParent() == region).count();
            if (children > 0) {
                HAS_CHILDREN.addPlaceholder("region", region.getId()).addPlaceholder("children", children).send(sender);
                return;
            }
            owner.remove(region);
        }
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "removed").send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "info", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Everything a region holds"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Tudo o que uma região guarda")
    })
    public void info(FCommandSender sender, @Arg("<region>") Region region) {
        CoreRegionCommands.sendInfo(sender, region);
    }

    @FinalCMD.SubCMD(subcmd = "list", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The regions of this command, optionally of one world"),
            @FCLocale(lang = LocaleType.PT_BR, text = "As regiões deste comando, opcionalmente de um mundo")
    })
    public void list(FCommandSender sender, @Arg("[world]") String world) {
        CoreRegionCommands.getListPage(manager.getOwner().getMetaInfo().getName(), world).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "flag", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Set, replace or clear (-) a flag of a region; -t gives the value a time to live, --fs k=v or {json} sets how it acts"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Define, troca ou limpa (-) uma flag da região; -t dá prazo ao valor, --fs k=v ou {json} define como ela age")
    })
    public void flag(FCommandSender sender, @Arg("<region>") Region region,
                     @Arg(value = "<flag>", parser = ArgParserRegionFlag.OrShortcut.class) String flagName,
                     @Arg(value = "[value...]", parser = ArgParserFlagValue.class) String valueText,
                     @Arg.Flag(value = "--ttl", aliases = {"-t"}, insideTail = true) FCTimeFrame ttl,
                     @Arg.Flag(value = "--flag-settings", aliases = {"--fs"}, insideTail = true) List<ObjectNode> flagSettings) {
        if (valueText.isEmpty() && flagSettings == null) {
            for (RegionFlag<?> flag : FlagValues.targets(flagName)) {
                FlagValue<?> value = region.getFlagValue(flag);
                FLAG_VALUE.addPlaceholder("flag", flag.getKey()).addPlaceholder("target", region.getId())
                        .addPlaceholder("value", value == null ? "-" : FlagValues.format(flag, value.getValue())).send(sender);
            }
            return;
        }
        if (!requireWritable(sender, region)) {   //own region, or a foreign one this node may edit and whose owner accepts it
            return;
        }
        boolean valueApplied = !valueText.isEmpty();
        if (valueApplied && !applyFlags(sender, region, null, flagName, valueText, ttl)) {
            return;
        }
        if (flagSettings != null) {
            applySettings(sender, region, flagName, flagSettings, valueApplied);
        }
    }

    /**
     * Writes every {@code --flag-settings} occurrence, merged in order (a later key wins), over the region's current
     * settings of each flag {@code flagName} targets. A refused key leaves the value already set in place.
     */
    private static void applySettings(FCommandSender sender, Region region, String flagName, List<ObjectNode> occurrences, boolean valueApplied) {
        ObjectNode patch = JsonNodeFactory.instance.objectNode();
        for (ObjectNode occurrence : occurrences) {
            patch.setAll(occurrence);
        }
        for (RegionFlag<?> flag : FlagValues.targets(flagName)) {
            FlagSettings settings;
            try {
                settings = FlagValues.parseSettings(flag, region.getFlagSettings(flag), patch);
            } catch (FlagParseException refused) {
                REFUSED.addPlaceholder("reason", refused.getMessage() + (valueApplied ? " The value was set; its settings were not." : "")).send(sender);
                return;
            }
            region.setFlagSettings(flag, settings);
            SETTINGS_DONE.addPlaceholder("flag", flag.getKey()).addPlaceholder("region", region.getId()).addPlaceholder("action", "overridden").send(sender);
        }
    }

    @FinalCMD.SubCMD(subcmd = "settings", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "How a flag acts in this region: field=value overrides the server's settings, - drops the override"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Como uma flag age nesta região: campo=valor sobrepõe as settings do servidor, - remove a sobreposição")
    })
    public void settings(FCommandSender sender, @Arg("<region>") Region region, @Arg("<flag>") RegionFlag<?> flag,
                         @Arg(value = "[settings...]", parser = ArgParserFlagValue.Settings.class) String settingsText) {
        if (settingsText.isEmpty()) {
            sendSettings(sender, region, flag);
            return;
        }
        if (!requireWritable(sender, region)) {
            return;
        }
        if (settingsText.trim().equals(FlagValues.CLEAR)) {
            region.removeFlagSettings(flag);
            SETTINGS_DONE.addPlaceholder("flag", flag.getKey()).addPlaceholder("region", region.getId())
                    .addPlaceholder("action", "back to the server's").send(sender);
            return;
        }
        FlagSettings settings;
        try {
            settings = FlagValues.parseSettings(flag, region.getFlagSettings(flag), settingsText);
        } catch (FlagParseException refused) {
            REFUSED.addPlaceholder("reason", refused.getMessage()).send(sender);
            return;
        }
        region.setFlagSettings(flag, settings);
        SETTINGS_DONE.addPlaceholder("flag", flag.getKey()).addPlaceholder("region", region.getId()).addPlaceholder("action", "overridden").send(sender);
        if (region.getFlagValue(flag) == null) {
            SETTINGS_WITHOUT_VALUE.addPlaceholder("flag", flag.getKey()).addPlaceholder("region", region.getId()).send(sender);
        }
    }

    @FinalCMD.SubCMD(subcmd = "priority", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Set the priority of a region"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Define a prioridade de uma região")
    })
    public void priority(FCommandSender sender, @Arg("<region>") Region region, @Arg("<priority>") Integer priority) {
        if (!requireWritable(sender, region)) {
            return;
        }
        region.setPriority(priority);
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "now has priority " + priority).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "parent", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Show, set or clear (-) the parent of a region"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mostra, define ou limpa (-) o pai de uma região")
    })
    public void parent(FCommandSender sender, @Arg("<region>") Region region, @Arg("[parent]") String parentText) {
        if (parentText == null) {
            Region parent = region.getParent();
            REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "has parent " + (parent == null ? "-" : parent.getId())).send(sender);
            return;
        }
        if (!requireWritable(sender, region)) {
            return;
        }
        Region parent = null;
        if (!parentText.equals(FlagValues.CLEAR)) {
            parent = parentText.indexOf(':') >= 0 ? byId(parentText) : region.getManager().get(parentText);
            if (parent == null) {
                ArgParserRegion.NO_REGION_CALLED.addPlaceholder("region_name", parentText).send(sender);
                return;
            }
        }
        try {
            region.setParent(parent);
        } catch (IllegalArgumentException refused) {
            REFUSED.addPlaceholder("reason", refused.getMessage()).send(sender);
            return;
        }
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "has parent " + (parent == null ? "-" : parent.getId())).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "tp", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Teleport to a region's teleport-point, or to the top of its center"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Teleporta para o teleport-point da região, ou para o topo do centro dela")
    })
    public void tp(FPlayer player, @Arg("<region>") Region region) {
        WorldLocPos destination = getTeleportPoint(region);
        if (destination == null) {
            REFUSED.addPlaceholder("reason", "Region " + region.getId() + " covers its whole world, so it has no center - give it a teleport-point flag.")
                    .send(player);
            return;
        }
        player.teleportTo(new FLocation(destination, null));
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "reached").send(player);
    }

    @FinalCMD.SubCMD(subcmd = "view", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Draw the outline of a region for a few seconds"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Desenha o contorno de uma região por alguns segundos")
    })
    public void view(FPlayer player, @Arg("<region>") Region region) {
        if (!RegionVisualizer.get().isEnabled()) {
            VISUALIZER_OFF.send(player);
            return;
        }
        VisualStyle style = region.getManager() == manager ? VisualStyle.OWN : VisualStyle.OTHER_OWNER;
        RegionVisualizer.get().show(player, region.getWorld(), region.getShape(), style, Duration.ofSeconds(ECSettings.REGIONS_VISUALIZER_VIEW_SECONDS));
    }

    @FinalCMD.SubCMD(subcmd = "tag", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Add or remove a tag of a region"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Adiciona ou remove uma tag de uma região")
    })
    public void tag(FCommandSender sender, @Arg("<region>") Region region, @Arg(value = "<action>", context = "add|remove") String action,
                    @Arg("<tag>") String tag) {
        if (!requireWritable(sender, region)) {
            return;
        }
        if (action.equalsIgnoreCase("add")) {
            region.addTag(tag);
        } else {
            region.removeTag(tag);
        }
        REGION_DONE.addPlaceholder("region", region.getId()).addPlaceholder("action", "tags: " + String.join(", ", region.getTags())).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "here", validation = RegionVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The regions of this command where you stand"),
            @FCLocale(lang = LocaleType.PT_BR, text = "As regiões deste comando onde você está")
    })
    public void here(FPlayer player) {
        List<Region> here = new ArrayList<>();
        if (player.getLocation() != null) {
            for (Region region : RegionLookup.get().at(player.getLocation().getWorldLocPos())) {
                if (region.getManager() == manager) {
                    here.add(region);
                }
            }
        }
        HERE_HEADER.addPlaceholder("owner", manager.getOwner().getMetaInfo().getName()).addPlaceholder("count", here.size()).send(player);
        for (Region region : here) {
            player.sendMessage(CoreRegionCommands.regionLine(region));
        }
    }

    // ------------------------------------------------------------------
    //  group <region> <group> ...
    // ------------------------------------------------------------------

    /** What {@code group <region> <group>} captured; the group is only looked up (and created) by a verb that may write. */
    public static final class GroupTarget {
        private final Region region;
        private final String groupName;

        GroupTarget(Region region, String groupName) {
            this.region = region;
            this.groupName = groupName;
        }

        public Region getRegion() {
            return region;
        }

        public String getGroupName() {
            return groupName;
        }
    }

    @FinalCMD.Node(subcmd = "group", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The access groups of a region: members, flags, priority and permission"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Os grupos de acesso de uma região: membros, flags, prioridade e permissão")
    })
    public class GroupBranch implements RegionScope {

        public RegionCommandNode getNode() {
            return RegionCommandNode.this;
        }

        @Override
        public RegionManager getManager() {
            return manager;
        }

        @FinalCMD.Capture
        public @Nullable GroupTarget capture(FCommandSender sender, @Arg("<region>") Region region, @Arg("<group>") String group) {
            try {
                return new GroupTarget(region, RegionId.requireName("group", group));
            } catch (IllegalArgumentException invalid) {
                REFUSED.addPlaceholder("reason", invalid.getMessage()).send(sender);
                return null;
            }
        }

        @FinalCMD.SubCMD(subcmd = "add", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "Add a member (online name or UUID); -t makes it temporary"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Adiciona um membro (nome online ou UUID); -t o torna temporário")
        })
        public void add(FCommandSender sender, @Arg.NodeCaptured GroupTarget target, @Arg("<player>") String player,
                        @Arg.Flag(value = "--ttl", aliases = {"-t"}) FCTimeFrame ttl) {
            if (!requireWritable(sender, target.region)) {
                return;
            }
            UUID uuid = playerUuid(player);
            if (uuid == null) {
                PLAYER_UNKNOWN.addPlaceholder("player", player).send(sender);
                return;
            }
            target.region.getGroup(target.groupName).addMember(uuid, ttl == null ? null : Duration.ofMillis(ttl.getMillis()));
            groupDone(sender, target, "added " + player + (ttl == null ? "" : " for " + ttl.toConfigString()));
        }

        @FinalCMD.SubCMD(subcmd = "remove", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "Remove a member (online name or UUID)"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Remove um membro (nome online ou UUID)")
        })
        public void remove(FCommandSender sender, @Arg.NodeCaptured GroupTarget target, @Arg("<player>") String player) {
            if (!requireWritable(sender, target.region)) {
                return;
            }
            UUID uuid = playerUuid(player);
            AccessGroup group = target.region.getGroupIfPresent(target.groupName);
            boolean removed = uuid != null && group != null && group.removeMember(uuid);
            groupDone(sender, target, removed ? "removed " + player : player + " was not a member");
        }

        @FinalCMD.SubCMD(subcmd = "flag", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "Set or clear (-) a flag for the members of a group; -t gives it a time to live"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Define ou limpa (-) uma flag para os membros do grupo; -t dá prazo ao valor")
        })
        public void flag(FCommandSender sender, @Arg.NodeCaptured GroupTarget target,
                         @Arg(value = "<flag>", parser = ArgParserRegionFlag.OrShortcut.class) String flagName,
                         @Arg(value = "[value...]", parser = ArgParserFlagValue.class) String valueText,
                         @Arg.Flag(value = "--ttl", aliases = {"-t"}, insideTail = true) FCTimeFrame ttl) {
            if (valueText.isEmpty()) {
                AccessGroup group = target.region.getGroupIfPresent(target.groupName);
                for (RegionFlag<?> flag : FlagValues.targets(flagName)) {
                    Object value = group == null ? null : group.getFlag(flag);
                    FLAG_VALUE.addPlaceholder("flag", flag.getKey()).addPlaceholder("target", target.region.getId() + "/" + target.groupName)
                            .addPlaceholder("value", value == null ? "-" : FlagValues.format(flag, value)).send(sender);
                }
                return;
            }
            if (!requireWritable(sender, target.region)) {
                return;
            }
            applyFlags(sender, target.region, target.groupName, flagName, valueText, ttl);
        }

        @FinalCMD.SubCMD(subcmd = "list", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "The members and flags of a group"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Os membros e as flags de um grupo")
        })
        public void list(FCommandSender sender, @Arg.NodeCaptured GroupTarget target) {
            AccessGroup group = target.region.getGroupIfPresent(target.groupName);
            if (group == null) {
                groupDone(sender, target, "does not exist yet");
                return;
            }
            groupDone(sender, target, "priority " + group.getPriority() + ", permission " + (group.getPermission() == null ? "-" : group.getPermission()));
            for (Membership member : group.getMembers()) {
                Duration left = member.getRemaining();
                sender.sendMessage(" §7member §f" + member.getUuid() + (left == null ? "" : " §7(" + CoreRegionCommands.remaining(left) + ")"));
            }
            for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
                Object value = group.getFlag(flag);
                if (value != null) {
                    sender.sendMessage(" §7flag §f" + flag.getKey() + " §7= §e" + FlagValues.format(flag, value));
                }
            }
        }

        @FinalCMD.SubCMD(subcmd = "priority", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "Set the priority of a group among the region's groups"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Define a prioridade do grupo entre os grupos da região")
        })
        public void priority(FCommandSender sender, @Arg.NodeCaptured GroupTarget target, @Arg("<priority>") Integer priority) {
            if (!requireWritable(sender, target.region)) {
                return;
            }
            target.region.getGroup(target.groupName).setPriority(priority);
            groupDone(sender, target, "priority " + priority);
        }

        @FinalCMD.SubCMD(subcmd = "permission", validation = RegionVerbAccess.class, locales = {
                @FCLocale(lang = LocaleType.EN_US, text = "Make holders of a permission members of the group, or clear it (-)"),
                @FCLocale(lang = LocaleType.PT_BR, text = "Torna membros do grupo quem tem uma permissão, ou a limpa (-)")
        })
        public void permission(FCommandSender sender, @Arg.NodeCaptured GroupTarget target, @Arg("<permission>") String permission) {
            if (!requireWritable(sender, target.region)) {
                return;
            }
            String value = permission.equals(FlagValues.CLEAR) ? null : permission;
            target.region.getGroup(target.groupName).setPermission(value);
            groupDone(sender, target, "permission " + (value == null ? "-" : value));
        }
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    /**
     * Whether this node may change {@code region}: its own always; another owner's only when {@link #allowForeign}
     * names that owner AND the owner accepts changes from other plugins. Says why not to the sender.
     */
    protected boolean requireWritable(FCommandSender sender, Region region) {
        if (region.getManager() == manager) {
            return true;
        }
        String owner = region.getId().getOwner();
        if (!foreignAccess.allows(region.getOwner())) {
            FOREIGN_NOT_ALLOWED.addPlaceholder("region", region.getId()).addPlaceholder("owner", owner).send(sender);
            return false;
        }
        if (!region.getManager().isForeignAccessAllowed()) {
            OWNER_REFUSES_FOREIGN.addPlaceholder("region", region.getId()).addPlaceholder("owner", owner).send(sender);
            return false;
        }
        return true;
    }

    /** Parses every target flag first, so a shortcut over two flags sets both or neither. */
    /** @return false when the value was refused and nothing was set */
    private boolean applyFlags(FCommandSender sender, Region region, @Nullable String groupName, String flagName, String valueText,
                               @Nullable FCTimeFrame ttl) {
        List<RegionFlag<?>> flags = FlagValues.targets(flagName);
        List<Object> values = new ArrayList<>();
        for (RegionFlag<?> flag : flags) {
            try {
                values.add(FlagValues.parse(flag, valueText));
            } catch (FlagParseException refused) {
                REFUSED.addPlaceholder("reason", refused.getMessage()).send(sender);
                return false;
            }
        }
        String target = groupName == null ? region.getId().toString() : region.getId() + "/" + groupName;
        Duration timeToLive = ttl == null ? null : Duration.ofMillis(ttl.getMillis());
        for (int i = 0; i < flags.size(); i++) {
            RegionFlag<?> flag = flags.get(i);
            Object value = values.get(i);
            if (value == null) {
                if (groupName == null) {
                    region.removeFlag(flag);
                } else {
                    AccessGroup group = region.getGroupIfPresent(groupName);
                    if (group != null) {
                        group.removeFlag(flag);
                    }
                }
                FLAG_CLEARED.addPlaceholder("flag", flag.getKey()).addPlaceholder("target", target).send(sender);
                continue;
            }
            setFlag(region, groupName, flag, value, timeToLive);
            FLAG_SET.addPlaceholder("flag", flag.getKey()).addPlaceholder("target", target)
                    .addPlaceholder("value", FlagValues.format(flag, value))
                    .addPlaceholder("ttl", ttl == null ? "" : " for " + ttl.toConfigString())
                    .send(sender);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> void setFlag(Region region, @Nullable String groupName, RegionFlag<T> flag, Object value, @Nullable Duration ttl) {
        T typed = (T) value;
        if (groupName == null) {
            if (ttl == null) {
                region.setFlag(flag, typed);
            } else {
                region.setFlag(flag, typed, ttl);
            }
            return;
        }
        AccessGroup group = region.getGroup(groupName);
        if (ttl == null) {
            group.setFlag(flag, typed);
        } else {
            group.setFlag(flag, typed, ttl);
        }
    }

    private static void sendSettings(FCommandSender sender, Region region, RegionFlag<?> flag) {
        FlagSettings override = region.getFlagSettings(flag);
        Map<String, JsonNode> server = FlagValues.getSettingsFields(flag, flag.getSettings());
        Map<String, JsonNode> effective = override == null ? server : FlagValues.getSettingsFields(flag, override);
        SETTINGS_HEADER.addPlaceholder("flag", flag.getKey()).addPlaceholder("region", region.getId()).send(sender);
        for (Map.Entry<String, JsonNode> field : effective.entrySet()) {
            boolean overridden = override != null && !field.getValue().equals(server.get(field.getKey()));
            sender.sendMessage(" §f" + field.getKey() + " §7= §e" + field.getValue().asText(field.getValue().toString())
                    + (overridden ? " §e(override)" : " §7(server)"));
        }
    }

    private void groupDone(FCommandSender sender, GroupTarget target, String change) {
        GROUP_DONE.addPlaceholder("group", target.groupName).addPlaceholder("region", target.region.getId()).addPlaceholder("change", change).send(sender);
    }

    /** The confirmed selection, or an open one that already has enough points; tells the player how to select when there is none. */
    private @Nullable Selected getSelection(FPlayer player, CommandPath path) {
        Selected confirmed = confirmedSelections.get(player.getUniqueId());
        if (confirmed != null) {
            return confirmed;
        }
        SelectionSession session = SelectionTool.getSession(player);
        Shape shape = session == null ? null : session.toShape();
        if (shape == null) {
            NEEDS_A_SELECTION.addPlaceholder("select_command", nodeCommand(path) + " select").send(player);
            return null;
        }
        return new Selected(session.getWorld(), shape);
    }

    private void consume(FPlayer player, Selected selected) {
        confirmedSelections.remove(player.getUniqueId(), selected);
        SelectionSession session = SelectionTool.getSession(player);
        if (session != null) {
            session.finish();
        }
    }

    private SelectionSpec copyShape(SelectionSpec shaped) {
        return shaped.withTool(selectionSpec.getTool()).withLivePreview(selectionSpec.isLivePreview()).withTimeout(selectionSpec.getTimeout());
    }

    /** {@code /<label> <path to this node>}, from the path of one of its verbs. */
    private static String nodeCommand(CommandPath path) {
        String parent = path.parentJoined();
        return "/" + path.getLabel() + (parent.isEmpty() ? "" : " " + parent);
    }

    private static @Nullable Region byId(String text) {
        try {
            return RegionLookup.get().byId(RegionId.parse(text));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static String describe(List<Overlap> overlaps) {
        return overlaps.stream().map(Overlap::getId).distinct().collect(Collectors.joining(", "));
    }

    private static @Nullable UUID playerUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAUuid) {
            FPlayer online = EverNifeCore.getPlatform().getPlayer(text);
            return online == null ? null : online.getUniqueId();
        }
    }

    /** The {@code teleport-point} flag of the region, else the top of the center of its box; null for a whole-world region. */
    private static @Nullable WorldLocPos getTeleportPoint(Region region) {
        Optional<RegionFlag<?>> pointFlag = FlagRegistry.get().find("teleport-point");
        if (pointFlag.isPresent()) {
            Object point = region.getFlag(pointFlag.get());
            if (point instanceof WorldLocPos) {
                return (WorldLocPos) point;
            }
        }
        Shape shape = region.getShape();
        if (shape instanceof WorldShape) {
            return null;
        }
        BoundingBox box = shape.getBounds();
        int top = box.isUnboundedY() ? EverNifeCore.getPlatform().getWorldHeight(region.getWorld()).getMaxY() : box.getMaxY();
        return WorldLocPos.of((box.getMinX() + box.getMaxX() + 1) / 2.0, top + 1, (box.getMinZ() + box.getMaxZ() + 1) / 2.0, region.getWorld());
    }

    private static final class Selected {
        final String world;
        final Shape shape;

        Selected(String world, Shape shape) {
            this.world = world;
            this.shape = shape;
        }
    }
}
