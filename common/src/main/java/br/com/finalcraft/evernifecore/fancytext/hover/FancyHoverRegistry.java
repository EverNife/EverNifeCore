package br.com.finalcraft.evernifecore.fancytext.hover;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-extensible catalogue of {@link FancyHoverType}s. The render pipeline never hardcodes a
 * hover kind: it looks the value's {@link FancyHover#typeId()} up here. Core registers {@code text}
 * and {@code item} at class load; a plugin integrator adds its own with {@link #register}.
 */
public final class FancyHoverRegistry {

    private static final Map<String, FancyHoverType<?>> TYPES = new ConcurrentHashMap<>();

    private FancyHoverRegistry() {
    }

    static {
        register(FancyHoverType.<TextHover>of(TextHover.TYPE_ID,
                // A null/empty tooltip renders no hover at all, same as before a structured hover value existed.
                textHover -> textHover.text() == null || textHover.text().isEmpty()
                        ? null
                        : HoverEvent.showText(FCColorUtil.colorfyComponent(textHover.text()))));

        register(FancyHoverType.<ItemHover>of(ItemHover.TYPE_ID,
                        itemHover -> HoverEvent.showItem(
                                Key.key(itemHover.rawItem()), 1, BinaryTagHolder.binaryTagHolder(itemHover.rawItem())))
                .withDegrade(itemHover -> new TextHover(itemHover.rawItem())));
    }

    /**
     * Registers a hover type under {@link FancyHoverType#typeId()}. Registering the exact same
     * instance again is a harmless no-op (a plugin re-running its own bootstrap on reload);
     * registering a different instance under an id that is already taken throws, so two unrelated
     * hover kinds can never silently shadow one another.
     */
    public static <H extends FancyHover> void register(FancyHoverType<H> type) {
        FancyHoverType<?> previous = TYPES.putIfAbsent(type.typeId(), type);
        if (previous != null && previous != type) {
            throw new IllegalStateException("A FancyHoverType is already registered for id '" + type.typeId() + "'");
        }
    }

    public static FancyHoverType<?> byId(String typeId) {
        return TYPES.get(typeId);
    }

    public static Set<String> registeredIds() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    /**
     * The {@link HoverEvent} to actually attach for this value, or {@code null} to attach none at
     * all. Consults the installed platform's {@code IPlatformChatAdapter#supportsHover(String)} for
     * the value's own type first; if unsupported, tries the type's declared degrade exactly once -
     * never chases a further degrade, so two types that degrade into one another cannot recurse.
     */
    public static HoverEvent<?> resolve(FancyHover hover) {
        if (hover == null) {
            return null;
        }
        FancyHoverType<FancyHover> type = typeOf(hover);
        if (type == null) {
            return null;
        }
        if (platformSupports(type.typeId())) {
            return type.render(hover);
        }

        FancyHover degraded = type.degrade(hover);
        if (degraded == null) {
            return null;
        }
        FancyHoverType<FancyHover> degradedType = typeOf(degraded);
        if (degradedType == null || !platformSupports(degradedType.typeId())) {
            return null;
        }
        return degradedType.render(degraded);
    }

    @SuppressWarnings("unchecked")
    private static FancyHoverType<FancyHover> typeOf(FancyHover hover) {
        return (FancyHoverType<FancyHover>) TYPES.get(hover.typeId());
    }

    /**
     * No platform installed (a bare test JVM) or no chat adapter at all (e.g. the no-op test
     * platform) means nothing has ever told us a type is unsupported, so treat every type as
     * supported - the same behaviour hover rendering always had before any platform was consulted.
     */
    private static boolean platformSupports(String typeId) {
        IPlatform platform = EverNifeCore.getProviders().getPlatformOrNull();
        if (platform == null) {
            return true;
        }
        IPlatformChatAdapter chatAdapter = platform.getChatAdapter();
        if (chatAdapter == null) {
            return true;
        }
        return chatAdapter.supportsHover(typeId);
    }
}
