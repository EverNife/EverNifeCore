package br.com.finalcraft.evernifecore.test.placeholder;

import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaceholderTest {

    @Data
    @AllArgsConstructor
    private static class SimplePlayer {
        final String name;
        final UUID uuid;
        final int kills = new Random().nextInt(100);
        final int deaths = new Random().nextInt(100);
    }

    @Test
    @Order(0)
    public void basicRegexReplacer() {
        RegexReplacer<SimplePlayer> REGEX_REPLACER = new RegexReplacer<SimplePlayer>()
                .addParser("name", SimplePlayer::getName)
                .addParser("uuid", SimplePlayer::getUuid)
                .addParser("kills", SimplePlayer::getKills)
                .addParser("deaths", SimplePlayer::getDeaths)
                .addParser("kdr", simplePlayer -> simplePlayer.getKills() / Math.max(1D, simplePlayer.getDeaths()))
                .addParser("math_kills_doubled", simplePlayer -> "Wrong")
                .addParser("math_kills_doubled_but_with_more_underlines", simplePlayer -> "Wrong Again");

        RegexReplacer<SimplePlayer> MATH_REPLACER = new RegexReplacer<SimplePlayer>()
                .addParser("kills_doubled", simplePlayer -> simplePlayer.getKills() * 2)
                .addParser("deaths_doubled", simplePlayer -> simplePlayer.getKills() * 2)
                .addParser("teste", simplePlayer -> simplePlayer.getKills() * 2);

        REGEX_REPLACER.addManipulator("math_{placeHolder}", MATH_REPLACER, (simplePlayer, manipulationContext) -> {

            String placeholder = manipulationContext.getString("{placeHolder}");

            return manipulationContext.quoteAndParse(simplePlayer, placeholder);
        });

        REGEX_REPLACER.addManipulator("math_{placeHolder}", MATH_REPLACER, (simplePlayer, simplePlayerRContext) -> {

            String placeholder = simplePlayerRContext.getString("{placeHolder}");
            String result = simplePlayerRContext.quoteAndParse(
                    simplePlayer, simplePlayerRContext.getString("{placeHolder}")
            );

            System.out.println(placeholder + ":" + result);

            return simplePlayerRContext.quoteAndParse(
                    simplePlayer, simplePlayerRContext.getString("{placeHolder}")
            );
        });

        SimplePlayer simplePlayer = new SimplePlayer( "Xablau", UUID.randomUUID());

        String text = "Hello %name% (%uuid%), you have %kills% kills and %deaths% deaths, your KDR is %kdr%";

        System.out.println(
                REGEX_REPLACER.apply(text, simplePlayer)
        );

        assert REGEX_REPLACER.apply("%name%", simplePlayer).equals(String.valueOf(simplePlayer.name));
        assert REGEX_REPLACER.apply("%uuid%", simplePlayer).equals(String.valueOf(simplePlayer.uuid));
        assert REGEX_REPLACER.apply("%kills%", simplePlayer).equals(String.valueOf(simplePlayer.kills));
        assert REGEX_REPLACER.apply("%deaths%", simplePlayer).equals(String.valueOf(simplePlayer.deaths));

        assert REGEX_REPLACER.apply("%math_kills_doubled%", simplePlayer).equals("Wrong") == false;
        assert REGEX_REPLACER.apply("%math_kills_doubled_but_with_more_underlines%", simplePlayer).equals("Wrong Again") == true;
    }

    @Test
    @Order(1)
    public void manipulableRegexReplacer() {

        LinkedHashMap<String, SimplePlayer> simplePlayerMap = new LinkedHashMap<>();
        simplePlayerMap.put("Xablau", new SimplePlayer("Xablau", UUID.fromString("00000000-0000-0000-0000-000000000000")));
        simplePlayerMap.put("Xablengs", new SimplePlayer("Xablengs", UUID.fromString("10000000-0000-0000-0000-000000000000")));
        simplePlayerMap.put("Xablings", new SimplePlayer("Xablings", UUID.fromString("20000000-0000-0000-0000-000000000000")));

        RegexReplacer<SimplePlayer> REGEX_REPLACER = new RegexReplacer<SimplePlayer>()
                .addParser("name", SimplePlayer::getName)
                .addParser("uuid", SimplePlayer::getUuid)
                .addParser("kills", SimplePlayer::getKills)
                .addParser("deaths", SimplePlayer::getDeaths);

        REGEX_REPLACER.addManipulator("{playerName}_{placeholder}", (simplePlayer, manipulationContext) -> {

            String playerName = manipulationContext.getString("{playerName}");
            String placeholder = manipulationContext.getString("{placeholder}");

            SimplePlayer dynamicPlayer = simplePlayerMap.get(playerName);

            if (dynamicPlayer == null){
                return "Player Not Found";
            }

            return REGEX_REPLACER.apply(Closures.PERCENT.quote(placeholder), dynamicPlayer);
        });

        System.out.println(REGEX_REPLACER.apply("%Xablau_name%", null));
        System.out.println(REGEX_REPLACER.apply("%Xablau_uuid%", null));

        assert REGEX_REPLACER.apply("%Xablau_uuid%", null).equals("00000000-0000-0000-0000-000000000000");
        assert REGEX_REPLACER.apply("%Xablau_uuid%", null).equals("00000000-0000-0000-0000-000000000000");
        assert REGEX_REPLACER.apply("%Xablau_uuid%", null).equals("00000000-0000-0000-0000-000000000000");

        REGEX_REPLACER.addManipulator("player_{playerIndex}_{placeholder}", (simplePlayer, manipulationContext) -> {

            String playerIndex = manipulationContext.getString("{playerIndex}");
            String placeholder = manipulationContext.getString("{placeholder}");

            Integer index = Integer.parseInt(playerIndex);

            SimplePlayer dynamicPlayer = new ArrayList<>(simplePlayerMap.values()).get(index);

            return REGEX_REPLACER.apply(Closures.PERCENT.quote(placeholder), dynamicPlayer);
        });

        System.out.println("\n");
        System.out.println(REGEX_REPLACER.apply("%player_0_name%", null));
        System.out.println(REGEX_REPLACER.apply("%player_1_name%", null));
        System.out.println(REGEX_REPLACER.apply("%player_2_name%", null));

        assert REGEX_REPLACER.apply("%player_0_name%", null).equals("Xablau");
        assert REGEX_REPLACER.apply("%player_1_name%", null).equals("Xablengs");
        assert REGEX_REPLACER.apply("%player_2_name%", null).equals("Xablings");

    }
}
