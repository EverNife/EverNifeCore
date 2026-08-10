package br.com.finalcraft.evernifecore.pageviewer.nav;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.CommandMessageContext;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.util.collection.SelfExpiringMap;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** The pages open in memory, one per reader per viewer, for the ten minutes after their last click. */
public final class PageSessionManager {

    private static final long TTL = TimeUnit.MINUTES.toMillis(10);

    // Renewed on use, not on creation: SelfExpiringMap stamps the deadline on put and get does not
    // renew it, so every navigation puts the session back. Held as the concrete type - a reader
    // that has to be exact sweeps it first - and every access below takes its monitor, which is
    // what a map that is not thread-safe needs from the one place that owns it.
    private static final SelfExpiringMap<UUID, PageSession> SESSIONS = new SelfExpiringMap<>(TTL);

    private PageSessionManager() {
    }

    /**
     * The reader's session on {@code viewer}, opened now or renewed if it was already open. Answers
     * {@code null} for a reader with no identity of their own - a session belongs to somebody, and
     * the console is nobody.
     */
    public static @Nullable PageSession openOrRenew(@Nullable FCommandSender reader,
                                                    PageViewer<?> viewer,
                                                    CommandMessageContext origin) {
        if (reader == null || reader.getUniqueId() == null) {
            return null;
        }

        //Derived from the pair rather than drawn at random: re-sending the same page then lands on
        //the reader's own entry instead of opening a second one beside it.
        UUID handle = handleOf(viewer, reader.getUniqueId());
        PageSession session = new PageSession(handle, reader.getUniqueId(), viewer, origin);
        synchronized (SESSIONS) {
            SESSIONS.put(handle, session);
        }
        return session;
    }

    /** The session {@code handle} names, or {@code null} when it expired or was never a handle at all. */
    public static @Nullable PageSession find(String handle) {
        UUID uuid = uuidOf(handle);
        if (uuid == null) {
            return null;
        }
        synchronized (SESSIONS) {
            return SESSIONS.get(uuid);
        }
    }

    /** Puts the deadline ten more minutes away, which is what using a session means. */
    public static void renew(PageSession session) {
        synchronized (SESSIONS) {
            SESSIONS.put(session.getHandle(), session);
        }
    }

    /** How many sessions are alive - what tells a strategy that allocates nothing from one that does. */
    public static int openSessions() {
        synchronized (SESSIONS) {
            //counted after the sweep: an expired session is gone, and a count that includes it is
            //the difference between "this strategy holds nothing" and a leak that is not there
            SESSIONS.sweepExpired();
            return SESSIONS.size();
        }
    }

    /** Drops every open session, so one test's readers are never another's. */
    public static void clear() {
        synchronized (SESSIONS) {
            SESSIONS.clear();
        }
    }

    private static UUID handleOf(PageViewer<?> viewer, UUID reader) {
        return UUID.nameUUIDFromBytes((viewer.getInstanceId() + ":" + reader).getBytes(StandardCharsets.UTF_8));
    }

    private static @Nullable UUID uuidOf(String handle) {
        try {
            return UUID.fromString(handle);
        } catch (IllegalArgumentException notAHandle) {
            return null;
        }
    }
}
