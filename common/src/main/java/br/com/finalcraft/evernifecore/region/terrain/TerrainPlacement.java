package br.com.finalcraft.evernifecore.region.terrain;

/** A snapshot being put back by {@link RegionTerrain#restoreSnapshot}, a budget at a time on the world's thread. */
public interface TerrainPlacement {

    /**
     * Places at most {@code blocks} more blocks; once the last block is down, also the entities the snapshot kept.
     *
     * @return true when everything is placed
     */
    boolean placeNext(int blocks);
}
