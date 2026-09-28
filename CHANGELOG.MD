# Changelog

All notable changes to this project. Follows [Semantic Versioning](https://semver.org/).

Versions are published as `evernifecore-minecraft`, `evernifecore-hytale`, `evernifecore-common`,
`evernifecore-common-relocated` and `evernifecore-common-tests` under the group
`br.com.finalcraft`.

> **About the `3.0.1-alpha.x` line.** `2.0.4.4` is the last version published to anyone. Everything
> after it is the 2.x -> 3.0 rework, and none of it was ever released: the alpha entries below are
> development milestones reconstructed from the history, not downloads. They carry a pre-release
> identifier because that is what they are - `3.0.1-alpha.1` through `3.0.1-alpha.X` all order below
> `3.0.1`, the version the whole rework converges on. There are no tags and no artifacts for any of
> them: the build has read `3.0.1` out of the version catalog the entire time.

## [3.0.1-alpha.49] - 2026-09-28

Reading an item stops losing what an older server keeps in the tag, and a single-page gui list can use
its arrow slots.

### Added

- **`ListComponent.spillIntoPageArrows(boolean)`.** While the whole list fits on one page counting the
  page-button slots, those slots hold entries: a 24-slot region with two arrows shows up to 26 entries
  on one screen, filling the region first and then previous, then next. From 27 on the list pages over
  the region alone and the arrows are drawn, so every page keeps the same size. Works for a materialized
  list and for a `PageSource` with `total(...)`.

### Changed

- **`ItemDataPartNBT` takes its engine in the constructor, and `getKeysOwnedElsewhere()` is gone.**
  `getPathsOwnedElsewhere()` answers the tag paths left out on that engine's runtime.

### Fixed

- **`nbt:` leaves out only what an active key already writes.** It used to strip `ench`, `Enchantments`,
  `HideFlags`, `CustomModelData` and `Damage` on every server, so on a server too old for `enchant:`
  (below 1.13) an enchanted item was read, and saved, without its enchants. The whole `display` compound
  was stripped too, which lost a leather armour's colour on every version: now only `Name` and `Lore`
  are left to `name:` and `lore:`. From 1.20.5 the tag is custom data only and nothing is stripped.
- **A read no longer reports the keys a server is too old for.** Every item read on 1.7.10 came back
  incomplete, naming `CustomModelData`, `hideflags`, `components` and `enchant`, which warned on every
  config save. Only a capability missing on a server new enough is a gap now.
- **`/iteminfo` keeps the copyable item-data block clean.** A real gap is shown on a line of its own
  instead of inside the block that is pasted into configs.

## [3.0.1-alpha.48] - 2026-09-24

The command framework learns to leave text alone and to clean up after itself: flags can be switched
off per command or per subcommand, read inside a variadic tail, and every global argument parser has an
owner that takes it back.

### Added

- **`@FinalCMD(flags = false)` and `@SubCMD(flags = false)`.** A command whose arguments are free text
  turns flag extraction off: every token reaches the method as a positional, the bare `--` included,
  and a word like `-zombie` is just a word. A method of such a command cannot declare an `@Arg.Flag`,
  and the refusal names the declaration that turned flags off. On a subcommand the switch covers its
  own window only; the words before its label are walked before the subcommand is known, so they follow
  the command's setting. A subcommand can only turn the scan off, never back on.
- **A declared flag readable inside the variadic tail.** `@Arg.Flag(insideTail = true)` makes a marker
  of that flag extracted even after the tail opened, and it leaves the tail: `/kit give Steve vip 7d -sb`
  reads `-sb` as the flag and hands `"7d"` to the tail. Any other marker-shaped word in the tail stays
  text, so the `--` escape is still only needed before the tail.
- **`ArgParserManager.addGlobalParser(owner, ...)` / `addGlobalContextualParser(owner, ...)`, with
  `unregisterGlobalParser` / `unregisterGlobalContextualParser`.** A global parser is now owned by a
  plugin. Taking it back restores the parser it had covered, so a plugin reload never leaves a type
  without one, and registering over another plugin's parser logs a warning naming both.

### Changed

- **`ArgParserManager.unregisterAll(ECPluginData)` releases the global parsers too.** The default
  `onECPluginShutdownPre` calls it together with the command teardown, so a plugin that is disabled or
  reloaded leaves no parser behind. The platform's builtin parsers are owned by the core itself, so a
  plugin's `unregisterAll` can never take them.

## [3.0.1-alpha.47] - 2026-09-24

A round of small additions the sibling plugins were asking for: ignore providers, a way to tell an
installed plugin from a running one, player queries beyond the loaded set, and a handful of
conveniences on `FPlayer`, config, the GUI list and the ontime provider.

### Added

- **`ECIgnores`, per-kind player ignore providers.** `IgnoreKind` names what is ignored (chat, private
  messages, teleport requests) and each kind can be answered by a different plugin through an
  `IIgnoreProvider`; a kind nobody answers ignores nobody. `isIgnoring` also honours a broader kind
  that covers the asked one - ignoring `CHAT` hides private messages too. Providers must be
  thread-safe, because chat asks once per recipient per line, and `removeProvider` only evicts the
  provider it is given, so a disabling plugin never removes its successor. Reached through
  `ECProviders.getIgnores()`.
- **`PlayerController.queryPlayers(Query)` and `getLoadMode()`.** `queryPlayers` matches the stored
  rows, so it reaches players `getAllLoaded()` does not hold when the load mode is `RECENT`. A loaded
  match comes back as the live instance; an unloaded one is a detached copy that is never cached and
  persists nothing - resolve it with `getPlayerData(UUID)` to edit it.
- **`IPlatform.isPluginEnabled(String)`.** `isPluginLoaded` now means installed, `isPluginEnabled`
  means installed and running; `ECListener`'s required plugins and the LuckPerms fallback ask the
  latter. The test engine gains `Platforms.pluginsDisabled(...)` for the installed-but-not-running case.
- **`FPlayer.hasPlayedBefore()`, on both platforms.** Bukkit answers from the player file, so during a
  first session it stays `false` until the player leaves; Hytale answers whether its universe player
  storage holds a file for the player, which the server writes on its own schedule, so it can turn
  `true` while the first session is still running.
- **`FCommandSender.sendRawMessage(String)`.** Shows text as written: section-sign codes apply and an
  `&` stays plain text, for a chat relay or a Discord bridge. `FCColorUtil.sectionCodedComponent`
  is the matching component builder.
- **`ConfigFactory.openAll(plugin, folder, recursive)` and `ConfigFactory.copyAsset(plugin, assetPath,
  target)`.** `openAll` opens every `.yml` / `.yaml` in a folder, in path order, and returns an empty
  list for a folder that does not exist yet. `copyAsset` seeds a default file out of the plugin's jar
  and never touches a target that already exists.
- **`ListComponent.every(long ticks)`.** A GUI list can redraw on a clock of its own while the screen is
  open, for entries that read a clock such as a countdown; the source is read again on each pass.
- **`OntimeManager.removeProvider(provider)`.** A provider takes itself back on shutdown and the core's
  own answer (zero for everyone) returns; a provider already replaced is left alone.
- **`HyFCScheduler.getMainWorldTps()`, `tpsOf(World)` and `ticksToMillis(ticks, tps)`.** Hytale ticks
  are scheduled on the world's own configured tick rate instead of a fixed 20, falling back to the
  server default of 30 when there is no world to ask.

### Changed

- **`AccountSection.merge` receives a `MergeReason`.** `IDENTITY_LINK` combines the rows of different
  identities, where a balance must add up; `WRITE_CONFLICT` reconciles two versions of the same row
  written by two servers, where adding would count the shared history twice and keeping the backend's
  version is a valid answer. A section whose combination is the same either way ignores it. The
  signature changed, so existing `merge` overrides must take the new parameter.
- **`IEconomyProvider` recommends main-thread writes instead of requiring them.** Threading is not
  enforced by the bridge; reading and writing from the main thread is recommended, and off it only
  reads are.
- **A stored location map that is empty reads as an unset location** instead of failing to decode.
- **The build moves to EveryDatabase 1.5.0**, where a `BigDecimal` read back from storage keeps its
  scale instead of coming back as a double.

## [3.0.1-alpha.46] - 2026-09-24

The Forge route gets an exit: every registration hands back a handle that undoes it.

### Added

- **`ForgeRegistration`, returned by every `ForgeListener.registerListener(...)`.** `unregister()` takes
  the listener off every bus that registration reached, so a plugin can undo its Forge listeners in its
  own shutdown instead of asking for a server restart. Only the first call does anything, and a hybrid
  that offers no way to take a listener off a bus fails with an `IllegalStateException` naming the bus
  and the member it looked for.

### Fixed

- **`unregister()` reaches every bus even when one of them fails**, instead of stopping at the first
  failure and leaving the rest registered.
- **The Mohist handler table is safe to change while an event is posting**, so a listener registered or
  removed mid-post no longer disturbs the dispatch in flight.

## [3.0.1-alpha.45] - 2026-09-24

The Bukkit half of the test engine is published, so a plugin's own Bukkit tests run on the same doubles
this repository uses.

### Added

- **`evernifecore-minecraft-tests`.** A plugin's Bukkit tests link against the relocated fat jar, so the
  kit brings the Jackson stack libby would download at runtime plus the `common-tests` engine rewritten
  onto the same relocated names. `spigot-api` is left out of its POM (the server API version is the
  consumer's choice) and the relocated Jackson modules stay discoverable through their service files.

## [3.0.1-alpha.44] - 2026-09-23

Forge and NeoForge detection made honest, and the dependency floor moved up.

### Changed

- **The build moves to EveryLibs 1.1.0 and `hytale-server` 0.6.8.**
- **The 1.7.10 default Forge registration also listens on FML's own bus.** 1.7.10 posts `TickEvent` and
  the FML `PlayerEvent` family on a bus of its own, so a registration on `MinecraftForge.EVENT_BUS`
  alone never heard them. From 1.8 FML hands out that same bus and it is registered once.

### Fixed

- **A Forge bus the server carries but cannot load is refused, not skipped in silence.** It used to read
  as absent, so the Arclight registration dropped every bus without a word.
- **NeoForge hybrids are recognised.** The loader detection stopped at the Forge 1.13+ `ModList`, so a
  NeoForge hybrid read as a plain Bukkit server and `isModded`, `isModLoaded` and `isFakePlayer` all
  answered `false`. Fake players are now recognised by reading the entity class directly, without
  needing NMSUtils.

## [3.0.1-alpha.43] - 2026-09-06

Accounts get friendlier to integrations and to commands, and the Hytale platform stops stubbing three
calls.

### Added

- **`AccountSection` as a command argument.** The token still names a player, but the row handed to the
  method is the one of that player's account, so two linked identities resolve to the same live
  instance. The value carries no player identity, so a command that also needs the name or the online
  state declares the player as a second parameter. The player lookup shared with `IPlayerData` moved
  into a common base.

### Changed

- **An account can be created under its anchor member's uuid.** `Accounts.linkExternal(...)` with an
  explicit account id may now pass the uuid of the player the account is being created for, which is how
  a network whose backend mints the platform uuids keeps `accountId == platform uuid` past the first
  link. That member is then the account's key and can never leave through `unlink`; every other member
  is an ordinary alias. The uuid of any other stored player is still refused.

### Fixed

- **Hytale `IPlatform` console and player command dispatch, and the third stub, are wired to the real
  API.** The code already existed in `FCHytaleUtil` and was never reached. The returned boolean reports
  dispatch only, since Hytale hands back no success signal.

## [3.0.1-alpha.42] - 2026-08-30

PlaceholderAPI presence is answered by the platform that owns it, the running bootstrap is published by
the core, and the Arclight Forge route stops claiming NeoForge servers.

### Added

- **`ECBootstrap`, the running bootstrap instance published by the core.** A plugin's common bootstrap
  declares `ECBootstrap<MyBootstrap> INSTANCE = ECBootstrap.of(MyBootstrap.class)` and reads it back
  through its own accessor. Publishing used to be the plugin's job across four lifecycle hooks, and
  missing the last one silently kept handing out an instance that had already shut down.

### Fixed

- **`PAPIIntegration.isPresent()` asks the platform.** It returned a hardcoded `false` while its
  siblings delegated, so every guarded registration was disabled on every server, with no error and no
  log, including the core's own Hytale loader.
- **The PlaceholderAPI probe names each platform's own class.** The Bukkit probe named the Hytale
  plugin's main class, which no Bukkit server loads, so the expansion never registered and `parse()`
  returned third-party tokens unresolved.
- **`InnerPAPIRegisterer.register` is only called when PlaceholderAPI is present**, on both platforms,
  so a server without it no longer risks a `NoClassDefFoundError`.
- **The Arclight Forge route is refused on a NeoForge runtime.** The Arclight brand spans eras that
  disagree on where the Forge types live; on a NeoForge Arclight the route used to win detection and
  die at first use with an unrelated `NoClassDefFoundError`. Now `isAvailable()` answers `false`,
  callers get the clean `IllegalStateException` a plain Bukkit server gives, and one warning names both
  classes.

## [3.0.1-alpha.41] - 2026-08-18

Account lifecycle becomes observable, and a Hytale player whose data failed to load is no longer let in.

### Added

- **`ECAccountLinkedEvent`, `ECAccountMergedEvent` and `ECAccountUnlinkedEvent`.** `Accounts` posts them
  on the bus as each operation completes. They are bus-only events (never mirrored into a platform bus)
  and are posted on the account pipeline's completion thread, so a handler must not assume the main
  thread. A link that attaches an identity without fusing a second account is `Linked`; two whole
  accounts fusing is `Merged`.
- **Audit stamps on accounts.** `Account` records `createdAt`, and a linked member records `linkedAt`
  and `linkedBy` (an `AccountActor`, the player, console or integration that performed the link).

### Changed

- **The build moves to `hytale-server` 0.5.9.**

### Fixed

- **The Hytale connect is denied when player data fails to load.** A failed login-time load used to
  admit a data-less session, where a fresh profile could overwrite the stored one. The setup connect
  event is now cancelled with a reason instead.

## [3.0.1-alpha.40] - 2026-08-16

The event bus becomes inspectable: a subscription is a value with options and a failure policy, lookups
no longer scan every handler, and `/ecore events` shows who listens to what.

### Added

- **`/ecore events [type]`.** Without a type it lists the subscribed event types with their counts and
  the active listener watches; with one it prints the bus subscriptions in delivery order and what
  every native audience reports, listening or silent, with the native listeners it can name. A type that
  only implements `IECEvent` is not put to the audiences, since no platform can ever see it. The type
  may also be one only a listener watch knows about, which is the case for an event nobody has
  subscribed to yet.
- **`ECSubscribeOptions` on functional subscriptions.** A subscription can skip cancelled events or
  hear only the exact class it named, the same options the annotation reads. The subscription handed
  back is a view an operator can list: type, options, subscriber and owner.
- **`ECEventExceptionHandler`.** A subscriber that throws goes to the bus's handler: the global bus
  keeps logging, a scoped bus may fail the test instead. Under `@ECoreTest` the global handler is
  swapped for a failing one for the length of the class, so a broken subscriber fails the test at the
  post that drove it.
- **`describeListeners` on the audience SPI.** Descriptive only; `hasListeners` keeps deciding
  presence. The Hytale and Forge audiences inherit the empty default.

### Changed

- **Bus subscriptions are indexed by the class they named.** The plan of a concrete event class is
  assembled from the lists of its ancestors, so registering a plugin's listeners no longer costs every
  other plugin's handlers per event class. Ties keep the global subscription order.
- **The Bukkit audience honours the `exact` option.**
- **A `with...` change that alters nothing hands back the same `ECSubscribeOptions` instance.**

## [3.0.1-alpha.39] - 2026-08-15

Bukkit events run through the bus, and a producer costs nothing while nobody listens.

### Added

- **`ECEventBus.hasListeners(Class)` and `postIfListened`.** The bus answers whether a post of exactly
  that class would reach anyone: local handlers by assignability, native audiences only for an
  `ECEvent` on a mirroring bus. `postIfListened` builds the event only on a yes. The audience SPI now
  takes the class instead of an instance, since the question is asked before the event exists.
- **`watchListeners`.** Follows a class's listener presence and runs one action when the first listener
  arrives and another when the last leaves, with the callbacks outside every lock. A native audience
  that can see its own registrations pushes changes through `refreshListenerWatches`; one that cannot
  is polled. `unsubscribeAll` drains the plugin's watches after its subscriptions.
- **`ECListener.registerWhileListened`.** Registers an on-demand producer on the first listener, bus or
  server, and unregisters it on the last.
- **`ECEvent.getHandlerListOf(Class)`.** Every EC event gets a `HandlerList` of its own on Bukkit, so
  the native gate answers per event type instead of for the whole family, and the common events
  declare the static `getHandlerList` that points at it. Every EC list reports its mutations to the
  bus.

### Changed

- **The chunk-change, craft and damage events extend `ECEvent`.** Bus subscribers and
  `@ECEventHandler` methods hear them, the thread guard covers them, and a Bukkit `@EventHandler`
  works unchanged. `PlayerMoveListener` compares chunk coordinates arithmetically and only looks
  chunks up when a listener exists.
- **The Forge audience reports no listeners while nothing can cross.** Answering the hybrid flag held
  every listener watch open, and built every gated event, on the hybrid servers this project runs on,
  for a bus no event reaches yet.

### Fixed

- **A producer whose registration blew up is retried.** The registered flag drops back on any exit that
  did not register, so a producer never stands as registered while nothing is.

## [3.0.1-alpha.38] - 2026-08-15

The Forge listener code moves into the core and resolves the Forge side by name, so a Bukkit jar builds
without any Forge artifact.

### Added

- **A forge audience.** `McForgeAudience` mirrors `ECEvent`s into the Forge bus next to the Bukkit
  one, registered in the Post enable hook.

### Changed

- **The Forge listeners live under the `minecraft` package.** This is a source-incompatible move:
  the previous package shipped in the deployed jar, so every plugin importing it needs its import
  rewritten. The CatServer adapter was dropped; nothing referenced it.
- **The Forge side is resolved by name, and its build-time jars are gone.** A compiled Forge type puts
  a descriptor in the bytecode, and the eras disagree on it (`MinecraftForge.EVENT_BUS` is a `cpw.mods`
  bus on 1.7.10 and an `IEventBus` from 1.16.5 on). The Mohist bridge registers programmatically instead
  of through an `@EventHandler` method. Building `:minecraft` needs `mohist-1.16.5-api.jar` in
  `PublicLibs/Bukkit`.
- **Detection is total.** A platform that only half-matches loses the Forge route and says so, where it
  used to fail the class initialization and turn every later call into a `NoClassDefFoundError`.

### Fixed

- **A by-name lookup that throws is refused, not only one that misses.** A hybrid's rewritten
  classloader raises a `LinkageError` from inside the lookup, and the raw error that escaped named a
  Bukkit class for a failure on the Forge side.

## [3.0.1-alpha.37] - 2026-08-14

Logging stops reading external text as a format string, and the test count learns its scope.

### Added

- **A `countTests` Gradle task.** Runs every module's test task and prints one line per task, the total
  with how many tasks it summed, and the tasks it left out, so a number never travels without its scope.

### Fixed

- **Externally built text is logged as a parameter, not concatenated into the format string.** A player
  name, a region name from config, a collection name or an exception message can contain `{}`, and when
  a `Throwable` followed, the placeholder consumed it and the stack trace never reached the line meant to
  explain the failure. Sites pasting only class names, method names, enum constants or numbers are
  unchanged; `ItemEngine.warn` keeps its single-`String` signature.

## [3.0.1-alpha.36] - 2026-08-14

The logging stack, rebuilt: a message format that cannot throw, one door for modular logging, and a
logger that answers before the server does.

### Added

- **`ECLogFormat`, `{}` messages that never throw.** Placeholders are filled left to right and every
  way the call could fail has a defined output instead of an exception: a placeholder with no
  argument stays literal, surplus arguments are appended in brackets, `\{}` escapes, an array renders
  its contents, and a `toString()` that throws becomes `<toString failed: FQCN>`. **A `Throwable` in
  the last position that no placeholder consumed has its stack trace appended, at any level** - which
  is why no `severe(String, Throwable)` overload is needed anywhere. The formatter is shared by the
  console logger and the file logger.
- **`ECLogLevel` on the log SPI.** `ILogAdapter` is now one method, `log(ECLogLevel, String)`:
  `common` names the four severities itself instead of imposing `java.util.logging` on every platform
  that implements the SPI, and mapping them onto the server's own logger is the adapter's whole job.
  Both platform adapters map `DEBUG` onto `INFO`, because those consoles filter everything below it.
- **`ECFallbackLog`, so `EverNifeCore.getLog()` always answers.** With no plugin data plugged in - a
  static initializer that beat the bootstrap, a plugin that reached the core first, a bare JUnit run -
  the call returns a logger publishing to the JUL logger named by `ECFallbackLog.LOGGER_NAME`, which
  is where an operator points a `logging.properties` handler. It needs no platform and no config.
- **Platform gating for debug modules.** `IDebugModule.isAvailable(IPlatform)` plus the new
  `PlatformId` constants: a module the running platform does not have is never seeded into
  `config.yml`, cannot log even when the file says its switch is on, and a key inherited from another
  platform is **removed from the file** by the next read - on its own, with nothing else pending.
- **`ECPluginData.loadDebugConfig()`, an explicit read of the `DebugMode` block.** The enable runs it
  before the first hook, so the whole enable already logs under the switches the file asks for, and
  the reload runs it up front, so the reload's own debug lines obey the edited file. It always
  re-reads and writes back only when it completed the file. Afterwards a query answers from memory and
  touches no disk; a query that somehow arrives first still triggers exactly one read however many
  threads race it.
- **A builder for `FCFileLogger`, and a roll on open.** `of(File)` / `of(File, String)` /
  `of(ECPluginData, String)` open a builder carrying `withTimestamps([pattern])` and
  `rollOnOpen([pattern])`; nothing touches the disk until `build()`. The roll archives the file
  already at the target the way a server treats `latest.log`, and never overwrites: a pattern that
  resolves onto an existing name is counted up, counter before the extension. Also `isOpen()` and
  `getFile()`.
- **A file logger opened from a plugin is closed by that plugin's shutdown**, after the last teardown
  hook - so a plugin still writes its closing lines during its own teardown, and one handle that
  refuses to close does not keep the others open. The two ownerless ports stay the caller's business.
- **An identity cache for plugin data.** `ECPluginManager` answers a known plugin object from an
  `IdentityHashMap` consulted before it validates or names anything, so a log call from a hot path no
  longer pays for a platform round trip. `IECPluginBootstrap.clearCachedPluginData()` is the
  bookkeeping that keeps the shortcut from outliving the registry entry.

### Changed

- **Log messages are `{}`, not printf.** `ECLogger` formatted with `String.format`, so a stray `%` in
  a message ended the call with `UnknownFormatConversionException` - a log line that became the
  incident. Nothing throws now, and a `%` is ordinary text.
- **Modular logging has one door.** The ten `*Module` methods of `ECLogger` are gone; a module logs
  through itself - `MyDebug.ARENA.debug(...)` / `.info(...)` / `.warning(...)` / `.severe(...)`.
  `ECLogger` and `IDebugModule` both lost their type parameter, so no call site carries `ECLogger<?>`
  any more. `debug` is the only verb the switch gates; the other three tag the line with the module
  name and always log.
- **`ECPluginManager.getECPluginsMap()` returns an immutable snapshot** (`Map`, was a live
  `HashMap`). The live map was a second exit from the registry, and removing a plugin through it
  would leave the identity cache answering for a plugin that no longer exists. Removal goes through
  `removePluginData(...)`.

### Removed

- **`ECLogger.severe(String, Throwable)`**, made unnecessary by the trailing-throwable rule. The call
  site is unchanged - it now resolves to the varargs verb - and the trace still lands next to the
  message it is about.
- **`info(Supplier)` / `warning(Supplier)` / `severe(Supplier)`**, on both `ECLogger` and
  `IDebugModule`. Nothing gates them, so the `get()` always ran: a supplier there bought laziness that
  did not exist. `debug(Supplier<String>)` stays, on both, because that one is gated.
- **`PDLog`**, and every private `Logger.getLogger("EverNifeCore")` behind it - thirteen files' worth
  of emergency loggers written because `getLog()` could throw. It cannot any more.
- **`FCFileLogger`'s public constructor and `getPs()`.** The `PrintStream` was a second way to write
  into a file the logger was already flushing per line; the constructor is the builder's now.
- **`ECPluginData.setDebugEnabled(Boolean)`.** The boxed form existed so `null` could mean "forget
  what you read"; that meaning is `loadDebugConfig()` now, and keeping both would have left
  `setDebugEnabled(null)` as a side door back into the unloaded state. The primitive stays as the
  hook for tests and tooling.

### Fixed

- **An escaped placeholder read the same way with and without arguments.** A fast path for the
  argument-less call skipped the scanner, and the scanner is what consumes an escape - so
  `format("a \{} b")` leaked its backslash into the log line while `format("a \{} b", "x")` did not.
  The shortcut now fires only where neither a placeholder nor an escape can exist.

## [3.0.1-alpha.35] - 2026-08-12

A section a query materialized answers for itself, and an account can list the identities it holds.

### Added

- **`PlayerController.getAccountMembers(accountId)`** - the player identities linked into one
  account, as a single indexed query over the base collection. An `AccountSection` could not list
  its own members: it holds one row for the account and knows nothing about the players sharing it,
  and `PlayerData.accountId` carried no index, so the answer meant scanning the whole base. The
  field is now `@Indexed` - on EveryDatabase 1.4.0 a `UUID` is indexable directly, so this needs no
  surrogate `String` field and no explicit `type`. A player already in the cache comes back as that
  live instance, so a caller never ends up holding a second copy of one player.

### Fixed

- **A `PDSection` read straight from the backend no longer fails on the members that need no player.**
  `querySection` returns decoded rows without loading their players - that is the point of it, and the
  test pinning the section cache at zero after the query says so - but every member delegating to the
  attached `PlayerData` threw `NullPointerException` on such a row, `getName()` included. `getName()`
  and `getPlayer()` now answer `null`, joining `getUniqueId()`, `getStorageKey()` and
  `isPlayerOnline()`, which already handled it. The members that genuinely read the PLAYER -
  `getFirstSeen`, `getLastSeen`, `getLastSaved`, `forceSavePlayerData` and the `getPDSection*` family -
  throw `IllegalStateException` naming what is missing and pointing at `getUniqueId()`, instead of a
  bare NPE. Returning `0` for the timestamps was rejected deliberately: "first seen in 1970" is a
  silent wrong answer an inactivity sweep would act on.

## [3.0.1-alpha.34] - 2026-08-11

Per-block data, rebuilt as a write-back store on the plugin's own backend.

### Added

- **`SVWorldDataManager<O>`, per-block data on any backend.** One persisted entity per
  `(world, chunk)`, and the instance living in the cache IS the state: a write mutates it under that
  chunk's monitor and a flush persists it later. `getBlock`/`peekBlock`/`getChunk`/`getRange`,
  `setBlock`/`removeBlock`/`computeBlock`, each also taking a `WorldBlockPos`. `getRange` is a cube
  and costs one backend call however wide the radius. The storage is a plugin-owned `ECStorage`, so
  the format on disk and the `Ref` graph are the ones the plugin's own config declared.
- **A cache preset an admin edits.** `policy` (`ALWAYS | TTL`), `ttl-seconds`, `max-chunks` and
  `preload` (`AUTO | ALWAYS | NEVER`), each default written into the section. `AUTO` reads the whole
  collection at boot exactly when the cache would end up holding it anyway - which is what a store
  built with no cache setting does. `NOCACHE` is refused with the reason: a changed block lives in
  the cache until the flush persists it.
- **A flush that cannot lose a write.** Every changed chunk goes out as one batched write of a
  snapshot taken under its monitor, a chunk left with no blocks has its entity deleted, and a failed
  write leaves its chunks dirty for the next pass. The `FlushReport` is returned and logged. It runs
  on a daemon thread of its own every ten minutes, jittered so servers sharing a backend do not flush
  in lockstep; `close()` stops it and persists what is left, bounded at 30 seconds.
- **A grid sentinel per collection.** The chunk size a collection was written with is recorded under
  its reserved `$meta` key, and a server whose grid disagrees is refused at build time - naming both
  sizes and the two ways out - instead of reading keys that name chunks of another size.
- **Ref participation, declared.** A store joins its plugin's `Ref` graph by default, so a `Ref` held
  elsewhere in the plugin resolves the live chunk; the second store of the same plugin is refused by
  name and points at `ISOLATED`, which collides with nothing and is invisible from outside.
- **Change listeners.** `onChange(...)` is called after each block change, outside every lock, and a
  replace fires once carrying both sides.
- **Coverage for the block store.** The write-back cycle against the in-memory and H2 backends, the
  cache preset and the preload, TTL against unsaved writes, the range cube's exact border, the `Ref`
  graph, the grid sentinel, concurrent writers against a failing backend, and the legacy import.

### Changed

- **`ECDebugModule.SVDATA_MANAGER` is now `SV_WORLD_DATA`.** The module name **is** the config key,
  so an existing `config.yml` keeps an orphaned `DebugMode.DebugModules.SVDATA_MANAGER` entry - not
  read, not deleted - and gains `SV_WORLD_DATA` seeded to `true` beside it. Delete the orphan by hand.
- **The legacy region-YAML importer reads a whole file per round trip.** One key-only lookup names
  every chunk of a region file the collection already has, where it used to ask once per chunk; what
  is missing goes out as one write. It still never deletes a YAML file and never lets a broken one
  abort the run.
- **A world name may not start with `$meta`.** That prefix opens the key space the store reserves for
  its own metadata, so the write is refused on the spot instead of being accepted and never persisted.

### Removed

- **The whole `worlddata` package.** `ServerData`, `WorldData`, `ChunkData`, `BlockMetaData`,
  `SVDataManager` and `WorldChunkDataBinding` are gone, with no shim - the in-memory tree they formed
  is the model that got replaced.

## [3.0.1-alpha.33] - 2026-08-10

The GUI framework, rebuilt in-house from title to teardown.

### Added

- **A GUI framework, in place of the deleted one.** `Gui` and its views, `Region`/`SlotSet`, icon
  states and cycles, list components with paging, storage regions that write through to an
  `ItemStore`, a navigation chain that carries a result back, and `askOnChat` for a free-text answer.
  Screens are declared once (`@GuiLayout`) and every window is painted for the viewer who opened it.
- **Layout files an admin owns.** A layout seeds its own YAML - type, rows, icons, slots, language
  blocks - and a per-language overlay restyles only what it names. `/ecoregui` (permission
  `evernifecore.command.gui`) diffs a class against its file and writes the missing keys.
- **`StoredInventory`, and a save format with a version on it.** A stored inventory carries its
  size, its per-slot ceilings and a schema version, so an old file is read forever and a file written
  by a newer version is refused with a message instead of read wrong.
- **A text-recipe item engine.** `ItemEngine` builds an item out of lines (`type:`, `amount:`,
  `name:`, `lore:`, `enchant:`, `nbt:`, `components:`, ...), each part declaring the runtime it needs;
  `ItemRuntime.probe()` measures the server once and a part the server cannot serve is refused by
  name instead of crashing where it is used.
- **`checkLinkage`.** A build gate that reads the compiled bytecode and asks every supported server's
  API jar whether each `org/bukkit` reference resolves there - including the class/interface flip,
  which no presence check can see. It guards both `check` and `shadowJar`, and every knowingly
  guarded reference is declared, with its site, in `tools/linkage-allowlist.txt`.
- **`minecraft:modules:modern`.** The artifact compiled against the post-flip shape of `Biome`;
  `BiomeAccessHolder` loads it or the legacy one by asking `Biome.class.isInterface()`.
- **Version table answers about releases.** `MCDetailedVersion.getReleaseFamily()`,
  `getLowestRelease()` and `getCraftBukkitClassNames(...)`, plus `MCVersion.v26` for the year-based
  scheme.
- **`McFCScheduler.repeatSyncInTicks(...)`**, and `scheduleSyncInTicks` now hands back the task.
- **Coverage for the command tree and the parse engine.** The tree, its captures, its registration
  errors, tab over the tree, `ParseResult`/`ParseOutcome`, the parser routing table, the contextual
  call grammar, the declarative flag matrix and the marker grammar, the tree help and its locale
  keys, the registry file and the access contract, the builtin commands against their own framework,
  and each platform's own parsers against the registry.
- **A page speaks the reader's language.** `setFormatHeader`/`setFormatLine`/`setFormatFooter` accept
  a `LocaleMessage`, and the header, the count and the navigation bar are resolved per recipient
  instead of in the plugin's default language.
- **Two named levels of placeholder.** `addRowPlaceholder` is computed once per entry and shared by
  everyone reading the page; the new `addViewerPlaceholder` is computed once per entry per reader, so
  a line can highlight the reader's own row or answer for their own state.
- **`PageSnapshot`, `CachePolicy` and `invalidate()`.** A page caches one ordered read of its source -
  the half nobody's language changes - with a policy declared where the cost is known: `ttl`,
  `none` or `manual`.
- **Three navigation strategies.** `PageNavigation.command("/finaljobs top %page%")` re-runs the
  caller's own line, `registered("plugin:name")` navigates by name through the new `/ecpage`, and
  `session()` keeps a ten-minute handle that only its own reader may use.
- **`PageTheme`, and an empty state.** The chrome is a theme (`classic()`, `autoFit()`, `none()`)
  instead of three booleans, and a page with no entries says so instead of printing buttons pointing
  at page 1 of 0.
- **The test engine refuses like a server.** The platform doubles refuse what a real server refuses,
  conserve items through drags and let a test player go offline, and the pieces needed to run the
  apparatus without a server are published.
- **Coverage for the gui framework.** Geometry and the slot codec, the click path from surface guard
  to dispatch, open refusal and teardown, render diff and layers, layout seeding and reload,
  per-viewer language, storage regions and the editable region, navigation and the chat prompt.

### Changed

- **The version table is ordered by release, not by a folded number.** Comparison walks digit group
  by digit group, which is what makes `26.2` land above `1.21.11`; constants declare the lowest
  release they cover instead of an integer; a server whose reported version string does not read as a
  release now fails by name instead of being guessed as the newest known one.
- **`IPlatform.runOnMainThread` means what it says.** It runs inline when the caller already is the
  main thread and schedules otherwise; the always-wait-for-the-next-tick form - which enable-time
  bootstrap depends on - is now `runOnMainThreadNextTick`.
- **The core compiles and tests against `spigot-api` 26.2**, with NBT-API on 2.16.0. The supported
  floor is unchanged (1.7.10): `checkLinkage` is what proves it, era by era.
- **Bean Validation rides into the jar unrelocated and pinned.** `jakarta.validation-api` is declared
  in the version catalog, because it is the contract a plugin's own validation rules compile against.
- **`FCSoundUtil.pickByEra` takes an `MCDetailedVersion`** instead of an era number.
- **`PageViewer` takes one type parameter and a step builder that asks for the ceiling.**
  `PageViewer.of(Class).source(...)` then `maxEntries(...)` or `unlimitedEntries()`; there is no
  default ceiling to be surprised by. Ordering is declared with `orderBy(...).ascending()` /
  `.descending()` and is the order that was asked for - the fixed `Collections.reverse` and the
  comparator nobody asked for are gone. Without `orderBy`, entries come out as the source returned
  them.
- **`showAll` shows all of it.** `maxEntries` caps the read and `pageSize` slices the message; what
  the ceiling left out is counted and announced in the hover of the total, costing no line of chat.

### Fixed

- **An action bar lasts as long as it was asked to.**
- **The dependency loader survives a class loader libraries cannot be injected into.**

### Removed

- **The old GUI stack, and triumph-gui with it.** `FCGuiFactory`, `PlayerGui`, `GuiComplex`,
  `PaginatedGuiComplex`, `GuiItemComplex`, `ComplexGuiBuilder`, `PaginatedComplexGuiBuilder`,
  `LayoutManager`, `FCLayoutScanner`, `LayoutIcon`/`LayoutIconBuilder`/`LayoutIconData`,
  `LayoutBaseData`, `IHasLayout` and `ConfigParseEngine` are gone. The shadow jar no longer carries a
  single relocated triumph-gui class.
- **`MCDetailedVersion.getValue()`, `getShortValue()` and `getShortVersion()`**, along with the
  constants `v1_11_R2`, `v1_12_R2`, `v1_14_R2` and `v1_15_R2` - revisions that never named a distinct
  release.
- **`/ecoredictinfo menu`.** The subcommand is gone; a server that ran an earlier version still has
  the old click persisted in `localization/lang_<LANG>.yml` and should delete that entry.
- **`Settings.PageViewers` is gone from `config.yml`** - both `REFRESH_TIME` (now a `CachePolicy`
  declared on the page) and `FULL_LOCALIZATION` (the localized chrome is the only path now).
- **The per-send dynamic command links.** A page no longer schedules three `DynamicCommand` entries
  per send, each retaining the viewer for thirty minutes.

## [3.0.1-alpha.32] - 2026-07-31

The parse engine: one way to answer a parse, one way to call a parser.

### Added

- **`ParseResult`, routed by `ParseEngine`.** Every parse answers a result object instead of a value
  plus a convention about `null`. All builtin parsers route through the engine.
- **A resolution phase for contextual parsers**, which now share the plain parsers' call shape. A
  parser is resolved by its own type before assignability, so a subtype no longer captures a
  supertype's slot.
- **A contextual `ItemStack` can name the equipment slot it wants.**

### Changed

- The builtin commands were adapted to the tree and the parse engine.

### Fixed

- A bad config value is reported to the log, not to the console chat.
- Each Hytale inventory section restores into its own container.

## [3.0.1-alpha.31] - 2026-07-30

The command framework becomes a tree.

### Added

- **A command node tree with a recursive registration scanner**, walked and dispatched in five
  ordered phases.
- **A variadic tail on `@Arg`**, which now folds every argument source into one annotation.
- **One flag namespace for the whole command path**, with help lines rendered per path instead of
  mutated in place, and permission and validation evaluated over the whole path.
- Tab answers from the tree, and only where the tree can answer.

### Changed

- **The command registry file is opt-in and per plugin.**

## [3.0.1-alpha.30] - 2026-07-29

Storage stops being described by backend type and starts being described by role.

### Added

- **`/ecstorage` can move the whole network family to another backend in one command**, and shows
  where a key goes instead of listing keys flat.
- A plugin can reach the shared network backend.
- The network cooldowns get an entry the admin can see and edit.

### Changed

- **Backends are seeded by role, not by type**, and the network backend lives under a block that
  says network.
- Two file backends that would share a directory are refused; a bind that merely risks a shared
  write warns instead of aborting.
- EveryDatabase 1.2.0, with the file change feed off by default.

## [3.0.1-alpha.29] - 2026-07-28

Section caching gets one descriptor and an idle policy.

### Added

- **A section lifecycle descriptor replaces the cache knobs**, and registering a section now requires
  an explicit, stable id. A repeated registration is treated as a reload.
- **A configurable idle grace**, defaulting to one hour, applied to sections and to account rows
  alike; idle account rows are swept, not just player sections. The advertised floor is a real
  number rather than zero.
- The admin can override a section's lifecycle, and `AccountSections` gets a visible, editable entry.
- A slow login explains itself instead of just being slow.

### Changed

- **`ONLINE` is the default section lifecycle.**
- Unregistered-section and `NOCACHE` failures are loud; a write on a released section instance is
  reported.
- A plugin `ECStorage` can no longer silently outlive a reload.
- EveryDatabase 1.1.1 (single-flight loads); ECForgeListener 1.0.7, rebuilt against this API.
- The server version is detected from the release the server reports.

## [3.0.1-alpha.28] - 2026-07-27

The test apparatus becomes a module, and two long-standing platform lies are corrected.

### Added

- **`common-tests`**, this repository's test engine and a published library: platform doubles, an
  installable test world, the storage and PlayerData lifecycle fixtures, the command harness, the
  senders and the fake plugin. Providers can be unregistered so a suite can rebuild the platform.
  The whole suite was migrated onto it and the duplicated fixtures deleted.
- **One economy provider contract**, checked against every implementation.

### Changed

- **Chat text is measured against the chat font, not the map font.** `MinecraftFont` is the map
  font: it disagrees on eight ASCII characters and most of Latin-1, and has no glyph above U+0191.
- The platform is detected by a real Bukkit classpath entry, and `/svinfo` prints what it detected.

### Removed

- **ChatMenuAPI.** The chat expectation listener is now this project's own.

## [3.0.1-alpha.27] - 2026-07-27

One placeholder engine for messages, and the FancyText vocabulary settles.

### Added

- **A message owns its placeholder provider**, with keys taken literally, over a `RegexReplacer`
  on `${key}`. The framework's own messages moved onto it, including `${label}` and `${subcmd}`.
- A guarded message can state its condition instead of wrapping it.
- A page's own declaration beats the automatic key, and page lines are resolved with the project's
  placeholder engine and published only once the list is complete.

### Changed

- **The FancyText decoration surface is `setX`**, `SendCustom` speaks the FancyText vocabulary, and
  the chat adapter owns the one broadcast audience.
- `render` returns the trailing colour instead of storing it; a locale with no language at all
  renders its key.
- The README documents the current FancyText and locale APIs; server owners are warned that an
  unreachable storage backend stops the boot.

## [3.0.1-alpha.26] - 2026-07-25

### Added

- **`${key}` placeholders resolved lazily through a single engine.**
- **Opt-in per-player locale resolution**, with `/eclocale self` to choose a language, if enabled on config.yml
- FancyText convenience: `appendLine`, `send(List)`, `toLegacyString`, `toPlainText`.

### Changed

- **The command context is scoped to the thread that is executing it.**
- The chain has one append semantics: always splice, always copy. List-replace helpers return a fresh
  list instead of mutating.
- Locale key collisions are reported across the whole plugin, not per class.

### Fixed

- The config codec keeps click and item hover payloads literal, and decides the hover branch by the
  hover value rather than by its string form.
- Each page line is sent to one recipient instead of the whole array.

## [3.0.1-alpha.25] - 2026-07-23

### Added

- **A runtime registry makes FancyText hover pluggable**, and hover is persisted through it.
- `ZH_CN` in `LocaleType`.

### Changed

- **`FancyText` is an interface with `FancySegment` as its leaf.**
- Click and hover types are written explicitly in the codec, and the click/hover vocabulary is
  unified across FancyText and `FCLocale` on both platforms.
- **The server stops when an enabled storage backend is unreachable.**

## [3.0.1-alpha.24] - 2026-07-23

The command surface becomes declarative.

### Added

- **`--name value` flags.** The tokenizer was reworked to the industry-standard syntax and the
  `@FlagArg` annotation finalized, with declarative extraction wired through `CMDMethodInterpreter`.
  Declared flags surface on help hover and tab-complete.
- **`def()` on `@Arg`** for declarative defaults.
- Registered commands are tracked on `ECPluginData`, with unregister APIs.

### Changed

- **`desc()` is gone from the command annotations in favour of `locales()`**, with a per-instance
  `LocaleMessage` override replacing `CMDData.desc`.
- Storage commands unified into `/ecstorage status|transfer`; `CMDAccount` renamed `CMDECAccount`;
  the `CMDECCooldown` set matrix collapsed into a `--network` flag.

### Fixed

- Varargs concat bugs in `FCArrayUtil`, plus ordering and array bugs the new registry tests exposed.

## [3.0.1-alpha.23] - 2026-07-23

### Added

- **A headless test harness for the FinalCMD command system**, and the matrix suites over it:
  registration, dispatch, `@Arg` parsing for the builtin parsers, usage/help/hover, tab-complete,
  the manual `MultiArgumentos` flag facility, and `ICustomFinalCMD`/`CustomizeContext`.

### Fixed

- Two headless locale gaps the dispatch tests exposed.

## [3.0.1-alpha.22] - 2026-07-22

### Added

- **`IECPluginBootstrap`**, the plugin lifecycle contract, with platform bridges, Pre/Post hooks and
  teardown-first cleanup. EverNifeCore's own mains run through it.
- **`runOnMainThread`**, a deferred-run primitive for both `Runnable` and `Supplier`.
- `IPlayerData.getNetworkCooldown`.

### Changed

- Cooldowns are organised into `player/` and `server/` subpackages, with builtin registration
  simplified and guarded against reload rebinding.
- `PlayerController`'s bootstrap is now `initialize`.

## [3.0.1-alpha.21] - 2026-07-20

The hardening pass reaches the platforms, and plugin-owned storage becomes a handle.

### Added

- **`ECStorage` absorbs `OwnedBackend` as an async per-plugin storage handle**, which reconnects
  after a core storage reload. A `Ref` inside a `PDSection` binds to the plugin's registry.
- An opt-in NBT-aware item similarity check, leaving the cheap default alone.
- LuckPerms meta is persisted and overridable; offline permissions resolve through LuckPerms.

### Fixed

- **Minecraft:** action bars are sent through Adventure with a public-API fallback, dropping the
  versioned `CraftPlayer` reflection; the action-bar and title queues are guarded against the async
  tick and cleared on quit; captured armor is cloned; `ComparableItemComplex` NBT is serialized so a
  match survives a reload; item damage is compared by value; per-era sound keys are modelled and the
  anvil-break key fixed; chunk changes are detected by coordinate and world instead of reference;
  paginated page numbers are clamped.
- **Framework:** silent losses in command method registration; argument parsers hardened against
  edge inputs; the dynamic-command registry bounded with a self-expiring map; `ECListener` is
  registered only when its required plugins are present and stays registered if `onRegister` fails;
  the Bukkit platform returns the real command-dispatch result; `IECEvent` reaches
  `@ECEventHandler` methods on every platform; update-check streams are closed.
- **Hytale:** teleport rotation, listener unregister key, placeholder parse null-guard, Vault
  economy assignment, `Vector2d` deserialized as double, the compact `Location` format validated,
  the chunk index widened to long, and opening the SQL backend fails fast with a clear message.
- EveryDatabase 1.1.0 (map order preserved, cache-sync scoped by store).

### Removed

- checkerframework, the dead `MinecraftArgumento` stub, the ignored flag parameter on
  `MultiArgumentos`, and the dead compound permission on `FinalCMDPluginCommand`.

## [3.0.1-alpha.20] - 2026-07-19

A hardening pass over the common module: about twenty correctness fixes, mostly in the areas the
rework had just moved.

### Fixed

- **FancyText and locale:** `FancyFormatter` cloning and empty-list access hardened and the
  main-thread block on send avoided; link and compound replacers applied per concatenated locale
  segment; empty locale maps guarded; backslashes escaped in placeholder replacements; zero-width
  fill strings rejected; the Adventure bridge closed on disable; cached page lines pinned during
  send.
- **PlayerData:** the freeze flag is re-read under the section lock before a flushed cell is
  cleared; the section cache migrates from the section manager so evicted-base cells transfer; an
  orphan section whose base is still cache-resident is kept; a disabled plugin's registry entries
  are released on unregister; `CacheableSupplier` is safe to read from several threads; a linked
  member's stranded former-key account row is deleted.
- **Storage:** the `openBackend` init failure is unwrapped into `StorageConfigException`; only a
  freshly claimed target collection is released on a failed transfer; `SVDataManager` pending writes
  are guarded by a stable monitor and an ordered concurrent set.
- A server-network cooldown is born persistent; unknown names are echoed in UUID normalization;
  config type adapters are deduped by class.
- The legacy migration logs, the storage backend docs and several stale javadocs were made honest.

## [3.0.1-alpha.19] - 2026-07-18

### Added

- **Third-party plugins can open their own inline backend** through a parameterized template;
  `openBackend` returns a handle carrying its backend definition.

### Changed

- `storage.yml`'s cache-sync block was redesigned and its backend references renamed.
- A finished legacy migration is consolidated into a single archive folder.

## [3.0.1-alpha.18] - 2026-07-17

### Added

- **Cooldowns remade as a server/player x local/network matrix**, with `/cooldown` to view and set
  across the whole reach matrix.
- **`ConfigFactory`'s type authority is bridged into the storage codecs**, so payload-schema
  migrations can be expressed over a type-aware `ConfigSection`.

## [3.0.1-alpha.17] - 2026-07-16

### Added

- The legacy migration tracks its progress in a metadata file, archives a legacy file only once all
  of it migrated, and narrates itself in the console and in a report.
- **A relocated compile view of the common module** is published, so downstream plugins compile
  against the classes the deployed jar actually carries.

### Fixed

- **FancyText on legacy clients:** sends are routed by chat capability, restoring hover and click on
  1.7.10; `&#RRGGBB` hex colours are parsed in `colorfyComponent`; a colour-name leak and unsplit
  newlines no longer garble the output; click events work again on pre-1.21.5 clients.
- `/fclocale list` no longer duplicates the active locale button.
- All kyori modules are included in the shadow jar.

## [3.0.1-alpha.16] - 2026-07-15

### Changed

- **Every version lives in the version catalog**, including the project version, and the build reads
  it from there.
- **Every module moved to the Java 25 toolchain**, with the bytecode caps declared at the root.
- **The CraftBukkit jars and the dead resolution overrides were dropped.** What remained of
  CraftBukkit is reflection strings resolved against the real server at runtime.
- EveryDatabase 1.0.9.

## [3.0.1-alpha.15] - 2026-07-13

### Added

- **`IECPlugin`**, and `ECTimeFormat` split out of `FCTimeUtil`.
- **Per-section schema migrations** with an eager sweep.
- A startup NBT-API self-test on the Crucible-fixed fork.

### Changed

- **[EveryLibs](https://github.com/EverNife/EveryLibs) is adopted**, dropping the duplicated reflection, util and executor classes.
- `jackson-dataformat-toml` is fetched through libby, and so is `commons-lang3` on Paper 1.16.5 when
  the server does not provide it.
- ECNmsUtils 1.1.5.

## [3.0.1-alpha.14] - 2026-07-06

The configuration engine is replaced.

### Added

- **`ConfigFactory`**, with the common built-in types registered centrally and the platform types
  registered by `IPlatform`. `FancyText`, `NumberWrapper`, `FCTimeFrame` and the item, inventory and
  position types describe themselves in config.

### Changed

- Every common and Minecraft call site was migrated to the [EveryConfig](https://github.com/evernife/EveryConfig) API, and `SVDataManager`
  world data now persists on the storage backend.

### Removed

- **The legacy Simple-YAML config engine.** EveryConfig is bundled in the shadow jar and Simple-YAML
  is no longer fetched at runtime.

## [3.0.1-alpha.13] - 2026-07-02

### Added

- **Runtime storage transfer**: write freeze, verified copy and re-bind, without stopping the server.

## [3.0.1-alpha.12] - 2026-07-01

PlayerData is rebuilt on a real database.

### Added

- **The EveryDatabase storage layer**: `ECStorage`, the registry and the backend types, configured
  through `storage.yml` (backend definitions, defaults and per-section admin config).
- **PlayerData and `PDSection` persist on EveryDatabase**, with per-plugin bindings, per-section
  cache policies, flush-on-quit and async login resolution.
- **Concurrent PlayerData writes resolve through a single visible first-wins `ADOPT_WINNER`
  pipeline.**
- **The account layer**: linked members, account-scoped sections and `/account`.
- **Integrity operations**: delete cascade, a paged orphan reaper and presence queries.
- **Legacy PlayerData YAML files are imported on first boot.**
- EveryDatabase's runtime dependencies are downloaded through libby; `docker-compose.yml` starts the
  network backends for local testing.

## [3.0.1-alpha.11] - 2026-06-21

### Changed

- **Gradle 9, and the whole project compiles on Java 25.**
- [EveryDatabase](https://github.com/EverNife/EveryDatabase) is added as a dependency, and the code prepared for the new PlayerData system.
- The vector API was updated from Hytale.

## [3.0.1-alpha.10] - 2026-05-27

### Added

- `INMSUtils` is back, and a prototype of `FCMinecraftAdventureUtil`.

## [3.0.1-alpha.9] - 2026-05-12

The platform seam gets its adapters.

### Added

- **`FCPlatformType`, `ArgumentoAdapter`, `FPlayerAdapter` and the platform vec adapters**, plus
  `IECBaseListener` in `api-contracts` so a platform listener is easier to extend.
- `ColorEnum` and an improved `FCColorUtil`.

### Changed

- **FancyText lives in common**, saved and loaded there; the Minecraft copy was removed.
- `registerConfigLoadableSalvables` and `registerArgParsers` moved to the platform.
- The WorldGuard default flags are no longer overridden by inheritance; reflection does it.
- `HyEverNifeCore` is now `EverNifeCoreHytalePlugin`.

## [3.0.1-alpha.8] - 2026-05-07

The great migration: about thirty-five subsystems left the Minecraft module for common.

### Changed

- **Moved to common and made platform-agnostic:** the locale system, config and PlayerData,
  `ECPlugin` and its events, listeners and `ECListener`, `ECLogger`, the console filter, the
  dependency manager, `FCScheduler`, `SimpleThread`, the cooldowns, `DynamicCommand`, `PageViewer`,
  placeholders and regex replacers, `ActionBarAPI`, `Argumento` and FinalCMD, `WorldData`,
  `CacheableSupplier`, `FCReflection`, `FCTimeFrame` and `DayOfToday`, ontime, the
  PlaceholderAPI and LuckPerms integrations, and `SaveConfigThread`.
- **A chat adapter replaces common's `FCTextUtil`**; `McEverNifeCore` implements the Minecraft side.
- `ProtectionHandler`, the integrations and the item/`ItemDataPart` layer were refactored to fit.

## [3.0.1-alpha.7] - 2026-05-02

### Added

- **The position system in common:** `BlockPos`, `ChunkPos`, `LocPos`, `RegionPos` and `FCVectors`,
  with the `MCGameVecAdapter` contract and `getMinecraftAdapter` bridging to the platform types.
  `CuboidSelection` gained a min/max height and a better serialization.

### Removed

- The old `FCPosUtil`, the duplicated position system inside `minecraft.modules.VersionedPlugins`
  (which now compiles against common), the Hibernate factories and database, and the FeatherBoard
  integration.

## [3.0.1-alpha.6] - 2026-04-22

### Added

- `FCommandSender.asPlayer()`, `Argumento.asMinecraftArg()`, `IPlatform.sendActionBarMessage()`, and
  an api-contract for `MinecraftArgumento`.

### Changed

- `getPluginData` is now `getMetaInfo`; providers are reached directly through `getProviders()`;
  nullability annotations are jakarta's.

## [3.0.1-alpha.5] - 2026-04-07

### Changed

- **NMSUtils left the project** for `br.com.finalcraft:ECNmsUtils`.
- Enchantments are serialized in `ItemDataPart` on Minecraft 1.21.1.

## [3.0.1-alpha.4] - 2026-04-04

### Changed

- **The bulk of the shared code moved into common:** `FCEcoUtil` and the economy manager,
  `VirtualThreadedScheduledExecutor` (no longer tied to log4j), the cache, `FCJavaVersion`, the time
  feature, the reflection helpers, the config layer, cooldowns, `PageViewer` and `FCRegexReplacers`,
  the vectors and `WorldData`, with `FCScheduler` split into its common and platform-specific parts.
- The Hytale module was overhauled in one move rather than split incrementally.

## [3.0.1-alpha.3] - 2026-04-03

The rework proper begins.

### Added

- **`api-contracts`**, the compile-time stub module: common references a class that only a platform
  can implement, and each platform ships the real class under the same fully-qualified name.
- **A platform-independent `FinalCMDManager`** and the contextual-argument concept, with the
  `ArgParsers` and several commands moved to common and Hytale-specific arguments added.
- **A Hytale loader.**

### Changed

- libby became a separate subproject.

## [3.0.1-alpha.2] - 2026-03-22

### Changed

- **`EconomyManager` was removed and the Vault integration re-implemented** on VaultUnlocked
  (Vault v2), with `double` overloads next to the `BigDecimal` ones to avoid pointless wrapping.
- Command unregistration calls `unregister` on modern Minecraft and also removes the extra
  `minecraft:`-prefixed aliases, which is what makes it able to remove Forge and vanilla commands.

## [3.0.1-alpha.1] - 2026-03-14

### Changed

- **The build flow was rebuilt around multiple modules** with the version-compat modules
   moved under `minecraft/`.
- **EverNifeHyCore was imported into this repository**, unifying the two projects.

---

# 2.x - the published line

Everything below was actually released only for Minecraft, at 3.0.x, the project start to aim
to be more platform agnostic and to also support Hytale as a platform.

## [2.0.4.4] - 2026-02-08

- `VirtualThreadedScheduledExecutor` for modern Java, `FCJavaVersion`, and the Java version shown in
  `/svinfo`.
- NMSUtils and NBT-API support up to Minecraft 1.21; `FancyTextManager` fixed on Paper 1.21.1+.
- `FConfig` gained `Map`/`HashMap`/`LinkedHashMap` serializers; `FCTimeFrame` became a loadable
  salvable; PlayerData gained a `firstSeen` tracker.
- EverForgeLib was removed; annotations moved to jakarta; the Vault integration retries and explains
  how to set CMI up as the economy.

## [2.0.4.3] - 2025-07-23

- `FConfig` split into per-field annotations, with `%field_name%` keys, key prefixes, optional
  kebab-case and enum loadables.
- Item, block and ore registry wrappers with a 1.7.10 implementation, an ore-dict `ArgParser`, and
  `/oredictinfo` with its own GUI.
- An `ArgParser` for `FCTimeFrame`; `PAGEVIEWERS_FULL_LOCALIZATION` for full PageViewer
  customization; `ActionBarMessage.setTicks`.
- Fixed `OutOfMemoryError: unable to create new native thread` and moved to a
  `ScheduledThreadPoolExecutor` that supports delayed tasks (community contribution).

## [2.0.4.2] - 2024-11-10

A memory release.

- **Caching one `SimpleYamlImplementation` per config file cut PlayerData memory use by ~98%**,
  together with `SmartCachedYamlFileHolder` and a `daysSinceLastLoginToLoadPlayerDataInMemory`
  setting.
- **`PDSectionConfiguration`**: sections declare how they are stored and cached, and are hot-loaded
  after a reload; PlayerData gained a `lastSaved` timestamp.
- **`FConfig`**, an annotation system for saving POJOs in YAML.
- `CacheableSupplier`, `FCTickUtil`, `FCJsonUtil`, `FCFileUtil`.
- WorldGuard's final flag list is patched through `sun.misc.Unsafe` on 1.7.10; `/entityinfo` added;
  ECForgeListener adopted as a dependency.

## [2.0.4.1] - 2024-03-19

- An `ArgParser` for `NumberWrapper`, triumph-gui relocated, and flags added to the modified
  WorldGuard 6.1 on 1.7.10.

## [2.0.3.4] - 2023-11-18

- `ComparableItem` and `ComparableItemComplex`, item comparators with and without NBT.
- Context-aware `@Arg` syntax (`<(Player)>` required-or-contextual, `[(Player)]`
  optional-or-contextual), `@FinalCMD.Ignore`, previous-argument awareness in tab completion, and an
  `ArgParser` for WorldGuard regions.
- **Jabel**: the code is written in Java 17 and still compiled to Java 8 bytecode.
- NMSUtils for 1.20.1 and 1.20.2, `MCDetailedVersion`, NBT-API updated to match.
- The basics of a Forge event handler reachable through Bukkit.

## [2.0.3.3] - 2023-04-13

- **The protection layer**: `ProtectionAll` reintroduced over WorldGuard, GriefDefender and
  GriefPreventionPlus handlers, with `/protectiontest` to see what EverNifeCore can actually listen
  to. `CuboidSelection` arrived with it.
- **The UUID system was reworked** so a server switching `online-mode` no longer corrupts its
  database.
- `@FCLocale.Child` for nested locale messages, localizable `@Arg`s, and multiple
  `CMDAccessValidation`s per command; `@Arg` was separated from `@FlagArg` and given an `ArgContext`.
- ItemStacks can be hover texts on FancyText; `FCPlayerInventory` and the `InvItem` system were
  refactored.
- Fluency was removed from the FinalCMD getters and setters.

## [2.0.3.2] - 2023-02-11

- `lastSeen` marks the PlayerData as changed; `MCVersion` compares the minor version.

## [2.0.3.1] - 2023-02-05

The largest 2.x release.

- **The Gradle build gained submodules** - `VersionedPlugins`, `v1_7_10`, `v1_12_2`, `v1_16_5` - so
  version-specific plugin dependencies (WorldGuard 6.1 and 7) are compiled against rather than
  reflected at.
- **A single WorldEdit adaptation for schematic copy and paste from 1.7.10 to 1.16.5.**
- `CMDAccessValidation` for the pre-command process, tab completion and help context, plus a context
  string on `@FinalCMD`.
- `TitleAPI`, the `SVDataManager` rework of server data, `ECLogger` and a modular debug system.
- The ItemStack save format moved onto `ItemDataPart`; the GUI layout icons and `PageViewer`
  step-builder arrived.
- **PlayerData saves asynchronously** - on 21k unique players, load time halved.
- Five events covering player-and-pet damage combinations; `FCMaterialUtil`; `MinMax`; `TriState`;
  `FCReflectionUtil` renamed; Java 12+ compatibility.

## [2.0.3.0] - 2022-11-05

- The placeholder system was completely reworked, the deprecated `FCPlaceholders` and
  `PlaceHolderIntegration` removed, and a console filter added so `/ecdcmd` stops flooding the log.

## [2.0.2.2] - 2022-09-14

- **The GUI layout system**: `LayoutIcon`, `LayoutBase`, `LayoutIconBuilder`, `@ConfigSetting`, a
  paginated builder and background icons.
- **NBT reading moved from NMSUtils to Item-NBT-API**, with NMSUtils implemented for 1.16.5.
- Config comments as a first-class API, `CompoundReplacer`, and indexed loadable lists.
- Spigot 1.18 with `api-version: 1.13`; PlaceholderAPI 2.11.2 compatibility, legacy support kept.

## [2.0.2.1] - 2022-05-07

- NBT-API embedded; the deprecated `Config.Loadable`/`Config.Salvable` annotations replaced by the
  standalone ones; javadoc and sources artifacts published.

## [2.0.1.9] - 2022-05-07

- **The config engine moved to Simple-YAML**, shadowed and relocated, with dependencies injected on
  first class load.
- A `DataHolder` system modelled on Minecraft's own (`MCServerData` -> `MCWorldData` ->
  `MCChunkData` -> block data).
- **The `DynamicCommand` system**: commands scheduled now and run when the player clicks a message.
- The `ECPluginManager` reload pattern, `/ecore info` showing every installed ECPlugin and its
  available updates, and an entire rework of how the locale system is stored and written.
- `FCItemStack` was removed in favour of the plain Bukkit `ItemStack`.

## [2.0.1.8] - 2022-03-23

- **[Glory to Mankind] the full FinalCMD rework**: the old `CMDBuilder` is gone, and tab completion,
  localization and `@Arg` argument parsing are all first-class.
- `FCItemBuilder` and `FCItemFactory`; `ECPluginData` to hold per-plugin localizations and tab
  completion; `ArgParser`s for boolean and UUID; `/eclocale` (alias `/fclocale`); `FCTimeUtil`;
  lombok adopted.

## [2.0.1.7] - 2022-02-12

- PageViewer line bounds fixed, ChatMenuAPI updated against ProtocolLib, and `ECListener`
  registration made failure-tolerant.

## [2.0.1.6] - 2022-02-07

- Player cooldowns are saved and loaded by the cooldown class itself instead of through
  `savePlayerData()`; the update-check message moved after the MOTD; `PageViewer` accepts `-1` as
  the last page.

## [2.0.1.5] - 2022-01-03

The first documented stretch after the initial public state.

- FancyText broadcasts, the `ActionBarAPI` with message priorities, the placeholder system, the
  `PageViewer`, and the locale system's growth into what the later versions build on.
- Libby as a runtime dependency loader, with Hibernate and SQLite support behind it.
- triumph-gui and the beginnings of the complex GUI features; `ItemDataPart` for item description.
- PlayerData events (`ECFullyLoggedInEvent`, `ECCraftItemEvent`, `ECPlayerChangeChunkEvent`), the
  LuckPerms and BossShopPro integrations, `EconomyManager`/`FCEcoUtil`, bStats metrics and the
  Spigot update checker.

## [2.0.1] - 2021-10-14

The repository's initial state, with the commands that had lived in FinalCraftCore moved in. There
is no history before this point.

[2.0.4.4]: https://github.com/evernife/EverNifeCore/compare/v2.0.4.3...v2.0.4.4
[2.0.4.3]: https://github.com/evernife/EverNifeCore/compare/v2.0.4.2...v2.0.4.3
[2.0.4.2]: https://github.com/evernife/EverNifeCore/compare/v2.0.4.1...v2.0.4.2
[2.0.4.1]: https://github.com/evernife/EverNifeCore/compare/v2.0.3.4...v2.0.4.1
[2.0.3.4]: https://github.com/evernife/EverNifeCore/compare/v2.0.3.3...v2.0.3.4
[2.0.3.3]: https://github.com/evernife/EverNifeCore/compare/v2.0.3.2...v2.0.3.3
[2.0.3.2]: https://github.com/evernife/EverNifeCore/compare/v2.0.3.1...v2.0.3.2
[2.0.3.1]: https://github.com/evernife/EverNifeCore/compare/v2.0.2.2...v2.0.3.1
[2.0.2.2]: https://github.com/evernife/EverNifeCore/compare/v2.0.2.1...v2.0.2.2
[2.0.2.1]: https://github.com/evernife/EverNifeCore/compare/v2.0.1.7...v2.0.2.1
[2.0.1.7]: https://github.com/evernife/EverNifeCore/compare/v2.0.1.6...v2.0.1.7
[2.0.1.6]: https://github.com/evernife/EverNifeCore/compare/v2.0.1.5...v2.0.1.6
[2.0.1.5]: https://github.com/evernife/EverNifeCore/compare/v2.0.1.4...v2.0.1.5
[2.0.1]: https://github.com/evernife/EverNifeCore/releases/tag/v2.0.1
