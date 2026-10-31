package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** The core's defaults: the bottom layer of {@link Screens#open(br.com.finalcraft.evernifecore.api.common.player.FPlayer, ScreenKey, ScreenRequest)}. */
final class CoreScreens {

    private static final Map<ScreenKey, Function<ViewContext, Screen<?>>> DEFAULTS;

    static {
        Map<ScreenKey, Function<ViewContext, Screen<?>>> defaults = new LinkedHashMap<>();
        defaults.put(ScreenKey.CONFIRM, ctx -> ConfirmScreen.of(ctx.getRequest(ConfirmRequest.class)));
        defaults.put(ScreenKey.DELIVERIES, ctx -> DeliveriesScreen.of());
        defaults.put(ScreenKey.RANKING, ctx -> RankingScreen.of(ctx.getRequest(RankingRequest.class)));
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    private CoreScreens() {

    }

    @Nullable
    static Function<ViewContext, Screen<?>> defaultOf(ScreenKey key) {
        return DEFAULTS.get(key);
    }

}
