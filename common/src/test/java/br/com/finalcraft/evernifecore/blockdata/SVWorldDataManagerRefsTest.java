package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.manager.Ref;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a store takes part in the {@code Ref} graph of the plugin that owns its storage: what a {@code Ref}
 * held elsewhere in that plugin can resolve, why a plugin gets one shared store and what the isolated one
 * buys - and gives up.
 */
@ECoreTest
@SuppressWarnings("rawtypes") //a Ref is built from WorldChunkData.class, which no block type parameterizes
class SVWorldDataManagerRefsTest extends BlockStoreTestBase {

    private static final String WORLD = "world";
    private static final BlockPos POS = BlockPos.of(5, 64, 7);
    private static final String CHUNK_KEY = WorldChunkData.keyOf(WORLD, POS.getChunkPos());

    @Test
    void aRefOnThePluginGraphResolvesTheLiveChunkOfASharedStore() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> store = storeOn(storage, "blocks_refs_shared");
        store.setBlock(WORLD, POS, new Marker("alice", 10)).join();

        Ref<String, WorldChunkData> ref = Ref.of(CHUNK_KEY, WorldChunkData.class, storage.refRegistry());
        WorldChunkData resolved = ref.resolve().join().orElse(null);

        assertNotNull(resolved, "the default participation puts this store in the plugin's Ref graph");
        assertNotNull(resolved.getValue(POS.serialize()));

        BlockPos later = BlockPos.of(8, 64, 9);
        store.setBlock(WORLD, later, new Marker("dave", 30)).join();
        assertNotNull(resolved.getValue(later.serialize()),
                "the Ref resolved the entity the store writes into, not a copy of it");
    }

    @Test
    void aRefToAChunkNoStoreHasAnswersEmpty() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeOn(storage, "blocks_refs_absent");

        Optional<WorldChunkData> resolved =
                Ref.of(CHUNK_KEY, WorldChunkData.class, storage.refRegistry()).resolve().join();

        assertFalse(resolved.isPresent());
    }

    @Test
    void aSecondSharedStoreOnThePluginIsRefusedAndNamesTheWayOut() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        storeOn(storage, "blocks_refs_first");

        CompletionException failed = assertThrows(CompletionException.class, () ->
                SVWorldDataManager.targeting(Marker.class).on(storage, "blocks_refs_second").build().join());

        StorageConfigException refused = assertInstanceOf(StorageConfigException.class, failed.getCause());
        assertTrue(refused.getMessage().contains("ISOLATED"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Ref graph"), refused.getMessage());
        assertTrue(refused.getMessage().contains("close the other manager"),
                "the other way out is closing the store that holds the type");
    }

    @Test
    void closingASharedStoreHandsTheEntityTypeBack() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        RefRegistry pluginGraph = storage.refRegistry();

        SVWorldDataManager<Marker> first = storeOn(storage, "blocks_refs_reload");
        assertTrue(pluginGraph.isRegistered(WorldChunkData.class));
        first.close();
        assertFalse(pluginGraph.isRegistered(WorldChunkData.class), "a reload rebuilds the store on this handle");

        SVWorldDataManager<Marker> rebuilt = storeOn(storage, "blocks_refs_reload_other");
        assertNotSame(first.manager(), rebuilt.manager());
    }

    @Test
    void anIsolatedStoreCollidesWithNothingAndIsInvisibleFromOutside() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> isolated = isolatedStoreOn(storage, "blocks_refs_isolated");
        isolated.setBlock(WORLD, POS, new Marker("alice", 10)).join();

        CompletionException unresolvable = assertThrows(CompletionException.class,
                () -> Ref.of(CHUNK_KEY, WorldChunkData.class, storage.refRegistry()).resolve().join());
        assertInstanceOf(IllegalStateException.class, unresolvable.getCause());
        assertTrue(unresolvable.getCause().getMessage().contains("No RefResolver registered"),
                unresolvable.getCause().getMessage());

        //and the type it did not take is still free for the store that IS meant to answer for it
        SVWorldDataManager<Marker> shared = storeOn(storage, "blocks_refs_alongside");
        shared.setBlock(WORLD, POS, new Marker("bob", 20)).join();
        WorldChunkData viaPluginGraph =
                Ref.of(CHUNK_KEY, WorldChunkData.class, storage.refRegistry()).resolve().join().orElse(null);

        assertNotNull(viaPluginGraph);
        assertEquals("bob", ((Marker) viaPluginGraph.getValue(POS.serialize())).getOwner(),
                "the plugin graph answers with the shared store, never with the isolated one");
    }

    @Test
    void twoIsolatedStoresLiveSideBySide() {
        ECStorage storage = openStorage(BackendDefinition.memory());
        SVWorldDataManager<Marker> first = isolatedStoreOn(storage, "blocks_refs_iso_a");
        SVWorldDataManager<Marker> second = isolatedStoreOn(storage, "blocks_refs_iso_b");

        first.setBlock(WORLD, POS, new Marker("alice", 10)).join();

        assertNotSame(first.manager(), second.manager());
        assertEquals("alice", first.getBlock(WORLD, POS).join().getOwner());
        assertNull(second.getBlock(WORLD, POS).join(), "each store answers for its own collection only");
    }

    private SVWorldDataManager<Marker> isolatedStoreOn(ECStorage storage, String collection) {
        return register(SVWorldDataManager.targeting(Marker.class)
                .on(storage, collection)
                .refs(RefParticipation.ISOLATED)
                .autoFlushEvery(Duration.ZERO)
                .build().join());
    }
}
