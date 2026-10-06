package br.com.finalcraft.evernifecore.region.flag;

/**
 * How a flag acts: {@link #PROTECTION} answers {@link RegionFlag#decide} when an action happens, {@link #SESSION}
 * follows a player through {@link RegionFlag#onEnter}/{@link RegionFlag#onLeave}/{@link RegionFlag#onRevalidate},
 * and {@link #DATA} only carries a value for someone else to read.
 */
public enum FlagCategory {
    PROTECTION, SESSION, DATA
}
