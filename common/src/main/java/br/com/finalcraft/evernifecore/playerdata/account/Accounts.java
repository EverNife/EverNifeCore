package br.com.finalcraft.evernifecore.playerdata.account;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.storage.BindingResolver;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The account/identity layer: maps a platform uuid to its canonical {@link Account} and stores every
 * account in its OWN collection on a shared backend (the account backend). A mutable instance behind
 * a static facade, mirroring {@link PlayerController}: an instance is bootstrapped once (from the
 * PlayerController bootstrap) and swapped in atomically.
 *
 * <p>A uuid that has never been linked resolves to a singleton account whose
 * {@code accountId == uuid}, so account scoping only changes behavior once identities are linked.
 * The first real link mints a brand-new random accountId (never a member's uuid - see
 * {@link #linkExternal(UUID, String, String, UUID)} for the validated escape hatch), persists the
 * canonical account row, and writes one alias row per identity so every member stays resolvable by
 * its own key. accountIds are OPAQUE: integrations must not derive meaning from them.
 *
 * <p>Identity only: every operation here writes exclusively to the account collection. The
 * account-wide DATA stored under a former key is absorbed into the canonical rows at each member's
 * next login, or via {@code /ecaccount migrate}.
 *
 * <p>The account collection lives on the network backend named in
 * {@code network.storage-backend-id}, and must be the SAME backend across every instance that shares
 * accounts. The layer always bootstraps: with no link, {@code ec_accounts} stays empty and every
 * identity is its own account.
 */
public final class Accounts {

    /** Fallback provider tag of a platform uuid identity, used before a platform is registered. */
    public static final String PLATFORM_PROVIDER = "platform";

    /** The account collection name on the network backend. */
    public static final String COLLECTION = "ec_accounts";

    /** Owner tag of this collection's registry claim. */
    private static final String CLAIM_OWNER = "EverNifeCore:Accounts";

    /** Alias chains longer than this abort resolution (defensive: only a corrupt cycle produces one). */
    private static final int MAX_ALIAS_HOPS = 5;

    /** Facade used before the layer is bootstrapped (and in tests): every uuid is its own singleton. */
    private static final Accounts UNBOOTSTRAPPED = new Accounts(null, null);

    private static volatile Accounts INSTANCE;

    private final String backendName;
    private final CachingManager<UUID, Account> manager; // null in the UNBOOTSTRAPPED facade

    private Accounts(String backendName, CachingManager<UUID, Account> manager) {
        this.backendName = backendName;
        this.manager = manager;
    }

    /**
     * True once the account/identity layer has bootstrapped - false before the storage boot and in
     * tests. Not an admin's answer to anything: there is no switch for the layer, and linking is an
     * operation ({@code /ecaccount link}), never a setting.
     */
    public static boolean isEnabled() {
        return INSTANCE != null;
    }

    /**
     * The account layer, or a no-op facade that resolves every uuid to its own singleton account -
     * NEVER null. Account scoping degrades to plain uuid keying when the layer is absent (disabled,
     * in tests, or before bootstrap), so the keying path can call this unconditionally; mutating
     * operations on that facade fail with a clear error. Use {@link #isEnabled()} to tell the two apart.
     */
    public static Accounts get() {
        Accounts instance = INSTANCE;
        return instance != null ? instance : UNBOOTSTRAPPED;
    }

    /**
     * Builds the account manager against storage.yml and installs it as the current instance. Called
     * by the PlayerController bootstrap after the registry is initialized. The account collection lives
     * on {@code network.storage-backend-id}; the manager is created in {@code globalRegistry} with an
     * unbounded, resident cache (an account is tiny and read on every account-scoped access).
     */
    public static Accounts bootstrap(ParsedStorageConfig parsed, StorageRegistry registry,
                                          RefRegistry globalRegistry) {
        String backendName = parsed.getNetworkBackendName();
        Storage storage = registry.get(backendName);
        EntityDescriptor<UUID, Account> descriptor = descriptor(parsed, backendName, globalRegistry);
        //claimed like every other collection on the backend: it is what stops a plugin from taking the
        //name, and what a network transfer enumerates to know this collection has to travel too
        if (!registry.claimCollection(backendName, COLLECTION, CLAIM_OWNER, descriptor)) {
            throw new StorageConfigException("The account registry wants collection '" + COLLECTION
                    + "' on the network backend '" + backendName + "', but it is already used by '"
                    + registry.getCollectionOwner(backendName, COLLECTION) + "'!");
        }
        CachingManager<UUID, Account> manager =
                globalRegistry.manager(descriptor, storage, CachePolicy.always());
        //warm every stored account/alias up-front: the sync keying fast-path (accountNow) is
        //cache-only, and the collection stays tiny (only explicitly linked identities persist rows),
        //so this keeps account-scoped keying correct even for offline players loaded at boot
        manager.preloadAll().join();

        Accounts fresh = new Accounts(backendName, manager);
        INSTANCE = fresh;
        return fresh;
    }

    /** Clears the current instance (on shutdown / a failed reload / a boot with the layer disabled). */
    public static void clear() {
        INSTANCE = null;
    }

    /**
     * The one descriptor of the account collection. The codec is ref-aware against the GLOBAL registry,
     * where the account manager itself lives: {@code Ref} resolution only walks upwards, so an account
     * can point at another framework-owned entity but never at a plugin's - which is correct, the
     * framework cannot depend on a plugin being installed.
     */
    private static EntityDescriptor<UUID, Account> descriptor(ParsedStorageConfig parsed, String backendName,
                                                              RefRegistry globalRegistry) {
        BackendDefinition backend = parsed.getBackend(backendName).orElseThrow(() ->
                new IllegalStateException("Network backend '" + backendName + "' is not declared/enabled!"));
        Codec<Account> codec = BindingResolver.defaultCodec(backend, Account.class, globalRegistry);
        return EntityDescriptor
                .builder(UUID.class, Account.class)
                .collection(COLLECTION)
                .keyExtractor(Account::getAccountId)
                .codec(codec)
                .build();
    }

    /**
     * Re-installs a previously captured instance. The PlayerController bootstrap publishes the fresh
     * layer while it boots (sections key through it during the load); when that boot FAILS its
     * registry is closed, so the surviving controller must get its own working layer back - leaving
     * the fresh one installed would route every account lookup through a closed storage.
     */
    public static void restore(Accounts previous) {
        INSTANCE = previous;
    }

    public String getBackendName() {
        return backendName;
    }

    /** The account cache/repository facade, or {@code null} on the unbootstrapped facade. */
    public CachingManager<UUID, Account> getManager() {
        return manager;
    }

    // ------------------------------------------------------------------
    // Identity helpers
    // ------------------------------------------------------------------

    /**
     * The provider tag of THIS platform's uuid identities ({@code "minecraft"}, {@code "hytale"}, ...),
     * taken from the registered platform; falls back to {@value #PLATFORM_PROVIDER} before one exists.
     */
    public static String platformProvider() {
        try {
            IPlatform platform = EverNifeCore.getPlatform();
            if (platform != null) {
                String providerId = platform.getPlatformProviderId();
                if (providerId != null && !providerId.isEmpty()) {
                    return providerId;
                }
            }
        } catch (Throwable platformNotRegistered) {
            //early boot: no platform registered yet - fall through to the generic tag
        }
        return PLATFORM_PROVIDER;
    }

    /**
     * True when {@code provider} tags a platform uuid identity. Platform tags are reserved:
     * {@link #linkExternal(UUID, String, String, UUID)} rejects them (platform uuids are joined
     * through {@link #link(UUID, UUID)} instead).
     */
    public static boolean isPlatformProvider(String provider) {
        return PLATFORM_PROVIDER.equals(provider)
                || "minecraft".equals(provider)
                || "hytale".equals(provider)
                || platformProvider().equals(provider);
    }

    /**
     * The deterministic storage key of an external identity's alias row. The account collection only
     * resolves by primary key, so each linked external identity gets an alias row under this derived
     * uuid - no database index, no scan.
     */
    public static UUID externalKey(String provider, String providerUid) {
        return UUID.nameUUIDFromBytes(("ext:" + provider + ":" + providerUid).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // Resolution (never null: an unlinked uuid resolves to its singleton account)
    // ------------------------------------------------------------------

    /**
     * The account of {@code platformUuid}, synchronously from cache - <b>never null</b>. An
     * unlinked/uncached uuid resolves to its singleton account ({@code accountId == uuid}), WITHOUT
     * seeding the cache (this is the sync keying fast-path used on every account-scoped access). Call
     * {@link #resolveOnLogin(UUID, String)} on the login path so the linked account is cached and this
     * returns it.
     */
    public Account accountNow(UUID platformUuid) {
        Objects.requireNonNull(platformUuid, "platformUuid cannot be null");
        if (manager != null) {
            Account current = manager.peek(platformUuid).orElse(null);
            int hops = 0;
            while (current != null && current.isAlias() && hops < MAX_ALIAS_HOPS) {
                UUID canonicalId = current.getAliasOf();
                Account canonical = manager.peek(canonicalId).orElse(null);
                if (canonical == null) {
                    //canonical not warm: a bare Account with the right id still keys sections correctly
                    return new Account(canonicalId);
                }
                current = canonical;
                hops++;
            }
            if (current != null && !current.isAlias()) {
                return current;
            }
        }
        return singletonOf(platformUuid);
    }

    /**
     * The account of {@code platformUuid}, resolved through the backend and cached. Completes with the
     * stored account (following alias rows to the canonical account) when linked, otherwise seeds
     * and returns the singleton account so a later {@link #accountNow(UUID)} is warm. Never null.
     */
    public CompletableFuture<Account> account(UUID platformUuid) {
        return resolveAndWarm(platformUuid, () -> singletonOf(platformUuid));
    }

    /**
     * Resolves + caches the account on the login path so {@link #accountNow(UUID)} is warm. Seeds the
     * singleton (with the current name) when the uuid has never been linked. The member's row is
     * re-read from the backend first: login is the reconciliation point where a link/unlink decided
     * on another instance (or while offline) must become visible.
     */
    public CompletableFuture<Account> resolveOnLogin(UUID platformUuid, String name) {
        Objects.requireNonNull(platformUuid, "platformUuid cannot be null");
        if (manager != null) {
            manager.invalidate(platformUuid);
        }
        return resolveAndWarm(platformUuid,
                () -> Account.singleton(platformUuid, platformProvider(), platformUuid.toString(), name));
    }

    /** Shared resolution: canonical lookup through the backend, seeding a singleton on a true miss. */
    private CompletableFuture<Account> resolveAndWarm(UUID platformUuid, Supplier<Account> singletonFactory) {
        Objects.requireNonNull(platformUuid, "platformUuid cannot be null");
        if (manager == null) {
            return CompletableFuture.completedFuture(singletonFactory.get());
        }
        return resolveCanonical(platformUuid).thenApply(resolved -> resolved.isPresent()
                ? resolved.get()
                : manager.seedIfAbsent(platformUuid, singletonFactory.get()));
    }

    /**
     * Resolves {@code key} to its canonical account through the backend, following alias rows: a
     * crash mid-merge can leave a 2-hop alias chain, so resolution tolerates chains and
     * opportunistically rewrites the entry alias back to a single hop when it finds one. Empty on a
     * true miss (the key was never linked).
     */
    public CompletableFuture<Optional<Account>> resolveCanonical(UUID key) {
        if (manager == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return followAlias(key, key, 0);
    }

    private CompletableFuture<Optional<Account>> followAlias(UUID entryKey, UUID key, int hops) {
        return manager.resolve(key).thenCompose(stored -> {
            Account account = stored.orElse(null);
            if (account == null) {
                if (hops == 0) {
                    return CompletableFuture.completedFuture(Optional.<Account>empty());
                }
                //dangling alias (canonical row unreadable): a bare account with the right id still
                //keys sections correctly
                return CompletableFuture.completedFuture(Optional.of(new Account(key)));
            }
            if (!account.isAlias()) {
                if (hops > 1) {
                    normalizeAliasChain(entryKey, account.getAccountId());
                }
                return CompletableFuture.completedFuture(Optional.of(account));
            }
            if (hops >= MAX_ALIAS_HOPS) {
                return CompletableFuture.completedFuture(Optional.<Account>empty());
            }
            return followAlias(entryKey, account.getAliasOf(), hops + 1);
        });
    }

    /** Best-effort rewrite of a multi-hop entry alias straight to the canonical account. */
    private void normalizeAliasChain(UUID entryKey, UUID canonicalId) {
        writeAlias(entryKey, canonicalId).exceptionally(bestEffort -> null);
    }

    /**
     * The account an external identity is linked into, or empty when that identity was never linked
     * (or the link was undone). Resolution goes through the backend: the derived-key alias row plus
     * the hop to the canonical account.
     */
    public CompletableFuture<Optional<Account>> findByExternal(String provider, String providerUid) {
        return resolveCanonical(externalKey(provider, providerUid));
    }

    /**
     * Whether {@code oldKey} is a FORMER key of the account now identified by {@code newKey}, from
     * {@code memberUuid}'s point of view - i.e. whether data rows stored under {@code oldKey} belong
     * to that account and may be absorbed into it. True for the member's own uuid (its pre-link
     * singleton key) and for any key aliased into the account (an absorbed explicit account, another
     * linked member). False otherwise - notably after an UNLINK, when the old stamped key is the
     * account the member just LEFT: those rows are the account's and must stay untouched.
     */
    public CompletableFuture<Boolean> isFormerKeyOf(UUID oldKey, UUID newKey, UUID memberUuid) {
        if (oldKey.equals(memberUuid)) {
            return CompletableFuture.completedFuture(true);
        }
        return resolveCanonical(oldKey).thenApply(resolved ->
                resolved.isPresent() && newKey.equals(resolved.get().getAccountId()));
    }

    private static Account singletonOf(UUID uuid) {
        return Account.singleton(uuid, platformProvider(), uuid.toString(), null);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException notAUuid) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Linking (identity only - data follows lazily at each member's next login)
    // ------------------------------------------------------------------

    /**
     * Links two platform identities into one account (the admin fallback behind
     * {@code /ecaccount link}): resolves both accounts and fuses them through
     * {@link #mergeAccounts(Account, Account)}. No data moves here.
     */
    public CompletableFuture<Account> link(UUID target, UUID source) {
        if (manager == null) {
            return PlayerController.failedFuture(disabled());
        }
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        //re-read both member rows: an admin operation must see links decided on other instances
        manager.invalidate(target);
        manager.invalidate(source);
        return account(target).thenCompose(targetAccount ->
                account(source).thenCompose(sourceAccount ->
                        mergeAccounts(targetAccount, sourceAccount)));
    }

    /**
     * Links an external identity {@code (provider, providerUid)} - a Discord id, a registration-site
     * user, ... - into the account of {@code playerUuid}. The caller is responsible for having
     * VERIFIED that the external identity belongs to that player (OTP, OAuth, panel); this only
     * executes the already-authorized link. See {@link #linkExternal(UUID, String, String, UUID)}.
     */
    public CompletableFuture<Account> linkExternal(UUID playerUuid, String provider, String providerUid) {
        return linkExternal(playerUuid, provider, providerUid, null);
    }

    /**
     * Links an external identity into the account of {@code playerUuid}, with an optional
     * {@code desiredAccountId} for integrations that manage their own id space (e.g. a registration
     * site that mints its users' uuids and wants the account born under them).
     *
     * <p>Outcomes: identity unknown - it joins the player's account (a never-linked player's explicit
     * account is born NOW, id minted by the core or the validated {@code desiredAccountId}); identity
     * already on the SAME account - idempotent no-op; identity on ANOTHER account - transitive fusion
     * of both accounts ({@link #mergeAccounts(Account, Account)}).</p>
     *
     * <p>{@code desiredAccountId} rules (any violation fails the link cleanly, nothing is written):
     * it only takes effect when this link CREATES the explicit account; when either side already
     * belongs to one, the id exists - passing the SAME id is a no-op, a DIFFERENT one is an error
     * (a live account is never re-keyed). On creation it must not exist in the account collection,
     * must not be the uuid of any member of the link, and must not collide with a stored PlayerData
     * uuid. Callers must mint random UUIDs in their OWN id space - never reuse a platform uuid.</p>
     */
    public CompletableFuture<Account> linkExternal(UUID playerUuid, String provider, String providerUid,
                                                   @Nullable UUID desiredAccountId) {
        if (manager == null) {
            return PlayerController.failedFuture(disabled());
        }
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        if (provider == null || provider.isEmpty() || providerUid == null || providerUid.isEmpty()) {
            return PlayerController.failedFuture(new IllegalArgumentException("provider and providerUid cannot be null/empty"));
        }
        if (provider.indexOf(':') >= 0) {
            return PlayerController.failedFuture(new IllegalArgumentException("provider cannot contain ':' - it would make"
                    + " the derived identity key ambiguous"));
        }
        if (isPlatformProvider(provider)) {
            return PlayerController.failedFuture(new IllegalArgumentException("provider '" + provider + "' is reserved for"
                    + " platform identities - join platform uuids through link(target, source)"));
        }
        //re-read the member row: the link must see the player's CURRENT account, not a stale cache
        manager.invalidate(playerUuid);
        return account(playerUuid).thenCompose(playerAccount ->
                findByExternal(provider, providerUid).thenCompose(linked -> {
                    if (!linked.isPresent()) {
                        return adoptExternalIdentity(playerUuid, playerAccount, provider, providerUid,
                                desiredAccountId);
                    }
                    Account externalAccount = linked.get();
                    if (externalAccount.getAccountId().equals(playerAccount.getAccountId())) {
                        //the same identity re-linked into the same account: idempotent
                        return checkDesiredMatches(desiredAccountId, externalAccount);
                    }
                    //the external identity already belongs to ANOTHER account: transitive fusion.
                    //The external's account is always explicit here, so a desired id can only
                    //restate the id that survives the merge.
                    if (desiredAccountId != null && !desiredAccountId.equals(externalAccount.getAccountId())) {
                        return PlayerController.failedFuture(desiredOnLiveAccount(desiredAccountId, externalAccount.getAccountId()));
                    }
                    return mergeAccounts(externalAccount, playerAccount);
                }));
    }

    /**
     * Unlinks an external identity: its derived alias row is deleted first, then the member leaves
     * the canonical row (a crash in between leaves the member listed but unresolvable, and a re-link
     * of the same identity heals the list). No data moves.
     *
     * @return {@code true} when the identity was linked and is now removed, {@code false} when it
     *         was not linked to begin with
     */
    public CompletableFuture<Boolean> unlinkExternal(String provider, String providerUid) {
        if (manager == null) {
            return PlayerController.failedFuture(disabled());
        }
        UUID key = externalKey(provider, providerUid);
        return resolveCanonical(key).thenCompose(linked -> {
            if (!linked.isPresent()) {
                return CompletableFuture.completedFuture(false);
            }
            Account account = linked.get();
            AccountMember member = account.findMember(provider, providerUid);
            return manager.deleteAndEvict(key).thenCompose(x -> {
                if (member == null) {
                    return CompletableFuture.completedFuture(true); //dangling alias: now healed
                }
                account.getMembers().remove(member);
                return manager.saveAndCache(account).thenApply(y -> true);
            });
        });
    }

    /**
     * Unlinks a platform member back to standing alone: its alias row is deleted and the member
     * leaves the account - which stays explicit and keeps ALL the account-wide data. The member
     * starts FRESH: at its next login the uuid resolves to its own singleton again and the account
     * stamp returns to the uuid (the login migration guard recognizes the left-behind account and
     * does not absorb its rows). Alias first, then the canonical row - a crash in between leaves the
     * member listed but already resolving standalone; a later re-link heals the list.
     */
    public CompletableFuture<Account> unlink(UUID memberUuid) {
        if (manager == null) {
            return PlayerController.failedFuture(disabled());
        }
        Objects.requireNonNull(memberUuid, "memberUuid cannot be null");
        //re-read the member row: an unlink decided against a stale cache would misreport "not linked"
        manager.invalidate(memberUuid);
        return resolveCanonical(memberUuid).thenCompose(linked -> {
            Account account = linked.orElse(null);
            if (account == null || account.getAccountId().equals(memberUuid)) {
                return PlayerController.failedFuture(new IllegalStateException("[" + memberUuid + "] is not linked into any account"));
            }
            AccountMember leaving = null;
            for (AccountMember candidate : account.getMembers()) {
                if (isPlatformProvider(candidate.getProvider())
                        && memberUuid.toString().equals(candidate.getProviderUid())) {
                    leaving = candidate;
                    break;
                }
            }
            final AccountMember member = leaving;
            return manager.deleteAndEvict(memberUuid).thenCompose(x -> {
                if (member != null) {
                    account.getMembers().remove(member);
                }
                return manager.saveAndCache(account);
            }).thenApply(x -> account);
        });
    }

    /**
     * Fuses two accounts into one canonical account, writing ONLY identity rows: the canonical row
     * first (member union), then the alias rows of every member and of an absorbed explicit account
     * (a crash in between leaves a resolvable 2-hop chain - see {@link #resolveCanonical(UUID)}).
     * Each member's account-wide DATA follows lazily at that member's next login.
     *
     * <p>Id of the fused account: when one side is already an explicit (stored) account its id
     * survives ({@code target} first when both are - the absorbed account's row becomes an alias);
     * a brand-new random id is minted only when two never-linked identities fuse.</p>
     */
    public CompletableFuture<Account> mergeAccounts(Account target, Account source) {
        if (manager == null) {
            return PlayerController.failedFuture(disabled());
        }
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        if (target.getAccountId().equals(source.getAccountId())) {
            return CompletableFuture.completedFuture(target);
        }
        Set<UUID> memberUuids = new HashSet<>();
        collectPlatformUuids(target, memberUuids);
        collectPlatformUuids(source, memberUuids);

        CompletableFuture<UUID> canonicalId;
        if (!target.isSingleton()) {
            canonicalId = CompletableFuture.completedFuture(target.getAccountId());
        } else if (!source.isSingleton()) {
            canonicalId = CompletableFuture.completedFuture(source.getAccountId());
        } else {
            canonicalId = mintAccountId(memberUuids);
        }

        return canonicalId.thenCompose(id -> {
            Account canonical = buildCanonical(id, target, source);
            fillMissingMemberNames(canonical);
            return manager.saveAndCache(canonical)
                    .thenCompose(x -> rewriteAliases(canonical, target, source))
                    .thenApply(x -> canonical);
        });
    }

    // ------------------------------------------------------------------
    // Linking internals
    // ------------------------------------------------------------------

    /** The external identity is unknown: it joins the player's account (born now if singleton). */
    private CompletableFuture<Account> adoptExternalIdentity(UUID playerUuid, Account playerAccount,
                                                             String provider, String providerUid,
                                                             @Nullable UUID desiredAccountId) {
        AccountMember external = new AccountMember(provider, providerUid, null);
        if (!playerAccount.isSingleton()) {
            //an already-linked player gains one more identity: the account id never changes
            return checkDesiredMatches(desiredAccountId, playerAccount).thenCompose(account -> {
                account.addMember(external); //an already-listed member means a half-done unlink: heal
                return manager.saveAndCache(account)
                        .thenCompose(x -> writeAlias(externalKey(provider, providerUid), account.getAccountId()))
                        .thenApply(x -> account);
            });
        }
        //the first real link of a never-linked player: the explicit account is born now
        Set<UUID> memberUuids = Collections.singleton(playerUuid);
        CompletableFuture<UUID> accountId = desiredAccountId != null
                ? validateDesiredAccountId(desiredAccountId, memberUuids)
                : mintAccountId(memberUuids);
        return accountId.thenCompose(id -> {
            Account account = new Account(id);
            for (AccountMember member : playerAccount.getMembers()) {
                account.addMember(member);
            }
            account.addMember(external);
            fillMissingMemberNames(account);
            return manager.saveAndCache(account)
                    .thenCompose(x -> writeAlias(playerUuid, id))
                    .thenCompose(x -> writeAlias(externalKey(provider, providerUid), id))
                    .thenApply(x -> account);
        });
    }

    /** Mints a random accountId, re-minting on the absurd collision (a member uuid or an existing row). */
    private CompletableFuture<UUID> mintAccountId(Set<UUID> memberUuids) {
        UUID candidate = UUID.randomUUID();
        if (memberUuids.contains(candidate)) {
            return mintAccountId(memberUuids);
        }
        return manager.repository().exists(candidate).thenCompose(taken ->
                taken ? mintAccountId(memberUuids) : CompletableFuture.completedFuture(candidate));
    }

    /** Enforces the creation-time rules of a caller-supplied accountId (nothing is written on failure). */
    private CompletableFuture<UUID> validateDesiredAccountId(UUID desired, Set<UUID> memberUuids) {
        if (memberUuids.contains(desired)) {
            return PlayerController.failedFuture(new IllegalArgumentException("desiredAccountId [" + desired + "] is the uuid"
                    + " of a member of this link - integrations must mint ids in their OWN id space,"
                    + " never reuse a platform uuid"));
        }
        return manager.repository().exists(desired).thenCompose(taken -> {
            if (taken) {
                return PlayerController.failedFuture(new IllegalArgumentException("desiredAccountId [" + desired + "] already"
                        + " exists in the account collection (as an account or an alias)"));
            }
            return PlayerController.getPlayerData(desired).thenCompose(playerData -> {
                if (playerData != null) {
                    return PlayerController.failedFuture(new IllegalArgumentException("desiredAccountId [" + desired + "]"
                            + " collides with a stored PlayerData uuid - integrations must mint ids"
                            + " in their OWN id space, never reuse a platform uuid"));
                }
                return CompletableFuture.completedFuture(desired);
            });
        });
    }

    private CompletableFuture<Account> checkDesiredMatches(@Nullable UUID desired, Account existing) {
        if (desired != null && !desired.equals(existing.getAccountId())) {
            return PlayerController.failedFuture(desiredOnLiveAccount(desired, existing.getAccountId()));
        }
        return CompletableFuture.completedFuture(existing);
    }

    private static IllegalArgumentException desiredOnLiveAccount(UUID desired, UUID existing) {
        return new IllegalArgumentException("desiredAccountId [" + desired + "] cannot be applied:"
                + " this link involves the live account [" + existing + "], and an existing account"
                + " is never re-keyed");
    }

    /** The canonical account of a merge: the surviving live instance (or a fresh one) holding the member union. */
    private static Account buildCanonical(UUID id, Account target, Account source) {
        Account canonical;
        if (id.equals(target.getAccountId())) {
            canonical = target;
        } else if (id.equals(source.getAccountId())) {
            canonical = source;
        } else {
            canonical = new Account(id);
        }
        //the union dedups identical identities, which also settles UNIQUE(provider, uid) cross-account
        if (canonical != target) {
            for (AccountMember member : target.getMembers()) {
                canonical.addMember(member);
            }
        }
        if (canonical != source) {
            for (AccountMember member : source.getMembers()) {
                canonical.addMember(member);
            }
        }
        return canonical;
    }

    /** Fills the display name of platform members that were seeded without one (best effort). */
    private static void fillMissingMemberNames(Account account) {
        for (AccountMember member : account.getMembers()) {
            if (member.getName() != null || !isPlatformProvider(member.getProvider())) {
                continue;
            }
            UUID memberUuid = parseUuid(member.getProviderUid());
            if (memberUuid != null) {
                member.setName(UUIDsController.getNameFromUUID(memberUuid));
            }
        }
    }

    /**
     * Writes the alias rows of a fused account: one per member key (platform uuid or derived external
     * key) plus the absorbed explicit account's old row, all pointing at the canonical id.
     */
    private CompletableFuture<Void> rewriteAliases(Account canonical, Account target, Account source) {
        List<UUID> aliasKeys = new ArrayList<>();
        for (AccountMember member : canonical.getMembers()) {
            UUID key = aliasKeyOf(member);
            if (key != null && !key.equals(canonical.getAccountId())) {
                aliasKeys.add(key);
            }
        }
        //an absorbed explicit account's own row becomes an alias: old references stay resolvable and
        //its data rows stay reachable for the lazy absorption at each member's next login
        if (!target.isSingleton() && !target.getAccountId().equals(canonical.getAccountId())) {
            aliasKeys.add(target.getAccountId());
        }
        if (!source.isSingleton() && !source.getAccountId().equals(canonical.getAccountId())) {
            aliasKeys.add(source.getAccountId());
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (UUID key : aliasKeys) {
            chain = chain.thenCompose(x -> writeAlias(key, canonical.getAccountId()));
        }
        return chain;
    }

    /** The alias-row key of a member: its uuid for platform identities, the derived key otherwise. */
    private static UUID aliasKeyOf(AccountMember member) {
        if (isPlatformProvider(member.getProvider())) {
            return parseUuid(member.getProviderUid());
        }
        return externalKey(member.getProvider(), member.getProviderUid());
    }

    /** Creates or redirects the alias row under {@code key}, preserving the row's optimistic lock. */
    private CompletableFuture<Void> writeAlias(UUID key, UUID canonicalId) {
        return manager.resolve(key).thenCompose(existing -> {
            Account row = existing.orElse(null);
            if (row == null) {
                return manager.saveAndCache(Account.alias(key, canonicalId));
            }
            if (row.isAlias() && canonicalId.equals(row.getAliasOf())) {
                return CompletableFuture.completedFuture(null); //already points there
            }
            row.redirectTo(canonicalId);
            return manager.saveAndCache(row);
        });
    }

    private static void collectPlatformUuids(Account account, Set<UUID> into) {
        for (AccountMember member : account.getMembers()) {
            if (isPlatformProvider(member.getProvider())) {
                UUID uuid = parseUuid(member.getProviderUid());
                if (uuid != null) {
                    into.add(uuid);
                }
            }
        }
    }

    private static IllegalStateException disabled() {
        return new IllegalStateException("The account layer is not bootstrapped on this instance -"
                + " it comes up with the PlayerController, so this ran either before the storage boot"
                + " finished or after it failed");
    }
}
