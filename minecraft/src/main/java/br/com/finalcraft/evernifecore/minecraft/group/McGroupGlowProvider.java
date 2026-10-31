package br.com.finalcraft.evernifecore.minecraft.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.IGroupGlowProvider;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code glow-members} on Bukkit 1.9+: {@code Entity.setGlowing}, coloured by one main-scoreboard team per group (the
 * colour on 1.12+, the prefix before). A player already on another plugin's team keeps it and glows white.
 */
public final class McGroupGlowProvider implements IGroupGlowProvider {

    private static final ChatColor[] COLOURS = {ChatColor.AQUA, ChatColor.GREEN, ChatColor.YELLOW, ChatColor.LIGHT_PURPLE,
            ChatColor.GOLD, ChatColor.RED, ChatColor.BLUE, ChatColor.DARK_AQUA, ChatColor.DARK_GREEN, ChatColor.DARK_PURPLE};

    private final Map<UUID, Set<Group>> glowing = new ConcurrentHashMap<>();
    private volatile boolean warnedTeam;

    /** Whether this server can make an entity glow. */
    public static boolean isSupported() {
        return MCVersion.isHigherEquals(MCDetailedVersion.v1_9_R1);
    }

    @Override
    public void glow(FPlayer player, Group group) {
        Player bukkit = Bukkit.getPlayer(player.getUniqueId());
        if (bukkit == null) {
            return;
        }
        glowing.computeIfAbsent(player.getUniqueId(), uuid -> ConcurrentHashMap.newKeySet()).add(group);
        bukkit.setGlowing(true);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = board.getEntryTeam(bukkit.getName());
        if (current != null && !current.getName().startsWith("enc-g")) {
            if (!warnedTeam) {
                warnedTeam = true;
                EverNifeCore.getLog().info("glow-members: " + bukkit.getName() + " is on the team '" + current.getName() + "' of another"
                        + " plugin, so they glow without their group's colour (logged once).");
            }
            return;
        }
        Team team = teamOf(board, group);
        if (current != team) {
            team.addEntry(bukkit.getName());
        }
    }

    @Override
    public void unglow(FPlayer player, Group group) {
        Set<Group> groups = glowing.get(player.getUniqueId());
        if (groups == null || !groups.remove(group)) {
            return;
        }
        Player bukkit = Bukkit.getPlayer(player.getUniqueId());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team ours = board.getTeam(teamName(group));
        if (bukkit != null && ours != null && ours.hasEntry(bukkit.getName())) {
            ours.removeEntry(bukkit.getName());
        }
        if (groups.isEmpty()) {
            glowing.remove(player.getUniqueId());
            if (bukkit != null) {
                bukkit.setGlowing(false);
            }
        } else if (bukkit != null) {
            glow(player, groups.iterator().next());   //still glows for another group: take its colour
        }
    }

    private static String teamName(Group group) {
        return "enc-g" + Integer.toHexString(group.getId().toString().hashCode());
    }

    private static Team teamOf(Scoreboard board, Group group) {
        Team team = board.getTeam(teamName(group));
        if (team == null) {
            team = board.registerNewTeam(teamName(group));
            ChatColor colour = COLOURS[Math.floorMod(group.getId().toString().hashCode(), COLOURS.length)];
            if (MCVersion.isHigherEquals(MCDetailedVersion.v1_12_R1)) {
                team.setColor(colour);
            } else {
                team.setPrefix(colour.toString());
            }
        }
        return team;
    }
}
