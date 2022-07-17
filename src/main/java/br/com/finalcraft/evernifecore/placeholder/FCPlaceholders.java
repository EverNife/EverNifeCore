package br.com.finalcraft.evernifecore.placeholder;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.bukkit.entity.Player;

import java.util.List;

@Deprecated
public class FCPlaceholders {

    public static RegexReplacer<Player> PLAYER_REPLACER = new RegexReplacer<>();

    static {
        PLAYER_REPLACER.getDefaultProvider().addMappedParser(
                "player_name",
                "The Player's Name",
                player -> player.getName()
        );
        PLAYER_REPLACER.getDefaultProvider().addMappedParser(
                "player_uuid",
                "The Player's UUID",
                player -> player.getUniqueId()
        );
    }

    public static String parseText(String text, Player player){
        return PLAYER_REPLACER.apply(text, player);
    }

    public static List<String> parseText(List<String> texts, Player player){
        return PLAYER_REPLACER.apply(texts, player);
    }

}
