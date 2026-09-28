package br.com.finalcraft.evernifecore.ignore;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ECIgnoresTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static final IIgnoreProvider EVERYONE = (ignorer, ignored) -> true;

    @Test
    void nobodyIgnoresAnybodyUntilAPluginSaysSo() {
        ECIgnores ignores = new ECIgnores();

        for (IgnoreKind kind : IgnoreKind.values()) {
            assertFalse(ignores.hasProvider(kind));
            assertSame(IIgnoreProvider.NOBODY, ignores.getProvider(kind));
            assertFalse(ignores.isIgnoring(ALICE, BOB, kind));
        }
    }

    @Test
    void ignoringChatCoversPrivateMessagesButNotTheOtherWay() {
        ECIgnores chatOnly = new ECIgnores();
        chatOnly.setProvider(IgnoreKind.CHAT, EVERYONE);
        assertTrue(chatOnly.isIgnoring(ALICE, BOB, IgnoreKind.PRIVATE_MESSAGE));
        assertFalse(chatOnly.isIgnoring(ALICE, BOB, IgnoreKind.TELEPORT_REQUEST));

        ECIgnores privateOnly = new ECIgnores();
        privateOnly.setProvider(IgnoreKind.PRIVATE_MESSAGE, EVERYONE);
        assertTrue(privateOnly.isIgnoring(ALICE, BOB, IgnoreKind.PRIVATE_MESSAGE));
        assertFalse(privateOnly.isIgnoring(ALICE, BOB, IgnoreKind.CHAT));
    }

    @Test
    void theAnswerIsDirectional() {
        ECIgnores ignores = new ECIgnores();
        ignores.setProvider(IgnoreKind.TELEPORT_REQUEST, (ignorer, ignored) -> ignorer.equals(ALICE) && ignored.equals(BOB));

        assertTrue(ignores.isIgnoring(ALICE, BOB, IgnoreKind.TELEPORT_REQUEST));
        assertFalse(ignores.isIgnoring(BOB, ALICE, IgnoreKind.TELEPORT_REQUEST));
    }

    @Test
    void aPlayerNeverIgnoresThemselves() {
        ECIgnores ignores = new ECIgnores();
        ignores.setProvider(IgnoreKind.CHAT, EVERYONE);

        assertFalse(ignores.isIgnoring(ALICE, ALICE, IgnoreKind.CHAT));
    }

    @Test
    void theAsyncAnswerDefaultsToTheSynchronousOne() {
        ECIgnores ignores = new ECIgnores();
        ignores.setProvider(IgnoreKind.TELEPORT_REQUEST, (ignorer, ignored) -> ignorer.equals(ALICE));

        assertTrue(ignores.isIgnoringAsync(ALICE, BOB, IgnoreKind.TELEPORT_REQUEST).join());
        assertFalse(ignores.isIgnoringAsync(BOB, ALICE, IgnoreKind.TELEPORT_REQUEST).join());
        assertFalse(ignores.isIgnoringAsync(ALICE, BOB, IgnoreKind.CHAT).join());
        assertFalse(ignores.isIgnoringAsync(ALICE, ALICE, IgnoreKind.TELEPORT_REQUEST).join());
    }

    @Test
    void theAsyncChainWaitsForTheBroaderKindBeforeAnsweringNo() {
        CompletableFuture<Boolean> chatAnswer = new CompletableFuture<>();
        ECIgnores ignores = new ECIgnores();
        ignores.setProvider(IgnoreKind.PRIVATE_MESSAGE, (ignorer, ignored) -> false);
        ignores.setProvider(IgnoreKind.CHAT, asyncOnly(chatAnswer));

        CompletableFuture<Boolean> answer = ignores.isIgnoringAsync(ALICE, BOB, IgnoreKind.PRIVATE_MESSAGE);
        assertFalse(answer.isDone());

        chatAnswer.complete(true);
        assertTrue(answer.join());

        ECIgnores notIgnoring = new ECIgnores();
        notIgnoring.setProvider(IgnoreKind.CHAT, asyncOnly(CompletableFuture.completedFuture(false)));
        assertFalse(notIgnoring.isIgnoringAsync(ALICE, BOB, IgnoreKind.PRIVATE_MESSAGE).join());
    }

    @Test
    void theAsyncChainStopsAtTheFirstYes() {
        ECIgnores ignores = new ECIgnores();
        ignores.setProvider(IgnoreKind.PRIVATE_MESSAGE, EVERYONE);
        ignores.setProvider(IgnoreKind.CHAT, asyncOnly(new CompletableFuture<>())); //never completes

        assertTrue(ignores.isIgnoringAsync(ALICE, BOB, IgnoreKind.PRIVATE_MESSAGE).join());
    }

    private static IIgnoreProvider asyncOnly(CompletableFuture<Boolean> answer) {
        return new IIgnoreProvider() {
            @Override
            public boolean isIgnoring(UUID ignorer, UUID ignored) {
                return false;
            }

            @Override
            public CompletableFuture<Boolean> isIgnoringAsync(UUID ignorer, UUID ignored) {
                return answer;
            }
        };
    }

    @Test
    void removingAReplacedProviderKeepsItsSuccessor() {
        ECIgnores ignores = new ECIgnores();
        IIgnoreProvider first = (ignorer, ignored) -> true;
        IIgnoreProvider second = (ignorer, ignored) -> true;

        ignores.setProvider(IgnoreKind.CHAT, first);
        ignores.setProvider(IgnoreKind.CHAT, second);

        assertFalse(ignores.removeProvider(IgnoreKind.CHAT, first));
        assertSame(second, ignores.getProvider(IgnoreKind.CHAT));

        assertTrue(ignores.removeProvider(IgnoreKind.CHAT, second));
        assertFalse(ignores.hasProvider(IgnoreKind.CHAT));
    }
}
