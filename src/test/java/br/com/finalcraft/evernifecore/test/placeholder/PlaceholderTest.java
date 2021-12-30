package br.com.finalcraft.evernifecore.test.placeholder;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Random;
import java.util.UUID;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaceholderTest {

    private static class SimplePlayer { //Class With Primitives Classes in Construtor
        String name;
        UUID uuid;
        int kills = new Random().nextInt(100);
        int deaths = new Random().nextInt(100);

        public String getName() {
            return name;
        }

        public UUID getUuid() {
            return uuid;
        }

        public int getKills() {
            return kills;
        }

        public int getDeaths() {
            return deaths;
        }

        @Override
        public String toString() {
            return "SimplePlayer{" +
                    "name='" + name + '\'' +
                    ", uuid=" + uuid +
                    ", kills=" + kills +
                    ", deaths=" + deaths +
                    '}';
        }
    }

    @Test
    @Order(0)
    public void testeConstrutor() {
        RegexReplacer<SimplePlayer> REGEX_REPLACER = new RegexReplacer<SimplePlayer>()
                .addMappedParser("name", SimplePlayer::getName)
                .addMappedParser("uuid", SimplePlayer::getUuid)
                .addMappedParser("kills", SimplePlayer::getKills)
                .addMappedParser("deaths", SimplePlayer::getDeaths)
                .addMappedParser("kdr", simplePlayer -> simplePlayer.getKills() / Math.max(1, simplePlayer.getDeaths()))
                .addMappedParser("math_kills_doubled", simplePlayer -> "Wrong");

        REGEX_REPLACER.addProvider("math")
                .addMappedParser("kills_doubled", simplePlayer -> simplePlayer.getKills() * 2)
                .addMappedParser("deaths_doubled", simplePlayer -> simplePlayer.getKills() * 2)
                .addMappedParser("teste", simplePlayer -> simplePlayer.getKills() * 2);

        SimplePlayer simplePlayer = new SimplePlayer();
        simplePlayer.name = "Xablau";
        simplePlayer.uuid = UUID.randomUUID();

        String text = "Hello %name% (%uuid%), you have %kills% kills and %deaths% deaths, your KDR is %kdr%";

        System.out.println(
                REGEX_REPLACER.apply(text, simplePlayer)
        );

        assert REGEX_REPLACER.apply("%name%", simplePlayer).equals(String.valueOf(simplePlayer.name));
        assert REGEX_REPLACER.apply("%uuid%", simplePlayer).equals(String.valueOf(simplePlayer.uuid));
        assert REGEX_REPLACER.apply("%kills%", simplePlayer).equals(String.valueOf(simplePlayer.kills));
        assert REGEX_REPLACER.apply("%deaths%", simplePlayer).equals(String.valueOf(simplePlayer.deaths));

        assert REGEX_REPLACER.apply("%math_kills_doubled%", simplePlayer).equals("Wrong") == false;
    }


}
