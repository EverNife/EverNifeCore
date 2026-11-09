package br.com.finalcraft.evernifecore.group.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.time.ECClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /ecore group <player>}: every group a player is a member of, of every manager and every provider, with what an
 * admin needs to understand it. Only reads - groups are created and changed by the plugin that owns them.
 */
public final class CoreGroupCommands {

    @FCLocale(lang = LocaleType.EN_US, text = "&4&l ▶ &cName the player whose groups to read: &e/ecore group <player>")
    @FCLocale(lang = LocaleType.PT_BR, text = "&4&l ▶ &cInforme o jogador cujos grupos ler: &e/ecore group <jogador>")
    public static LocaleMessage NEEDS_A_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "&6Groups of &e${player}&6: &e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "&6Grupos de &e${player}&6: &e${count}")
    public static LocaleMessage HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "&e${group} &7leader &f${leader} &7members &f${members}")
    @FCLocale(lang = LocaleType.PT_BR, text = "&e${group} &7líder &f${leader} &7membros &f${members}")
    private static LocaleMessage PROVIDER_GROUP;

    @FCLocale(lang = LocaleType.EN_US, text = "&e${group} &7kind &f${kind} &7leader &f${leader} &7online &f${online}/${size}")
    @FCLocale(lang = LocaleType.PT_BR, text = "&e${group} &7tipo &f${kind} &7líder &f${leader} &7online &f${online}/${size}")
    private static LocaleMessage GROUP_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = " &7roles &f${roles} &7member since &f${since} &7expires &f${expires}")
    @FCLocale(lang = LocaleType.PT_BR, text = " &7cargos &f${roles} &7membro desde &f${since} &7expira &f${expires}")
    private static LocaleMessage MEMBER_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = " &7roles &f${roles} &7member since &f${since} &7never expires")
    @FCLocale(lang = LocaleType.PT_BR, text = " &7cargos &f${roles} &7membro desde &f${since} &7nunca expira")
    private static LocaleMessage MEMBER_LINE_NEVER_EXPIRES;

    @FCLocale(lang = LocaleType.EN_US, text = " &7flag &f${flag} &7= &e${value} &7<- ${group}")
    @FCLocale(lang = LocaleType.PT_BR, text = " &7flag &f${flag} &7= &e${value} &7<- ${group}")
    private static LocaleMessage FLAG_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = " &7flag &f${flag} &7= &e${value} &7<- ${group} &7(${expires})")
    @FCLocale(lang = LocaleType.PT_BR, text = " &7flag &f${flag} &7= &e${value} &7<- ${group} &7(${expires})")
    private static LocaleMessage FLAG_LINE_EXPIRING;

    private CoreGroupCommands() {
    }

    /** Sends {@code target}'s groups to {@code sender}. */
    public static void show(FCommandSender sender, FPlayer target) {
        List<GroupId> ids = GroupLookup.get().getGroupsOf(target.getUniqueId());
        HEADER.addPlaceholder("player", target.getName()).addPlaceholder("count", ids.size()).send(sender);
        for (GroupId id : ids) {
            Group group = GroupLookup.get().byId(id);
            if (group == null) {   //a provider's group: only what a provider answers
                UUID leader = GroupLookup.get().getLeader(id);
                PROVIDER_GROUP.custom().addPlaceholder("group", id).addPlaceholder("leader", leader == null ? "-" : nameOf(leader))
                        .addPlaceholder("members", GroupLookup.get().getMembers(id).size()).send(sender);
                continue;
            }
            describe(sender, group, target.getUniqueId());
        }
    }

    private static void describe(FCommandSender sender, Group group, UUID player) {
        GROUP_LINE.custom().addPlaceholder("group", group.getId()).addPlaceholder("kind", group.getKind().getKey())
                .addPlaceholder("leader", group.getLeader() == null ? "-" : nameOf(group.getLeader()))
                .addPlaceholder("online", group.getOnlineMembers().size()).addPlaceholder("size", group.size()).send(sender);
        GroupMember member = group.getMember(player);
        if (member != null) {
            String roles = member.getRoles().stream().map(GroupRole::getName).collect(Collectors.joining(", "));
            (member.getExpiresAt() == null ? MEMBER_LINE_NEVER_EXPIRES : MEMBER_LINE).custom().addPlaceholder("roles", roles.isEmpty() ? "-" : roles)
                    .addPlaceholder("since", member.getJoinedAt()).addPlaceholder("expires", expiry(sender, member.getExpiresAt())).send(sender);
        }
        for (Map.Entry<FlagKey, FlagValue<?>> entry : FlagRegistry.get().supportedOf(group.getFlags()).entrySet()) {
            FlagValue<?> value = entry.getValue();
            (value.getExpiresAt() == null ? FLAG_LINE : FLAG_LINE_EXPIRING).custom().addPlaceholder("flag", entry.getKey())
                    .addPlaceholder("value", value.getValue()).addPlaceholder("group", group.getId())
                    .addPlaceholder("expires", expiry(sender, value.getExpiresAt())).send(sender);
        }
    }

    private static String expiry(FCommandSender viewer, Instant at) {
        return at == null ? "" : at + " (" + CoreRegionCommands.remaining(viewer, Duration.between(ECClock.now(), at)) + ")";
    }

    private static String nameOf(UUID uuid) {
        FPlayer online = EverNifeCore.getPlatform().getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString();
    }
}
