package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Something owed to a player that could not be handed over when it was earned - a full inventory, an offline
 * recipient: the raw effect nodes, read through {@code Effects.registry()} only when it is claimed, so an effect of
 * a plugin that is not loaded stays as it was written. Kept in the recipient's {@link DeliveriesSection}. Immutable.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Delivery {

    /** The plugin whose effect types are always loaded. */
    static final String CORE = "evernifecore";

    private String id;
    private String recipient;
    private DeliveryOrigin origin;
    private long createdAt;
    private @Nullable Long expiresAt;
    private DeliveryPolicy policy;
    private List<JsonNode> effects = new ArrayList<>();

    private Delivery() {
        //Jackson no-arg constructor
    }

    Delivery(UUID id, UUID recipient, DeliveryOrigin origin, Instant createdAt, @Nullable Instant expiresAt, DeliveryPolicy policy,
             List<JsonNode> effects) {
        this.id = id.toString();
        this.recipient = recipient.toString();
        this.origin = origin;
        this.createdAt = createdAt.toEpochMilli();
        this.expiresAt = expiresAt == null ? null : expiresAt.toEpochMilli();
        this.policy = policy;
        this.effects = new ArrayList<>(effects);
    }

    /** The same delivery, still owing only {@code remaining}. */
    Delivery withEffects(List<JsonNode> remaining) {
        return new Delivery(getId(), getRecipient(), origin, getCreatedAt(), getExpiresAt(), policy, remaining);
    }

    public UUID getId() {
        return UUID.fromString(id);
    }

    public UUID getRecipient() {
        return UUID.fromString(recipient);
    }

    public DeliveryOrigin getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return Instant.ofEpochMilli(createdAt);
    }

    /** When it is dropped unclaimed; null keeps it until claimed or purged. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt == null ? null : Instant.ofEpochMilli(expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && now.toEpochMilli() >= expiresAt;
    }

    public DeliveryPolicy getPolicy() {
        return policy;
    }

    /** The raw effect nodes still owed, in order. */
    public List<JsonNode> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    /** Whether every effect is one of the core's, so it can be handed out whatever plugins are loaded. */
    public boolean isSelfContained() {
        for (JsonNode effect : effects) {
            if (!isCoreEffect(effect)) {
                return false;
            }
        }
        return true;
    }

    /** The plugin that registers the type of {@code effect}, as the node names it: {@code owner:} or the registered owner of a bare name. */
    static Optional<String> ownerOf(JsonNode effect) {
        JsonNode type = effect.get(TypeRegistry.TYPE_FIELD);
        String written = type != null && type.isTextual() ? type.asText().trim() : effect.size() == 1 ? effect.fieldNames().next() : null;
        if (written == null) {
            return Optional.empty();
        }
        int colon = written.indexOf(':');
        if (colon > 0) {
            return Optional.of(written.substring(0, colon).toLowerCase(Locale.ROOT));
        }
        return Effects.registry().find(written).map(TypeDescriptor::getOwner);
    }

    static boolean isCoreEffect(JsonNode effect) {
        return ownerOf(effect).map(CORE::equals).orElse(false);
    }

    @Override
    public String toString() {
        return "Delivery " + id + " to " + recipient + " from " + origin + " " + effects;
    }
}
