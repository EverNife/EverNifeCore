package br.com.finalcraft.evernifecore.placeholder;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.bukkit.entity.Player;

import java.util.List;

public class FCPlaceholders {

    public static RegexReplacer<Player> PLAYER_REPLACER = new RegexReplacer<>();

    static {
        PLAYER_REPLACER.getDefaultProvider().addSimpleParser(
                player -> player.getName(),
                "playername", "name"
        );
    }

    public static String parseText(String text, Player player){
        return PLAYER_REPLACER.apply(text, player);
    }

    public static List<String> parseText(List<String> texts, Player player){
        return PLAYER_REPLACER.apply(texts, player);
    }

}
