package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What a type's factory receives: every parameter its {@link TypeDescriptor} declares, already validated, typed and
 * with the defaults applied. A getter returns null (false for {@link #getBoolean}) for an optional parameter the
 * definition left out and that has no default; asking for a parameter the type never declared, or with the getter of
 * another kind, is a programming error and throws.
 */
public final class ParsedParams {

    private final TypeDescriptor<?> descriptor;
    private final Map<String, Object> values;
    private final JsonNode raw;
    private final ECPluginData caller;

    ParsedParams(TypeDescriptor<?> descriptor, Map<String, Object> values, JsonNode raw, ECPluginData caller) {
        this.descriptor = descriptor;
        this.values = values;
        this.raw = raw;
        this.caller = caller;
    }

    /** A {@link ParamSpec.Kind#STRING STRING}, {@link ParamSpec.Kind#ID ID}, {@link ParamSpec.Kind#OWNER OWNER} or {@link ParamSpec.Kind#TEXT TEXT} value, as written. */
    public @Nullable String getString(String name) {
        return value(name, String.class, ParamSpec.Kind.STRING, ParamSpec.Kind.ID, ParamSpec.Kind.OWNER, ParamSpec.Kind.TEXT);
    }

    public @Nullable BigDecimal getNumber(String name) {
        return value(name, BigDecimal.class, ParamSpec.Kind.NUMBER);
    }

    public boolean getBoolean(String name) {
        return Boolean.TRUE.equals(value(name, Boolean.class, ParamSpec.Kind.BOOLEAN));
    }

    public @Nullable Duration getDuration(String name) {
        return value(name, Duration.class, ParamSpec.Kind.DURATION);
    }

    /** A {@link ParamSpec.Kind#TEXT TEXT} value as a {@link FancyText}; placeholders in it are still unresolved. */
    public @Nullable FancyText getText(String name) {
        Object text = value(name, Object.class, ParamSpec.Kind.TEXT);
        return text == null ? null : text instanceof FancyText ? (FancyText) text : FancyText.of((String) text);
    }

    public @Nullable Window getWindow(String name) {
        return value(name, Window.class, ParamSpec.Kind.WINDOW);
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String name) {
        List<String> list = value(name, List.class, ParamSpec.Kind.LIST);
        return list == null ? Collections.<String>emptyList() : list;
    }

    /** A {@link ParamSpec.Kind#NODE NODE} child, or null when it is absent or still {@link #getPending pending}. */
    @Nullable
    public <N> N getNode(String name, Class<N> type) {
        PendingDefinition<N> pending = getPending(name);
        return pending == null ? null : pending.get().map(type::cast).orElse(null);
    }

    /** The {@link ParamSpec.Kind#NODE NODE} child as a {@link PendingDefinition} - resolved or not; null when absent. */
    @SuppressWarnings("unchecked")
    @Nullable
    public <N> PendingDefinition<N> getPending(String name) {
        return value(name, PendingDefinition.class, ParamSpec.Kind.NODE);
    }

    /**
     * The children of a {@link ParamSpec.Kind#NODE_LIST NODE_LIST} that resolved, in order. A child whose type is
     * still absent is left out; {@link #getPendingNodes} has every one.
     */
    public <N> List<N> getNodes(String name, Class<N> type) {
        List<N> resolved = new ArrayList<>();
        for (PendingDefinition<N> each : this.<N>getPendingNodes(name)) {
            each.get().ifPresent(value -> resolved.add(type.cast(value)));
        }
        return resolved;
    }

    /** Every child of a {@link ParamSpec.Kind#NODE_LIST NODE_LIST}, resolved or pending, in order. */
    @SuppressWarnings("unchecked")
    public <N> List<PendingDefinition<N>> getPendingNodes(String name) {
        List<PendingDefinition<N>> list = value(name, List.class, ParamSpec.Kind.NODE_LIST);
        return list == null ? Collections.<PendingDefinition<N>>emptyList() : list;
    }

    /** The node as written, the keys the type does not declare included (a registry's reserved keys). */
    public JsonNode getRaw() {
        return raw;
    }

    /** The plugin whose definition is being read. */
    public ECPluginData getCaller() {
        return caller;
    }

    public TypeDescriptor<?> getDescriptor() {
        return descriptor;
    }

    @Nullable
    private <V> V value(String name, Class<V> type, ParamSpec.Kind... kinds) {
        ParamSpec param = descriptor.getParam(name);
        if (param == null) {
            throw new IllegalArgumentException("Type " + descriptor.getKey() + " has no parameter '" + name + "'. Its parameters: "
                    + descriptor.describeParams() + ".");
        }
        for (ParamSpec.Kind kind : kinds) {
            if (param.getKind() == kind) {
                return type.cast(values.get(param.getName()));
            }
        }
        throw new IllegalArgumentException("Parameter '" + name + "' of " + descriptor.getKey() + " is " + param.getKind()
                + ", which this getter does not read.");
    }
}
