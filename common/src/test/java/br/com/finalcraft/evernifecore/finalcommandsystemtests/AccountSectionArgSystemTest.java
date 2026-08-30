package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.AccountSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An {@link AccountSection} as a command parameter. The token still names a PLAYER - same lookup, same
 * tab-completion, same {@code [online]} rule as a PDSection argument - but what reaches the method is
 * that player's ACCOUNT-wide row, so two identities linked into one account are handed the very same
 * instance. A parameter with no {@code @Arg}, and one declaring {@code fromSender}, read the sender's
 * own account.
 */
class AccountSectionArgSystemTest {

    //NEVER a plain @TempDir: the locale bootstrap's async save can race JUnit's cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) {
            harness.close();
        }
        PlayerDataWorld.tearDown();
    }

    @Test
    void aTokenNamesThePlayerAndTheValueIsTheirAccountRow() {
        harness = new FinalCmdTestHarness("AccountArgNamed", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_named")).accountSections(WalletSection.class).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerData alicePlayerData = PlayerController.handleLogin(alice, "Alice").join();

        NamedCommand executor = new NamedCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command, "an AccountSection subtype has to resolve to a parser of its own");

        harness.dispatch(command, new TestCommandSender("Console"), "Alice");

        assertNotNull(executor.captured, "the named player's account row never reached the method");
        assertEquals(alice, executor.captured.getAccountId(), "an unlinked player's account keys by the uuid");
        assertSame(alicePlayerData.getAccountSection(WalletSection.class).join(), executor.captured,
                "the parser must hand over the live cached row, not a second copy");
    }

    /** The account is the subject: whichever of its identities was typed, the row is the same one. */
    @Test
    void twoLinkedIdentitiesResolveToTheSameRow() {
        harness = new FinalCmdTestHarness("AccountArgLinked", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_linked")).accountSections(WalletSection.class).boot(tempDir);

        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        UUID canonicalId = linkIntoOneAccount(memberA, "MainName", memberB, "AltName");
        PlayerController.handleLogin(memberA, "MainName").join();
        PlayerController.handleLogin(memberB, "AltName").join();

        NamedCommand executor = new NamedCommand();
        FinalCMDPluginCommand command = harness.register(executor);

        harness.dispatch(command, new TestCommandSender("Console"), "MainName");
        WalletSection viaA = executor.captured;
        harness.dispatch(command, new TestCommandSender("Console"), "AltName");
        WalletSection viaB = executor.captured;

        assertEquals(canonicalId, viaA.getAccountId(), "a linked member's row keys by the canonical id");
        assertSame(viaA, viaB, "both identities of the account must be handed the same live row");
    }

    /** An unknown name has no account either: the lookup fails at the player, before any row is read. */
    @Test
    void anUnknownNameIsRefusedBeforeAnyRowIsRead() {
        harness = new FinalCmdTestHarness("AccountArgUnknown", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_unknown")).accountSections(WalletSection.class).boot(tempDir);

        NamedCommand executor = new NamedCommand();
        FinalCMDPluginCommand command = harness.register(executor);

        harness.dispatch(command, new TestCommandSender("Console"), "NobodyHere");

        assertNull(executor.captured, "a name nobody answers to must not run the method");
    }

    @Test
    void anOmittedAccountSectionIsTheSendersOwn() {
        harness = new FinalCmdTestHarness("AccountArgFromSender", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_from_sender")).accountSections(WalletSection.class).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();

        FromSenderCommand executor = new FromSenderCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command, "a command declaring @Arg(fromSender = true) has to register");

        harness.dispatch(command, new TestFPlayerSender("Alice", alice), "");

        assertNotNull(executor.captured, "the omitted argument was not answered from the sender");
        assertEquals(alice, executor.captured.getAccountId());
    }

    @Test
    void aContextualAccountSectionIsTheSendersOwn() {
        harness = new FinalCmdTestHarness("AccountArgContextual", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_contextual")).accountSections(WalletSection.class).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();

        ContextualCommand executor = new ContextualCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command, "a parameter with no @Arg needs a contextual parser for AccountSection");

        harness.dispatch(command, new TestFPlayerSender("Alice", alice), "");

        assertNotNull(executor.captured, "the sender's own account row was not injected");
        assertEquals(alice, executor.captured.getAccountId());
    }

    /** The console has no account, and the contextual parser says so by making the command player-only. */
    @Test
    void theConsoleGetsNoContextualAccountRow() {
        harness = new FinalCmdTestHarness("AccountArgConsole", tempDir);
        PlayerDataWorld.with(Storages.h2("account_arg_console")).accountSections(WalletSection.class).boot(tempDir);

        ContextualCommand executor = new ContextualCommand();
        FinalCMDPluginCommand command = harness.register(executor);

        harness.dispatch(command, new TestCommandSender("Console"), "");

        assertNull(executor.captured, "the console is nobody, so the method must not run");
    }

    // ------------------------------------------------------------------

    /** Persists one canonical account holding both identities, plus an alias row for each. */
    private UUID linkIntoOneAccount(UUID memberA, String nameA, UUID memberB, String nameB) {
        UUID canonicalId = UUID.randomUUID();
        Account canonical = Account.singleton(canonicalId, Accounts.PLATFORM_PROVIDER,
                canonicalId.toString(), "Owner");
        canonical.addMember(new AccountMember(Accounts.PLATFORM_PROVIDER, memberA.toString(), nameA));
        canonical.addMember(new AccountMember(Accounts.PLATFORM_PROVIDER, memberB.toString(), nameB));
        Accounts.get().getManager().saveAndCache(canonical).join();
        Accounts.get().getManager().saveAndCache(Account.alias(memberA, canonicalId)).join();
        Accounts.get().getManager().saveAndCache(Account.alias(memberB, canonicalId)).join();
        return canonicalId;
    }

    /** A conserved balance: absorbing two wallets of one account sums them. */
    public static class WalletSection extends AccountSection<WalletSection> {

        public long balance;

        public WalletSection() {
            //Required no-arg constructor
        }

        @Override
        public WalletSection merge(List<WalletSection> others) {
            WalletSection merged = new WalletSection();
            merged.balance = this.balance;
            for (WalletSection other : others) {
                merged.balance += other.balance;
            }
            return merged;
        }
    }

    public static class NamedCommand {

        WalletSection captured;

        @FinalCMD(aliases = {"accountargnamed"})
        public void run(FCommandSender sender, @Arg("<player>") WalletSection wallet) {
            this.captured = wallet;
        }
    }

    public static class FromSenderCommand {

        WalletSection captured;

        @FinalCMD(aliases = {"accountargfromsender"})
        public void run(FCommandSender sender, @Arg(value = "[player]", fromSender = true) WalletSection wallet) {
            this.captured = wallet;
        }
    }

    public static class ContextualCommand {

        WalletSection captured;

        @FinalCMD(aliases = {"accountargcontextual"})
        public void run(FCommandSender sender, WalletSection wallet) {
            this.captured = wallet;
        }
    }
}
