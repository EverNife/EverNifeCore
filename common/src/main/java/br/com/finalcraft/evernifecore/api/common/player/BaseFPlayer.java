package br.com.finalcraft.evernifecore.api.common.player;

import java.util.Objects;

public abstract class BaseFPlayer<DELEGATE> implements FPlayer {

    protected final DELEGATE delegate;

    public BaseFPlayer(DELEGATE delegate) {
        this.delegate = delegate;
    }

    @Override
    public DELEGATE getDelegate() {
        return delegate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseFPlayer)) return false;
        return Objects.equals(getDelegate(), ((BaseFPlayer<?>) o).getDelegate());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getDelegate());
    }

}
