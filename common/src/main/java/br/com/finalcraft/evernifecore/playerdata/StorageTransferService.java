package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.PlayerDataBinding;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.CachingManager.FreezeHandle;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.transfer.StorageTransfer;
import br.com.finalcraft.everydatabase.transfer.TransferError;
import br.com.finalcraft.everydatabase.transfer.TransferReport;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime transfer of a collection between backends (the {@code /ecorestoragetransfer} pipeline):
 * freeze the manager's writes (dirty accumulates in memory) -> flush everything else -> verified
 * copy via {@link StorageTransfer} -> on success: re-bind, persist the admin's choice into
 * storage.yml and drain the accumulated dirty to the new backend.
 *
 * <p>The freeze lives in the manager being transferred, which doubles as the duplicate-transfer
 * guard: taking it is atomic, so this service holds no freeze state of its own.
 */
final class StorageTransferService {

    private final PlayerController controller;

    StorageTransferService(PlayerController controller) {
        this.controller = controller;
    }

    /**
     * Moves a PDSection's collection to another enabled backend at runtime. On failure the binding
     * stays intact and the report is returned (the future only fails on config/JVM-level errors).
     * The source collection is never cleared - it stays as a backup (the admin decides).
     * Maintenance window recommended.
     */
    CompletableFuture<TransferReport> transferPDSection(Class<? extends PDSection> pdSectionClass, String targetBackend) {
        @SuppressWarnings("unchecked")
        PDSectionBinding<PDSection> binding = (PDSectionBinding<PDSection>) controller.getBinding((Class) pdSectionClass);
        if (binding == null) {
            return PlayerController.failedFuture(PlayerController.notRegisteredPDSection(pdSectionClass));
        }
        Throwable invalidTarget = validateTransferTarget(targetBackend, binding.getBackendName());
        if (invalidTarget != null) return PlayerController.failedFuture(invalidTarget);
        //an eager schema sweep rewrites the very rows this transfer is about to copy - let it finish
        //rather than race it into a backend that is being replaced underneath it
        if (controller.isSweeping(binding.getCollection())) {
            return PlayerController.failedFuture(new IllegalStateException("PDSection [" + pdSectionClass.getSimpleName()
                    + "] is being schema-swept right now - retry the transfer once the sweep finishes."));
        }
        //the freeze IS the duplicate-transfer guard: taking it is atomic, so a second transfer of the
        //same section cannot slip through, and meanwhile the section's writes pile up as dirty
        FreezeHandle freeze = binding.getManager().tryFreezeWrites().orElse(null);
        if (freeze == null) {
            return PlayerController.failedFuture(new IllegalStateException("PDSection [" + pdSectionClass.getSimpleName()
                    + "] is already being transferred!"));
        }

        //a failed pre-copy flush must RELEASE the freeze: leaving it on would silently no-op every
        //future flush of this section (dirty piling up in memory) until a reboot
        return controller.flushAll().whenComplete((ok, flushError) -> {
            if (flushError != null) freeze.close();
        }).thenCompose(flushed -> {
            RefRegistry refRegistry = controller.registries().of(binding.getConfiguration().getPluginData());
            //a claim rebindTo reserves on a target that had none is fresh - THIS transfer created it, so a
            //failure must release it. A target that already owned the collection (a previous backend kept
            //as a backup) holds a legitimate claim that must survive a failed transfer.
            boolean targetClaimWasFresh = controller.registry()
                    .getCollectionOwner(targetBackend, binding.getCollection()) == null;
            PDSectionBinding<PDSection> rebound;
            try {
                //the rebound manager registers itself for the same type: release the current
                //registration first (a failed transfer re-registers the old manager below)
                refRegistry.unregister(pdSectionClass);
                //reserve the target collection and resolve the target codec up front
                rebound = BindingResolver.rebindTo(binding, targetBackend, controller.storageConfig(),
                        controller.registry(), refRegistry);
            } catch (Throwable bindError) {
                restoreRegistration(refRegistry, pdSectionClass, binding.getManager());
                freeze.close();
                return PlayerController.failedFuture(bindError);
            }
            return executeTransfer(binding.getStorage(), rebound.getStorage(),
                    binding.getDescriptor(), rebound.getDescriptor())
                    .handle((report, error) -> finishSectionTransfer(pdSectionClass, binding, rebound, targetBackend, freeze, targetClaimWasFresh, report, error))
                    .thenCompose(future -> future);
        });
    }

