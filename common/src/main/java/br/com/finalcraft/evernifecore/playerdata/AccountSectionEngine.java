package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.everydatabase.manager.writeback.OptimisticConflictException;
import br.com.finalcraft.evernifecore.playerdata.storage.PdSyncBindGuard;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The account-wide-section machinery of a {@link PlayerController} instance: binding each
 * registered {@link AccountSection} type onto the account backend, loading/refreshing rows on a
 * member's login, releasing them after the last online member quits, and the lazy resolution the
 * accessors delegate to.
 *
 * <p>Cache lifecycle (fixed by design): a row stays resident while ANY member of its account is
 * online on this instance; on login it is re-read from the backend when no other local session was
 * already using it (data written by other instances of the network becomes visible at the natural
 * reconciliation point); after the last member quits it is evicted following a short grace.</p>
 */
final class AccountSectionEngine {

    /** How long a released row stays cached after the last online member quits. */
    private static final long QUIT_RELEASE_GRACE_MS = 60_000L;

    private final PlayerController controller;
    private final Map<Class<?>, AccountSectionBinding<?>> bindings = new ConcurrentHashMap<>();

    AccountSectionEngine(PlayerController controller) {
        this.controller = controller;
    }

    Collection<AccountSectionBinding<?>> bindings() {
        return bindings.values();
    }

    @SuppressWarnings("unchecked")
    <S extends AccountSection<S>> AccountSectionBinding<S> getBinding(Class<S> sectionClass) {
        return (AccountSectionBinding<S>) bindings.get(sectionClass);
    }

