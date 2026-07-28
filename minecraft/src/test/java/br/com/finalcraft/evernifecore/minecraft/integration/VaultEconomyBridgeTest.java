package br.com.finalcraft.evernifecore.minecraft.integration;

import br.com.finalcraft.evernifecore.economy.EcoResponse;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.testing.EconomyConformance;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two Vault bridges against a fake economy, which is where the historical bugs lived: v1 emulating
 * {@code set} by moving the difference, v2 setting for real, and every v2 transaction being stamped with
 * the same author.
 *
 * <p>The fakes are dynamic proxies because both Vault interfaces are wide and only a handful of their
 * methods matter here - and because a mock library would be the first one this repository owns.</p>
 */
public class VaultEconomyBridgeTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @Test
    void theClassicVaultBridgeConformsToTheContract() {
        List<String> failures = EconomyConformance.checkMutating(v1Bridge(new FakeEconomy()), PLAYER_UUID);

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void theVaultUnlockedBridgeConformsToTheContract() {
        List<String> failures = EconomyConformance.checkMutating(v2Bridge(new FakeEconomy()), PLAYER_UUID);

        assertEquals(Collections.<String>emptyList(), failures);
    }

    @Test
    void settingTheBalanceItAlreadyHasNeverReachesTheProvider() {
        FakeEconomy v1Economy = new FakeEconomy();
        FakeEconomy v2Economy = new FakeEconomy();
        IEconomyProvider v1 = v1Bridge(v1Economy);
        IEconomyProvider v2 = v2Bridge(v2Economy);
        v1.set(PLAYER_UUID, new BigDecimal("100"));
        v2.set(PLAYER_UUID, new BigDecimal("100"));
        int v1Transactions = v1Economy.transactions;
        int v2Transactions = v2Economy.transactions;

        assertTrue(v1.set(PLAYER_UUID, new BigDecimal("100")).isSuccess());
        assertTrue(v2.set(PLAYER_UUID, new BigDecimal("100")).isSuccess());

        assertEquals(v1Transactions, v1Economy.transactions, "v1 moved money to reach a balance it was already at");
        assertEquals(v2Transactions, v2Economy.transactions, "v2 wrote a no-op into the provider's ledger");
    }

    @Test
    void everyVaultUnlockedTransactionIsStampedWithTheSameAuthor() {
        FakeEconomy economy = new FakeEconomy();
        IEconomyProvider v2 = v2Bridge(economy);

        v2.give(PLAYER_UUID, BigDecimal.TEN);
        v2.take(PLAYER_UUID, BigDecimal.ONE);
        v2.set(PLAYER_UUID, new BigDecimal("42"));
        v2.getBalance(PLAYER_UUID);

        assertEquals(Collections.singleton(IEconomyProvider.TRANSACTION_SOURCE), economy.authors);
    }

    @Test
    void aRefusedTransactionCarriesTheProvidersOwnText() {
        FakeEconomy economy = new FakeEconomy();
        economy.refusalMessage = "the bank is closed";

        EcoResponse response = v2Bridge(economy).give(PLAYER_UUID, BigDecimal.TEN);

        assertEquals(EcoResponse.Reason.PROVIDER_ERROR, response.getReason());
        assertEquals("the bank is closed", response.getDetail());
    }

    // =========================================================================
    // The fakes
    // =========================================================================

    private static IEconomyProvider v1Bridge(FakeEconomy economy) {
        Object vaultEconomy = Proxy.newProxyInstance(
                net.milkbowl.vault.economy.Economy.class.getClassLoader(),
                new Class<?>[]{net.milkbowl.vault.economy.Economy.class},
                (proxy, method, args) -> economy.answerV1(method.getName(), args));

        //The uuid conversion is injected, so this test needs no running server.
        return new VaultEconomyV1(vaultEconomy, VaultEconomyBridgeTest::offlinePlayerOf);
    }

    private static IEconomyProvider v2Bridge(FakeEconomy economy) {
        Object vaultEconomy = Proxy.newProxyInstance(
                net.milkbowl.vault2.economy.Economy.class.getClassLoader(),
                new Class<?>[]{net.milkbowl.vault2.economy.Economy.class},
                (proxy, method, args) -> economy.answerV2(method.getName(), args));

        return new VaultEconomyV2(vaultEconomy);
    }

    private static OfflinePlayer offlinePlayerOf(UUID uniqueId) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, args) -> "getUniqueId".equals(method.getName()) ? uniqueId : null);
    }

    /**
     * One balance map answering both Vault generations, so the two bridges are checked against the same
     * economy. The v1 types stay fully qualified because both APIs name their classes {@code Economy}
     * and {@code EconomyResponse}, and only one of the two can be imported.
     */
    private static final class FakeEconomy {

        private final Map<UUID, BigDecimal> balances = new HashMap<UUID, BigDecimal>();
        private final Set<String> authors = new HashSet<String>();
        private int transactions;
        private String refusalMessage;

        Object answerV1(String method, Object[] args) {
            if ("getBalance".equals(method)) {
                return balanceOf(uuidOf(args[0])).doubleValue();
            }
            if ("has".equals(method)) {
                return balanceOf(uuidOf(args[0])).compareTo(BigDecimal.valueOf((Double) args[1])) >= 0;
            }
            if ("depositPlayer".equals(method)) {
                return v1Response(uuidOf(args[0]), BigDecimal.valueOf((Double) args[1]));
            }
            if ("withdrawPlayer".equals(method)) {
                return v1Response(uuidOf(args[0]), BigDecimal.valueOf((Double) args[1]).negate());
            }
            if ("format".equals(method)) {
                return "$" + args[0];
            }
            throw new UnsupportedOperationException("the v1 fake was not taught " + method);
        }

        Object answerV2(String method, Object[] args) {
            if ("format".equals(method)) {
                return "$" + args[args.length - 1]; //the single-argument overload carries no plugin name
            }

            authors.add((String) args[0]);
            if ("balance".equals(method)) {
                return balanceOf((UUID) args[1]);
            }
            if ("has".equals(method)) {
                return balanceOf((UUID) args[1]).compareTo((BigDecimal) args[2]) >= 0;
            }
            if ("deposit".equals(method)) {
                return v2Response((UUID) args[1], (BigDecimal) args[2]);
            }
            if ("withdraw".equals(method)) {
                return v2Response((UUID) args[1], ((BigDecimal) args[2]).negate());
            }
            if ("set".equals(method)) {
                return v2SetResponse((UUID) args[1], (BigDecimal) args[2]);
            }
            throw new UnsupportedOperationException("the v2 fake was not taught " + method);
        }

        private net.milkbowl.vault.economy.EconomyResponse v1Response(UUID playerUUID, BigDecimal delta) {
            if (refusalMessage != null) {
                return new net.milkbowl.vault.economy.EconomyResponse(delta.doubleValue(), balanceOf(playerUUID).doubleValue(),
                        net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, refusalMessage);
            }
            transactions++;
            BigDecimal balance = balanceOf(playerUUID).add(delta);
            balances.put(playerUUID, balance);
            return new net.milkbowl.vault.economy.EconomyResponse(delta.doubleValue(), balance.doubleValue(),
                    net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, null);
        }

        private EconomyResponse v2Response(UUID playerUUID, BigDecimal delta) {
            return v2Store(playerUUID, delta, balanceOf(playerUUID).add(delta));
        }

        private EconomyResponse v2SetResponse(UUID playerUUID, BigDecimal target) {
            return v2Store(playerUUID, target, target);
        }

        private EconomyResponse v2Store(UUID playerUUID, BigDecimal amount, BigDecimal balance) {
            if (refusalMessage != null) {
                return new EconomyResponse(amount, balanceOf(playerUUID), EconomyResponse.ResponseType.FAILURE, refusalMessage);
            }
            transactions++;
            balances.put(playerUUID, balance);
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        private BigDecimal balanceOf(UUID playerUUID) {
            BigDecimal balance = balances.get(playerUUID);
            return balance != null ? balance : BigDecimal.ZERO;
        }

        private static UUID uuidOf(Object offlinePlayer) {
            return ((OfflinePlayer) offlinePlayer).getUniqueId();
        }
    }

}
