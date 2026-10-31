package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.GroupCases;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * The group engine on a Bukkit server: {@code glow-members} makes the bot glow from 1.9 on ({@code Player.isGlowing()},
 * read by reflection since 1.7.10 and 1.8 have no such method) and reports itself unsupported below; the group's
 * {@code deny-friendly-fire} reaches the bot; {@code max-members} refuses past its ceiling; {@code /ecore group <bot>}
 * lists the bot's party.
 */
public final class GroupsSuite implements ConformanceSuite {

    private final ECPluginData plugin;

    public GroupsSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "groups";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.of("glow-members", ctx, player -> GroupCases.glowMembers(plugin, ctx.getPlayerOrNull(),
                        MCVersion.isHigherEquals(MCDetailedVersion.v1_9_R1), member -> isGlowing(Bukkit.getPlayer(member.getUniqueId())))),
                PlayerCases.of("deny-friendly-fire-reaches-the-member", ctx,
                        player -> GroupCases.friendlyFireReachesTheMember(plugin, ctx.getPlayerOrNull())),
                PlayerCases.of("max-members-refuses-past-the-ceiling", ctx,
                        player -> GroupCases.maxMembersRefusesPastTheCeiling(plugin, ctx.getPlayerOrNull())),
                PlayerCases.of("group-command-lists-the-party", ctx,
                        player -> GroupCases.groupCommandListsTheParty(plugin, ctx.getPlayerOrNull())));
    }

    /** {@code Player.isGlowing()}; null where the method does not exist. */
    private static Boolean isGlowing(Player player) {
        try {
            Method glowing = player.getClass().getMethod("isGlowing");
            return (Boolean) glowing.invoke(player);
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }
}
