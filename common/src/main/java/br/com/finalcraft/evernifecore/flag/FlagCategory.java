package br.com.finalcraft.evernifecore.flag;

/**
 * How a flag acts: {@link #PROTECTION} answers {@link Flag#decide} when an action happens, {@link #SESSION}
 * follows a player through {@link Flag#onEnter}/{@link Flag#onLeave}/{@link Flag#onRevalidate},
 * and {@link #DATA} only carries a value for someone else to read.
 */
public enum FlagCategory {
    PROTECTION, SESSION, DATA
}
