package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The owner kinds the core answers: {@code player}, {@code global}, {@code region}, {@code world}. */
final class CoreOwners {

    private CoreOwners() {
    }

    static List<ProgressOwnerProvider> all() {
        List<ProgressOwnerProvider> all = new ArrayList<>();
        all.add(new PlayerOwners());
        all.add(new GlobalOwners());
        all.add(new RegionOwners());
        all.add(new WorldOwners());
        return all;
    }

    private static final class PlayerOwners implements ProgressOwnerProvider {
        @Override
        public String getKind() {
            return ProgressOwner.PLAYER;
        }

        @Override
        public String describe(ProgressOwner owner, Locale locale) {
            FPlayer player = online(owner);
            return player == null ? owner.getId() : player.getName();
        }

        @Override
        public Collection<FPlayer> getMembers(ProgressOwner owner) {
            FPlayer player = online(owner);
            return player == null ? Collections.<FPlayer>emptyList() : Collections.singletonList(player);
        }

        @Override
        public boolean isAlive(ProgressOwner owner) {
            return true;
        }

        private static FPlayer online(ProgressOwner owner) {
            FPlayer player = EverNifeCore.getPlatform().getPlayer(UUID.fromString(owner.getId()));
            return player != null && player.isOnline() ? player : null;
        }
    }

    private static final class GlobalOwners implements ProgressOwnerProvider {
        @Override
        public String getKind() {
            return ProgressOwner.GLOBAL;
        }

        @Override
        public String describe(ProgressOwner owner, Locale locale) {
            return "the server";
        }

        @Override
        public Collection<FPlayer> getMembers(ProgressOwner owner) {
            return EverNifeCore.getPlatform().getOnlinePlayers();
        }

        @Override
        public boolean isAlive(ProgressOwner owner) {
            return true;
        }
    }

    private static final class RegionOwners implements ProgressOwnerProvider {
        @Override
        public String getKind() {
            return ProgressOwner.REGION;
        }

        @Override
        public String describe(ProgressOwner owner, Locale locale) {
            return "region " + owner.getId();
        }

        /** The players whose region session is inside the region now. */
        @Override
        public Collection<FPlayer> getMembers(ProgressOwner owner) {
            RegionId id = RegionId.parse(owner.getId());
            List<FPlayer> inside = new ArrayList<>();
            for (RegionPlayerSession session : RegionLookup.get().getSessions()) {
                for (Region region : session.getCurrent()) {
                    if (region.getId().equals(id)) {
                        FPlayer player = session.getOnlinePlayer();
                        if (player != null) {
                            inside.add(player);
                        }
                        break;
                    }
                }
            }
            return inside;
        }

        @Override
        public boolean isAlive(ProgressOwner owner) {
            return RegionLookup.get().byId(RegionId.parse(owner.getId())) != null;
        }
    }

    private static final class WorldOwners implements ProgressOwnerProvider {
        @Override
        public String getKind() {
            return ProgressOwner.WORLD;
        }

        @Override
        public String describe(ProgressOwner owner, Locale locale) {
            return "world " + owner.getId();
        }

        @Override
        public Collection<FPlayer> getMembers(ProgressOwner owner) {
            List<FPlayer> inWorld = new ArrayList<>();
            for (FPlayer player : EverNifeCore.getPlatform().getOnlinePlayers()) {
                FLocation location = player.getLocation();
                if (location != null && location.getWorldName().equals(owner.getId())) {
                    inWorld.add(player);
                }
            }
            return inWorld;
        }

        @Override
        public boolean isAlive(ProgressOwner owner) {
            return true;
        }
    }
}
