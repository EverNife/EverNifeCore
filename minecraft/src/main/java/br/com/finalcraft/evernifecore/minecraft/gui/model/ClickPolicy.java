package br.com.finalcraft.evernifecore.minecraft.gui.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a viewer is allowed to do inside a gui, as a {@code ClickType} x {@code InventoryAction}
 * matrix. Nothing is allowed until it is named: {@link #DENY_ALL} is the default of every gui.
 *
 * <p>The matrix is keyed by NAME on the click-type axis and by {@link ClickKind} on the action axis,
 * and no constant of either platform enum is ever referenced. A click type this framework does not
 * know - {@code SWAP_OFFHAND} on 1.16+, whatever a future version adds - is denied until the caller
 * names it through {@link Builder#allowIfPresent(String)}, so a modern constant can never break the
 * class on an old server.</p>
 *
 * <p>The builder reads as a sequence of edits, not as a set of independent flags: every call changes
 * what the previous one left, and the last word wins.</p>
 *
 * <pre>{@code
 * ClickPolicy.builder().allowEverything().denyCreativeClone().build();   // everything but cloning
 * ClickPolicy.builder().allowEverything().denyEverything()
 *            .allowCreativeClone().build();                              // cloning and nothing else
 * }</pre>
 */
public final class ClickPolicy {

    /**
     * Every click type a 1.7.10 server already had. A name outside this set is unknown to the
     * framework and denied unless {@link Builder#allowIfPresent(String)} names it.
     */
    private static final Set<String> KNOWN_CLICK_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "LEFT", "SHIFT_LEFT", "RIGHT", "SHIFT_RIGHT",
            "WINDOW_BORDER_LEFT", "WINDOW_BORDER_RIGHT",
            "MIDDLE", "NUMBER_KEY", "DOUBLE_CLICK",
            "DROP", "CONTROL_DROP", "CREATIVE"
    )));

    /** Cancels everything. The gui default, and the answer to any slot nobody opened up. */
    public static final ClickPolicy DENY_ALL = builder().build();

    /**
     * Everything a player needs in order to move an item, without the creative clone, which
     * duplicates a stack, and without the double click, which gathers every matching stack of the
     * whole window at once - the screen's own icons included. Gathering does not duplicate anything;
     * it is denied because no per-slot rule can express a gesture that reaches every slot there is.
     */
    public static final ClickPolicy EDIT_ALL = builder()
            .allowEverything()
            .denyCreativeClone()
            .deny(ClickKind.COLLECT_TO_CURSOR)
            .allowIfPresent("SWAP_OFFHAND")
            .build();

    private final Set<ClickKind> allowedKinds;
    private final Set<String> extraClickTypes;

    private ClickPolicy(Set<ClickKind> allowedKinds, Set<String> extraClickTypes) {
        this.allowedKinds = allowedKinds;
        this.extraClickTypes = extraClickTypes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether this click goes through. Both arguments are the platform enums' {@code name()}.
     *
     * <p>An unknown action, an unknown click type or a kind nobody allowed all answer {@code false}.</p>
     */
    public boolean allows(String clickTypeName, String actionName) {
        return allowsKind(clickTypeName, ClickKind.ofAction(actionName));
    }

    /**
     * Whether this click goes through, when the caller has already decided what the click is trying to
     * do - a shift-move judged as the take or the place it actually is, depending on which way it goes.
     */
    public boolean allowsKind(String clickTypeName, ClickKind kind) {
        if (kind == null || kind == ClickKind.UNKNOWN) {
            return false;
        }
        if (!allowedKinds.contains(kind)) {
            return false;
        }
        return isKnownClickType(clickTypeName);
    }

    /** Whether a drag that touches this policy's slots goes through. */
    public boolean allowsDrag() {
        return allowedKinds.contains(ClickKind.DRAG);
    }

    /** Whether anything at all is allowed - the cheap check before building a click context. */
    public boolean isDenyAll() {
        return allowedKinds.isEmpty();
    }

    private boolean isKnownClickType(String clickTypeName) {
        if (clickTypeName == null) {
            return false;
        }
        return KNOWN_CLICK_TYPES.contains(clickTypeName) || extraClickTypes.contains(clickTypeName);
    }

    public static final class Builder {

        private final Set<ClickKind> allowedKinds = EnumSet.noneOf(ClickKind.class);
        private final Set<String> extraClickTypes = new LinkedHashSet<>();

        private Builder() {

        }

        public Builder allow(ClickKind... kinds) {
            for (ClickKind kind : kinds) {
                if (kind == ClickKind.UNKNOWN) {
                    throw new IllegalArgumentException("UNKNOWN cannot be allowed: it is what this framework "
                            + "answers for a click it does not recognise, and recognising nothing means denying.");
                }
                allowedKinds.add(kind);
            }
            return this;
        }

        public Builder deny(ClickKind... kinds) {
            allowedKinds.removeAll(Arrays.asList(kinds));
            return this;
        }

        public Builder allowTake() {
            return allow(ClickKind.TAKE);
        }

        public Builder denyTake() {
            return deny(ClickKind.TAKE);
        }

        public Builder allowPlace() {
            return allow(ClickKind.PLACE);
        }

        public Builder denyPlace() {
            return deny(ClickKind.PLACE);
        }

        public Builder allowSwap() {
            return allow(ClickKind.SWAP);
        }

        public Builder denySwap() {
            return deny(ClickKind.SWAP);
        }

        public Builder allowDrop() {
            return allow(ClickKind.DROP);
        }

        public Builder denyDrop() {
            return deny(ClickKind.DROP);
        }

        public Builder allowDrag() {
            return allow(ClickKind.DRAG);
        }

        public Builder denyDrag() {
            return deny(ClickKind.DRAG);
        }

        /** The creative middle-click that duplicates a stack. */
        public Builder allowCreativeClone() {
            return allow(ClickKind.CLONE);
        }

        public Builder denyCreativeClone() {
            return deny(ClickKind.CLONE);
        }

        /**
         * Every kind this framework classifies, creative cloning included - the name is not a summary
         * of a chest, it is literal. Narrow it afterwards with the {@code deny} of what is too much.
         */
        public Builder allowEverything() {
            for (ClickKind kind : ClickKind.values()) {
                if (kind != ClickKind.UNKNOWN) {
                    allowedKinds.add(kind);
                }
            }
            return this;
        }

        /**
         * Takes every kind back, leaving the policy as shut as {@link ClickPolicy#DENY_ALL}. The click
         * types named through {@link #allowIfPresent(String)} stay: they are the other axis, and with
         * no kind allowed nothing goes through anyway.
         */
        public Builder denyEverything() {
            allowedKinds.clear();
            return this;
        }

        /**
         * Also honours a click type this framework does not know by name - {@code "SWAP_OFFHAND"} and
         * whatever comes next. On a server that never sends it the entry simply never matches.
         */
        public Builder allowIfPresent(String clickTypeName) {
            if (clickTypeName != null && !clickTypeName.trim().isEmpty()) {
                extraClickTypes.add(clickTypeName.trim());
            }
            return this;
        }

        public ClickPolicy build() {
            Set<ClickKind> kinds = EnumSet.noneOf(ClickKind.class);
            kinds.addAll(allowedKinds);
            return new ClickPolicy(
                    Collections.unmodifiableSet(kinds),
                    Collections.unmodifiableSet(new LinkedHashSet<>(extraClickTypes))
            );
        }

    }

}
