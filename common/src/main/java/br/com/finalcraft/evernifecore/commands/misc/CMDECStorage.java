package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserPDSectionId;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserStorageBackend;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.everydatabase.transfer.TransferError;

import java.util.UUID;

/**
 * Admin entry point over the PlayerData storage layer: a read-only health snapshot
 * ({@code status}) and a runtime backend migration for a single PDSection ({@code transfer}).
 *
 * <p>{@code status} shows the routing (which backend/collection each entity persists on) plus
 * the counters that reveal degradation BEFORE data is at risk - quit-flush retry backlog,
 * adopted optimistic-lock conflicts and the last failed write.</p>
 *
 * <p>{@code transfer} migrates a single PDSection's collection to another storage backend at
 * runtime. The PDSection is identified by its {@code PluginName:SectionSimpleName} id; the target
 * is a backend declared and enabled in storage.yml. The work is delegated to
 * {@link PlayerController#transferPDSection(Class, String)} - it freezes the section's writes,
 * copies through StorageTransfer (keeping the source as a backup), cuts over and persists the
 * choice into storage.yml. A maintenance window is recommended.</p>
 *
 * <pre>
 * /ecstorage status
 * /ecstorage transfer &lt;plugin:section&gt; &lt;backend&gt;
 * </pre>
 */
@FinalCMD(
        aliases = {"ecstorage"}
)
public class CMDECStorage {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §6Storage status:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §6Status do armazenamento:")
    private static LocaleMessage STATUS_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7%line%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7%line%")
    private static LocaleMessage STATUS_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe storage is not ready yet (PlayerController is not bootstrapped). Try again after the server finishes loading.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO armazenamento ainda não está pronto (o PlayerController não foi inicializado). Tente novamente após o servidor terminar de carregar.")
    private static LocaleMessage STORAGE_NOT_READY;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §6Starting the storage transfer of PDSection §e[%section%]§6 to backend §e[%backend%]§6.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §6Iniciando a transferência de armazenamento da PDSection §e[%section%]§6 para o backend §e[%backend%]§6.")
    private static LocaleMessage TRANSFER_STARTING;

    @FCLocale(lang = LocaleType.EN_US, text = "§7This runs asynchronously and the source collection is kept as a backup. A maintenance window is recommended.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Isso roda de forma assíncrona e a coleção de origem é mantida como backup. Recomenda-se uma janela de manutenção.")
    private static LocaleMessage TRANSFER_ASYNC_NOTE;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aTransfer complete: §e%count%§a entities in §e%time%ms§a. The source collection was kept as a backup.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aTransferência concluída: §e%count%§a entidades em §e%time%ms§a. A coleção de origem foi mantida como backup.")
    private static LocaleMessage TRANSFER_COMPLETE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cTransfer failed: §f%error%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cFalha na transferência: §f%error%")
    private static LocaleMessage TRANSFER_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cTransfer FAILED (binding unchanged):")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cFalha na transferência (vínculo inalterado):")
    private static LocaleMessage TRANSFER_FAILED_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7  - [%collection%] §f%cause%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7  - [%collection%] §f%cause%")
    private static LocaleMessage TRANSFER_FAILED_LINE;

    @FinalCMD.SubCMD(
            subcmd = "status",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_STATUS,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show the PlayerData storage routing and health counters."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra o roteamento e os contadores de saúde do armazenamento de PlayerData.")
            }
    )
    public void status(FCommandSender sender) {
        STATUS_HEADER.send(sender);
        for (String line : PlayerController.storageStatus().split("\n")) {
            //one chat line per routing entry keeps it readable in-game and in console
            for (String piece : line.split(" \\| ")) {
                STATUS_LINE.addPlaceholder("%line%", piece).send(sender);
            }
        }
    }

    @FinalCMD.SubCMD(
            subcmd = "transfer",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_TRANSFER,
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Migrate a PDSection's storage to another backend at runtime."),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Migra o armazenamento de uma PDSection para outro backend em tempo real.")
            }
    )
    public void transfer(FCommandSender sender,
                          @Arg(name = "<plugin:section>", parser = ArgParserPDSectionId.class) Class<? extends PDSection> pdSectionClass,
                          @Arg(name = "<backend>", parser = ArgParserStorageBackend.class) String targetBackend) {

        PlayerController controller = PlayerController.get();
        if (controller == null) {
            STORAGE_NOT_READY.send(sender);
            return;
        }

        String sectionName = pdSectionClass.getSimpleName();

        TRANSFER_STARTING
            .addPlaceholder("%section%", sectionName)
            .addPlaceholder("%backend%", targetBackend)
            .send(sender);

        TRANSFER_ASYNC_NOTE.send(sender);

        // Capture only the sender's identity; the live sender is re-fetched in the callback so a
        // logged-off admin never NPEs. Console is a stable singleton, so its reference is kept as-is.
        // The outcome is always logged to the console through the ECLogger as well.
        FCommandSender consoleSender = sender.isConsole() ? sender : null;
        UUID senderUuid = sender.getUniqueId();

        controller.transferPDSection(pdSectionClass, targetBackend).whenComplete((report, error) -> {
            FCommandSender target = resolveTarget(consoleSender, senderUuid);

            if (error != null) {
                String message = error.getMessage() != null ? error.getMessage() : error.toString();
                EverNifeCore.getLog().severe("Storage transfer of PDSection [" + sectionName + "] to backend [" + targetBackend + "] failed: " + message);
                if (target != null) TRANSFER_FAILED.addPlaceholder("%error%", message).send(target);
                return;
            }

            if (report.success()) {
                EverNifeCore.getLog().info("Storage transfer of PDSection [" + sectionName + "] to backend [" + targetBackend
                        + "] completed: " + report.totalEntities() + " entities in " + report.durationMs() + "ms.");
                if (target != null) {
                    TRANSFER_COMPLETE
                            .addPlaceholder("%count%", report.totalEntities())
                            .addPlaceholder("%time%", report.durationMs())
                            .send(target);
                }
            } else {
                EverNifeCore.getLog().severe("Storage transfer of PDSection [" + sectionName + "] to backend [" + targetBackend
                        + "] failed - the binding stays unchanged. " + report.errors().size() + " error(s).");
                if (target != null) {
                    TRANSFER_FAILED_HEADER.send(target);
                    for (TransferError transferError : report.errors()) {
                        String cause = transferError.cause() != null ? transferError.cause().getMessage() : "unknown error";
                        TRANSFER_FAILED_LINE
                                .addPlaceholder("%collection%", transferError.collection())
                                .addPlaceholder("%cause%", cause)
                                .send(target);
                    }
                }
            }
        });
    }

    /**
     * Resolves the sender to message in the async callback: the console singleton as-is, or a
     * player re-fetched by UUID (null when the admin has logged off - the outcome is still logged).
     */
    private static FCommandSender resolveTarget(FCommandSender consoleSender, UUID senderUuid) {
        if (consoleSender != null) return consoleSender;
        if (senderUuid == null) return null;
        return EverNifeCore.getPlatform().getPlayer(senderUuid);
    }
}
