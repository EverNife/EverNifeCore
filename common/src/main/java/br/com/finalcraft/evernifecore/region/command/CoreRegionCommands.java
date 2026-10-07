package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.pageviewer.CachePolicy;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.Resolution;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.Membership;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@code /ecore region here|info|list}: what an admin reads to understand the regions of every owner. Every verb only
 * reads - creating, changing and removing regions is the business of the plugin that owns them.
 */
public class CoreRegionCommands {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cName the player whose position to read: §e/ecore region here <player>")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cInforme o jogador cuja posição ler: §e/ecore region here <jogador>")
    public static LocaleMessage HERE_NEEDS_A_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe platform has no location for §e${player}§c.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA plataforma não tem a localização de §e${player}§c.")
    public static LocaleMessage NO_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Regions at §e${position}§6 (§e${player}§6): §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Regiões em §e${position}§6 (§e${player}§6): §e${count}")
    public static LocaleMessage HERE_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Flags that hold here, for §e${player}§6:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Flags que valem aqui, para §e${player}§6:")
    public static LocaleMessage HERE_FLAGS_HEADER;

    private static final Map<String, PageViewer<Region>> LIST_PAGES = new ConcurrentHashMap<>();

    @FinalCMD.SubCMD(subcmd = "here", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Every region at a player's position, of every owner, and why each flag holds there"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Toda região na posição de um jogador, de todo dono, e por que cada flag vale ali")
    })
    public void here(FCommandSender sender, @Arg(value = "[player]", fromSender = true) FPlayer target) {
        if (target == null) {
            HERE_NEEDS_A_PLAYER.send(sender);
            return;
        }
        FLocation location = target.getLocation();
        if (location == null) {
            NO_LOCATION.addPlaceholder("player", target.getName()).send(sender);
            return;
        }
        WorldLocPos pos = location.getWorldLocPos();
        List<Region> at = RegionLookup.get().at(pos);
        HERE_HEADER.addPlaceholder("position", describe(pos))
                .addPlaceholder("player", target.getName())
                .addPlaceholder("count", at.size())
                .send(sender);
        for (Region region : at) {
            sender.sendMessage(regionLine(region));
        }
        HERE_FLAGS_HEADER.addPlaceholder("player", target.getName()).send(sender);
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            sendExplanation(sender, flag, pos, target);
        }
    }

    @FinalCMD.SubCMD(subcmd = "info", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Everything a region holds: shape, flags, settings, groups and members"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Tudo o que uma região guarda: forma, flags, settings, grupos e membros")
    })
    public void info(FCommandSender sender, @Arg("<region>") Region region) {
        sendInfo(sender, region);
    }

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The regions of every owner, or of one owner and one world"),
            @FCLocale(lang = LocaleType.PT_BR, text = "As regiões de todo dono, ou de um dono e um mundo")
    })
    public void list(FCommandSender sender, @Arg("[owner]") String owner, @Arg("[world]") String world) {
        getListPage(owner, world).send(sender);
    }

    /**
     * The page of the regions of {@code owner} in {@code world} - either null (or {@code *}) for any - built once per
     * filter and read fresh on every send.
     */
    public static PageViewer<Region> getListPage(@Nullable String owner, @Nullable String world) {
        String ownerFilter = owner == null || owner.equals("*") ? null : owner;
        String worldFilter = world == null || world.equals("*") ? null : world;
        String key = (ownerFilter == null ? "*" : ownerFilter.toLowerCase()) + ":" + (worldFilter == null ? "*" : worldFilter);
        return LIST_PAGES.computeIfAbsent(key, ignored -> PageViewer.of(Region.class)
                .id("evernifecore:region:" + key)
                .source(() -> RegionLookup.get().all().stream()
                        .filter(region -> ownerFilter == null || region.getId().getOwner().equalsIgnoreCase(ownerFilter))
                        .filter(region -> worldFilter == null || region.getWorld().equals(worldFilter))
                        .collect(Collectors.toList()))
                .unlimitedEntries()
                .orderBy(region -> region.getId().toString()).ascending()
                .setFormatLine(FancyText.of("§7# ${number}: §e${region_id} §7prio ${priority} §8${world}").setHover("${summary}"))
                .addRowPlaceholder("region_id", region -> region.getId().toString())
                .addRowPlaceholder("priority", Region::getPriority)
                .addRowPlaceholder("world", Region::getWorld)
                .addRowPlaceholder("summary", CoreRegionCommands::summary)
                .cache(CachePolicy.none())
                .theme(PageTheme.classic().withTotalCount())
                .build());
    }

    /** The full description of a region, line by line - what {@code info} prints under any command. */
    public static void sendInfo(FCommandSender sender, Region region) {
        sender.sendMessage("§6Region §e" + region.getId());
        sender.sendMessage("§7owner: §f" + region.getOwner().getMetaInfo().getName() + " §7world: §f" + region.getWorld());
        sender.sendMessage("§7shape: §f" + describe(region.getWorld(), region.getShape()));
        Region parent = region.getParent();
        sender.sendMessage("§7priority: §f" + region.getPriority() + " §7parent: §f" + (parent == null ? "-" : parent.getId().toString()));
        Instant expiresAt = region.getExpiresAt();
        sender.sendMessage("§7persistence: §f" + region.getPersistence() + " §7expires: §f"
                + (expiresAt == null ? "never" : expiresAt + " (" + remaining(Duration.between(RegionTime.now(), expiresAt)) + ")"));
        sender.sendMessage("§7foreign edits: §f" + (region.getManager().isForeignAccessAllowed() ? "accepted" : "refused by the owner"));

        sender.sendMessage("§7flags: §f" + region.getFlags().size());
        for (Map.Entry<FlagKey, FlagValue<?>> entry : region.getFlags().entrySet()) {
            RegionFlag<?> flag = FlagRegistry.get().get(entry.getKey());
            StringBuilder line = new StringBuilder(" §7- §f" + entry.getKey() + " §7= §e" + valueText(flag, entry.getValue()));
            if (flag != null && region.getFlagSettings(flag) != null) {
                line.append(" §7settings: §eoverride");
            }
            ECPluginData handlerOwner = flag == null ? null : region.getFlagHandlerOwner(flag);
            if (handlerOwner != null) {
                line.append(" §7handler: §e").append(handlerOwner.getMetaInfo().getName());
            }
            sender.sendMessage(line.toString());
        }
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            if (region.getFlagValue(flag) == null && (region.getFlagSettings(flag) != null || region.getFlagHandlerOwner(flag) != null)) {
                ECPluginData handlerOwner = region.getFlagHandlerOwner(flag);
                sender.sendMessage(" §7- §f" + flag.getKey() + " §7(no value)"
                        + (region.getFlagSettings(flag) != null ? " §7settings: §eoverride" : "")
                        + (handlerOwner != null ? " §7handler: §e" + handlerOwner.getMetaInfo().getName() : ""));
            }
        }
        if (!region.getUnknownFlags().isEmpty()) {
            sender.sendMessage("§7unknown flags (no plugin registers them): §f" + region.getUnknownFlags().size());
        }

        sender.sendMessage("§7groups: §f" + region.getGroups().size());
        for (AccessGroup group : region.getGroups().values()) {
            sender.sendMessage(" §7- §e" + group.getName() + " §7prio §f" + group.getPriority()
                    + " §7permission §f" + (group.getPermission() == null ? "-" : group.getPermission())
                    + " §7check §f" + group.getMembershipCheck().toText());
            for (Membership member : group.getMembers()) {
                Duration left = member.getRemaining();
                sender.sendMessage("    §7member §f" + member.getUuid() + (left == null ? "" : " §7(" + remaining(left) + ")"));
            }
            for (Map.Entry<FlagKey, FlagValue<?>> entry : group.getFlags().entrySet()) {
                sender.sendMessage("    §7flag §f" + entry.getKey() + " §7= §e" + valueText(FlagRegistry.get().get(entry.getKey()), entry.getValue()));
            }
        }
        sender.sendMessage("§7tags: §f" + (region.getTags().isEmpty() ? "-" : String.join(", ", region.getTags())));
    }

    /** One line naming a region with its priority, shape and parent, for a list of regions. */
    public static String regionLine(Region region) {
        Region parent = region.getParent();
        return " §e" + region.getId() + " §7prio " + region.getPriority() + " §8" + region.getShape().getType().getId()
                + (parent == null ? "" : ", parent " + parent.getId());
    }

    private static <T> void sendExplanation(FCommandSender sender, RegionFlag<T> flag, WorldLocPos pos, FPlayer target) {
        Resolution<T> resolution = RegionLookup.get().explain(flag, pos.getWorldName(), pos.getX(), pos.getY(), pos.getZ(), target);
        T value = resolution.getValue();
        if (value == null) {
            return;
        }
        Region source = resolution.getSource();
        StringBuilder hover = new StringBuilder("§6" + flag.getKey());
        for (Resolution.Step step : resolution.getSteps()) {
            hover.append("\n§7- §f").append(step.getRegion() == null ? "-" : step.getRegion().getId().toString())
                    .append(" §7prio §f").append(step.getPriority())
                    .append(Optional.of(step).map(Resolution.Step::getGroup).map(group -> " §7group §f" + group.getName()).orElse(""))
                    .append(" §7value §f").append(step.getValue() == null ? "-" : FlagValues.format(flag, step.getValue()))
                    .append(" §7(").append(step.getReason()).append(")");
        }
        sender.sendMessage(FancyText.of("§f" + flag.getKey() + ": §e" + flag.getType().format(value) + " §7<- "
                + (source == null ? "default" : source.getId().toString()) + " (" + resolution.getReason() + ")").setHover(hover.toString()));
    }

    private static String summary(Region region) {
        return "§6" + region.getId()
                + "\n§7shape: §f" + describe(region.getWorld(), region.getShape())
                + "\n§7flags: §f" + region.getFlags().size() + " §7groups: §f" + region.getGroups().size()
                + "\n§7tags: §f" + (region.getTags().isEmpty() ? "-" : String.join(", ", region.getTags()));
    }

    private static String describe(String world, Shape shape) {
        BoundingBox box = shape.getBounds();
        if (shape.getType().getId().equals("world")) {
            return "world (the whole of " + world + ")";
        }
        long volume = shape.getVolume();
        String yRange = box.getMinY() + ".." + box.getMaxY();
        if (box.isUnboundedY()) {
            WorldHeight height = EverNifeCore.getPlatform().getWorldHeight(world);
            yRange = "unbounded (" + height.getMinY() + ".." + height.getMaxY() + " here)";
            volume = (long) (box.getMaxX() - box.getMinX() + 1) * (box.getMaxZ() - box.getMinZ() + 1) * (height.getMaxY() - height.getMinY() + 1);
        }
        return shape.getType().getId() + " x " + box.getMinX() + ".." + box.getMaxX() + " y " + yRange
                + " z " + box.getMinZ() + ".." + box.getMaxZ() + " §7volume §f" + volume;
    }

    private static String describe(WorldLocPos pos) {
        return pos.getWorldName() + " " + (int) Math.floor(pos.getX()) + " " + (int) Math.floor(pos.getY()) + " " + (int) Math.floor(pos.getZ());
    }

    private static String valueText(@Nullable RegionFlag<?> flag, FlagValue<?> value) {
        String text = flag == null ? String.valueOf(value.getValue()) : FlagValues.format(flag, value.getValue());
        Duration left = value.getRemaining();
        return left == null ? text : text + " §7(" + remaining(left) + ")";
    }

    static String remaining(Duration left) {
        return FCTimeFrame.of(Math.max(0, left.toMillis())).toConfigString() + " left";
    }
}