    /** Moves the base PlayerData collection to another enabled backend (same pipeline). */
    CompletableFuture<TransferReport> transferPlayerData(String targetBackend) {
        PlayerDataBinding current = controller.playerDataBinding();
        Throwable invalidTarget = validateTransferTarget(targetBackend, current.getBackendName());
        if (invalidTarget != null) return PlayerController.failedFuture(invalidTarget);
        FreezeHandle freeze = current.getManager().tryFreezeWrites().orElse(null);
        if (freeze == null) {
            return PlayerController.failedFuture(new IllegalStateException("PlayerData is already being transferred!"));
        }

        //a failed pre-copy flush must RELEASE the freeze (same rationale as transferPDSection)
        return controller.flushAll().whenComplete((ok, flushError) -> {
            if (flushError != null) freeze.close();
        }).thenCompose(flushed -> {
            RefRegistry refRegistry = controller.registries().global();
            //same fresh-claim capture as the section path: only a claim THIS transfer creates on the
            //target is released on failure; a pre-existing backup claim on the target is left untouched
            boolean targetClaimWasFresh = controller.registry()
                    .getCollectionOwner(targetBackend, current.getCollection()) == null;
            PlayerDataBinding rebound;
            try {
                //same registration swap as the section path: release, rebind, restore on failure
                refRegistry.unregister(PlayerData.class);
                rebound = PlayerDataBinding.rebindTo(current, targetBackend, controller.storageConfig(),
                        controller.registry(), refRegistry);
            } catch (Throwable bindError) {
                restoreRegistration(refRegistry, PlayerData.class, current.getManager());
                freeze.close();
                return PlayerController.failedFuture(bindError);
            }
            return executeTransfer(current.getStorage(), rebound.getStorage(),
                    current.getDescriptor(), rebound.getDescriptor())
                    .handle((report, error) -> finishPlayerDataTransfer(current, rebound, targetBackend, freeze, targetClaimWasFresh, report, error))
                    .thenCompose(future -> future);
        });
    }

    private CompletableFuture<TransferReport> finishSectionTransfer(Class<? extends PDSection> pdSectionClass,
                                                                    PDSectionBinding<PDSection> current,
                                                                    PDSectionBinding<PDSection> rebound,
                                                                    String targetBackend,
                                                                    FreezeHandle freeze,
                                                                    boolean targetClaimWasFresh,
                                                                    TransferReport report, Throwable error) {
        String what = "PDSection {" + pdSectionClass.getSimpleName() + "}";
        RefRegistry refRegistry = controller.registries().of(current.getConfiguration().getPluginData());
        if (error != null) {
            restoreRegistration(refRegistry, pdSectionClass, current.getManager());
            releaseFreshTargetClaim(targetBackend, current.getCollection(), targetClaimWasFresh);
            freeze.close();
            PDLog.severe("Unexpected failure while transferring %s to backend '%s':", what, targetBackend);
            error.printStackTrace();
            return PlayerController.failedFuture(error);
        }
        if (!report.success()) {
            restoreRegistration(refRegistry, pdSectionClass, current.getManager());
            releaseFreshTargetClaim(targetBackend, current.getCollection(), targetClaimWasFresh);
            freeze.close();
            logTransferFailure(what, targetBackend, current.getBackendName(), report);
            return CompletableFuture.completedFuture(report);
        }
        //cutover - re-bind, carry the live section instances over so the plugin's references
        //(and any write made during the freeze) keep flowing into the NEW manager, persist the
        //admin's choice, drain the backlog. Installing the binding is what resumes writing: the new
        //manager was never frozen, so releasing the old one's freeze only retires the old manager
        migrateCachedSections(current, rebound);
        controller.installSectionBinding(pdSectionClass, rebound);
        persistSectionBackend(current.getConfiguration().getPluginData() != null
                        ? current.getConfiguration().getPluginData().getMetaInfo().getName() : "UnknownPlugin",
                pdSectionClass.getSimpleName(), targetBackend);
        freeze.close();
        controller.onBindingsChanged(); //rebind cache-sync + reschedule the ttl purge over the new manager set
        PDLog.info("%s transferred to backend '%s' (%s entities in %sms). The source collection on '%s'"
                        + " was kept untouched as a backup.",
                what, targetBackend, report.totalEntities(), report.durationMs(), current.getBackendName());
        return controller.flushAll().thenApply(x -> report);
    }

    /**
     * Carries the live (already attached) section instances from the old manager's cache into the
     * new one on a transfer cutover. The backend data was copied by the StorageTransfer; re-seeding
     * the same instances keeps the plugin's references valid and lets a freeze-window mutation drain
     * into the new backend on the next flush (the seed never overwrites a value already cached there).
     */
    private <S extends PDSection> void migrateCachedSections(PDSectionBinding<S> from, PDSectionBinding<S> to) {
        CachingManager<UUID, S> oldManager = from.getManager();
        CachingManager<UUID, S> newManager = to.getManager();
        //iterate the section manager's OWN cache (keyed by its storage key): a section whose base
        //PlayerData was evicted would be missed by walking the base cache instead
        for (S section : oldManager.cachedValues()) {
            newManager.seedIfAbsent(section.getStorageKey(), section);
        }
    }

