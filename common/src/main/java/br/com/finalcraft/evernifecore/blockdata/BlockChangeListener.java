package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Notified after a block value changed. An insert has a {@code null} {@code oldValue}, a removal a
 * {@code null} {@code newValue}, and a replace fires ONCE carrying both sides - never a removal followed by
 * an insert.
 *
 * <p>Called outside every internal lock, on whichever thread completed the write (a storage callback thread
 * for a chunk that had to be loaded). Keep it short and hop to your own scheduler for anything that must run
 * on the server thread.
 *
 * @param <O> the block-value type of the owning manager
 */
@FunctionalInterface
public interface BlockChangeListener<O> {

    void onBlockChange(String world, BlockPos pos, @Nullable O oldValue, @Nullable O newValue);
}
