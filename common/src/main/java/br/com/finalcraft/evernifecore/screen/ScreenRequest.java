package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What an open by {@link ScreenKey} carries to whichever factory answers it: whose content it shows (what an
 * {@link ScreenScope#ownedBy owner-scoped} override matches on), the {@code {0}..{n}} arguments, a typed payload the
 * screen is built from ({@link ViewContext#getRequest(Class)}), and - the most specific layer of all - another key the
 * caller wants answered instead.
 */
public final class ScreenRequest {

    private final ECPluginData owner;
    private ScreenKey screen;
    private Object payload;
    private List<Object> arguments = Collections.emptyList();

    private ScreenRequest(@Nullable ECPluginData owner) {
        this.owner = owner;
    }

    /** A request for content {@code owner} owns; {@code null} when it is nobody's (only server overrides and defaults answer). */
    @Nonnull
    public static ScreenRequest of(@Nullable ECPluginData owner) {
        return new ScreenRequest(owner);
    }

    /** Asks for {@code screen} instead of the key being opened - the caller's own replacement, before any override. */
    @Nonnull
    public ScreenRequest withScreen(@Nullable ScreenKey screen) {
        this.screen = screen;
        return this;
    }

    /** What the screen is built from, read back by {@link ViewContext#getRequest(Class)}: a {@code ConfirmRequest}, a {@code RankingRequest}. */
    @Nonnull
    public ScreenRequest payload(@Nullable Object payload) {
        this.payload = payload;
        return this;
    }

    /** What {@code {0}}..{@code {n}} read in the screen's text. */
    @Nonnull
    public ScreenRequest args(@Nonnull Object... arguments) {
        this.arguments = Collections.unmodifiableList(Arrays.asList(arguments.clone()));
        return this;
    }

    @Nullable
    public ECPluginData getOwner() {
        return owner;
    }

    @Nullable
    public ScreenKey getScreen() {
        return screen;
    }

    @Nullable
    public Object getPayload() {
        return payload;
    }

    @Nonnull
    public List<Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ScreenRequest{owner=" + (owner == null ? null : owner.getMetaInfo().getName()) + ", screen=" + screen
                + ", payload=" + (payload == null ? null : payload.getClass().getSimpleName()) + ", args=" + arguments + "}";
    }

}
