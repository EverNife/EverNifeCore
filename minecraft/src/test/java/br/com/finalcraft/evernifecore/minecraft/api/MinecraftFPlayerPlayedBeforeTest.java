package br.com.finalcraft.evernifecore.minecraft.api;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** On Bukkit, "played before" is the server's own answer for the player file, passed through untouched. */
class MinecraftFPlayerPlayedBeforeTest {

    private static OfflinePlayer offlinePlayer(boolean playedBefore) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPlayedBefore")) {
                        return playedBefore;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theAnswerIsTheOfflinePlayersOwn() {
        assertTrue(MinecraftFPlayer.of(offlinePlayer(true)).hasPlayedBefore());
        assertFalse(MinecraftFPlayer.of(offlinePlayer(false)).hasPlayedBefore());
    }
}
