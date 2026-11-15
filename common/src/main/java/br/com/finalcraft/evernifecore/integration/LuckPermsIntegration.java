package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.time.BlockingJoinWarning;
import jakarta.annotation.Nullable;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsIntegration {

    public static LuckPerms getApi() {
        return LuckPermsProvider.get();
    }

    /** The user LuckPerms keeps in memory (online, or loaded a moment ago); null when it would have to load them. Never blocks. */
    public static @Nullable User getLoadedUser(UUID playerUuid){
        return getApi().getUserManager().getUser(playerUuid);
    }

    /** Loads the user on LuckPerms' own threads; a {@code join()} of it on the server thread is warned about. */
    public static CompletableFuture<User> loadUser(UUID playerUuid){
        return BlockingJoinWarning.watch(getApi().getUserManager().loadUser(playerUuid));
    }

    /** Whether {@code permission} is set and true on the loaded user; false when LuckPerms does not hold them in memory. Never blocks. */
    public static boolean holdsLoaded(UUID playerUuid, String permission){
        User user = getLoadedUser(playerUuid);
        return user != null && user.getCachedData().getPermissionData(QueryOptions.nonContextual()).checkPermission(permission) == Tristate.TRUE;
    }

    public static User getOrLoadUser(UUID playerUuid){
        User user = getApi().getUserManager().getUser(playerUuid);
        if (user != null){
            return user;
        }

        return getApi().getUserManager().loadUser(playerUuid).join();
    }

    public static String getMetaValue(UUID playerUuid, String name){
        return getOrLoadUser(playerUuid).getCachedData().getMetaData(QueryOptions.nonContextual()).getMetaValue(name);
    }

    public static void setMetaValue(UUID playerUuid, String name, String value){
        User user = getOrLoadUser(playerUuid);
        if (value == null){
            // clear any existing meta nodes with the same key
            user.getData(DataType.NORMAL).clear(node -> node.getKey().equalsIgnoreCase(name));
        }else {
            // override: drop same-key meta nodes before adding, so values never accumulate
            user.getData(DataType.NORMAL).clear(node -> node.getKey().equalsIgnoreCase(name));
            user.getData(DataType.NORMAL).add(MetaNode.builder(name, value).build());
        }
        // persist the mutation, otherwise the change is only in-memory
        getApi().getUserManager().saveUser(user).join();
    }

    public static boolean hasPermission(UUID playerUuid, String permission){
        return getOrLoadUser(playerUuid)
                .getCachedData()
                .getPermissionData(QueryOptions.nonContextual())
                .checkPermission(permission)
                .asBoolean();
    }

}
