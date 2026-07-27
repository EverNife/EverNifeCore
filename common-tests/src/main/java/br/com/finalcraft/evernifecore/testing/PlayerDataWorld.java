package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The PlayerData layer, booted and torn down as one thing.
 *
 * <pre>{@code
 * try (PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("my_db")).sections(JobsSection.class).boot(tempDir)) {
 *     PlayerController.handleLogin(uuid, "Petrus").join();
 * }
 * }</pre>
 *
 * <p>Closing it does the whole teardown, not the part you remembered: shut the controller down,
 * drop the configured sections, and clear the schema migrations. Leaving any of the three behind
 * hands state to the next test class in the JVM, and the failure surfaces somewhere else.</p>
 */
public final class PlayerDataWorld implements AutoCloseable {

    private final Storages storages;
    private final List<Class<? extends PDSection>> sections = new ArrayList<Class<? extends PDSection>>();
    private File storageYml;
    private boolean closed = false;

    private PlayerDataWorld(Storages storages) {
        this.storages = storages;
    }

    public static PlayerDataWorld with(Storages storages) {
        return new PlayerDataWorld(storages);
    }

    /** Sections registered before the boot, so the first load already knows about them. */
    @SafeVarargs
    public final PlayerDataWorld sections(Class<? extends PDSection>... sectionClasses) {
        Collections.addAll(sections, sectionClasses);
        return this;
    }

    /** Writes the storage.yml into {@code baseDir} and initializes the controller against it. */
    public PlayerDataWorld boot(Path baseDir) {
        storageYml = storages.writeTo(baseDir);

        for (Class<? extends PDSection> sectionClass : sections) {
            PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, sectionClass).build());
        }

        PlayerController.initialize(storageYml);
        return this;
    }

    /** Boots again against the same file - the atomic swap a "restart the server" test needs. */
    public PlayerDataWorld reboot() {
        PlayerController.initialize(requireBooted());
        return this;
    }

    /** The generated storage.yml, for a test that wants to boot by hand or assert on the file. */
    public File storageYml() {
        return requireBooted();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        tearDown();
    }

    /**
     * The whole teardown, for a test that boots the controller by hand: shut it down, drop the
     * configured player and account sections, and clear the schema migrations.
     *
     * <p>All four, always. Every class used to do the subset it happened to need, and whatever it
     * skipped reached the next class in the JVM.</p>
     */
    public static void tearDown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
        PlayerController.getConfiguredAccountSections().clear();
        EntitySchemaMigrations.clear();
    }

    private File requireBooted() {
        if (storageYml == null) {
            throw new IllegalStateException("PlayerDataWorld.boot(baseDir) has not been called yet");
        }
        return storageYml;
    }
}
