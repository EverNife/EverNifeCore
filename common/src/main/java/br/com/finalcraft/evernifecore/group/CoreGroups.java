package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** What the group engine registers on the core's enable: the {@code group} owner kind, the {@code dissolve-group} effect and the ACTOR layer. */
public final class CoreGroups {

    private CoreGroups() {
    }

    /** Registers everything under {@code core}, the EverNifeCore plugin data. */
    public static void registerAll(ECPluginData core) {
        ProgressOwners.register(core, new GroupOwners());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:dissolve-group")
                .describe("Dissolves a group; without 'group', the group that owns the context.")
                .param(ParamSpec.optional("group", ParamSpec.Kind.STRING, null, "The group's id, owner:name or owner:region/group."))
                .factory(params -> new DissolveGroup(params.getString("group")))
                .build());
        GroupLookup.get().attachToResolver(core);
    }

    /** {@code ProgressOwner.group(id)}: its members are the online explicit members, and it lives while the group exists. */
    private static final class GroupOwners implements ProgressOwnerProvider {
        @Override
        public String getKind() {
            return ProgressOwner.GROUP;
        }

        @Override
        public String describe(ProgressOwner owner, Locale locale) {
            Group group = GroupLookup.get().byId(GroupId.parse(owner.getId()));
            return group != null && group.getDisplayName() != null ? group.getDisplayName() : owner.getId();
        }

        @Override
        public Collection<FPlayer> getMembers(ProgressOwner owner) {
            List<FPlayer> online = new ArrayList<>();
            for (UUID uuid : GroupLookup.get().getMembers(GroupId.parse(owner.getId()))) {
                FPlayer player = EverNifeCore.getPlatform().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    online.add(player);
                }
            }
            return online;
        }

        @Override
        public boolean isAlive(ProgressOwner owner) {
            return GroupLookup.get().exists(GroupId.parse(owner.getId()));
        }
    }

    private static final class DissolveGroup implements Effect {
        private final @Nullable String group;

        DissolveGroup(@Nullable String group) {
            this.group = group;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            String id = group;
            if (id == null) {
                if (!ctx.getOwner().isKind(ProgressOwner.GROUP)) {
                    throw new IllegalStateException("dissolve-group without 'group' dissolves the group that owns the context, and the"
                            + " owner of this context is " + ctx.getOwner() + ": write group: <owner:name>, or run it with a group owner.");
                }
                id = ctx.getOwner().getId();
            }
            Group target = GroupLookup.get().byId(GroupId.parse(id));
            if (target == null) {
                //already gone (another effect, the sweeper): nothing left to dissolve
                ECDebugModule.GROUPS.debug("dissolve-group of {}: group {} does not exist.", ctx.getCaller(), id);
                return;
            }
            target.dissolve(DissolveCause.EFFECT);
        }

        @Override
        public String toString() {
            return "dissolve-group" + (group == null ? "" : " " + group);
        }
    }
}
