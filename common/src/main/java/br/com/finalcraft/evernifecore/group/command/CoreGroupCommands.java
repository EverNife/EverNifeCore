package br.com.finalcraft.evernifecore.group.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.group.IGroupGlowProvider;
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

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cName the player whose groups to read: §e/ecore group <player>")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cInforme o jogador cujos grupos ler: §e/ecore group <jogador>")
    public static LocaleMessage NEEDS_A_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Groups of §e${player}§6: §e${count}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Grupos de §e${player}§6: §e${count}")
    public static LocaleMessage HEADER;

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
                sender.sendMessage("§e" + id + " §7leader §f" + (leader == null ? "-" : nameOf(leader)) + " §7members §f"
                        + GroupLookup.get().getMembers(id).size());
                continue;
            }
            describe(sender, group, target.getUniqueId());
        }
    }

    private static void describe(FCommandSender sender, Group group, UUID player) {
        sender.sendMessage("§e" + group.getId() + " §7kind §f" + group.getKind().getKey() + " §7leader §f"
                + (group.getLeader() == null ? "-" : nameOf(group.getLeader())) + " §7online §f" + group.getOnlineMembers().size() + "/"
                + group.size());
        GroupMember member = group.getMember(player);
        if (member != null) {
            String roles = member.getRoles().stream().map(GroupRole::getName).collect(Collectors.joining(", "));
            sender.sendMessage(" §7roles §f" + (roles.isEmpty() ? "-" : roles) + " §7member since §f" + member.getJoinedAt()
                    + " §7expires §f" + expiry(member.getExpiresAt()));
        }
        for (Map.Entry<FlagKey, FlagValue<?>> entry : group.getFlags().entrySet()) {
            FlagValue<?> value = entry.getValue();
            String line = " §7flag §f" + entry.getKey() + " §7= §e" + value.getValue() + " §7<- " + group.getId()
                    + (value.getExpiresAt() == null ? "" : " §7(" + expiry(value.getExpiresAt()) + ")");
            if (entry.getKey().equals(CoreFlags.GLOW_MEMBERS.getKey())
                    && EverNifeCore.getProviders().getBaseProvider().provideOrNull(IGroupGlowProvider.class) == null) {
                line += " §c(glow-members: unsupported on this platform)";
            }
            sender.sendMessage(line);
        }
    }

    private static String expiry(Instant at) {
        return at == null ? "never" : at + " (" + CoreRegionCommands.remaining(Duration.between(ECClock.now(), at)) + ")";
    }

    private static String nameOf(UUID uuid) {
        FPlayer online = EverNifeCore.getPlatform().getPlayer(uuid);
        return online != null ? online.getName() : uuid.toString();
    }
}
