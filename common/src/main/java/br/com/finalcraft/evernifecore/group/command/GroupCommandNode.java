package br.com.finalcraft.evernifecore.group.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.NodeVerbAccess;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.VerbPermissionBase;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagParseException;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.group.DissolveCause;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.GroupOptions;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.region.command.FlagValues;
import br.com.finalcraft.evernifecore.region.command.RegionCommandNode;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The verbs of one {@link GroupKind} a plugin mounts under its own command - {@code create}, {@code invite},
 * {@code join}, {@code leave}, {@code kick}, {@code promote}, {@code demote}, {@code disband}, {@code info}, {@code list},
 * {@code flag} - over the groups of one {@link GroupManager}:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "party", permission = "myplugin.party")
 * private final GroupCommandNode parties = new GroupCommandNode(manager, PARTY, "myplugin.party");
 * }</pre>
 * The mount's permission is the door; a {@code permissionBase} adds {@code <base>.<verb>} per verb ({@link NodeVerbAccess}).
 * In a kind of cardinality ONE, {@code leave} without a group leaves the sender's. Every verb runs on the thread that
 * dispatched it.
 */
public class GroupCommandNode implements VerbPermissionBase {

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §a${kind} §e${group}§a: ${change}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §a${kind} §e${group}§a: ${change}.")
    public static LocaleMessage DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §c${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §c${reason}")
    public static LocaleMessage REFUSED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no ${kind} §e${group}§c - §elist§c shows the ones there are.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe ${kind} §e${group}§c - §elist§c mostra os que existem.")
    public static LocaleMessage NO_SUCH_GROUP;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cNo online player is called §e${player}§c - name an online player or give the UUID.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNenhum jogador online se chama §e${player}§c - informe um jogador online ou o UUID.")
    public static LocaleMessage PLAYER_UNKNOWN;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cName the ${kind} to leave: you may be in several.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cInforme o ${kind} de onde sair: você pode estar em vários.")
    public static LocaleMessage NAME_THE_GROUP;

    @FCLocale(lang = LocaleType.EN_US, text = "§6${kind} §e${group}§6: leader §e${leader}§6, §e${online}§6/§e${size}§6 online, priority §e${priority}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6${kind} §e${group}§6: líder §e${leader}§6, §e${online}§6/§e${size}§6 online, prioridade §e${priority}")
    public static LocaleMessage INFO_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Every ${kind}: §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Todos os ${kind}: §e${count}")
    public static LocaleMessage LIST_HEADER;

    /** How long an invite lasts when the verb names no time. */
    public static final Duration DEFAULT_INVITE_TTL = Duration.ofMinutes(5);

    private final GroupManager manager;
    private final GroupKind kind;
    private final @Nullable String permissionBase;

    /**
     * @param kind           the kind the verbs create and act on; registered on {@code manager} by its owner
     * @param permissionBase when not null, each verb also needs {@code <permissionBase>.<verb>}
     */
    public GroupCommandNode(GroupManager manager, GroupKind kind, @Nullable String permissionBase) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.permissionBase = permissionBase;
    }

    public GroupManager getManager() {
        return manager;
    }

    public GroupKind getKind() {
        return kind;
    }

    @Override
    public @Nullable String getPermissionBase() {
        return permissionBase;
    }

    // ------------------------------------------------------------------
    //  Verbs
    // ------------------------------------------------------------------

