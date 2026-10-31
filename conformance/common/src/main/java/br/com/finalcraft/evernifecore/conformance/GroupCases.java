package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.DissolveCause;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.group.IGroupGlowProvider;
import br.com.finalcraft.evernifecore.group.command.CoreGroupCommands;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * The group engine on a live server, both platforms: a party of the case's plugin with the player and a second member
 * (offline, by uuid), the group's {@code deny-friendly-fire} reaching the player through the server's lookup,
 * {@code max-members} refusing past its ceiling, {@code /ecore group <player>} listing the party and, where the platform
 * glows, {@code glow-members} handed to its provider. Each case dissolves its party when it answers.
 */
public final class GroupCases {

    private static final AtomicInteger NEXT = new AtomicInteger();

    private GroupCases() {
    }

    /** A TRANSIENT party of {@code plugin}, its kind registered on first use; the player is its first member. */
    public static Group party(ECPluginData plugin, FPlayer player) {
        Group party = emptyParty(plugin);
        party.add(player.getUniqueId());
        return party;
    }

    private static Group emptyParty(ECPluginData plugin) {
        GroupManager manager = GroupManager.of(plugin);
        GroupKind kind = null;
        for (GroupKind known : manager.getKinds()) {
            if (known.getKey().getName().equals("ecc-party")) {
                kind = known;
            }
        }
        if (kind == null) {
            kind = GroupKind.of(plugin, "ecc-party").build();
            manager.registerKind(kind);
        }
        return manager.create("ecc-" + NEXT.incrementAndGet(), kind);
    }

    /**
     * The group's {@code deny-friendly-fire = TRUE} resolves TRUE at the player (the ACTOR layer registered on the real
     * server), the player shares the group with the second member, and this platform serves the DAMAGE source that the
     * flag decides on. One bot per server: the hit between two real players is the unit suite's.
     */
    public static Verdict friendlyFireReachesTheMember(ECPluginData plugin, FPlayer player) {
        Group party = party(plugin, player);
        UUID other = UUID.randomUUID();
        try {
            party.add(other);
            party.setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.TRUE);
            Tristate resolved = RegionLookup.get().resolve(CoreFlags.DENY_FRIENDLY_FIRE, player);
            boolean shared = GroupLookup.get().shareGroup(player.getUniqueId(), other);
            boolean served = ActionSupport.isServed(EventSource.DAMAGE);
            return Verdict.of(resolved == Tristate.TRUE && shared && served, "resolved=" + resolved + " shareGroup=" + shared
                    + " damageServed=" + served);
        } finally {
            party.dissolve(DissolveCause.OWNER);
        }
    }

    /** {@code max-members = 2}: a third member is refused by {@code add}, and the player's {@code join} of a full group says why. */
    public static Verdict maxMembersRefusesPastTheCeiling(ECPluginData plugin, FPlayer player) {
        Group party = party(plugin, player);
        Group full = emptyParty(plugin);
        try {
            party.setFlag(CoreFlags.MAX_MEMBERS, 2);
            boolean second = party.add(UUID.randomUUID());
            boolean third = party.add(UUID.randomUUID());
            full.setFlag(CoreFlags.MAX_MEMBERS, 1);
            full.add(UUID.randomUUID());
            GateResult join = full.join(player);
            String why = join.isPassed() ? "" : join.explain().getText();
            return Verdict.of(second && !third && !join.isPassed() && why.contains("is full"), "second=" + second + " third=" + third
                    + " join=" + join.isPassed() + " why=" + why + " size=" + party.size());
        } finally {
            party.dissolve(DissolveCause.OWNER);
            full.dissolve(DissolveCause.OWNER);
        }
    }

    /** {@code /ecore group <player>} (what {@code CoreGroupCommands} sends) lists the player's party by its id. */
    public static Verdict groupCommandListsTheParty(ECPluginData plugin, FPlayer player) {
        Group party = party(plugin, player);
        try {
            List<String> lines = new ArrayList<>();
            CoreGroupCommands.show(capturing(lines), player);
            String id = party.getId().toString();
            boolean listed = lines.stream().anyMatch(line -> line.contains(id));
            return Verdict.of(listed, "looked for " + id + " in " + lines);
        } finally {
            party.dissolve(DissolveCause.OWNER);
        }
    }

    /**
     * {@code glow-members = TRUE} on the party: where the platform registers an {@link IGroupGlowProvider},
     * {@code glowing} reads the player's native glow and must answer true; elsewhere {@code /ecore group} says the flag
     * is unsupported. {@code expectSupported} is what this server's version should do.
     */
    public static Verdict glowMembers(ECPluginData plugin, FPlayer player, boolean expectSupported, Function<FPlayer, Boolean> glowing) {
        boolean supported = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IGroupGlowProvider.class) != null;
        Group party = party(plugin, player);
        try {
            party.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
            if (!supported) {
                List<String> lines = new ArrayList<>();
                CoreGroupCommands.show(capturing(lines), player);
                boolean told = lines.stream().anyMatch(line -> line.contains("glow-members: unsupported on this platform"));
                return Verdict.of(!expectSupported && told, "glow unsupported (expected " + (expectSupported ? "supported" : "unsupported")
                        + "), told=" + told + " in " + lines);
            }
            Boolean glows = glowing.apply(player);
            return Verdict.of(expectSupported && Boolean.TRUE.equals(glows), "glow supported (expected "
                    + (expectSupported ? "supported" : "unsupported") + "), glowing=" + glows);
        } finally {
            party.dissolve(DissolveCause.OWNER);
        }
    }

    /** A console-like sender that keeps every line it is told, colors stripped. */
    private static FCommandSender capturing(List<String> lines) {
        return new FCommandSender() {
            @Override
            public String getName() {
                return "ECoreConformance";
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
                lines.add(FCColorUtil.stripColor(FCColorUtil.componentToString(component)));
            }

            @Override
            public Object getDelegate() {
                return this;
            }
        };
    }
}
