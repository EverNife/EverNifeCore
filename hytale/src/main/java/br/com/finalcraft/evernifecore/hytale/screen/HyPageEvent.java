package br.com.finalcraft.evernifecore.hytale.screen;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import jakarta.annotation.Nullable;

/**
 * What the client sends back from a core page: the binding that fired ({@code Kind}), the cell it was bound to
 * ({@code Slot}) and, for a field, its text ({@code @Value} - the client fills a key starting with {@code @} with the
 * property the binding names).
 */
public final class HyPageEvent {

    public static final String SLOT = "Slot";
    public static final String KIND = "Kind";
    public static final String VALUE = "@Value";

    public static final BuilderCodec<HyPageEvent> CODEC = BuilderCodec.builder(HyPageEvent.class, HyPageEvent::new)
            .append(new KeyedCodec<>(SLOT, Codec.STRING), (event, value) -> event.slot = value, event -> event.slot).add()
            .append(new KeyedCodec<>(KIND, Codec.STRING), (event, value) -> event.kind = value, event -> event.kind).add()
            .append(new KeyedCodec<>(VALUE, Codec.STRING), (event, value) -> event.value = value, event -> event.value).add()
            .build();

    private String slot;
    private String kind;
    private String value;

    public HyPageEvent() {

    }

    public HyPageEvent(@Nullable String slot, @Nullable String kind, @Nullable String value) {
        this.slot = slot;
        this.kind = kind;
        this.value = value;
    }

    @Nullable
    public String getSlot() {
        return slot;
    }

    @Nullable
    public String getKind() {
        return kind;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "HyPageEvent{slot=" + slot + ", kind=" + kind + ", value=" + value + "}";
    }

}
