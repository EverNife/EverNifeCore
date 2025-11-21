package br.com.finalcraft.evernifecore.placeholder.replacer;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.UUID;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RegexReplacerTest {

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
                .addParser("math_kills_doubled", simplePlayer -> "HigherPriorityPlaceholder1")
                .addParser("math_kills_doubled_but_with_more_underlines", simplePlayer -> "HigherPriorityPlaceholder2");

        RegexReplacer<SimplePlayer> MATH_REPLACER = new RegexReplacer<SimplePlayer>()
                .addParser("kills_doubled", simplePlayer -> simplePlayer.getKills() * 2)
                .addParser("kills_doubled_alternative", simplePlayer -> simplePlayer.getKills() * 2)
                .addParser("deaths_doubled", simplePlayer -> simplePlayer.getDeaths() * 2);

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

        SimplePlayer simplePlayer = new SimplePlayer( "X$blau", UUID.randomUUID());

        String text = "Hello %name% (%uuid%), you have %kills% kills and %deaths% deaths, your KDR is %kdr%";

        System.out.println(
                REGEX_REPLACER.apply(text, simplePlayer)
        );

        System.out.println(REGEX_REPLACER.apply("%name%", simplePlayer));
        assert REGEX_REPLACER.apply("%name%", simplePlayer).equals(String.valueOf(simplePlayer.name));
        assert REGEX_REPLACER.apply("%uuid%", simplePlayer).equals(String.valueOf(simplePlayer.uuid));
        assert REGEX_REPLACER.apply("%kills%", simplePlayer).equals(String.valueOf(simplePlayer.kills));
        assert REGEX_REPLACER.apply("%deaths%", simplePlayer).equals(String.valueOf(simplePlayer.deaths));

        //Using manipulator with the prefix %math_ we should end on the custom placeholder handling
        assert REGEX_REPLACER.apply("%math_kills_doubled_alternative%"  , simplePlayer).equals(String.valueOf(simplePlayer.kills * 2));
        assert REGEX_REPLACER.apply("%math_deaths_doubled%"             , simplePlayer).equals(String.valueOf(simplePlayer.deaths * 2));

        //Even though math_kills_doubled should be a duplicated amount of kills, based on the custom manipulator,
        // as we have a HIGHER_PRIORITY 'exact match' parser, it should return a different result
        assert REGEX_REPLACER.apply("%math_kills_doubled%", simplePlayer).equals("HigherPriorityPlaceholder1") == true;
        assert REGEX_REPLACER.apply("%math_kills_doubled_but_with_more_underlines%", simplePlayer).equals("HigherPriorityPlaceholder2") == true;

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
