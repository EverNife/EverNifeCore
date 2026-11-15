package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestEconomy;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facade against the in-memory economy: every overload has to land on the same account, and the
 * rules that used to differ per platform (no-op set, negative amounts, a refused withdraw) have to
 * hold whichever entry point the caller picked.
 */
@ECoreTest
public class FCEcoUtilTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    private TestEconomy economy;

    @BeforeEach
    void installEconomy(ECoreTestWorld world) {
        economy = Economies.inMemory().balance(PLAYER_UUID, 100).build();
        world.withEconomy(economy);
    }

    @Test
    void readsTheBalanceThroughEveryKindOfKey() {
        FPlayer player = Senders.player("Petrus", PLAYER_UUID);

        assertEquals(100.0, FCEcoUtil.ecoGet(PLAYER_UUID));
        assertEquals(100.0, FCEcoUtil.ecoGet(player));
        assertEquals(100.0, FCEcoUtil.ecoGet(playerDataOf(PLAYER_UUID)));
        assertEquals(0, FCEcoUtil.ecoGetInBigDecimal(PLAYER_UUID).compareTo(new BigDecimal("100")));
        assertEquals(0, FCEcoUtil.ecoGetInBigDecimal(player).compareTo(new BigDecimal("100")));
        assertEquals(0, FCEcoUtil.ecoGetInBigDecimal(playerDataOf(PLAYER_UUID)).compareTo(new BigDecimal("100")));
    }

    @Test
    void givingMovesTheBalanceAndReportsTheNewOne() {
        EcoResponse response = FCEcoUtil.ecoGive(caller(), PLAYER_UUID, 50);

        assertTrue(response.isSuccess());
        assertEquals(0, response.getBalance().compareTo(new BigDecimal("150")));
        assertEquals(0, economy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("150")));
    }

    @Test
    void takingMoreThanTheBalanceFailsAndMovesNothing() {
        EcoResponse response = FCEcoUtil.ecoTake(caller(), PLAYER_UUID, 500);

        assertEquals(EcoResponse.Reason.INSUFFICIENT_FUNDS, response.getReason());
        assertEquals(0, response.getBalance().compareTo(new BigDecimal("100")));
        assertEquals(0, economy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("100")));
    }

    @Test
    void settingTheBalanceItAlreadyHasIsASuccess() {
        EcoResponse response = FCEcoUtil.ecoSet(caller(), PLAYER_UUID, 100);

        assertTrue(response.isSuccess());
        assertEquals(0, economy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("100")));
    }

    @Test
    void aNegativeAmountIsRefusedByEveryVerb() {
        assertEquals(EcoResponse.Reason.INVALID_AMOUNT, FCEcoUtil.ecoGive(caller(), PLAYER_UUID, -1).getReason());
        assertEquals(EcoResponse.Reason.INVALID_AMOUNT, FCEcoUtil.ecoTake(caller(), PLAYER_UUID, -1).getReason());
        assertEquals(EcoResponse.Reason.INVALID_AMOUNT, FCEcoUtil.ecoSet(caller(), PLAYER_UUID, -1).getReason());
        assertEquals(0, economy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("100")));
    }

    @Test
    void theDoubleAndBigDecimalOverloadsAgree() {
        FCEcoUtil.ecoGive(caller(), PLAYER_UUID, 0.5);
        FCEcoUtil.ecoGive(caller(), PLAYER_UUID, new BigDecimal("0.5"));

        assertEquals(0, economy.getBalance(PLAYER_UUID).compareTo(new BigDecimal("101")));
    }

    @Test
    void hasEnoughFollowsTheBalance() {
        assertTrue(FCEcoUtil.ecoHasEnough(PLAYER_UUID, 100));
        assertFalse(FCEcoUtil.ecoHasEnough(PLAYER_UUID, 100.01));
        assertTrue(FCEcoUtil.ecoHasEnough(PLAYER_UUID, 0), "asking for nothing is always affordable");
        assertTrue(FCEcoUtil.ecoHasEnough(PLAYER_UUID, -5));
    }

    @Test
    void formattingComesFromTheProvider() {
        assertEquals("$100", FCEcoUtil.ecoFormat(new BigDecimal("100")));
        assertTrue(FCEcoUtil.isEcoAvailable());
    }

    //The facade's IPlayerData overloads only read the uuid, so a proxy that answers that one question is
    //the whole collaborator - booting the PlayerData layer here would test the storage, not the facade.
    private static IPlayerData playerDataOf(UUID uniqueId) {
        return (IPlayerData) Proxy.newProxyInstance(
                IPlayerData.class.getClassLoader(),
                new Class<?>[]{IPlayerData.class},
                (proxy, method, args) -> "getUniqueId".equals(method.getName()) ? uniqueId : null);
    }

    @Test
    void aMoveIsMadeOnTheCallerThePluginPassed(@TempDir Path dir) {
        LogicWorld shop = LogicWorld.open("FakeShop", dir);
        try {
            List<BalanceChangeEvent> changes = shop.recorded(BalanceChangeEvent.class);
            Caller buy = shop.caller("shop/buy");

            FCEcoUtil.ecoTake(buy, PLAYER_UUID, 30);

            assertEquals(1, changes.size(), changes.toString());
            assertEquals(buy, changes.get(0).getCaller());
            assertSame(shop.getOwner(), changes.get(0).getCaller().getPlugin(), "the move is the shop's, not the core's");
        } finally {
            shop.close();
        }
    }

    private static Caller caller() {
        return Caller.of(EverNifeCore.getEcPluginData(), "test");
    }
}
