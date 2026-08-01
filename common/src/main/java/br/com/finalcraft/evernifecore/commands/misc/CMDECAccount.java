package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

/**
 * Admin/player command over the account/identity layer ({@link Accounts}).
 *
 * <p>{@code info} inspects the account a player belongs to. {@code link} fuses the accounts of two
 * platform identities (identity only - each member's account-wide data is absorbed at that member's
 * next login). {@code unlink} makes a member stand alone again (it starts fresh; the account keeps
 * the shared data). {@code migrate} forces the login-time data reconciliation for an offline player.
 * External identities (Discord, a registration site, ...) are linked through the
 * {@link Accounts#linkExternal(java.util.UUID, String, String)} API by the integrating
 * plugin/bridge, not through this command.</p>
 *
 * <pre>
 * /ecaccount info &lt;player&gt;
 * /ecaccount link &lt;target&gt; &lt;source&gt;
 * /ecaccount unlink &lt;player&gt;
 * /ecaccount migrate &lt;player&gt;
 * </pre>
 */
@FinalCMD(
        aliases = {"ecaccount"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT
)
public class CMDECAccount {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe account layer is not ready yet (PlayerController is not bootstrapped).")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cA camada de contas ainda não está pronta (o PlayerController não foi inicializado).")
    private static LocaleMessage NOT_READY;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §6Account §e[${account}]§6 has §e${count}§6 linked identity(ies):")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §6A conta §e[${account}]§6 tem §e${count}§6 identidade(s) vinculada(s):")
    private static LocaleMessage INFO_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  - §f${provider}§7:§f${uid} §7(${name})")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  - §f${provider}§7:§f${uid} §7(${name})")
    private static LocaleMessage INFO_MEMBER;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §aIdentities linked into account §e[${account}]§a. Each member's data is absorbed at their next login.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §aIdentidades vinculadas na conta §e[${account}]§a. Os dados de cada membro são absorvidos no próximo login dele.")
    private static LocaleMessage LINK_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §a${player} now stands alone and starts fresh at the next login. Account §e[${account}]§a keeps the shared data.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §a${player} agora está sozinho e começa zerado no próximo login. A conta §e[${account}]§a mantém os dados compartilhados.")
    private static LocaleMessage UNLINK_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §aAccount data of ${player} migrated to the current account.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §aDados de conta de ${player} migrados para a conta atual.")
    private static LocaleMessage MIGRATE_DONE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §6Nothing to migrate: ${player} is already on its current account key.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §6Nada a migrar: ${player} já está na chave de conta atual.")
    private static LocaleMessage MIGRATE_NOTHING;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cAccount operation failed: ${error}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cOperação de conta falhou: ${error}")
    private static LocaleMessage OPERATION_FAILED;

    @FinalCMD.SubCMD(
            subcmd = "info",
            usage = "<player>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show the account a player belongs to."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra a conta a que um jogador pertence.")
            }
    )
    public void info(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0)) {
            helpLine.sendTo(sender);
            return;
        }
        if (!checkLayerReady(sender)) {
            return;
        }
        PlayerData playerData = argumentos.get(0).getPlayerData();
        if (playerData == null) {
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        //resolve through the backend (not the cache-only fast path): an admin inspecting the layer
        //must see the STORED account, not a fabricated singleton for a not-yet-warm player
        PlayerController.whenCompleteOnMainThread(
                Accounts.get().account(playerData.getUniqueId()),
                (account, error) -> {
                    if (error != null) {
                        sendFailure(sender, error);
                        return;
                    }
                    sendAccountInfo(sender, account);
                });
    }

    private void sendAccountInfo(FCommandSender sender, Account account) {
        INFO_HEADER
                .addPlaceholder("account", account.getAccountId())
                .addPlaceholder("count", account.getMembers().size())
                .send(sender);
        for (AccountMember member : account.getMembers()) {
            INFO_MEMBER
                    .addPlaceholder("provider", member.getProvider())
                    .addPlaceholder("uid", member.getProviderUid())
                    .addPlaceholder("name", member.getName() != null ? member.getName() : "?")
                    .send(sender);
        }
    }

    @FinalCMD.SubCMD(
            subcmd = "link",
            usage = "<target> <source>",
            //own permission node: an irreversible cross-player data merge must never ride the same
            //node as the read-only 'info'
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT_LINK,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Link two identities into one account (data follows at each member's next login)."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Vincula duas identidades em uma conta (os dados seguem no próximo login de cada membro).")
            }
    )
    public void link(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0, 1)) {
            helpLine.sendTo(sender);
            return;
        }
        if (!checkLayerReady(sender)) {
            return;
        }
        PlayerData target = argumentos.get(0).getPlayerData();
        PlayerData source = argumentos.get(1).getPlayerData();
        if (target == null) {
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }
        if (source == null) {
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(1));
            return;
        }

        PlayerController.whenCompleteOnMainThread(
                Accounts.get().link(target.getUniqueId(), source.getUniqueId()),
                (account, error) -> {
                    if (error != null) {
                        sendFailure(sender, error);
                        return;
                    }
                    LINK_DONE.addPlaceholder("account", account.getAccountId()).send(sender);
                });
    }

    @FinalCMD.SubCMD(
            subcmd = "unlink",
            usage = "<player>",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT_LINK,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Unlink a member from its account (the member starts fresh; the account keeps the data)."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Desvincula um membro da sua conta (o membro começa zerado; a conta mantém os dados).")
            }
    )
    public void unlink(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0)) {
            helpLine.sendTo(sender);
            return;
        }
        if (!checkLayerReady(sender)) {
            return;
        }
        PlayerData playerData = argumentos.get(0).getPlayerData();
        if (playerData == null) {
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        PlayerController.whenCompleteOnMainThread(
                Accounts.get().unlink(playerData.getUniqueId()),
                (account, error) -> {
                    if (error != null) {
                        sendFailure(sender, error);
                        return;
                    }
                    UNLINK_DONE
                            .addPlaceholder("player", playerData.getName())
                            .addPlaceholder("account", account.getAccountId())
                            .send(sender);
                });
    }

    @FinalCMD.SubCMD(
            subcmd = "migrate",
            usage = "<player>",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT_LINK,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Force the account-data reconciliation of an offline player (it runs at login otherwise)."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Força a reconciliação de dados de conta de um jogador offline (roda no login normalmente).")
            }
    )
    public void migrate(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0)) {
            helpLine.sendTo(sender);
            return;
        }
        if (!checkLayerReady(sender)) {
            return;
        }
        PlayerData playerData = argumentos.get(0).getPlayerData();
        if (playerData == null) {
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        PlayerController.whenCompleteOnMainThread(
                PlayerController.migrateAccountData(playerData.getUniqueId()),
                (migrated, error) -> {
                    if (error != null) {
                        sendFailure(sender, error);
                        return;
                    }
                    LocaleMessage message = migrated ? MIGRATE_DONE : MIGRATE_NOTHING;
                    message.addPlaceholder("player", playerData.getName()).send(sender);
                });
    }

    /**
     * Shared readiness guard of every subcommand. Both halves now mean the same thing - the storage
     * boot has not finished, or it failed - so they answer with one message. The layer is not
     * something an admin switches on, which is why there is no "it is turned off" case to report.
     */
    private boolean checkLayerReady(FCommandSender sender) {
        if (PlayerController.get() == null || !Accounts.isEnabled()) {
            NOT_READY.send(sender);
            return false;
        }
        return true;
    }

    private static void sendFailure(FCommandSender sender, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        OPERATION_FAILED.addPlaceholder("error", String.valueOf(cause.getMessage())).send(sender);
    }
}