    AccountSectionBinding<?> getBindingUnchecked(Class<?> sectionClass) {
        return bindings.get(sectionClass);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void bindUnchecked(AccountSectionConfiguration<?> cfg) {
        bind((AccountSectionConfiguration) cfg);
    }

    List<CachingManager<?, ?>> managers() {
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        for (AccountSectionBinding<?> binding : bindings.values()) {
            managers.add(binding.getManager());
        }
        return managers;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Bind / unbind
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Materializes a registration into a binding on the account backend and hot-loads the rows of
     * the members already online (a hot reload / a registration arriving after the boot).
     */
    <S extends AccountSection<S>> void bind(AccountSectionConfiguration<S> cfg) {
        Class<S> sectionClass = cfg.getSectionClass();
        if (bindings.containsKey(sectionClass)) {
            return;
        }
        ParsedStorageConfig parsed = controller.storageConfig();
        String backendName = parsed.getAccountBackendName();
        BackendDefinition backend = parsed.getBackend(backendName).orElseThrow(() ->
                new StorageConfigException("Account backend '" + backendName + "' is not declared/enabled!"));
        Storage storage = controller.registry().get(backendName);

        String pluginName = cfg.getPluginData() != null
                ? cfg.getPluginData().getMetaInfo().getName() : "UnknownPlugin";
        String sectionId = pluginName + ":" + sectionClass.getSimpleName();

        String collection = cfg.getCollection() != null
                ? cfg.getCollection()
                : BindingResolver.collectionName("acs", pluginName, sectionClass.getSimpleName());
        if (!StorageYamlParser.VALID_COLLECTION.matcher(collection).matches()) {
            throw new StorageConfigException("AccountSection '" + sectionId + "' resolved to invalid"
                    + " collection name '" + collection + "' - must match "
                    + StorageYamlParser.VALID_COLLECTION.pattern());
        }
        if (!controller.registry().claimCollection(backendName, collection, sectionId)) {
            throw new StorageConfigException("AccountSection '" + sectionId + "' wants collection '"
                    + collection + "' on backend '" + backendName + "', but it is already used by '"
                    + controller.registry().getCollectionOwner(backendName, collection) + "'!");
        }

        // the plugin's child registry - shared by the codec (so a Ref in an account section resolves) and
        // the manager below, exactly as the PDSection path pairs them in BindingResolver.resolve
        RefRegistry accountRegistry = controller.registries().of(cfg.getPluginData());
        Codec<S> codec = EntitySchemaMigratingCodec.wrap(sectionClass,
                BindingResolver.defaultCodec(backend, sectionClass, accountRegistry), "accountId");
        EntityDescriptor<UUID, S> descriptor = EntityDescriptor
                .builder(UUID.class, sectionClass)
                .collection(collection)
                .keyExtractor(AccountSection::getAccountId)
                .codec(codec)
                .build();

        //account rows may be written from several servers of the network: reject/warn a backend
        //that cannot enforce the optimistic lock, scoped to the account family
        List<String> warnings = new ArrayList<>();
        PdSyncBindGuard.check("AccountSection '" + sectionId + "'", descriptor, storage, parsed,
                parsed.isMultiplatformAccountsEnabled(), warnings);
        for (String warning : warnings) {
            PDLog.warning(warning);
        }

        CachingManager<UUID, S> manager = accountRegistry.manager(descriptor, storage, CachePolicy.always());
        AccountSectionBinding<S> binding = new AccountSectionBinding<>(cfg, backendName, descriptor, manager);
        bindings.put(sectionClass, binding);
        PDLog.info("Bound AccountSection {%s} (collection '%s' on account backend '%s').",
                sectionClass.getSimpleName(), collection, backendName);

        //hot-load for the members already online (their login pipeline ran before this bind)
        for (PlayerData playerData : controller.baseManager().cachedValues()) {
            if (playerData.isPlayerOnline()) {
                resolveThroughBinding(binding, playerData.getAccountId());
            }
        }

        //eager schema sweep of this account section (async, post-ready, O(1) when nothing eager is pending)
        controller.maybeSweepAccountSection(binding);
    }

    /** Unbinds every account section owned by {@code pluginName}: final flush, cache drop, claim release. */
    void unbind(String pluginName, List<Class<?>> classes) {
        for (Class<?> sectionClass : classes) {
            AccountSectionBinding<?> binding = bindings.remove(sectionClass);
            if (binding == null) continue;
            try {
                controller.flushAll().join();
            } catch (Throwable flushFailure) {
                PDLog.warning("Final flush while unregistering AccountSection {%s} of plugin '%s' failed: %s",
                        sectionClass.getSimpleName(), pluginName, String.valueOf(flushFailure.getMessage()));
            }
            binding.getManager().clearCache();
            //drop the manager from the plugin's RefRegistry so its Class object is not retained
            controller.registries().of(binding.getPluginData()).unregister(binding.getSectionClass());
            controller.registry().releaseCollection(binding.getBackendName(), binding.getCollection());
            PDLog.info("Unregistered AccountSection {%s} of plugin '%s' (collection '%s' released).",
                    sectionClass.getSimpleName(), pluginName, binding.getCollection());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Login / quit lifecycle
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Loads every registered account section of a player's account on login, re-reading a cached
     * row from the backend first when no other local session was already using it - the natural
     * point where writes made by OTHER instances of the network become visible.
     */
    CompletableFuture<Void> hotLoadOnLogin(PlayerData playerData) {
        if (bindings.isEmpty()) return CompletableFuture.completedFuture(null);
        UUID accountKey = playerData.getAccountId();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (AccountSectionBinding<?> binding : bindings.values()) {
            refreshIfIdle(binding, accountKey, playerData.getUniqueId());
            futures.add(resolveThroughBinding(binding, accountKey));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Invalidates a clean cached row so the login re-reads it, unless another online member of the
     * same account is already using it on this instance (the live session is the local truth) or
     * the row carries unflushed changes (they win; divergence is merged on the flush conflict).
     */
    private void refreshIfIdle(AccountSectionBinding<?> binding, UUID accountKey, UUID loggingInUuid) {
        AccountSection<?> cell = binding.getManager().peek(accountKey).orElse(null);
        if (cell == null || cell.isDirty()) return;
        if (anyOtherOnlineMember(accountKey, loggingInUuid)) return;
        binding.getManager().invalidate(accountKey);
    }

    /**
     * Schedules the release of the quitting player's account rows: after a short grace, each row is
     * evicted unless a member of that account is (back) online or the row still has unflushed
     * changes (then it is flushed and re-checked).
     */
    void scheduleQuitRelease(PlayerData playerData) {
        if (bindings.isEmpty()) return;
        UUID accountKey = playerData.getAccountId();
        for (AccountSectionBinding<?> binding : bindings.values()) {
            controller.lifecycleScheduler().schedule(
                    () -> releaseIfIdle(binding, accountKey),
                    QUIT_RELEASE_GRACE_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void releaseIfIdle(AccountSectionBinding<?> binding, UUID accountKey) {
        if (anyOtherOnlineMember(accountKey, null)) return;
        AccountSection<?> cell = binding.getManager().peek(accountKey).orElse(null);
        if (cell == null) return;
        if (cell.isDirty()) {
            //an unflushed write is pending: persist it, then re-check after another grace
            controller.flushAccountSection(cell);
            controller.lifecycleScheduler().schedule(
                    () -> releaseIfIdle(binding, accountKey),
                    QUIT_RELEASE_GRACE_MS, TimeUnit.MILLISECONDS);
            return;
        }
        binding.getManager().evict(accountKey);
    }

    /** True when any online player loaded on this instance (except {@code exceptUuid}) belongs to the account. */
    private boolean anyOtherOnlineMember(UUID accountKey, UUID exceptUuid) {
        for (PlayerData playerData : controller.baseManager().cachedValues()) {
            if (!playerData.isPlayerOnline()) continue;
            if (playerData.getUniqueId().equals(exceptUuid)) continue;
            if (accountKey.equals(playerData.getAccountId())) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Key migration (rows follow the account when identities are linked)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Absorbs every registered account-section row stored under {@code oldKey} (a former key of the
     * account: a member's pre-link singleton key, an absorbed account's id) into the row of
     * {@code newKey}. Runs once per section, sequentially - every section lives on the one account
     * backend, so no cross-backend coordination is involved. Write-before-delete plus the per-row
     * {@code mergedKeys} ledger make an interrupted or repeated run safe even for non-idempotent
     * merges (a sum, for instance, is never double-applied).
     */
    CompletableFuture<Void> migrateKeyedRows(UUID oldKey, UUID newKey) {
        if (bindings.isEmpty() || oldKey.equals(newKey)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (AccountSectionBinding<?> binding : bindings.values()) {
            chain = chain.thenCompose(x -> migrateRow(binding, oldKey, newKey));
        }
        return chain;
    }

    private <S extends AccountSection<S>> CompletableFuture<Void> migrateRow(
            AccountSectionBinding<S> binding, UUID oldKey, UUID newKey) {
        return binding.getRepository().find(oldKey).thenCompose(oldStored -> {
            if (!oldStored.isPresent()) {
                return CompletableFuture.completedFuture(null);
            }
            S oldRow = oldStored.get();
            oldRow.warnIfStaleSchema(); //never merge an old-schema payload into a current-schema row
            return resolveThroughBinding(binding, newKey).thenCompose(target -> {
                boolean absorb;
                target.getLock().lock();
                try {
                    AccountSection.MergedKeyRecord record = target.findMergedKey(oldKey);
                    //recorded with the SAME lock version = crash-resume (written but not deleted):
                    //skip the merge and just finish the delete. Anything else - never absorbed, or
                    //the old row was re-written afterwards by a stale session - is (re-)absorbed.
                    absorb = record == null
                            || !Objects.equals(record.getLockVersion(), oldRow.lockVersion);
                    if (absorb) {
                        target.absorbMigratedState(oldRow);
                    }
                } finally {
                    target.getLock().unlock();
                }
                CompletableFuture<Void> persisted = absorb
                        ? flushMigrated(target, 3)
                        : CompletableFuture.completedFuture(null);
                return persisted
                        .thenCompose(x -> binding.getManager().deleteAndEvict(oldKey))
                        .thenAccept(x -> PDLog.info(
                                "Absorbed %s row [%s] into account [%s].",
                                binding.getSectionClass().getSimpleName(), oldKey, newKey));
            });
        });
    }

    /**
     * Write-before-delete: the absorbed state must land in the backend before the old row goes. A
     * concurrent-write conflict already merged the winner into the live row, so it is just retried.
     */
    private CompletableFuture<Void> flushMigrated(AccountSection<?> target, int attemptsLeft) {
        return controller.flushAccountSection(target).handle((ok, failure) -> failure).thenCompose(failure -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(null);
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            if (cause instanceof OptimisticConflictException && attemptsLeft > 1) {
                return flushMigrated(target, attemptsLeft - 1);
            }
            return PlayerController.failedFuture(cause);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Resolution (the accessors delegate here)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** Resolves the account row for {@code accountKey}, seeding a transient default on a true miss. */
    <S extends AccountSection<S>> CompletableFuture<S> resolve(Class<S> sectionClass, UUID accountKey) {
        AccountSectionBinding<S> binding = getBinding(sectionClass);
        if (binding == null) return notRegistered(sectionClass);
        return resolveThroughBinding(binding, accountKey);
    }

    /** Presence-only resolution: no default is seeded; a cached transient default reports absence. */
    <S extends AccountSection<S>> CompletableFuture<Optional<S>> resolveIfPresent(Class<S> sectionClass, UUID accountKey) {
        AccountSectionBinding<S> binding = getBinding(sectionClass);
        if (binding == null) return notRegistered(sectionClass);
        return binding.getManager().resolve(accountKey).thenApply(stored -> {
            if (!stored.isPresent()) return Optional.<S>empty();
            S section = stored.get();
            if (section.isTransientDefault()) return Optional.<S>empty();
            StoredSection.upcastOrEvict(binding.getManager(), accountKey, section);
            return Optional.of(section);
        });
    }

    /** The cached account row, or null when it is not in memory (never touches storage). */
    <S extends AccountSection<S>> S getLoaded(Class<S> sectionClass, UUID accountKey) {
        AccountSectionBinding<S> binding = getBinding(sectionClass);
        if (binding == null) return null;
        return binding.getManager().peek(accountKey).orElse(null);
    }

    private <S extends AccountSection<S>> CompletableFuture<S> resolveThroughBinding(
            AccountSectionBinding<S> binding, UUID accountKey) {
        CachingManager<UUID, S> manager = binding.getManager();
        return manager.resolve(accountKey).thenApply(stored -> {
            if (stored.isPresent()) {
                S section = stored.get();
                StoredSection.upcastOrEvict(manager, accountKey, section);
                return section;
            }
            return manager.seedIfAbsent(accountKey, newDefault(binding.getSectionClass(), accountKey));
        });
    }

    private static <S extends AccountSection<S>> S newDefault(Class<S> sectionClass, UUID accountKey) {
        S section = StoredSection.newDefault(sectionClass);
        section.attachAccountId(accountKey);
        return section;
    }

    private static <X> CompletableFuture<X> notRegistered(Class<?> sectionClass) {
        return PlayerController.failedFuture(PlayerController.notRegisteredAccountSection(sectionClass));
    }
}
