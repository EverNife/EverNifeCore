package br.com.finalcraft.evernifecore.api.common.player;

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
    public int hashCode() {
        return getDelegate().hashCode();
    }

}
