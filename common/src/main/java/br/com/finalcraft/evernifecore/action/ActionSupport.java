package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Which core sources this platform raises, and which of them have a pre-outcome point (a cancellable {@code Pre}).
 * Declared once by the platform at boot; until then every source counts as raised, with a {@code Pre}. A plugin's own
 * sources are its to raise and always count as raised.
 */
public final class ActionSupport {

    private static volatile Declaration declared = new Declaration("an undeclared platform", null, null, true, Collections.emptySet());

    private ActionSupport() {
    }

    /**
     * Platform: what it raises. Every flag that needs a core source outside {@code post} then says in its owner's log
     * that it has no effect here.
     *
     * @param platform            how a message names this server, e.g. {@code "Paper 1.21.1"} or {@code "Hytale 0.7.2"}
     * @param post                the core sources raised; null for every one
     * @param pre                 the core sources with a cancellable {@code Pre}; null for every one raised
     * @param nativeCommandSource whether the platform raises the command source from its own command event, so the
     *                            core's command framework must not raise it a second time
     * @param neverClassified     the actor kinds no context of this platform ever carries (Hytale has no fake players): a
     *                            flag only about them says in the boot log that it never matches; null for none
     */
    public static void declare(String platform, @Nullable Collection<EventSource> post, @Nullable Collection<EventSource> pre,
                               boolean nativeCommandSource, @Nullable Collection<ECEntityType> neverClassified) {
        declared = new Declaration(Objects.requireNonNull(platform, "platform"), copy(post), copy(pre), nativeCommandSource,
                neverClassified == null || neverClassified.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(neverClassified)));
        FlagRegistry.get().warnFlagsWithoutEffect();
    }

    private static @Nullable Set<EventSource> copy(@Nullable Collection<EventSource> sources) {
        return sources == null ? null : Collections.unmodifiableSet(new HashSet<>(sources));
    }

    /** How messages name this server. */
    public static String getPlatform() {
        return declared.platform;
    }

    /** Whether this platform raises {@code source}; a plugin's own source always counts as raised. */
    public static boolean isServed(EventSource source) {
        Declaration now = declared;
        return !source.getOwner().equals(CoreFlags.OWNER) || now.post == null || now.post.contains(source);
    }

    /** Whether this platform posts the {@code Pre} of {@code source}; false for a source with no action event. */
    public static boolean hasPre(EventSource source) {
        Declaration now = declared;
        return ActionEvents.of(source) != null && isServed(source) && (now.pre == null || now.pre.contains(source));
    }

    /** Whether no actor this platform builds is of the kind {@code entry} names ({@link ECEntityType#getWord()}); false for any other entry. */
    public static boolean neverClassifies(String entry) {
        for (ECEntityType kind : declared.neverClassified) {
            if (kind.getWord().equals(entry)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the platform's own command event raises the command source - the command framework then stays out of it. */
    public static boolean isNativeCommandSource() {
        return declared.nativeCommandSource;
    }

    /**
     * Fails loud for a definition that needs {@code source} - and its {@code Pre}, when {@code needsPre} - on a platform
     * that never raises it.
     *
     * @param what names the definition, e.g. {@code "The objective 'fish' of finalquests:lake-day"}
     * @throws UnsupportedSourceException naming the definition, the source and this server, and what to do about it
     */
    public static void requireServed(EventSource source, boolean needsPre, String what) {
        String platform = getPlatform();
        if (!isServed(source)) {
            throw new UnsupportedSourceException(source, what + " needs the " + source + " action source, which this server ("
                    + platform + ") never raises. Remove it here, or keep it for the platforms that raise that source.");
        }
        if (needsPre && !hasPre(source)) {
            throw new UnsupportedSourceException(source, what + " needs to cancel " + source + " before its outcome, but this server ("
                    + platform + ") raises that source only after the outcome. Drop the need to cancel it, or act on it after the fact.");
        }
    }

    private static final class Declaration {
        final String platform;
        final @Nullable Set<EventSource> post;
        final @Nullable Set<EventSource> pre;
        final boolean nativeCommandSource;
        final Set<ECEntityType> neverClassified;

        Declaration(String platform, @Nullable Set<EventSource> post, @Nullable Set<EventSource> pre, boolean nativeCommandSource,
                    Set<ECEntityType> neverClassified) {
            this.platform = platform;
            this.post = post;
            this.pre = pre;
            this.nativeCommandSource = nativeCommandSource;
            this.neverClassified = neverClassified;
        }
    }
}
