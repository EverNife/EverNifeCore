package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Whose opens a {@link Screens#override screen override} answers: only those whose content a given plugin owns, or
 * every open on the server. An owner-scoped override beats the server one, which beats the core's default.
 */
public final class ScreenScope {

    private static final ScreenScope SERVER = new ScreenScope(null);

    private final ECPluginData contentOwner;

    private ScreenScope(@Nullable ECPluginData contentOwner) {
        this.contentOwner = contentOwner;
    }

    /** Only the opens whose {@link ScreenRequest#getOwner() content owner} is {@code contentOwner}. */
    @Nonnull
    public static ScreenScope ownedBy(@Nonnull ECPluginData contentOwner) {
        return new ScreenScope(Objects.requireNonNull(contentOwner, "contentOwner"));
    }

    /** Every open of the key, whoever's content it shows. Only one plugin can hold it per key. */
    @Nonnull
    public static ScreenScope server() {
        return SERVER;
    }

    /** The plugin this scope is limited to; {@code null} for {@link #server()}. */
    @Nullable
    public ECPluginData getContentOwner() {
        return contentOwner;
    }

    public boolean isServer() {
        return contentOwner == null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScreenScope && ((ScreenScope) o).contentOwner == contentOwner;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(contentOwner);
    }

    @Override
    public String toString() {
        return isServer() ? "server" : "ownedBy(" + contentOwner.getMetaInfo().getName() + ")";
    }

}
