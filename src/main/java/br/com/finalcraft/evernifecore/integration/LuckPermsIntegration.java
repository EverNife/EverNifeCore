package br.com.finalcraft.evernifecore.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

public class LuckPermsIntegration {

    public static LuckPerms getApi() {
        return LuckPermsProvider.get();
    }

    public static User getUser(Player player){
        return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
    }

    public static String getMetaValue(Player player, String name){
        return getUser(player).getCachedData().getMetaData(QueryOptions.nonContextual()).getMetaValue(name);
    }

    public static void setMetaValue(Player player, String name, String value){
        if (value == null){
            // clear any existing meta nodes with the same key - we want to override
            getUser(player).getData(DataType.NORMAL).clear(node -> node.getKey().equalsIgnoreCase(name));
        }else {
            // add the new node
            getUser(player).getData(DataType.NORMAL).add(MetaNode.builder(name, value).build());
        }
    }

}
