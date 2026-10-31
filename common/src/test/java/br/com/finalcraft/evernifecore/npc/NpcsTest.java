package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageHold;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.FakeNpcProvider;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry: one provider per id, lookups by the provider a reference names, the first available one resolving. */
@ECoreTest
class NpcsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData quests;
    private ECPluginData dialogs;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        quests = Plugins.fakePluginData("FakeQuests", tempDir.resolve("quests").toFile());
        dialogs = Plugins.fakePluginData("FakeDialogs", tempDir.resolve("dialogs").toFile());
    }

    @AfterEach
    void close() {
        Npcs.unregisterAll(quests);
        Npcs.unregisterAll(dialogs);
        world.close();
    }

    @Test
    void aSecondProviderWithTheSameIdIsRefusedNamingBothOwners() {
        Npcs.register(quests, new FakeNpcProvider("citizens"));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Npcs.register(dialogs, new FakeNpcProvider("Citizens")));
        assertTrue(refused.getMessage().contains("FakeQuests") && refused.getMessage().contains("FakeDialogs"), refused.getMessage());
    }

    @Test
    void unregisterAllForgetsOnlyThatOwnersProviders() {
        Npcs.register(quests, new FakeNpcProvider("citizens"));
        Npcs.register(dialogs, new FakeNpcProvider("fancynpcs"));

        Npcs.unregisterAll(quests);

        assertFalse(Npcs.provider("citizens").isPresent());
        assertTrue(Npcs.provider("fancynpcs").isPresent());
        assertEquals(1, Npcs.getProviders().size());
    }

    @Test
    void resolveAsksTheFirstAvailableProviderAndSkipsAnUnavailableOne() {
        NativeEntity guard = new NativeEntity(UUID.randomUUID());
        Npcs.register(quests, new FakeNpcProvider("citizens").available(false).knows(guard, NpcRef.of("citizens", "1")));
        Npcs.register(dialogs, new FakeNpcProvider("hytale").knows(guard, NpcRef.of("hytale", "2")));

        assertEquals(2, Npcs.getProviders().size(), "both are registered, available or not");
        assertEquals(Optional.of(NpcRef.of("hytale", "2")), Npcs.resolve(guard));
        assertFalse(Npcs.resolve(new NativeEntity(UUID.randomUUID())).isPresent(), "an entity nobody knows");
    }

    @Test
    void lookupsGoToTheNamedProviderAndFindNothingWhileItIsUnavailable() {
        NpcRef merchant = NpcRef.of("znpcsplus", "merchant");
        FakeNpcProvider provider = new FakeNpcProvider("znpcsplus").at(merchant, WorldLocPos.of(1, 2, 3, "world")).named(merchant, "§aMerchant");
        Npcs.register(quests, provider);

        assertEquals(Optional.of("§aMerchant"), Npcs.getName(merchant));
        assertEquals(Optional.of(WorldLocPos.of(1, 2, 3, "world")), Npcs.getLocation(merchant));

        provider.available(false);
        assertFalse(Npcs.getName(merchant).isPresent());
        assertFalse(Npcs.find(merchant).isPresent());
    }

    @Test
    void aReferenceToAnUnregisteredProviderIsValidButRequiringItListsTheRegisteredOnes() {
        Npcs.register(quests, new FakeNpcProvider("citizens"));
        NpcRef pixelmon = NpcRef.parse("pixelmon:4f2a");

        assertFalse(Npcs.getLocation(pixelmon).isPresent());
        NpcProviderException refused = assertThrows(NpcProviderException.class, () -> Npcs.requireProvider(pixelmon));
        assertTrue(refused.getMessage().contains("'pixelmon'") && refused.getMessage().contains("registered: citizens"), refused.getMessage());
    }

    @Test
    void aProviderRaisesClicksOnlyWhileInteractNpcIsNeeded() {
        FakeNpcProvider provider = new FakeNpcProvider("citizens");
        Npcs.register(quests, provider);
        assertFalse(provider.isStarted(), "nobody needs interact-npc yet");

        UsageHold hold = EventSourceUsage.get().acquire(EventSource.INTERACT_NPC);
        assertTrue(provider.isStarted());
        Npcs.unregisterAll(quests);
        assertFalse(provider.isStarted(), "leaving with its owner stops it");
        hold.release();
    }
}
