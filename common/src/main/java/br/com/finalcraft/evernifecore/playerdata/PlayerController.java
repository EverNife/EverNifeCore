package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldownsLocal;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldownsNetwork;
import br.com.finalcraft.evernifecore.cooldown.ServerCooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.playerdata.storage.CacheSyncWiring;
import br.com.finalcraft.evernifecore.playerdata.storage.ECRegistries;
import br.com.finalcraft.everydatabase.manager.writeback.OptimisticConflictException;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.PlayerDataBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionCachePolicy;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.PDSectionYamlWriter;
import br.com.finalcraft.evernifecore.storage.config.PlayerDataAdminConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlDefaults;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.log.StorageLogSinks;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.query.Cursor;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import br.com.finalcraft.everydatabase.query.ScanRow;
import br.com.finalcraft.everydatabase.transfer.TransferReport;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * PlayerData controller: a mutable instance behind a static facade.
 * {@link #bootstrap()} builds a new instance (parses storage.yml, initializes
 * the backends, loads the players) and swaps it in atomically - a reload that fails never
 * affects the running instance.
 *
 * <p>The heavy machinery lives in dedicated engines owned by each instance:
 * {@link FlushEngine} (batch flush + ADOPT_WINNER), {@link LifecycleEngine} (periodic flush tick,
 * quit-flush + retry, working-set eviction, ttl purge), {@link StorageTransferService} (runtime
 * backend transfer) and {@link LegacyBootstrap} (first-boot YAML import). This class keeps the
 * bootstrap, the registration/binding of sections and the read facade.</p>
 *
 * <p>API split:</p>
 * <ul>
 *   <li>MEMORY ONLY (sync): {@link #getLoaded(UUID)}, {@link #getAllLoaded()},
 *       {@link #getLoadedCount()}.</li>
 *   <li>MAY TOUCH STORAGE (async): {@link #getPlayerData(UUID)},
 *       {@link #getOrCreate(UUID)}, {@link #getPDSection(UUID, Class)},
 *       {@link #handleLogin(UUID, String)}.</li>
 * </ul>
 *
 * <p>Threading: the async futures complete on storage threads. To touch game state with the result,
 * bridge back with {@link #whenCompleteOnMainThread(CompletableFuture, BiConsumer)}. {@code .join()}
 * is fine on an already-async thread, but on the server thread it turns a cache miss into
 * synchronous backend I/O - avoid it there.</p>
 */
public class PlayerController {

    private static volatile PlayerController INSTANCE;

    /** Section registrations made by devs - survive reloads (plugins register once on enable). */
    private static final Map<Class<? extends PDSection>, PDSectionConfiguration<?>> REGISTERED_SECTIONS = new ConcurrentHashMap<>();

    /** Account-section registrations - same reload-surviving contract as the per-player sections. */
    private static final Map<Class<?>, AccountSectionConfiguration<?>> REGISTERED_ACCOUNT_SECTIONS = new ConcurrentHashMap<>();

    // ---- instance state ----
    private final Config storageYml;
    private final ParsedStorageConfig storageConfig;
    private final StorageRegistry registry;
    private final ECRegistries ecRegistries = new ECRegistries(); //one root + per-plugin child RefRegistry
    private volatile PlayerDataBinding playerDataBinding; //swapped by transferPlayerData
    private final Map<Class<? extends PDSection>, PDSectionBinding<? extends PDSection>> bindings = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>(); //gate that holds storage access during the legacy import

    // ---- engines ----
    private final FlushEngine flushEngine = new FlushEngine(this);
    private final LifecycleEngine lifecycleEngine = new LifecycleEngine(this);
    private final StorageTransferService transferService = new StorageTransferService(this);
    private final LegacyBootstrap legacyBootstrap = new LegacyBootstrap(this);
    private final AccountSectionEngine accountEngine = new AccountSectionEngine(this);
    private final EagerSweepEngine sweepEngine = new EagerSweepEngine(this);

    // ---- cross-instance cache coherence (null unless a shared transport actually starts) ----
    private volatile CacheSyncWiring.Handle cacheSync;

    // ---- orphan reaper throttle (last run epoch millis; 0 = never) ----
    private volatile long lastOrphanReapAt = 0L;
    private final AtomicBoolean orphanReapRunning = new AtomicBoolean(false);
    /** Rows per reaper page - matches the schema sweep, the other full-collection scan over this data. */
    private static final int REAP_BATCH_SIZE = 256;

    public static PlayerController get() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Bootstrap
    // -----------------------------------------------------------------------------------------------------------------------------//

    public static void bootstrap(){
        File dataFolder = EverNifeCore.getEcPluginData().getMetaInfo().getDataFolder();
        bootstrap(new File(dataFolder, "storage.yml"));
    }

    public static synchronized void bootstrap(File storageYmlFile){
        PlayerController old = INSTANCE;
        if (old != null){
            //flush the old instance BEFORE the fresh one reads the backend: otherwise the fresh
            //cache would be seeded from pre-flush rows and, on a non-versioned backend, its next
            //flush would silently overwrite up to a full tick of the old instance's mutations
            try {
                old.flushAll().join();
            }catch (Throwable e){
                PDLog.severe("Failed to flush the previous PlayerController instance before reload:");
                e.printStackTrace();
            }
        }

        //the fresh constructor publishes a fresh Accounts (sections key through it while the
        //fresh instance loads); capture the current one so a FAILED boot can put it back - the fresh
        //layer's registry gets closed on failure and must not keep serving account lookups
        Accounts previousAccounts = Accounts.isEnabled() ? Accounts.get() : null;
        ServerCooldowns previousServerCooldowns = ServerCooldowns.get();
        PlayerController fresh = new PlayerController(storageYmlFile);

        //check the legacy import - when pending, the players are NOT loaded now;
        //the import + the load run on the first tick, after every plugin has registered its adapters
        File legacyFolder = new File(storageYmlFile.getParentFile(), "PlayerData");
        boolean importPending = fresh.legacyBootstrap.isImportPending(legacyFolder);

        if (!importPending){
            try {
                fresh.start();
            }catch (Throwable bootFailure){
                fresh.registry.closeAll(); //don't leak pools on a failed boot; the old instance stays intact
                Accounts.restore(previousAccounts);
                ServerCooldowns.restore(previousServerCooldowns);
                if (bootFailure instanceof RuntimeException) throw (RuntimeException) bootFailure;
                if (bootFailure instanceof Error) throw (Error) bootFailure;
                throw new RuntimeException(bootFailure);
            }
        }

        INSTANCE = fresh;                       //atomic instance swap
        ECStorage.initialize(fresh.registry);
        if (!importPending){
            fresh.ready.complete(null);         //held until the import on the first boot
            //a registration that raced this bootstrap (arrived after fresh.start() visited the
            //static registry but before the swap) bound to the OLD instance only - sweep it in
            for (PDSectionConfiguration<?> configuration : REGISTERED_SECTIONS.values()){
                if (!fresh.bindings.containsKey(configuration.getPdSectionClass())){
                    fresh.bindSection(configuration);
                }
            }
            for (AccountSectionConfiguration<?> configuration : REGISTERED_ACCOUNT_SECTIONS.values()){
                if (fresh.accountEngine.getBindingUnchecked(configuration.getSectionClass()) == null){
                    fresh.accountEngine.bindUnchecked(configuration);
                }
            }
        }

        if (old != null){
            //residual mutations made while the fresh instance was loading flush now, then close
            old.closeCacheSync();
            old.lifecycleEngine.stop();
            try {
                old.flushAll().join();
            }catch (Throwable e){
                PDLog.severe("Failed to flush the previous PlayerController instance on reload:");
                e.printStackTrace();
            }
            old.registry.closeAll().join();
        }

        if (importPending){
            PDLog.info("Legacy PlayerData YAML files found at [%s] - the one-time import will run on"
                    + " the first server tick (after every plugin has registered its sections);"
                    + " player logins are held until it finishes.", legacyFolder.getPath());
            EverNifeCore.getPlatform().runOnFirstTick(() -> fresh.legacyBootstrap.runImportThenStart(legacyFolder));
        }
    }

    private PlayerController(File storageYmlFile){
        PlayerData.registerBaseSchemas(); //before ANY row decode (idempotent)
        StorageYamlDefaults.writeDefault(storageYmlFile);
        this.storageYml = ConfigFactory.open(EverNifeCore.getEcPluginData(), storageYmlFile);
        StorageYamlDefaults.ensureMultiplatformAccounts(storageYml); //older files predate the block
        this.storageConfig = StorageYamlParser.parse(storageYml);
        for (String warning : storageConfig.getWarnings()){
            PDLog.warning(warning);
        }

        //route EveryDatabase logging to the ECore logger
        StorageLogSinks.installDefault(PDLog::routeStorageLogEvent);
        StorageLogConfig logConfig = StorageLogConfig.defaults()
                .defaultLevel(storageConfig.getLoggingLevel());

        this.registry = StorageYamlParser.buildRegistry(storageConfig, logConfig);
        this.registry.initAll().join(); //fail-fast: an enabled backend that is down aborts the boot
        this.playerDataBinding = PlayerDataBinding.resolve(storageConfig, registry, ecRegistries.global());
        for (String warning : playerDataBinding.getResolutionWarnings()){
            PDLog.warning(warning);
        }
        if (storageConfig.isMultiplatformAccountsEnabled()){
            //bring up the account/identity layer on the shared account backend before any account
            //row is keyed - the login pipeline stamps each player's accountId from it
            Accounts.bootstrap(storageConfig, registry, ecRegistries.global());
        }else {
            //disabled: linked accounts stored on this backend would silently key their account-wide
            //data by the wrong id - refuse the boot while real links exist (enabling back is safe)
            long storedAccounts = Accounts.countStoredAccounts(storageConfig, registry);
            if (storedAccounts > 0){
                throw new StorageConfigException(
                        "storage.yml has 'multi-platform-accounts.enabled: false', but the account"
                        + " collection '" + Accounts.COLLECTION + "' on backend '"
                        + storageConfig.getAccountBackendName() + "' already holds " + storedAccounts
                        + " row(s) of linked accounts. Disabling the layer would make their shared"
                        + " data unreachable - re-enable it, or undo every link (/account unlink)"
                        + " before disabling.");
            }
            Accounts.clear(); //every identity resolves to its own singleton account
        }
    }

    /** The base PlayerData cache + repository façade (in {@code ECRegistries.global()}). */
    CachingManager<UUID, PlayerData> baseManager(){
        return playerDataBinding.getManager();
    }

    void start(){
        long start = System.currentTimeMillis();
        UUIDsController.getUuidHashMap().clear();

        //load (ALL | RECENT) into the base manager's cache (the cached set == the loaded set)
        CachingManager<UUID, PlayerData> baseManager = baseManager();
        PlayerDataAdminConfig adminConfig = storageConfig.getPlayerData();
        if (adminConfig.getLoadMode() == PlayerDataAdminConfig.LoadMode.RECENT){
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(adminConfig.getRecentDays());
            List<PlayerData> recent = baseManager.repository().query(Query.range("lastSeen", cutoff, null)).join();
            for (PlayerData playerData : recent){
                playerData.warnIfStaleSchema();
                baseManager.seedIfAbsent(playerData.getUniqueId(), playerData); //cache it, no write
            }
        }else {
            baseManager.preloadAll().join();
            for (PlayerData playerData : baseManager.cachedValues()){
                playerData.warnIfStaleSchema();
            }
        }

        //UUIDsController (duplicate NAME: the most recent lastSeen wins)
        Map<String, PlayerData> byName = new HashMap<>();
        for (PlayerData playerData : baseManager.cachedValues()){
            if (playerData.getName() == null) continue;
            String nameKey = playerData.getName().toLowerCase(Locale.ROOT);
            PlayerData existing = byName.get(nameKey);
            if (existing != null){
                PlayerData winner = playerData.getLastSeen() >= existing.getLastSeen() ? playerData : existing;
                PDLog.warning("There are two PlayerData with the NAME [%s] (%s and %s)!"
                                + " The name will resolve to the most recently seen one (%s)."
                                + " This usually happens after an OnlineMode flip.",
                        playerData.getName(), playerData.getUniqueId(), existing.getUniqueId(),
                        winner.getUniqueId());
                byName.put(nameKey, winner);
            }else {
                byName.put(nameKey, playerData);
            }
        }
        for (PlayerData playerData : byName.values()){
            UUIDsController.addOrUpdateUUIDName(playerData.getUniqueId(), playerData.getName());
        }

        long end = System.currentTimeMillis();
        PDLog.info("Finished Loading PlayerData of %s players! (%s)",
                baseManager.cachedSize(), formatDuration(end - start));

        //hot reload - re-bind the online players
        for (FPlayer onlinePlayer : EverNifeCore.getPlatform().getOnlinePlayers()){
            PlayerData playerData = doHandleLogin(onlinePlayer.getUniqueId(), onlinePlayer.getName()).join();
            playerData.setPlayer(onlinePlayer);
        }

        //register the framework's own player-cooldown rows before the bind loops pick them up, so a
        //player cooldown has a storage route without every plugin declaring one
        registerBuiltinCooldownSections();

        //bind + hot-load each registered section (one by one, timed - as before)
        for (PDSectionConfiguration<?> configuration : REGISTERED_SECTIONS.values()){
            bindSection(configuration);
        }
        for (AccountSectionConfiguration<?> configuration : REGISTERED_ACCOUNT_SECTIONS.values()){
            accountEngine.bindUnchecked(configuration);
        }

        //network-wide server cooldowns: bound unconditionally (the reach is declared at the call site,
        //not in the config) onto the same shared backend the account family uses
        ServerCooldowns.bootstrap(storageConfig, registry, ecRegistries.global(), PDLog::log);

        startCacheSync();
        lifecycleEngine.schedulePurgeForTtlManagers();
        lifecycleEngine.startPeriodicFlush();

        //eager schema sweep of the base entity (async, post-ready, O(1) when nothing eager is pending)
        sweepEngine.maybeSweepBase(playerDataBinding);
    }

    /**
     * Wires cross-instance cache coherence over the base + section managers when the admin enabled it
     * (a clean no-op by default). Re-startable: closes any previous sync first (a section rebound by a
     * runtime transfer produced a new manager, so the sync must be rebuilt over the current set).
     */
    private void startCacheSync(){
        closeCacheSync(); //a rebind after a runtime transfer must not leak the previous sync/transport
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        managers.add(baseManager());
        for (PDSectionBinding<? extends PDSection> binding : bindings.values()){
            managers.add(binding.getManager());
        }
        managers.addAll(accountEngine.managers());
        //the account layer shares the same coherence need: a link made on another instance must
        //invalidate this one's resident account cache, or account keying diverges per server
        Accounts accounts = Accounts.get();
        if (accounts.getManager() != null){
            managers.add(accounts.getManager());
        }
        //network cooldowns carry the same need: a cooldown started on another instance must invalidate
        //this one's warm row, or it stays free here and the whole point of the network reach is lost
        ServerCooldowns serverCooldowns = ServerCooldowns.get();
        if (serverCooldowns != null){
            managers.add(serverCooldowns.getManager());
        }
        try {
            cacheSync = CacheSyncWiring.startIfEnabled(storageConfig, managers,
                    message -> PDLog.info(message), message -> PDLog.warning(message));
        }catch (Throwable cacheSyncFailure){
            //never let a cache-sync wiring failure abort the boot - it is a coherence layer, not the store
            PDLog.severe("Failed to start cache-sync - continuing without cross-instance coherence: %s",
                    cacheSyncFailure.getMessage());
        }
    }

    private void closeCacheSync(){
        CacheSyncWiring.Handle sync = cacheSync;
        if (sync != null){
            sync.close(); //closes the sync AND its transport (a redis connection would leak otherwise)
            cacheSync = null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Engine wiring (package-private accessors the engines drive)
    // -----------------------------------------------------------------------------------------------------------------------------//

    Collection<PDSectionBinding<? extends PDSection>> sectionBindings(){
        return bindings.values();
    }

    ParsedStorageConfig storageConfig(){
        return storageConfig;
    }

    StorageRegistry registry(){
        return registry;
    }

    ECRegistries registries(){
        return ecRegistries;
    }

    Config storageYml(){
        return storageYml;
    }

    PlayerDataBinding playerDataBinding(){
        return playerDataBinding;
    }

    void installPlayerDataBinding(PlayerDataBinding rebound){
        for (String warning : rebound.getResolutionWarnings()){
            PDLog.warning(warning);
        }
        this.playerDataBinding = rebound;
    }

    void installSectionBinding(Class<? extends PDSection> pdSectionClass, PDSectionBinding<? extends PDSection> rebound){
        for (String warning : rebound.getResolutionWarnings()){
            PDLog.warning(warning);
        }
        bindings.put(pdSectionClass, rebound);
    }

    /** Rebuilds the manager-set-dependent wiring after a transfer cutover swapped a binding. */
    void onBindingsChanged(){
        startCacheSync();
        lifecycleEngine.schedulePurgeForTtlManagers();
    }

    void completeReady(){
        ready.complete(null);
    }

    /** The boot gate the eager sweep waits on (completes after start(), post legacy-import). */
    CompletableFuture<Void> whenReady(){
        return ready;
    }

    /** True while an eager schema sweep is running over {@code collection} (transfer mutual exclusion). */
    boolean isSweeping(String collection){
        return sweepEngine.isSweeping(collection);
    }

    /** Schedules an eager schema sweep for a just-bound account section (async, post-ready, O(1) if none). */
    <S extends AccountSection<S>> void maybeSweepAccountSection(AccountSectionBinding<S> binding){
        sweepEngine.maybeSweepAccount(binding);
    }

    /** The eager sweep engine (package-visible, for same-package integration tests). */
    EagerSweepEngine sweepEngine(){
        return sweepEngine;
    }

    void failReady(Throwable bootFailure){
        ready.completeExceptionally(bootFailure);
    }

    AccountSectionEngine accountEngine(){
        return accountEngine;
    }

    /** The shared lifecycle scheduler (working-set eviction, account-row release, login timeout). */
    ScheduledExecutorService lifecycleScheduler(){
        return lifecycleEngine.scheduler();
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // PDSection registration / unregistration
    // -----------------------------------------------------------------------------------------------------------------------------//

    public static void registerPDSectionCfg(ECPluginData ecPluginData, Class<? extends PDSection> pdSectionClass){
        registerPDSectionCfg(PDSectionConfiguration.builder(ecPluginData, pdSectionClass).build());
    }

    public static void registerPDSectionCfg(PDSectionConfiguration<?> pdSectionConfiguration){
        //install the schema-migration chain FIRST, before the section can bind/decode any row: the
        //register-before-load ordering (EntitySchemaMigrations) becomes structural (the chain travels with
        //the config). registerChain replaces wholesale, so a plugin re-enable reinstalls cleanly.
        EntitySchemaMigrations.registerChain(pdSectionConfiguration.getPdSectionClass(),
                pdSectionConfiguration.getMigrations());
        REGISTERED_SECTIONS.put(pdSectionConfiguration.getPdSectionClass(), pdSectionConfiguration);
        PlayerController controller = INSTANCE;
        if (controller != null){
            controller.bindSection(pdSectionConfiguration);
        }
        //registrations made before the bootstrap are bound in PlayerController.start()
    }

    public static <T extends AccountSection<T>> void registerAccountSectionCfg(ECPluginData ecPluginData,
                                                                               Class<T> sectionClass){
        registerAccountSectionCfg(AccountSectionConfiguration.builder(ecPluginData, sectionClass).build());
    }

    public static void registerAccountSectionCfg(AccountSectionConfiguration<?> configuration){
        //install the schema-migration chain FIRST (see registerPDSectionCfg)
        EntitySchemaMigrations.registerChain(configuration.getSectionClass(), configuration.getMigrations());
        REGISTERED_ACCOUNT_SECTIONS.put(configuration.getSectionClass(), configuration);
        PlayerController controller = INSTANCE;
        if (controller != null){
            controller.accountEngine.bindUnchecked(configuration);
        }
        //registrations made before the bootstrap are bound in PlayerController.start()
    }

    /**
     * Seeds the framework's own player-cooldown rows into the section registries the bind loops of
     * {@link #start()} consume: a per-player LOCAL row (loads with the player, evicts a grace after
     * quit) and an account-wide NETWORK row (on the shared account backend). Idempotent and
     * reload-surviving, so a player cooldown always has a storage route without every plugin
     * declaring one. The network reach is opted into at the call site, so both rows are registered
     * unconditionally regardless of whether the multi-platform-accounts layer is enabled.
     *
     * <p>Also called before the legacy import binds its adapters, so the local row's
     * {@code legacyYaml("Cooldown", ...)} claim is in place when the importer scans a v3 file.</p>
     */
    void registerBuiltinCooldownSections(){
        if (!REGISTERED_SECTIONS.containsKey(PlayerCooldownsLocal.class)){
            PDSectionConfiguration<PlayerCooldownsLocal> local = PDSectionConfiguration
                    .builder(EverNifeCore.getEcPluginData(), PlayerCooldownsLocal.class)
                    .cache(SectionCachePolicy.workingSet())
                    //claim the legacy v3 'Cooldown:' block so a first-boot import migrates it here
                    //instead of leaving it pending forever (there is no other owner for that root key)
                    .legacyYaml("Cooldown", PlayerCooldownsLocal::fromLegacyYaml)
                    .build();
            EntitySchemaMigrations.registerChain(PlayerCooldownsLocal.class, local.getMigrations());
            REGISTERED_SECTIONS.put(PlayerCooldownsLocal.class, local);
        }
        if (!REGISTERED_ACCOUNT_SECTIONS.containsKey(PlayerCooldownsNetwork.class)){
            AccountSectionConfiguration<PlayerCooldownsNetwork> network = AccountSectionConfiguration
                    .builder(EverNifeCore.getEcPluginData(), PlayerCooldownsNetwork.class)
                    .build();
            EntitySchemaMigrations.registerChain(PlayerCooldownsNetwork.class, network.getMigrations());
            REGISTERED_ACCOUNT_SECTIONS.put(PlayerCooldownsNetwork.class, network);
        }
    }

    /**
     * Unregisters every PDSection the given plugin registered: flushes each section's dirty cells,
     * drops the binding (cache + collection claim released) and removes the schema-migration steps
     * of those classes. MUST be called when the plugin is disabled at runtime - otherwise the
     * registry keeps the plugin's classes (and its classloader) alive, and a re-enabled plugin's
     * re-registration (new classloader, new Class object) would duplicate the collection claim.
     */
    public static void unregisterPDSections(ECPluginData ecPluginData){
        if (ecPluginData == null) return;
        String pluginName = ecPluginData.getMetaInfo().getName();
        List<Class<? extends PDSection>> owned = new ArrayList<>();
        for (PDSectionConfiguration<?> configuration : REGISTERED_SECTIONS.values()){
            ECPluginData owner = configuration.getPluginData();
            if (owner != null && owner.getMetaInfo().getName().equals(pluginName)){
                owned.add(configuration.getPdSectionClass());
            }
        }
        List<Class<?>> ownedAccountSections = new ArrayList<>();
        for (AccountSectionConfiguration<?> configuration : REGISTERED_ACCOUNT_SECTIONS.values()){
            ECPluginData owner = configuration.getPluginData();
            if (owner != null && owner.getMetaInfo().getName().equals(pluginName)){
                ownedAccountSections.add(configuration.getSectionClass());
            }
        }
        if (owned.isEmpty() && ownedAccountSections.isEmpty()) return;
        for (Class<? extends PDSection> pdSectionClass : owned){
            REGISTERED_SECTIONS.remove(pdSectionClass);
            EntitySchemaMigrations.clear(pdSectionClass);
        }
        for (Class<?> sectionClass : ownedAccountSections){
            REGISTERED_ACCOUNT_SECTIONS.remove(sectionClass);
            EntitySchemaMigrations.clear(sectionClass);
        }
        PlayerController controller = INSTANCE;
        if (controller != null){
            controller.unbindSections(pluginName, owned);
            controller.accountEngine.unbind(pluginName, ownedAccountSections);
            controller.ecRegistries.drop(ecPluginData); //release the plugin's child RefRegistry too
        }
    }

    private void unbindSections(String pluginName, List<Class<? extends PDSection>> classes){
        boolean anyRemoved = false;
        for (Class<? extends PDSection> pdSectionClass : classes){
            PDSectionBinding<? extends PDSection> binding = bindings.remove(pdSectionClass);
            if (binding == null) continue;
            anyRemoved = true;
            try {
                //final flush so nothing the plugin wrote in its last moments is lost
                flushEngine.flushAll().join();
            }catch (Throwable flushFailure){
                PDLog.warning("Final flush while unregistering PDSection {%s} of plugin '%s' failed: %s",
                        pdSectionClass.getSimpleName(), pluginName, String.valueOf(flushFailure.getMessage()));
            }
            binding.getManager().clearCache();
            //drop the manager from the plugin's RefRegistry so its Class object is not retained
            ecRegistries.of(binding.getConfiguration().getPluginData()).unregister(pdSectionClass);
            registry.releaseCollection(binding.getBackendName(), binding.getCollection());
            PDLog.info("Unregistered PDSection {%s} of plugin '%s' (collection '%s' on backend '%s' released).",
                    pdSectionClass.getSimpleName(), pluginName, binding.getCollection(), binding.getBackendName());
        }
        if (anyRemoved){
            onBindingsChanged(); //cache-sync and the ttl purge must stop referencing the dropped managers
        }
    }

    public static Map<Class<? extends PDSection>, PDSectionConfiguration<?>> getConfiguredPDSections() {
        return REGISTERED_SECTIONS;
    }

    public static Map<Class<?>, AccountSectionConfiguration<?>> getConfiguredAccountSections() {
        return REGISTERED_ACCOUNT_SECTIONS;
    }

    /**
     * Names of the storage backends that are declared AND enabled in storage.yml, in
     * declaration order. Empty when the controller is not bootstrapped. Intended for admin
     * tooling (tab-completion, an early friendly error): the hard validation of a transfer
     * target is still done by {@link #transferPDSection(Class, String)} / {@link #transferPlayerData(String)}.
     */
    public static List<String> getEnabledBackendNames() {
        PlayerController controller = INSTANCE;
        if (controller == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (BackendDefinition backend : controller.storageConfig.getBackends().values()){
            if (backend.isEnabled()){
                names.add(backend.getName());
            }
        }
        return names;
    }

    /** Human-readable summary of where the base entity and each section currently persist. */
    public static String storageSummary() {
        PlayerController controller = INSTANCE;
        if (controller == null) return "PlayerController not bootstrapped";
        StringBuilder sb = new StringBuilder("playerdata-backend=")
                .append(controller.playerDataBinding.getBackendName())
                .append(" collection=").append(controller.playerDataBinding.getCollection());
        for (PDSectionBinding<? extends PDSection> binding : controller.bindings.values()){
            sb.append(" | ").append(binding.getPdSectionClass().getSimpleName())
                    .append("-backend=").append(binding.getBackendName())
                    .append(" collection=").append(binding.getCollection());
        }
        return sb.toString();
    }

    /**
     * Health snapshot for admin tooling ({@code /ecorestoragetransfer status}): routing plus the
     * counters that reveal a degrading storage BEFORE data is at risk (retry backlog, adopted
     * conflicts, last failed write).
     */
    public static String storageStatus() {
        PlayerController controller = INSTANCE;
        if (controller == null) return "PlayerController not bootstrapped";
        long lastFailure = controller.flushEngine.lastWriteFailureAt();
        return storageSummary()
                + "\nloaded-players=" + controller.baseManager().cachedSize()
                + " | quit-flush-retry-backlog=" + controller.lifecycleEngine.retryBacklogSize()
                + " | conflicts-adopted=" + controller.flushEngine.conflictsAdoptedCount()
                + " | last-write-failure=" + (lastFailure == 0L ? "never"
                        : ((System.currentTimeMillis() - lastFailure) / 1000) + "s ago");
    }

    @SuppressWarnings("unchecked")
    void bindSection(PDSectionConfiguration<?> configuration){
        bindSectionTyped((PDSectionConfiguration<PDSection>) configuration);
    }

    private <S extends PDSection> void bindSectionTyped(PDSectionConfiguration<S> cfg){
        @SuppressWarnings("unchecked")
        PDSectionBinding<S> binding = (PDSectionBinding<S>) bindings.get(cfg.getPdSectionClass());
        if (binding == null){
            String pluginName = cfg.getPluginData() != null ? cfg.getPluginData().getMetaInfo().getName() : "UnknownPlugin";
            String pluginAuthor = cfg.getPluginData() != null ? cfg.getPluginData().getMetaInfo().getAuthor() : "Unknown";

            //auto-generate the pdsections.<Plugin>.<Section> entry when missing.
            //A freshly generated entry matches the dev defaults the resolver
            //falls back to, so it doesn't need a re-parse on the first registration.
            String backendValue = cfg.getDefaultBackend() != null ? cfg.getDefaultBackend() : storageConfig.getDefaultBackendName();
            PDSectionYamlWriter.ensureEntry(storageYml, pluginName, pluginAuthor,
                    cfg.getPdSectionClass().getSimpleName(), backendValue,
                    cfg.getSuggestedBackends(), storageConfig.getBackends().keySet());

            //resolve backend/collection/codec/cache + claim + caching manager
            binding = BindingResolver.resolve(pluginName, cfg, storageConfig, registry,
                    ecRegistries.of(cfg.getPluginData()));
            for (String warning : binding.getResolutionWarnings()){
                PDLog.warning(warning);
            }

            bindings.put(cfg.getPdSectionClass(), binding);
        }
        //re-entrant on purpose: a section bound before the players are loaded
        //(registered between a bootstrap with a pending import and the first tick) gets its
        //hot-load done when start() visits it again

        //hot-load over the already-loaded players (in batch through the manager)
        List<PlayerData> loadedPlayers = baseManager().cachedValues();
        if (cfg.shouldHotLoad() && !loadedPlayers.isEmpty()){
            long start = System.currentTimeMillis();
            CachingManager<UUID, S> manager = binding.getManager();
            Class<S> sectionClass = cfg.getPdSectionClass();
            List<UUID> keys = new ArrayList<>(loadedPlayers.size());
            for (PlayerData playerData : loadedPlayers){
                keys.add(playerData.getUniqueId());
            }
            //getAll never overwrites an unflushed dirty cell (dirty-wins), so the old
            //"skip recentChanged" guard is automatic
            manager.getAll(keys).join();
            for (PlayerData playerData : loadedPlayers){
                UUID key = playerData.getUniqueId();
                S stored = manager.peek(key).orElse(null);
                S section;
                if (stored != null){
                    try {
                        StoredSection.upcastOrEvict(manager, key, stored); //lazy upcast before it reaches the plugin
                    }catch (RuntimeException migrationFailure){
                        continue; //already logged + evicted: skip this player, never abort the whole bind
                    }
                    section = stored;
                }else {
                    section = manager.seedIfAbsent(key, StoredSection.newDefault(sectionClass));
                }
                section.attachPlayerData(playerData);
            }
            long end = System.currentTimeMillis();
            PDLog.info("Finished Loading PDSection {%s} of %s players! (%s)",
                    cfg.getPdSectionClass().getSimpleName(), loadedPlayers.size(),
                    formatDuration(end - start));
        }

        warmup(cfg, binding);

        //eager schema sweep of this section (async, post-ready, O(1) when nothing eager is pending); this
        //hook covers boot binds AND late registerPDSectionCfg calls from dependent plugins
        sweepEngine.maybeSweep(binding);
    }

    /**
     * Warms a section's cache at bind time per {@link SectionCachePolicy.Warmup}: NONE is lazy (nothing
     * pre-loaded, the default); ALL pre-loads the whole collection ({@code preloadAll}). Best-effort -
     * a warmup failure never aborts the bind.
     */
    private <S extends PDSection> void warmup(PDSectionConfiguration<S> cfg, PDSectionBinding<S> binding){
        SectionCachePolicy.Warmup warmupMode = cfg.getWarmup();
        if (warmupMode == null || warmupMode == SectionCachePolicy.Warmup.NONE) return;
        try {
            binding.getManager().preloadAll().join();
        }catch (Throwable warmupFailure){
            PDLog.warning("Warmup (%s) of PDSection {%s} failed - continuing lazily: %s",
                    warmupMode, cfg.getPdSectionClass().getSimpleName(), String.valueOf(warmupFailure.getMessage()));
        }
    }

    <T extends PDSection> PDSectionBinding<T> getBinding(Class<T> pdSectionClass){
        return (PDSectionBinding<T>) bindings.get(pdSectionClass);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // MEMORY ONLY facade (sync - never touches storage)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** @return the loaded PlayerData, or null when that player is not in memory. */
    public static PlayerData getLoaded(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        return controller == null ? null : controller.baseManager().peek(uuid).orElse(null);
    }

    public static PlayerData getLoaded(FPlayer player){
        Objects.requireNonNull(player, "Player can't be null");
        return getLoaded(player.getUniqueId());
    }

    public static PlayerData getLoaded(String playerName){
        Objects.requireNonNull(playerName, "PlayerName can't be null");
        UUID uuid = UUIDsController.getUUIDFromName(playerName);
        return uuid == null ? null : getLoaded(uuid);
    }

    public static Collection<PlayerData> getAllLoaded(){
        PlayerController controller = INSTANCE;
        return controller == null ? Collections.<PlayerData>emptyList() : controller.baseManager().cachedValues();
    }

    public static int getLoadedCount(){
        PlayerController controller = INSTANCE;
        return controller == null ? 0 : controller.baseManager().cachedSize();
    }

    /** That player's cached section, or null when it isn't loaded (never touches storage). */
    public static <T extends PDSection> T getLoadedSection(UUID uuid, Class<T> pdSectionClass){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return null;
        PDSectionBinding<T> binding = controller.getBinding(pdSectionClass);
        if (binding == null) return null;
        return binding.getManager().peek(uuid).orElse(null);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // async facade (may touch storage; call .join() for synchronous use).
    // Every path below waits on the {@code ready} gate: during the first-boot legacy import the
    // collection is still empty, and answering from it would misreport stored players as absent
    // (or create a duplicate the import then skips).
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Completes {@code callback} on the platform's main thread once {@code future} finishes - the
     * safe bridge for code that must touch game state with an async result. Exactly one of the
     * callback arguments is non-null (the value on success, the failure otherwise).
     */
    public static <T> void whenCompleteOnMainThread(CompletableFuture<T> future, BiConsumer<T, Throwable> callback){
        future.whenComplete((value, error) ->
                EverNifeCore.getPlatform().runOnMainThread(() -> callback.accept(value, error)));
    }

    /** Memory or lazy-load; completes with null when the player doesn't exist in the backend. */
    public static CompletableFuture<PlayerData> getPlayerData(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.doGetIfExists(uuid));
    }

    public static CompletableFuture<PlayerData> getPlayerData(String playerName){
        Objects.requireNonNull(playerName, "PlayerName can't be null");
        UUID uuid = UUIDsController.getUUIDFromName(playerName);
        if (uuid != null) return getPlayerData(uuid);
        //not in the in-memory name index (RECENT load-mode, or a name never seen since boot):
        //consult the @Indexed 'name' column instead of wrongly reporting a stored player as absent
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.findByNameInBackend(playerName));
    }

    /**
     * Indexed backend lookup by exact name (case-sensitive - the stored value). Duplicate names
     * (an OnlineMode flip) resolve to the most recently seen row, mirroring the boot-time index
     * rule; a hit seeds the name index and joins the live cache through the normal load path.
     */
    private CompletableFuture<PlayerData> findByNameInBackend(String playerName){
        return playerDataBinding.getRepository().query(Query.eq("name", playerName)).thenCompose(rows -> {
            PlayerData winner = null;
            for (PlayerData row : rows){
                if (winner == null || row.getLastSeen() >= winner.getLastSeen()){
                    winner = row;
                }
            }
            if (winner == null) return CompletableFuture.completedFuture(null);
            UUIDsController.addOrUpdateUUIDName(winner.getUniqueId(), winner.getName());
            //re-resolve through the manager so the cached instance stays canonical (the query
            //result is a detached copy, never handed out)
            return doGetIfExists(winner.getUniqueId());
        });
    }

    /** Memory, lazy-load or create+persist a brand-new PlayerData. */
    public static CompletableFuture<PlayerData> getOrCreate(UUID uuid){
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.doGetOrCreate(uuid));
    }

    /**
     * The player's section, lazy-loading the player itself when needed (memory hit stays instant).
     * Completes with {@code null} only when the player does not exist in the backend at all.
     */
    public static <T extends PDSection> CompletableFuture<T> getPDSection(UUID uuid, Class<T> pdSectionClass){
        PlayerData loaded = getLoaded(uuid);
        if (loaded != null) {
            return loaded.getPDSection(pdSectionClass);
        }
        //not in memory (RECENT load-mode, or evicted): honor the async contract and consult storage
        //instead of reporting real stored data as absent
        return getPlayerData(uuid).thenCompose(playerData ->
                playerData == null
                        ? CompletableFuture.<T>completedFuture(null)
                        : playerData.getPDSection(pdSectionClass));
    }

    public static <T extends PDSection> CompletableFuture<T> getPDSection(FPlayer player, Class<T> pdSectionClass){
        return getPDSection(player.getUniqueId(), pdSectionClass);
    }

    public static <T extends PDSection> CompletableFuture<T> getPDSection(String playerName, Class<T> pdSectionClass){
        //routes through the name fallback too: a stored-but-unloaded player must resolve
        return getPlayerData(playerName).thenCompose(playerData ->
                playerData == null
                        ? CompletableFuture.<T>completedFuture(null)
                        : playerData.getPDSection(pdSectionClass));
    }

    /** Discards that player's in-memory state and reloads it from the backend. */
    public static CompletableFuture<PlayerData> reloadPlayerData(UUID uuid){
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> {
            controller.baseManager().evict(uuid);
            return controller.doGetOrCreate(uuid);
        });
    }

    /** Discards every cached instance of that section (the next reads reload from the backend). */
    public static void clearPDSections(Class<? extends PDSection> pdSectionClass){
        PlayerController controller = INSTANCE;
        if (controller == null) return;
        PDSectionBinding<? extends PDSection> binding = controller.bindings.get(pdSectionClass);
        if (binding != null){
            binding.getManager().clearCache();
        }
    }

    /** Marks a player's cached section as stale - the next read reloads it. */
    public static void invalidatePDSection(Class<? extends PDSection> pdSectionClass, UUID uuid){
        PlayerController controller = INSTANCE;
        if (controller == null) return;
        PDSectionBinding<? extends PDSection> binding = controller.bindings.get(pdSectionClass);
        if (binding != null){
            binding.getManager().invalidate(uuid);
        }
    }

    /** Marks a cached account row as stale - the next read reloads it. */
    public static void invalidateAccountSection(Class<?> sectionClass, UUID accountId){
        PlayerController controller = INSTANCE;
        if (controller == null || accountId == null) return;
        AccountSectionBinding<?> binding = controller.accountEngine.getBindingUnchecked(sectionClass);
        if (binding != null){
            binding.getManager().invalidate(accountId);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Delete + cascade + orphan reaper (integrity)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Deletes a player's base PlayerData AND every registered section row for that player (cascade),
     * evicting each cell from its cache. The player's account-wide rows are deleted too when the
     * account is a singleton (the player alone); a LINKED account's rows are shared with the other
     * identities and are kept (unlink first). The base is deleted last, so a failed section delete
     * fails the whole operation before the base is removed - the base then stays as the anchor a
     * retry (or {@link #reapOrphanSections()}) keys off, never a stranded section.
     *
     * <p>Removes stored rows only; it does not touch the {@code UUIDsController} name index (an
     * out-of-game admin action, not a rename). Intended for a single player at a time.</p>
     */
    public static CompletableFuture<Void> deletePlayerData(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.doDeletePlayerData(uuid));
    }

    private CompletableFuture<Void> doDeletePlayerData(UUID uuid){
        //an online player's live references would silently resurrect the rows (any held section can
        //be re-dirtied and re-flushed after the delete) - deleting is an offline admin action
        PlayerData loaded = baseManager().peek(uuid).orElse(null);
        if (loaded != null && loaded.isPlayerOnline()){
            return failedFuture(new IllegalStateException("Cannot delete the PlayerData of ["
                    + uuid + "] while the player is ONLINE - kick the player first."));
        }
        //resolve the STORED account truth (not the possibly stale stamp): a linked player's account
        //rows are shared with the other identities and must survive this player's deletion
        return Accounts.get().account(uuid).thenCompose(account -> {
            UUID accountKey = account.getAccountId();
            boolean linkedAccount = !accountKey.equals(uuid);
            List<CompletableFuture<?>> sectionDeletes = new ArrayList<>();
            for (PDSectionBinding<? extends PDSection> binding : bindings.values()){
                sectionDeletes.add(binding.getManager().deleteAndEvict(uuid));
            }
            for (AccountSectionBinding<?> binding : accountEngine.bindings()){
                if (linkedAccount){
                    PDLog.warning("deletePlayerData(%s): keeping AccountSection {%s} - its row [%s] is"
                                    + " shared with the other identities linked into the account.",
                            uuid, binding.getSectionClass().getSimpleName(), accountKey);
                    continue;
                }
                sectionDeletes.add(binding.getManager().deleteAndEvict(accountKey));
            }
            CompletableFuture<Void> allSections = sectionDeletes.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(sectionDeletes.toArray(new CompletableFuture[0]));
            //delete the base last: if a section delete fails the whole future fails before the base is
            //removed, so the base survives as the anchor a retry (or the reaper) keys off - never the
            //reverse (base gone, sections stranded with no anchor)
            return allSections.thenCompose(y -> baseManager().deleteAndEvict(uuid)).thenApply(existed -> null);
        });
    }

    /**
     * Conservative periodic sweep that removes PDSection rows whose base PlayerData no longer
     * exists - the leftovers of an out-of-band base delete or an interrupted cascade. OFF by
     * default (opt-in via {@code playerdata.orphan-reaper.enabled} in storage.yml); when enabled it runs
     * infrequently by design (default {@code playerdata.orphan-reaper.interval-minutes: 360}).
     *
     * <p><b>Account sections are never swept:</b> their rows key by an {@code accountId} and belong to
     * the account, not to one base PlayerData - a missing base uuid does not imply an orphan there.
     * Returns the number of orphan rows removed.</p>
     */
    public CompletableFuture<Long> reapOrphanSections(){
        List<CompletableFuture<Long>> perSection = new ArrayList<>();
        for (PDSectionBinding<? extends PDSection> binding : bindings.values()){
            perSection.add(reapOrphansOf(binding));
        }
        if (perSection.isEmpty()) return CompletableFuture.completedFuture(0L);
        return CompletableFuture.allOf(perSection.toArray(new CompletableFuture[0]))
                .thenApply(x -> {
                    long total = 0L;
                    for (CompletableFuture<Long> f : perSection) total += f.join();
                    return total;
                });
    }

    /**
     * Reaps one section collection, one key-ordered page at a time.
     *
     * <p>Consistency: this is an incremental scan over live data, never a snapshot - a row created
     * while it runs may or may not be visited this cycle, and one missed is simply reaped by the next
     * cycle. That is safe because nothing here decides by a total count: every key is judged on its
     * own by a base lookup, and a base delete publishes the base row last precisely so that a reaper
     * racing a cascade still sees the anchor and keeps its hands off (see {@code doDeletePlayerData}).
     */
    private <S extends PDSection> CompletableFuture<Long> reapOrphansOf(PDSectionBinding<S> binding){
        return reapOrphanPage(binding, Cursor.scan(), 0L);
    }

    /** One page: collect the stored keys, reap the orphans among them, then recurse into the next page. */
    private <S extends PDSection> CompletableFuture<Long> reapOrphanPage(PDSectionBinding<S> binding,
                                                                        Cursor cursor, long removedSoFar){
        return binding.getRepository().scanAll(cursor, REAP_BATCH_SIZE).thenCompose(slice -> {
            List<UUID> pageKeys = new ArrayList<>();
            for (ScanRow<S> row : slice.content()){
                //a row whose payload will not decode is left ALONE: on the file backends its key is only
                //a best-effort guess, and deleting on a guess is worse than leaving a poisoned row for an
                //admin to look at. Naming it here is the point - a plain read would drop it silently.
                if (row.isFailed()){
                    PDLog.warning("Orphan reaper: skipping section {%s} row '%s' - its payload does not"
                                    + " decode (%s). It is left untouched; reap it by hand if it is dead.",
                            binding.getCollection(), row.key(), String.valueOf(row.error()));
                    continue;
                }
                UUID key = parseSectionKey(row.key());
                if (key != null) pageKeys.add(key);
            }
            return reapOrphansAmong(binding, pageKeys).thenCompose(removedHere -> {
                long removed = removedSoFar + removedHere;
                Optional<Cursor> next = slice.hasNext() ? slice.nextCursor() : Optional.empty();
                return next.isPresent()
                        ? reapOrphanPage(binding, next.get(), removed)
                        : CompletableFuture.completedFuture(removed);
            });
        });
    }

    /** Deletes the given section keys whose base PlayerData is gone; returns how many rows went away. */
    private <S extends PDSection> CompletableFuture<Long> reapOrphansAmong(PDSectionBinding<S> binding,
                                                                          List<UUID> pageKeys){
        if (pageKeys.isEmpty()) return CompletableFuture.completedFuture(0L);
        //one key+version read for the whole page instead of a round-trip per key: versions() reports only
        //the keys that exist, so "absent from the map" IS the batched existence answer (the version value
        //itself is never read - backends that do not version report a literal 0 for every live key)
        return baseManager().repository().versions(pageKeys).thenCompose(livingBases -> {
            List<CompletableFuture<Boolean>> deletes = new ArrayList<>();
            for (UUID key : pageKeys){
                if (livingBases.containsKey(key)) continue; //base row still present in the backend
                //a base live in cache but not yet flushed is NOT an orphan: reaping its section here
                //would delete data the player still owns before the base reaches the backend
                if (baseManager().peek(key).isPresent()) continue;
                deletes.add(binding.getManager().deleteAndEvict(key));
            }
            if (deletes.isEmpty()) return CompletableFuture.completedFuture(0L);
            return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0]))
                    .thenApply(y -> {
                        long removed = 0L;
                        for (CompletableFuture<Boolean> d : deletes) if (Boolean.TRUE.equals(d.join())) removed++;
                        return removed;
                    });
        });
    }

    /** A stored section key back into a UUID; {@code null} when the stored key is not one (corrupt row). */
    private static UUID parseSectionKey(String storageKey){
        try {
            return UUID.fromString(storageKey);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /**
     * The throttled entry point the periodic tick calls: a no-op unless the admin enabled
     * the orphan reaper AND at least {@code interval-minutes} elapsed since the last run. Reaping is
     * guarded by a CAS so two overlapping ticks never run it twice, and any failure is logged, never
     * propagated (the reaper is best-effort maintenance).
     */
    public void maybeReapOrphans(){
        PlayerDataAdminConfig adminConfig = storageConfig.getPlayerData();
        if (!adminConfig.isOrphanReaperEnabled()) return;
        long now = System.currentTimeMillis();
        long intervalMillis = TimeUnit.MINUTES.toMillis(adminConfig.getOrphanReaperIntervalMinutes());
        if (lastOrphanReapAt != 0L && now - lastOrphanReapAt < intervalMillis) return;
        if (!orphanReapRunning.compareAndSet(false, true)) return;
        lastOrphanReapAt = now;
        //fully async: a reap sweeps whole collections, and blocking the periodic thread on it would
        //stall the flush cadence (and the retry-queue drain) exactly on large installs
        reapOrphanSections().whenComplete((removed, reapFailure) -> {
            if (reapFailure != null){
                PDLog.warning("Orphan reaper failed (will retry next cycle): %s", String.valueOf(reapFailure.getMessage()));
            }else if (removed != null && removed > 0){
                PDLog.info("Orphan reaper removed %s section row(s) whose base PlayerData no longer exists.", removed);
            }
            orphanReapRunning.set(false);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Login / rename / uuid swap - no longer involves files
    // -----------------------------------------------------------------------------------------------------------------------------//

    public static CompletableFuture<PlayerData> handleLogin(UUID uuid, String playerName){
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        //the ready gate only holds logins during the legacy import on the first boot
        return controller.ready.thenCompose(v -> controller.doHandleLogin(uuid, playerName));
    }

    /**
     * Login resolution bounded by the admin-configured timeout ({@code playerdata.login-timeout-seconds},
     * default 5s): the async pipeline of {@link #handleLogin} with a hard timeout, so a hung backend
     * cannot hang the platform's login thread forever. The returned future completes exceptionally
     * (with a {@link TimeoutException}) when storage does not answer in time - the caller (the
     * platform's pre-login listener) then DENIES the login instead of blocking. On a fast backend it
     * behaves exactly like {@code handleLogin} (no added latency).
     */
    public static CompletableFuture<PlayerData> handleLoginWithTimeout(UUID uuid, String playerName){
        PlayerController controller = INSTANCE;

        if (controller == null){
            return failedFuture(notBootstrapped());
        }

        long timeoutSeconds = controller.storageConfig.getPlayerData().getLoginTimeoutSeconds();
        CompletableFuture<PlayerData> login = handleLogin(uuid, playerName);
        CompletableFuture<PlayerData> bounded = new CompletableFuture<>();
        ScheduledFuture<?> timeout = controller.lifecycleEngine.scheduler().schedule(
                () -> bounded.completeExceptionally(new TimeoutException(
                        "PlayerData login resolution timed out after " + timeoutSeconds
                                + "s (storage down?) - denying the login for [" + uuid + "]")),
                TimeUnit.SECONDS.toMillis(timeoutSeconds), TimeUnit.MILLISECONDS);
        login.whenComplete((playerData, error) -> {
            timeout.cancel(false);
            if (error != null) bounded.completeExceptionally(error);
            else bounded.complete(playerData);
        });
        return bounded;
    }

    private CompletableFuture<PlayerData> doHandleLogin(UUID currentUUID, String currentName){
        return Accounts.get().resolveOnLogin(currentUUID, currentName)
                .thenCompose(account -> doHandleLoginResolved(currentUUID, currentName)
                        .thenCompose(playerData ->
                                //login is the ONE reconciliation point of the stamped accountId: a
                                //link/unlink decided elsewhere (other instance, offline) lands here,
                                //BEFORE the account rows are loaded under that key
                                migrateAndStamp(playerData, account.getAccountId())
                                        .thenCompose(x -> accountEngine.hotLoadOnLogin(playerData))
                                        .thenApply(x -> playerData)));
    }

    /**
     * Reconciles the stamped accountId with the account resolved at login. When they differ because
     * the old stamp is a FORMER key of that same account (the pre-link uuid, an absorbed account's
     * id), the data rows stored under it are absorbed into the canonical rows FIRST and the new id
     * is stamped only after every section migrated - a crash mid-way keeps the old stamp, and the
     * ledger makes the re-run at the next login safe even for non-idempotent merges. When the old
     * stamp is NOT a former key (an unlink: the account the player just left), nothing is absorbed -
     * the rows belong to that account - and the new identity is simply adopted. A migration failure
     * keeps the old stamp for this session and retries at the next login.
     */
    private CompletableFuture<Void> migrateAndStamp(PlayerData playerData, UUID resolvedId){
        UUID stamped = playerData.getAccountId();
        if (stamped.equals(resolvedId)){
            return CompletableFuture.completedFuture(null);
        }
        return Accounts.get().isFormerKeyOf(stamped, resolvedId, playerData.getUniqueId())
                .thenCompose(formerKey -> {
                    if (!formerKey){
                        playerData.stampAccountId(resolvedId);
                        return CompletableFuture.completedFuture(null);
                    }
                    return accountEngine.migrateKeyedRows(stamped, resolvedId)
                            .handle((ok, failure) -> failure)
                            .thenAccept(failure -> {
                                if (failure == null){
                                    playerData.stampAccountId(resolvedId);
                                } else {
                                    PDLog.warning("Account data migration of [%s] (%s -> %s) failed -"
                                            + " keeping the previous account stamp for this session;"
                                            + " it will retry at the next login: %s",
                                            playerData.getUniqueId(), stamped, resolvedId,
                                            String.valueOf(failure));
                                }
                            });
                });
    }

    private CompletableFuture<PlayerData> doHandleLoginResolved(UUID currentUUID, String currentName){
        if (UUIDsController.isUUIDLinkedToName(currentUUID, currentName)){
            //99% of re-logins: name and uuid unchanged
            return doGetOrCreate(currentUUID);
        }

        String existingName = UUIDsController.getNameFromUUID(currentUUID);
        UUID existingUUID = UUIDsController.getUUIDFromName(currentName);

        if (existingName != null && !existingName.equals(currentName)){
            PDLog.info("[PlayerController] [%s] changed his name from %s to %s", currentUUID, existingName, currentName);
        }
        if (existingUUID != null && !existingUUID.equals(currentUUID)){
            //OnlineMode flip or an old player's name reused by a new account: the
            //old record stays in the backend keyed by its own UUID; only the name link is remapped
            PDLog.info("[PlayerController] The name [%s] now belongs to %s (previously %s)",
                    currentName, currentUUID, existingUUID);
        }

        UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
        return doGetOrCreate(currentUUID).thenApply(playerData -> {
            if (!currentName.equals(playerData.getName())){
                playerData.setName(currentName);
                playerData.markDirty();
            }
            return playerData;
        });
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // load / create internals
    // -----------------------------------------------------------------------------------------------------------------------------//

    private CompletableFuture<PlayerData> doGetIfExists(UUID uuid){
        CachingManager<UUID, PlayerData> baseManager = baseManager();
        PlayerData loaded = baseManager.peek(uuid).orElse(null);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);

        //resolve caches the instance (always() policy); wake it and hot-load its sections.
        //Repeated resolves return the same cached instance, so the wake/name update is idempotent.
        return baseManager.resolve(uuid).thenCompose(stored -> {
            if (!stored.isPresent()) return CompletableFuture.completedFuture(null);
            PlayerData playerData = stored.get();
            playerData.warnIfStaleSchema();
            if (playerData.getName() != null){
                UUIDsController.addOrUpdateUUIDName(playerData.getUniqueId(), playerData.getName());
            }
            return hotLoadSectionsFor(playerData).thenApply(x -> playerData);
        });
    }

    private CompletableFuture<PlayerData> doGetOrCreate(UUID uuid){
        Objects.requireNonNull(uuid, "PlayerUUID can't be null");
        return doGetIfExists(uuid).thenCompose(existing -> {
            if (existing != null) return CompletableFuture.completedFuture(existing);

            String knownName = UUIDsController.getNameFromUUID(uuid);
            PlayerData playerData = new PlayerData(uuid, knownName != null ? knownName : uuid.toString());
            //seedIfAbsent wins a creation race: another caller's instance, if present, becomes canonical
            PlayerData raced = baseManager().seedIfAbsent(uuid, playerData);
            if (raced != playerData) return CompletableFuture.completedFuture(raced);

            playerData.markDirty();
            //persist immediately and keep the cell cached (like the old addNewPlayerData + forceSave)
            return baseManager().saveAndCache(playerData)
                    .thenCompose(x -> hotLoadSectionsFor(playerData))
                    .thenApply(x -> playerData);
        });
    }

    /** Loads each hotLoad section of a freshly loaded player (the lazy-load and create paths). */
    private CompletableFuture<Void> hotLoadSectionsFor(PlayerData playerData){
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (PDSectionBinding<? extends PDSection> binding : bindings.values()){
            if (binding.getConfiguration().shouldHotLoad()){
                futures.add(attachThroughManager(playerData, binding));
            }
        }
        if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * The single lazy-load path of a section: resolve it through the manager (cache or backend),
     * seed a default when the backend has nothing, attach the PlayerData and return the live cached
     * instance. (Package-visible: {@link PlayerData#getPDSection(Class)} delegates here.)
     */
    <T extends PDSection> CompletableFuture<T> resolveSection(PlayerData pd, Class<T> cls){
        PDSectionBinding<T> binding = getBinding(cls);
        if (binding == null){
            return failedFuture(notRegisteredPDSection(cls));
        }
        return attachThroughManager(pd, binding);
    }

    private <S extends PDSection> CompletableFuture<S> attachThroughManager(PlayerData playerData, PDSectionBinding<S> binding){
        CachingManager<UUID, S> manager = binding.getManager();
        UUID key = playerData.getUniqueId();
        return manager.resolve(key).thenApply(stored -> {
            S section;
            if (stored.isPresent()){
                section = stored.get();
                StoredSection.upcastOrEvict(manager, key, section); //never hand out a half-migrated instance
            }else {
                section = manager.seedIfAbsent(key, StoredSection.newDefault(binding.getPdSectionClass()));
            }
            section.attachPlayerData(playerData);
            return section;
        });
    }

    /**
     * Presence-only resolution of a section (no default is seeded): resolves through the manager and
     * completes with the attached stored/cached section, or {@link Optional#empty()} on a true miss.
     * The bulk-safe counterpart of {@link #resolveSection(PlayerData, Class)}. (Package-visible:
     * {@link PlayerData#getPDSectionIfPresent(Class)} delegates here.)
     */
    <T extends PDSection> CompletableFuture<Optional<T>> resolveSectionIfPresent(PlayerData pd, Class<T> cls){
        PDSectionBinding<T> binding = getBinding(cls);
        if (binding == null){
            return failedFuture(notRegisteredPDSection(cls));
        }
        CachingManager<UUID, T> manager = binding.getManager();
        UUID key = pd.getUniqueId();
        return manager.resolve(key).thenApply(stored -> {
            if (!stored.isPresent()) return Optional.<T>empty();
            T section = stored.get();
            if (section.isTransientDefault()){
                //the resolve found only a framework-seeded default (cache-only, e.g. from the
                //hot-load): no stored row exists, so the presence primitive must report absence
                return Optional.<T>empty();
            }
            StoredSection.upcastOrEvict(manager, key, section);
            section.attachPlayerData(pd);
            return Optional.of(section);
        });
    }

    /**
     * Cache-then-backend existence check for a section row (via the manager's {@code exists()}): a
     * live cached cell answers without I/O, otherwise the backend is consulted. Async because there is
     * no synchronous {@code hasKey}. (Package-visible: {@link PlayerData#hasPDSection(Class)} delegates
     * here.)
     */
    CompletableFuture<Boolean> hasSection(UUID uuid, Class<? extends PDSection> cls){
        PDSectionBinding<? extends PDSection> binding = bindings.get(cls);
        if (binding == null){
            return failedFuture(notRegisteredPDSection(cls));
        }
        UUID key = uuid;
        PDSection cached = binding.getManager().peek(key).orElse(null);
        if (cached != null){
            //answer from the cell - but a framework-seeded default is cache-only, and the manager's
            //exists() would wrongly report it as a stored row, so the flag decides instead
            return CompletableFuture.completedFuture(!cached.isTransientDefault());
        }
        return binding.getManager().exists(key);
    }

    /**
     * Runs an indexed backend query over a section's collection WITHOUT loading the whole collection
     * into the cache - the primitive behind an aggregate like {@code /baltop} (one indexed query with
     * {@code orderBy} + {@code limit} instead of scanning every cached section).
     *
     * <p><b>Contract:</b> every field referenced by {@code query} or by {@code options.orderBy()} MUST
     * be {@code @Indexed} on the section - otherwise the backend throws {@link IllegalArgumentException}
     * at execution time (the returned future completes exceptionally).</p>
     */
    public <T extends PDSection> CompletableFuture<List<T>> querySection(Class<T> cls, Query query, QueryOptions options){
        PDSectionBinding<T> binding = getBinding(cls);
        if (binding == null){
            return failedFuture(notRegisteredPDSection(cls));
        }
        return binding.getRepository().query(query, options == null ? QueryOptions.none() : options);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Account-section facade (the engine does the work; a PDSection stays keyed by the plain uuid)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * The account row of {@code playerUuid}'s account, lazy-loading the player itself when needed
     * (a transient default is seeded on a true miss). Completes with {@code null} only when the
     * player does not exist in the backend at all.
     */
    public static <T extends AccountSection<T>> CompletableFuture<T> getAccountSection(UUID playerUuid, Class<T> sectionClass){
        return getPlayerData(playerUuid).thenCompose(playerData ->
                playerData == null
                        ? CompletableFuture.<T>completedFuture(null)
                        : playerData.getAccountSection(sectionClass));
    }

    /**
     * The account row stored under {@code accountId} DIRECTLY - the offline/aggregate primitive
     * (no player resolution; a transient default is seeded on a true miss). Prefer
     * {@link #getAccountSection(UUID, Class)} when what you have is a player uuid.
     */
    public static <T extends AccountSection<T>> CompletableFuture<T> getAccountSectionByAccountId(UUID accountId, Class<T> sectionClass){
        Objects.requireNonNull(accountId, "accountId can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.accountEngine.resolve(sectionClass, accountId));
    }

    /** That account's cached row, or null when it isn't loaded (never touches storage). */
    public static <T extends AccountSection<T>> T getLoadedAccountSection(UUID playerUuid, Class<T> sectionClass){
        Objects.requireNonNull(playerUuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return null;
        PlayerData playerData = controller.baseManager().peek(playerUuid).orElse(null);
        if (playerData == null) return null;
        return controller.accountEngine.getLoaded(sectionClass, playerData.getAccountId());
    }

    /**
     * Indexed backend query over an account section's collection (the account-family counterpart of
     * {@link #querySection(Class, Query, QueryOptions)}) - same {@code @Indexed} contract.
     */
    public <T extends AccountSection<T>> CompletableFuture<List<T>> queryAccountSection(Class<T> sectionClass,
                                                                                        Query query, QueryOptions options){
        AccountSectionBinding<T> binding = accountEngine.getBinding(sectionClass);
        if (binding == null){
            return failedFuture(notRegisteredAccountSection(sectionClass));
        }
        return binding.getRepository().query(query, options == null ? QueryOptions.none() : options);
    }

    /**
     * Forces the account-data reconciliation of an OFFLINE player - the same absorption that runs
     * automatically at login - for an admin who does not want to wait for the member's next login.
     * Completes with {@code true} when the stamp moved (and any former-key rows were absorbed),
     * {@code false} when the stamp was already current; fails when the player is online (an online
     * session keeps its stamp until the next login) or does not exist.
     */
    public static CompletableFuture<Boolean> migrateAccountData(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return failedFuture(notBootstrapped());
        return controller.ready.thenCompose(v -> controller.doGetIfExists(uuid).thenCompose(playerData -> {
            if (playerData == null){
                return failedFuture(new IllegalStateException(
                        "There is no stored PlayerData for [" + uuid + "]"));
            }
            if (playerData.isPlayerOnline()){
                return failedFuture(new IllegalStateException("[" + uuid + "] is online - the"
                        + " reconciliation runs automatically at login and an online session keeps"
                        + " its account stamp until the next one"));
            }
            return Accounts.get().account(uuid).thenCompose(account -> {
                UUID resolvedId = account.getAccountId();
                if (playerData.getAccountId().equals(resolvedId)){
                    return CompletableFuture.completedFuture(false);
                }
                //migrateAndStamp only logs a migration failure (the login path must survive it);
                //here the admin asked explicitly, so an unchanged stamp is reported as a failure
                return controller.migrateAndStamp(playerData, resolvedId).thenCompose(x -> {
                    if (resolvedId.equals(playerData.getAccountId())){
                        return CompletableFuture.completedFuture(true);
                    }
                    return PlayerController.<Boolean>failedFuture(new IllegalStateException(
                            "The account data migration of [" + uuid + "] did not complete - check"
                                    + " the server log; it will retry at that player's next login"));
                });
            });
        }));
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Flush + quit + transfer facade (the engines do the work)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Background flush of every dirty entity (base batch + one batch per section manager). A
     * conflict is resolved by ADOPT_WINNER and only logged - the returned future never fails on a
     * conflict (it is the periodic/shutdown path, not a caller).
     */
    public CompletableFuture<Void> flushAll(){
        return flushEngine.flushAll();
    }

    /**
     * Immediate flush of a single player - see {@link FlushEngine#flushPlayer(PlayerData)}. A
     * conflict completes the returned future EXCEPTIONALLY (with
     * {@link OptimisticConflictException}) after the winner is adopted.
     */
    CompletableFuture<Void> flushPlayer(PlayerData playerData){
        return flushEngine.flushPlayer(playerData);
    }

    /** Immediate flush of a SINGLE section in isolation - see {@link FlushEngine#flushSection(PDSection)}. */
    CompletableFuture<Void> flushSection(PDSection section){
        return flushEngine.flushSection(section);
    }

    /** Immediate flush of a SINGLE account row in isolation (see {@link FlushEngine}). */
    CompletableFuture<Void> flushAccountSection(AccountSection<?> section){
        return flushEngine.flushAccountSection(section);
    }

    /** Failed-write count since the last call; the periodic tick logs ONE aggregate line per tick. */
    public int drainWriteFailureCount(){
        return flushEngine.drainWriteFailureCount();
    }

    /**
     * The quit entry point for the platform listeners: detaches the live player, records the session
     * end (so a durable {@code lastSeen} survives an otherwise clean session), then FLUSHES that player
     * on a bounded async pool - the quit event is never blocked. On a storage outage the player is
     * enqueued (never dropped: dropping is the lost-write) and re-flushed on the next tick / when storage
     * returns. A {@code workingSet} section additionally evicts that player's cell after a short grace.
     */
    public static void handlePlayerQuit(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");
        PlayerController controller = INSTANCE;
        if (controller == null) return;
        PlayerData playerData = controller.baseManager().peek(uuid).orElse(null);
        if (playerData == null) return;
        controller.lifecycleEngine.handleQuit(playerData);
    }

    /** Drains the storage-down quit-flush backlog (see {@link LifecycleEngine#drainFlushRetryQueue()}). */
    public void drainFlushRetryQueue(){
        lifecycleEngine.drainFlushRetryQueue();
    }

    /** Releases expired cells from every ttl section manager; best-effort, never propagates. */
    void purgeTtlManagers(){
        lifecycleEngine.purgeTtlManagers();
    }

    /** Moves a PDSection's collection to another enabled backend at runtime (see {@link StorageTransferService}). */
    public CompletableFuture<TransferReport> transferPDSection(Class<? extends PDSection> pdSectionClass, String targetBackend){
        return transferService.transferPDSection(pdSectionClass, targetBackend);
    }

    /** Moves the base PlayerData collection to another enabled backend (see {@link StorageTransferService}). */
    public CompletableFuture<TransferReport> transferPlayerData(String targetBackend){
        return transferService.transferPlayerData(targetBackend);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Shutdown
    // -----------------------------------------------------------------------------------------------------------------------------//

    public static synchronized void shutdown(){
        PlayerController controller = INSTANCE;
        if (controller == null) return;
        controller.sweepEngine.shutdown();
        controller.closeCacheSync();
        controller.lifecycleEngine.stop();
        try {
            controller.flushAll().join();
        }catch (Throwable e){
            PDLog.severe("Failed to flush PlayerData on shutdown:");
            e.printStackTrace();
        }
        try {
            controller.registry.closeAll().join();
        }catch (Throwable e){
            PDLog.severe("Failed to close the storage backends on shutdown:");
            e.printStackTrace();
        }
        Accounts.clear();
        ServerCooldowns.clear();
        INSTANCE = null;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------------------//

    private static String formatDuration(long millis){
        try {
            return FCTimeFrame.of(millis).getFormattedDiscursive();
        }catch (Throwable localeNotLoaded){
            //pure JUnit runtime: the locale messages are not plugged in
            return millis + "ms";
        }
    }

    private static IllegalStateException notBootstrapped(){
        return new IllegalStateException("PlayerController is not bootstrapped yet! It becomes"
                + " available after EverNifeCore's onLoadPre (ConfigManager.initialize).");
    }

    /** The uniform "PDSection not registered" error (a plugin resolved a section it never registered). */
    static IllegalStateException notRegisteredPDSection(Class<?> pdSectionClass){
        return new IllegalStateException("PDSection [" + pdSectionClass.getName() + "] is not registered!"
                + " Call PlayerController.registerPDSectionCfg(...) on your plugin's enable.");
    }

    /** The uniform "AccountSection not registered" error. */
    static IllegalStateException notRegisteredAccountSection(Class<?> sectionClass){
        return new IllegalStateException("AccountSection [" + sectionClass.getName() + "] is not registered!"
                + " Call PlayerController.registerAccountSectionCfg(...) on your plugin's enable.");
    }

    public static <T> CompletableFuture<T> failedFuture(Throwable error){
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