    private CompletableFuture<TransferReport> finishPlayerDataTransfer(PlayerDataBinding current,
                                                                       PlayerDataBinding rebound,
                                                                       String targetBackend,
                                                                       FreezeHandle freeze,
                                                                       boolean targetClaimWasFresh,
                                                                       TransferReport report, Throwable error) {
        if (error != null) {
            restoreRegistration(controller.registries().global(), PlayerData.class, current.getManager());
            releaseFreshTargetClaim(targetBackend, current.getCollection(), targetClaimWasFresh);
            freeze.close();
            PDLog.severe("Unexpected failure while transferring PlayerData to backend '%s':", targetBackend);
            error.printStackTrace();
            return PlayerController.failedFuture(error);
        }
        if (!report.success()) {
            restoreRegistration(controller.registries().global(), PlayerData.class, current.getManager());
            releaseFreshTargetClaim(targetBackend, current.getCollection(), targetClaimWasFresh);
            freeze.close();
            logTransferFailure("PlayerData", targetBackend, current.getBackendName(), report);
            return CompletableFuture.completedFuture(report);
        }
        //cutover - carry the live base instances over so online players' references (and any write
        //made during the freeze) keep flowing into the NEW manager, swap the binding, persist the
        //admin's choice, drain the freeze-window backlog into the new backend. Swapping the binding
        //is what resumes writing: the new manager was never frozen
        migrateCachedPlayerData(current, rebound);
        controller.installPlayerDataBinding(rebound);
        controller.storageYml().setValue("playerdata.storage-backend-id", targetBackend);
        controller.storageYml().save();
        freeze.close();
        controller.onBindingsChanged(); //rebind cache-sync over the new manager set
        PDLog.info("PlayerData transferred to backend '%s' (%s entities in %sms). The source collection"
                        + " on '%s' was kept untouched as a backup.",
                targetBackend, report.totalEntities(), report.durationMs(), current.getBackendName());
        return controller.flushAll().thenApply(x -> report);
    }

    /**
     * Carries the live base {@link PlayerData} instances from the old manager's cache into the new
     * one on a transfer cutover. The backend data was copied by the StorageTransfer; re-seeding the
     * same instances keeps online players' references valid and lets a freeze-window mutation drain
     * into the new backend on the next flush (the seed never overwrites a value already cached there).
     */
    private void migrateCachedPlayerData(PlayerDataBinding from, PlayerDataBinding to) {
        CachingManager<UUID, PlayerData> oldManager = from.getManager();
        CachingManager<UUID, PlayerData> newManager = to.getManager();
        for (PlayerData playerData : oldManager.cachedValues()) {
            newManager.seedIfAbsent(playerData.getUniqueId(), playerData);
        }
    }

    /** Puts the pre-transfer manager back as the type's resolver after a failed rebind/copy. */
    private static void restoreRegistration(RefRegistry refRegistry, Class<?> type, CachingManager<?, ?> oldManager) {
        refRegistry.unregister(type);
        refRegistry.register(type, oldManager);
    }

    /**
     * Releases the target-collection claim a FAILED transfer left behind - but only when THIS transfer
     * created it. A claim the target already held (a previous backend kept as a backup) is legitimate,
     * so an unconditional release would drop a valid backup's claim.
     */
    private void releaseFreshTargetClaim(String targetBackend, String collection, boolean wasFresh) {
        if (wasFresh) {
            controller.registry().releaseCollection(targetBackend, collection);
        }
    }

    /** The target must be declared, enabled and different from the source. */
    private Throwable validateTransferTarget(String targetBackend, String currentBackend) {
        BackendDefinition backend = controller.storageConfig().getBackend(targetBackend).orElse(null);
        if (backend == null) {
            return new StorageConfigException("Transfer target backend '" + targetBackend
                    + "' is not declared under 'storage-backends:' in storage.yml!");
        }
        if (!backend.isEnabled()) {
            return new StorageConfigException("Transfer target backend '" + targetBackend
                    + "' is DISABLED - set 'storage-backends." + targetBackend + ".enabled: true' first!");
        }
        if (targetBackend.equals(currentBackend)) {
            return new IllegalArgumentException("Already stored on backend '" + targetBackend + "'!");
        }
        return null;
    }

    private static <V> CompletableFuture<TransferReport> executeTransfer(Storage from, Storage to,
                                                                         EntityDescriptor<UUID, V> srcDescriptor,
                                                                         EntityDescriptor<UUID, V> dstDescriptor) {
        return StorageTransfer.builder()
                .from(from).to(to)
                .descriptor(srcDescriptor, dstDescriptor)   //same collection, target codec
                .failIfTargetCollectionNotEmpty(true)       //the target collection must be empty
                .verifyCounts(true)
                .build()
                .execute();
    }

    /** The transfer result becomes the admin's choice persisted in storage.yml. */
    private void persistSectionBackend(String pluginName, String sectionName, String targetBackend) {
        controller.storageYml().setValue("pdsections." + pluginName + "." + sectionName + ".storage-backend-id", targetBackend);
        controller.storageYml().save();
    }

    private static void logTransferFailure(String what, String targetBackend, String keptBackend, TransferReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transfer of %s to backend '%s' FAILED - the binding stays on '%s'"
                + " and nothing was changed. Errors:", what, targetBackend, keptBackend));
        for (TransferError transferError : report.errors()) {
            sb.append("\n  - [").append(transferError.collection()).append("] ")
                    .append(transferError.cause() != null ? transferError.cause().getMessage() : "unknown error");
        }
        PDLog.warning(sb.toString());
    }
}
