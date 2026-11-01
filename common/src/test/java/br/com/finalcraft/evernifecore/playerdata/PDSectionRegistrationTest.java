package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registration contract of a PDSection: the identity it must declare, what happens when a class
 * nobody registered is resolved, what a REPEATED registration does to the live cache, and the manual
 * release. Runs on H2 mem - no Docker.
 */
@ECoreTest
class PDSectionRegistrationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    public static class JobsSection extends PDSection {
        public int level;
    }

    public static class OtherSection extends PDSection {
    }

    /** Registered by nobody, on purpose. */
    public static class StraySection extends PDSection {
    }

    public abstract static class AbstractSection extends PDSection {
    }

    public static class NoNoArgConstructorSection extends PDSection {
        public NoNoArgConstructorSection(int required) {
            this.level = required;
        }
        public int level;
    }

    // ------------------------------------------------------------------
    // The declared identity
    // ------------------------------------------------------------------

    @Test
    void invalidSectionIdIsRejected() {
        //rejected, never sanitized: 'my-section' and 'mysection' would otherwise share a collection
        assertThrows(IllegalArgumentException.class,
                () -> PDSectionConfiguration.builder(null, JobsSection.class, "my-section"));
        assertThrows(IllegalArgumentException.class,
                () -> PDSectionConfiguration.builder(null, JobsSection.class, ""));
        assertThrows(IllegalArgumentException.class,
                () -> PDSectionConfiguration.builder(null, JobsSection.class, null));
    }

    @Test
    void sectionIdIsCanonicalizedToLowercase() {
        assertEquals("jobs", PDSectionConfiguration.builder(null, JobsSection.class, "Jobs").build().getSectionId());
    }

    @Test
    void twoSectionsOfOnePluginCannotShareAnId() {
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, JobsSection.class, "jobs").build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PlayerController.registerPDSectionCfg(
                        PDSectionConfiguration.builder(null, OtherSection.class, "jobs").build()));
        assertTrue(error.getMessage().contains("jobs"));
    }

    @Test
    void aClassTheFrameworkCannotInstantiateIsRejectedAtRegistration() {
        IllegalStateException abstractError = assertThrows(IllegalStateException.class,
                () -> PlayerController.registerPDSectionCfg(
                        PDSectionConfiguration.builder(null, AbstractSection.class, "abs").build()));
        assertTrue(abstractError.getMessage().contains("abstract"));

        IllegalStateException ctorError = assertThrows(IllegalStateException.class,
                () -> PlayerController.registerPDSectionCfg(
                        PDSectionConfiguration.builder(null, NoNoArgConstructorSection.class, "noctor").build()));
        assertTrue(ctorError.getMessage().contains("no-arg constructor"));
    }

    // ------------------------------------------------------------------
    // A section nobody registered
    // ------------------------------------------------------------------

    @Test
    void asyncAccessorsFailForAnUnregisteredSection() throws IOException {
        PlayerController.initialize(Storages.h2("r_unreg_async").writeTo(tempDir));
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Stray").join();
        PlayerData playerData = PlayerController.getLoaded(uuid);

        assertUnregistered(() -> playerData.getPDSection(StraySection.class).join());
        assertUnregistered(() -> playerData.getPDSectionIfPresent(StraySection.class).join());
        assertUnregistered(() -> playerData.hasPDSection(StraySection.class).join());
    }

    @Test
    void syncAccessorsThrowForAnUnregisteredSection() throws IOException {
        PlayerController.initialize(Storages.h2("r_unreg_sync").writeTo(tempDir));
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Stray").join();
        PlayerData playerData = PlayerController.getLoaded(uuid);

        //null would read as "not loaded", which is a perfectly ordinary answer - it must not be
        //what an unregistered class produces
        assertThrows(IllegalStateException.class,
                () -> PlayerController.getLoadedSection(uuid, StraySection.class));
        assertThrows(IllegalStateException.class,
                () -> playerData.getPDSectionIfLoaded(StraySection.class));
        assertThrows(IllegalStateException.class,
                () -> playerData.hasPDSectionIfLoaded(StraySection.class));
    }

    @Test
    void anUnknownPlayerDoesNotMaskAnUnregisteredSection() throws IOException {
        PlayerController.initialize(Storages.h2("r_unreg_nobody").writeTo(tempDir));
        //the player does not exist in the backend: the accessor used to short-circuit to null here
        //and swallow the registration error entirely
        assertUnregistered(() -> PlayerController.getPDSection(UUID.randomUUID(), StraySection.class).join());
    }

    private static void assertUnregistered(Runnable call) {
        CompletionException wrapper = assertThrows(CompletionException.class, call::run);
        assertTrue(wrapper.getCause() instanceof IllegalStateException,
                "expected the not-registered IllegalStateException, got " + wrapper.getCause());
        assertTrue(wrapper.getCause().getMessage().contains("not registered"),
                "the message must name the missing registration: " + wrapper.getCause().getMessage());
    }

    // ------------------------------------------------------------------
    // Registering again = reloading the section
    // ------------------------------------------------------------------

    @Test
    void reRegisteringFlushesThenDropsTheCachedState() throws IOException {
        PlayerController.initialize(Storages.h2("r_reload").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, JobsSection.class, "jobs").build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Reloader").join();
        JobsSection before = PlayerController.getLoaded(uuid).getPDSection(JobsSection.class).join();
        before.level = 5;
        before.markDirty();

        //a plugin reload re-runs its config init, which re-registers the section
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, JobsSection.class, "jobs").build());

        assertNull(PlayerController.getLoadedSection(uuid, JobsSection.class),
                "the previous session's cell must be gone - the owner is not online, so the rebind"
                        + " does not hot-load it back");
        assertTrue(PlayerController.get().getBinding(JobsSection.class).getRepository().exists(uuid).join(),
                "the unflushed change must have been persisted BEFORE the cache was dropped");

        JobsSection after = PlayerController.getLoaded(uuid).getPDSection(JobsSection.class).join();
        assertNotSame(before, after, "the reload must hand out a fresh instance read from the backend");
        assertEquals(5, after.level, "and it must carry the state that was flushed");
    }

    @Test
    void reRegisteringDiscardsUnflushedStateWhenTheSectionAsksForIt() throws IOException {
        PlayerController.initialize(Storages.h2("r_reload_discard").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(PDSectionConfiguration
                .builder(null, JobsSection.class, "jobs").discardDirtyOnReload().build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Derived").join();
        JobsSection section = PlayerController.getLoaded(uuid).getPDSection(JobsSection.class).join();
        section.level = 9;
        section.markDirty();

        PlayerController.registerPDSectionCfg(PDSectionConfiguration
                .builder(null, JobsSection.class, "jobs").discardDirtyOnReload().build());

        assertNull(PlayerController.getLoadedSection(uuid, JobsSection.class));
        assertFalse(PlayerController.get().getBinding(JobsSection.class).getRepository().exists(uuid).join(),
                "discardDirtyOnReload must drop the unflushed state instead of persisting it");
    }

    // ------------------------------------------------------------------
    // Manual release
    // ------------------------------------------------------------------

    @Test
    void releaseFreesOfflineCellsAndKeepsOnlineOnes() throws IOException {
        PlayerController.initialize(Storages.h2("r_release").writeTo(tempDir));
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, JobsSection.class, "jobs").build());

        UUID online = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        PlayerController.handleLogin(online, "Online").join();
        PlayerController.handleLogin(offline, "Offline").join();
        PlayerController.getLoaded(online).setPlayer(Senders.player("Online", online));

        PlayerController.getLoaded(online).getPDSection(JobsSection.class).join();
        PlayerController.getLoaded(offline).getPDSection(JobsSection.class).join();

        int released = PlayerController.releasePDSection(JobsSection.class).join();

        assertEquals(1, released, "only the cell of the offline owner may be released");
        assertNotNull(PlayerController.getLoadedSection(online, JobsSection.class),
                "an online player's cell has to stay canonical");
        assertNull(PlayerController.getLoadedSection(offline, JobsSection.class));
    }

    // ------------------------------------------------------------------
    // A registration left behind by a plugin that is already gone
    // ------------------------------------------------------------------

    public static class DeadPluginSection extends PDSection {
        @JsonProperty("level")
        public int level;
    }

    /**
     * Defines {@link DeadPluginSection} on its own and, once closed, refuses every class lookup with
     * the exception Bukkit's plugin class loader throws after the plugin's jar was closed.
     */
    private static final class ClosingLoader extends ClassLoader {
        private final String definedHere = DeadPluginSection.class.getName();
        private volatile boolean closed;

        ClosingLoader() {
            super(PDSectionRegistrationTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (closed) throw new IllegalStateException("zip file closed");
            if (!name.equals(definedHere)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                String resource = name.replace('.', '/') + ".class";
                try (InputStream in = getParent().getResourceAsStream(resource)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    for (int read; (read = in.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
                    return defineClass(name, bytes.toByteArray(), 0, bytes.size());
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSectionOfAPluginWhoseJarClosedBeforeTheBootstrapIsDroppedAndTheCoreStillStarts() throws Exception {
        ClosingLoader loader = new ClosingLoader();
        Class<? extends PDSection> dead = (Class<? extends PDSection>) loader.loadClass(DeadPluginSection.class.getName());
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, dead, "dead").build());
        PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, JobsSection.class, "jobs").build());
        loader.closed = true; //its enable failed ahead of the core: Bukkit disabled it and closed its jar

        PlayerController.initialize(Storages.h2("r_dead_plugin").writeTo(tempDir));

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Alive").join();
        assertNotNull(PlayerController.getLoaded(uuid).getPDSection(JobsSection.class).join(),
                "the sections of the plugins still alive must bind");
        assertUnregistered(() -> PlayerController.getLoaded(uuid).getPDSection(dead).join());
    }
}
