package br.com.finalcraft.evernifecore.region;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A new parent changes the depth of a whole subtree, and the index re-orders it without being told. */
class RegionReorderTest extends ManagedRegionFixture {

    @Test
    void reparentingMovesTheSubtreeInEveryBucketItSits() {
        RegionManager manager = manager();
        //the parents live far from the chunk the children share, so only a subtree re-publish touches that chunk
        Region grandparent = created(manager.create("grandparent", WORLD, box(1000, 1000, 1001, 1001)));
        Region parentA = created(manager.create("parent_a", WORLD, box(2000, 2000, 2001, 2001)));
        Region parentB = created(manager.create("parent_b", WORLD, box(3000, 3000, 3001, 3001)));
        Region childA = created(manager.create("child_a", WORLD, box(0, 0, 9, 9), RegionOptions.defaults().withParent(parentA)));
        Region childB = created(manager.create("child_b", WORLD, box(0, 0, 9, 9), RegionOptions.defaults().withParent(parentB)));
        assertEquals(Arrays.asList(childA, childB), RegionLookup.get().at(WORLD, 5, 50, 5), "same priority and depth: by id");

        parentB.setParent(grandparent);

        assertEquals(Arrays.asList(childB, childA), RegionLookup.get().at(WORLD, 5, 50, 5), "child_b is now the deeper one");
        Region[] bucket = RegionLookup.get().getIndex().snapshot(WORLD).buckets.get(RegionIndex.chunkKey(0, 0));
        assertEquals(Arrays.asList(childB, childA), Arrays.asList(bucket), "the shared bucket itself is in order again");
    }
}
