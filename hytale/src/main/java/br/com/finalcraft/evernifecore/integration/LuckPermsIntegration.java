package br.com.finalcraft.evernifecore.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.query.QueryOptions;

import java.util.UUID;

public class LuckPermsIntegration {

    public static LuckPerms getApi() {
        return LuckPermsProvider.get();
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
        if (value == null){
            // clear any existing meta nodes with the same key - we want to override
            getOrLoadUser(playerUuid).getData(DataType.NORMAL).clear(node -> node.getKey().equalsIgnoreCase(name));
        }else {
            // add the new node
            getOrLoadUser(playerUuid).getData(DataType.NORMAL).add(MetaNode.builder(name, value).build());
        }
    }

}
