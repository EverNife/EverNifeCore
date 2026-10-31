package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * What a {@link Screens#override screen factory} builds from: who is about to see the screen, whose content it shows,
 * the arguments and the typed request of the open.
 */
public final class ViewContext {

    private final FPlayer viewer;
    private final ScreenKey key;
    private final ScreenRequest request;
    private ExecutionContext executionContext;

    ViewContext(@Nonnull FPlayer viewer, @Nonnull ScreenKey key, @Nonnull ScreenRequest request) {
        this.viewer = viewer;
        this.key = key;
        this.request = request;
    }

    @Nonnull
    public FPlayer getViewer() {
        return viewer;
    }

    /** The key being answered - the caller's replacement when it asked for one. */
    @Nonnull
    public ScreenKey getKey() {
        return key;
    }

    /** The plugin whose content the screen shows; {@code null} when the open named none. */
    @Nullable
    public ECPluginData getOwner() {
        return request.getOwner();
    }

    @Nonnull
    public List<Object> getArguments() {
        return request.getArguments();
    }

    /** The viewer's language, as the logic of the screen reads it. */
    @Nonnull
    public Locale getLocale() {
        return getExecutionContext().getLocale();
    }

    /** A context with the content owner (the core when none) as the caller and the viewer as the player. */
    @Nonnull
    public ExecutionContext getExecutionContext() {
        if (executionContext == null) {
            ECPluginData caller = getOwner() != null ? getOwner() : EverNifeCore.getEcPluginData();
            executionContext = ExecutionContext.builder(Caller.of(caller, "screen:" + key)).player(viewer).build();
        }
        return executionContext;
    }

    /**
     * The payload the open carried, as {@code type}.
     *
     * @throws IllegalStateException when it carried none, or one of another type
     */
    @Nonnull
    public <R> R getRequest(@Nonnull Class<R> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalStateException("The screen [" + key + "] is built from a " + type.getSimpleName() + ", and the open "
                    + "carried " + (payload == null ? "none" : "a " + payload.getClass().getSimpleName()) + ". Open it with "
                    + "ScreenRequest.of(plugin).payload(" + type.getSimpleName() + ".of(...)).");
        }
        return type.cast(payload);
    }

    @Override
    public String toString() {
        return "ViewContext{" + key + " for " + viewer.getName() + ", " + request + "}";
    }

}