    @FinalCMD.SubCMD(subcmd = "create", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Create a group, optionally with its leader (online name or UUID), who joins it"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Cria um grupo, opcionalmente com o líder (nome online ou UUID), que entra nele")
    })
    public void create(FCommandSender sender, @Arg("<name>") String name,
                       @Arg(value = "[leader]", parser = RegionCommandNode.MemberName.class) String leader) {
        GroupOptions options = GroupOptions.defaults();
        if (leader != null) {
            UUID uuid = playerUuid(leader);
            if (uuid == null) {
                PLAYER_UNKNOWN.addPlaceholder("player", leader).send(sender);
                return;
            }
            options = options.withLeader(uuid);
        }
        try {
            Group group = manager.create(name, kind, options);
            done(sender, group, leader == null ? "created" : "created, led by " + leader);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            refuse(sender, refused.getMessage());
        }
    }

    @FinalCMD.SubCMD(subcmd = "invite", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Invite a player into a group, for a time (5 minutes when none is given)"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Convida um jogador para um grupo, por um tempo (5 minutos se nenhum for dado)")
    })
    public void invite(FCommandSender sender, @Arg("<group>") String groupName,
                       @Arg(value = "<player>", parser = RegionCommandNode.MemberName.class) String player, @Arg("[ttl]") FCTimeFrame ttl) {
        Group group = group(sender, groupName);
        UUID uuid = group == null ? null : member(sender, player);
        if (uuid == null) {
            return;
        }
        Duration lasting = ttl == null ? DEFAULT_INVITE_TTL : Duration.ofMillis(ttl.getMillis());
        UUID by = sender instanceof FPlayer ? ((FPlayer) sender).getUniqueId() : null;
        group.invite(uuid, lasting, by);
        done(sender, group, "invited " + player + " for " + (ttl == null ? "5m" : ttl.toConfigString()));
    }

    @FinalCMD.SubCMD(subcmd = "join", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Join a group - by its invite when you have one"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Entra num grupo - pelo convite, quando você tem um")
    })
    public void join(FPlayer player, @Arg("<group>") String groupName) {
        Group group = group(player, groupName);
        if (group == null) {
            return;
        }
        GateResult result = group.getInvite(player.getUniqueId()) != null ? group.accept(player) : group.join(player);
        answer(player, group, result, "joined by " + player.getName());
    }

    @FinalCMD.SubCMD(subcmd = "leave", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Leave a group - yours, when the kind allows one per player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Sai de um grupo - o seu, quando o tipo permite um por jogador")
    })
    public void leave(FPlayer player, @Arg("[group]") String groupName) {
        Group group;
        if (groupName != null) {
            group = group(player, groupName);
        } else if (kind.getCardinality() == GroupKind.Cardinality.ONE) {
            group = manager.getGroupOf(player.getUniqueId(), kind);
            if (group == null) {
                refuse(player, player.getName() + " is in no " + kindName() + ".");
            }
        } else {
            NAME_THE_GROUP.addPlaceholder("kind", kindName()).send(player);
            return;
        }
        if (group != null) {
            answer(player, group, group.leave(player), "left by " + player.getName());
        }
    }

    @FinalCMD.SubCMD(subcmd = "kick", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Take a member out of a group"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Tira um membro de um grupo")
    })
    public void kick(FCommandSender sender, @Arg("<group>") String groupName,
                     @Arg(value = "<player>", parser = RegionCommandNode.MemberName.class) String player) {
        Group group = group(sender, groupName);
        UUID uuid = group == null ? null : member(sender, player);
        if (uuid == null) {
            return;
        }
        if (group.kick(uuid, null)) {
            done(sender, group, "kicked " + player);
        } else {
            refuse(sender, player + " is not a member of the " + kindName() + " '" + group.getName() + "'.");
        }
    }

    @FinalCMD.SubCMD(subcmd = "promote", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Give a member a role of the kind's ladder"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Dá a um membro um papel da escada do tipo")
    })
    public void promote(FCommandSender sender, @Arg("<group>") String groupName,
                        @Arg(value = "<player>", parser = RegionCommandNode.MemberName.class) String player, @Arg("<role>") String role) {
        changeRole(sender, groupName, player, role, true);
    }

    @FinalCMD.SubCMD(subcmd = "demote", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Take a role from a member"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Tira um papel de um membro")
    })
    public void demote(FCommandSender sender, @Arg("<group>") String groupName,
                       @Arg(value = "<player>", parser = RegionCommandNode.MemberName.class) String player, @Arg("<role>") String role) {
        changeRole(sender, groupName, player, role, false);
    }

    @FinalCMD.SubCMD(subcmd = "disband", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Dissolve a group: every member leaves"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Dissolve um grupo: todos os membros saem")
    })
    public void disband(FCommandSender sender, @Arg("<group>") String groupName) {
        Group group = group(sender, groupName);
        if (group != null) {
            group.dissolve(DissolveCause.OWNER);
            done(sender, group, "disbanded");
        }
    }

    @FinalCMD.SubCMD(subcmd = "info", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "A group's leader, members with their roles and time left, invites and flags"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O líder, os membros com papéis e prazo, os convites e as flags de um grupo")
    })
    public void info(FCommandSender sender, @Arg("<group>") String groupName) {
        Group group = group(sender, groupName);
        if (group == null) {
            return;
        }
        INFO_HEADER.addPlaceholder("kind", kindName()).addPlaceholder("group", group.getName())
                .addPlaceholder("leader", group.getLeader() == null ? "-" : nameOf(group.getLeader()))
                .addPlaceholder("online", group.getOnlineMembers().size()).addPlaceholder("size", group.size())
                .addPlaceholder("priority", group.getPriority()).send(sender);
        for (GroupMember member : group.getMembers()) {
            Duration left = member.getRemaining();
            String roles = member.getRoles().stream().map(GroupRole::getName).collect(Collectors.joining(", "));
            sender.sendMessage(" §7member §f" + nameOf(member.getUuid()) + (roles.isEmpty() ? "" : " §7[" + roles + "]")
                    + (left == null ? "" : " §7(" + CoreRegionCommands.remaining(left) + ")"));
        }
        group.getInvites().forEach(invite -> sender.sendMessage(" §7invite §f" + nameOf(invite.getPlayer()) + " §7(until "
                + invite.getExpiresAt() + ")"));
        for (Map.Entry<?, FlagValue<?>> entry : group.getFlags().entrySet()) {
            sender.sendMessage(" §7flag §f" + entry.getKey() + " §7= §e" + entry.getValue().getValue());
        }
    }

    @FinalCMD.SubCMD(subcmd = "list", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Every group of this kind, with its size"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Todo grupo deste tipo, com o tamanho")
    })
    public void list(FCommandSender sender) {
        LIST_HEADER.addPlaceholder("kind", kindName()).addPlaceholder("count", manager.getByKind(kind).size()).send(sender);
        for (Group group : manager.getByKind(kind)) {
            sender.sendMessage(" §e" + group.getName() + " §7" + group.size() + " member(s), leader "
                    + (group.getLeader() == null ? "-" : nameOf(group.getLeader())));
        }
    }

    @FinalCMD.SubCMD(subcmd = "flag", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Read, set or clear (-) a flag of a group; -t gives the value a time to live"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Lê, define ou limpa (-) uma flag de um grupo; -t dá prazo ao valor")
    })
    public void flag(FCommandSender sender, @Arg("<group>") String groupName, @Arg("<flag>") String flagName, @Arg("[value]") String valueText,
                     @Arg.Flag(value = "--ttl", aliases = {"-t"}) FCTimeFrame ttl) {
        Group group = group(sender, groupName);
        if (group == null) {
            return;
        }
        Flag<?> flag = FlagRegistry.get().find(flagName).orElse(null);
        if (flag == null) {
            refuse(sender, "No flag is called '" + flagName + "' - /ecore region here lists the flags of this server.");
            return;
        }
        if (valueText == null) {
            done(sender, group, flag.getKey() + " = " + FlagValues.format(flag, group.getFlag(flag)));
            return;
        }
        if (valueText.equals(FlagValues.CLEAR)) {
            group.removeFlag(flag);
            done(sender, group, flag.getKey() + " cleared");
            return;
        }
        try {
            set(group, flag, valueText, ttl);
        } catch (FlagParseException | IllegalArgumentException refused) {
            refuse(sender, refused.getMessage());
            return;
        }
        done(sender, group, flag.getKey() + " = " + FlagValues.format(flag, group.getFlag(flag))
                + (ttl == null ? "" : " for " + ttl.toConfigString()));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static <T> void set(Group group, Flag<T> flag, String text, @Nullable FCTimeFrame ttl) throws FlagParseException {
        T value = FlagValues.parse(flag, text);
        if (value == null) {
            group.removeFlag(flag);
        } else if (ttl == null) {
            group.setFlag(flag, value);
        } else {
            group.setFlag(flag, value, Duration.ofMillis(ttl.getMillis()));
        }
    }

    private void changeRole(FCommandSender sender, String groupName, String player, String roleName, boolean give) {
        Group group = group(sender, groupName);
        UUID uuid = group == null ? null : member(sender, player);
        if (uuid == null) {
            return;
        }
        GroupRole role = kind.getRole(roleName);
        if (role == null) {
            refuse(sender, "The " + kindName() + " has no role '" + roleName + "': its ladder is "
                    + kind.getRoles().stream().map(GroupRole::getName).collect(Collectors.toList()) + ".");
            return;
        }
        if (!group.isMember(uuid)) {
            refuse(sender, player + " is not a member of the " + kindName() + " '" + group.getName() + "'.");
            return;
        }
        boolean changed = give ? group.addRole(uuid, role) : group.removeRole(uuid, role);
        done(sender, group, changed ? player + (give ? " is now " : " is no longer ") + role.getName()
                : player + (give ? " already is " : " is not ") + role.getName());
    }

    private @Nullable Group group(FCommandSender sender, String name) {
        Group group = manager.get(name);
        if (group == null || !group.getKind().equals(kind)) {
            NO_SUCH_GROUP.addPlaceholder("kind", kindName()).addPlaceholder("group", name).send(sender);
            return null;
        }
        return group;
    }

    private static @Nullable UUID member(FCommandSender sender, String player) {
        UUID uuid = playerUuid(player);
        if (uuid == null) {
            PLAYER_UNKNOWN.addPlaceholder("player", player).send(sender);
        }
        return uuid;
    }

    private void answer(FCommandSender sender, Group group, GateResult result, String change) {
        if (result.isPassed()) {
            done(sender, group, change);
        } else {
            refuse(sender, result.explain().toPlainText());
        }
    }

    private void done(FCommandSender sender, Group group, String change) {
        DONE.addPlaceholder("kind", kindName()).addPlaceholder("group", group.getName()).addPlaceholder("change", change).send(sender);
    }

    private static void refuse(FCommandSender sender, String reason) {
        REFUSED.addPlaceholder("reason", reason).send(sender);
    }

    private String kindName() {
        return kind.getKey().getName();
    }

    private static String nameOf(UUID uuid) {
        FPlayer online = EverNifeCore.getPlatform().getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString();
    }

    private static @Nullable UUID playerUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAUuid) {
            FPlayer online = EverNifeCore.getPlatform().getPlayer(text);
            return online == null ? null : online.getUniqueId();
        }
    }
}
