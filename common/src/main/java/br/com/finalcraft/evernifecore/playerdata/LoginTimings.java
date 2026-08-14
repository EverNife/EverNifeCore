package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stopwatch of one login, and the breakdown it prints when that login was slow.
 *
 * <p>The login holds the player's connection while it resolves the base row and every section that
 * loads at login, so a slow backend or one heavy section shows up to the admin as "EverNifeCore is
 * taking forever". This measures each step and names it: which section, whose plugin, which backend.
 * Without that, the framework in front takes the blame for the plugin behind it.</p>
 *
 * <p>Off unless a login crosses the configured threshold, so the normal case pays only a couple of
 * {@link System#nanoTime()} reads and a list append per section.</p>
 */
final class LoginTimings {

    /** What a login gets while the report is turned off: every entry point below is a no-op. */
    static final LoginTimings DISABLED = new LoginTimings(null, null, 0L);

    private final UUID uuid;
    private final String playerName;
    /** Duration above which the breakdown is printed; {@code <= 0} means the report is off. */
    private final long thresholdNanos;
    private final long startNanos;
    private final List<Phase> phases = new CopyOnWriteArrayList<>();
    private final List<Entry> sections = new CopyOnWriteArrayList<>();
    private final AtomicBoolean printed = new AtomicBoolean();

    private LoginTimings(UUID uuid, String playerName, long thresholdNanos) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.thresholdNanos = thresholdNanos;
        this.startNanos = System.nanoTime();
    }

    static LoginTimings start(UUID uuid, String playerName, int thresholdSeconds) {
        return thresholdSeconds <= 0
                ? DISABLED
                : new LoginTimings(uuid, playerName, TimeUnit.SECONDS.toNanos(thresholdSeconds));
    }

    boolean isEnabled() {
        return thresholdNanos > 0;
    }

    /** Records a named step of the login pipeline that is not a section load. */
    void phase(String name, long phaseStartNanos) {
        if (!isEnabled()) return;
        phases.add(new Phase(name, System.nanoTime() - phaseStartNanos));
    }

    /**
     * Times one section load without changing how it completes: the returned future settles exactly
     * like {@code load}, so a failure still propagates to the login that is waiting on it.
     */
    <T> CompletableFuture<T> track(PDSectionBinding<?> binding, long loadStartNanos, CompletableFuture<T> load) {
        if (!isEnabled()) return load;
        Entry entry = new Entry(binding.getConfiguration().getSectionId(),
                binding.getConfiguration().getPluginData(), binding.getBackendName(), false, loadStartNanos);
        sections.add(entry);
        return load.whenComplete((value, failure) -> entry.finish());
    }

    /** @see #track(PDSectionBinding, long, CompletableFuture) */
    <T> CompletableFuture<T> trackAccount(AccountSectionBinding<?> binding, long loadStartNanos,
                                          CompletableFuture<T> load) {
        if (!isEnabled()) return load;
        Entry entry = new Entry(binding.getConfiguration().getSectionId(),
                binding.getConfiguration().getPluginData(), binding.getBackendName(), true, loadStartNanos);
        sections.add(entry);
        return load.whenComplete((value, failure) -> entry.finish());
    }

    /** Prints the breakdown if this login crossed the threshold. */
    void reportIfSlow() {
        if (!isEnabled()) return;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed < thresholdNanos) return;
        print(elapsed, false);
    }

    /**
     * Prints the breakdown of a login that ran out of time and was denied - the case where naming
     * the culprit matters most, and where the sections still in flight are the evidence.
     */
    void reportTimeout() {
        if (!isEnabled()) return;
        print(System.nanoTime() - startNanos, true);
    }

    private void print(long elapsedNanos, boolean timedOut) {
        if (!printed.compareAndSet(false, true)) return;
        try {
            for (String line : format(elapsedNanos, timedOut)) {
                //the line carries section names a plugin chose; a '{}' in one is text, not a placeholder
                EverNifeCore.getLog().warning("{}", line);
            }
        } catch (Throwable formattingFailure) {
            //a diagnostic must never be what breaks a login
            EverNifeCore.getLog().warning("Could not report the slow login of [{}]: {}", playerName, formattingFailure);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Formatting - plain ASCII columns (console-safe, no color codes) with a couple of markers
    // -----------------------------------------------------------------------------------------------------------------------------//

    List<String> format(long elapsedNanos, boolean timedOut) {
        List<Entry> rows = new ArrayList<>(sections);
        Collections.sort(rows, Comparator.comparingLong(Entry::elapsedNanos).reversed());

        String[] headers = {"#", "SECTION", "PLUGIN", "AUTHOR", "BACKEND", "TIME"};
        List<String[]> cells = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Entry entry = rows.get(i);
            cells.add(new String[]{
                    String.valueOf(i + 1),
                    entry.sectionId,
                    entry.pluginName(),
                    entry.author(),
                    entry.backendName,
                    seconds(entry.elapsedNanos())});
        }
        int[] widths = new int[headers.length];
        for (int column = 0; column < headers.length; column++) {
            widths[column] = headers[column].length();
            for (String[] row : cells) {
                widths[column] = Math.max(widths[column], row[column].length());
            }
        }

        List<String> body = new ArrayList<>();
        body.add(row(headers, widths));
        for (int i = 0; i < cells.size(); i++) {
            body.add(row(cells.get(i), widths) + marker(rows.get(i), i == 0));
        }
        body.add("");
        body.add(phaseLine());
        body.add(slowestLine(rows));
        if (timedOut) {
            body.add("This login was DENIED: storage did not answer within"
                    + " 'playerdata.login-timeout-seconds'.");
        }

        String title = "EverNifeCore | slow login: " + playerName + " [" + uuid + "] took "
                + seconds(elapsedNanos) + " (reporting above " + seconds(thresholdNanos) + ")";
        //Only the LEFT edge is drawn. A closing edge would have to be padded to a column, and the
        //emoji markers are one Java char but two terminal columns - the frame would drift exactly on
        //the lines that carry the interesting information.
        int width = title.length() + 8;
        for (String line : body) {
            width = Math.max(width, line.length() + 3);
        }

        List<String> lines = new ArrayList<>();
        lines.add("+-- " + title + " " + repeat('-', width - title.length() - 5));
        for (String line : body) {
            lines.add(trimTrailing("|  " + line));
        }
        lines.add("+" + repeat('-', width - 1));
        return lines;
    }

    private String phaseLine() {
        StringBuilder line = new StringBuilder();
        for (Phase phase : phases) {
            if (line.length() > 0) line.append(" | ");
            line.append(phase.name).append(' ').append(seconds(phase.elapsedNanos));
        }
        return line.length() == 0 ? "(no phase recorded)" : line.toString();
    }

    private String slowestLine(List<Entry> rows) {
        if (rows.isEmpty()) {
            return "No section load was involved - the time went to the player row or the account.";
        }
        Entry slowest = rows.get(0);
        return "🐢 slowest: " + slowest.sectionId + " (" + slowest.pluginName()
                + ", by " + slowest.author() + ") on backend '" + slowest.backendName + "'";
    }

    private static String marker(Entry entry, boolean slowest) {
        if (!entry.isFinished()) return "  ⏳ still loading";
        String suffix = entry.account ? "  (account)" : "";
        return slowest ? suffix + "  🐢" : suffix;
    }

    private static String row(String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < cells.length; column++) {
            if (column > 0) line.append("  ");
            line.append(cells[column]);
            line.append(repeat(' ', widths[column] - cells[column].length()));
        }
        return line.toString();
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0D);
    }

    /** The last column is padded to its width; the console has no use for that padding. */
    private static String trimTrailing(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') end--;
        return line.substring(0, end);
    }

    private static String repeat(char character, int amount) {
        if (amount <= 0) return "";
        char[] filled = new char[amount];
        java.util.Arrays.fill(filled, character);
        return new String(filled);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//

    private static final class Phase {
        private final String name;
        private final long elapsedNanos;

        private Phase(String name, long elapsedNanos) {
            this.name = name;
            this.elapsedNanos = elapsedNanos;
        }
    }

    /** One section load: still open until {@link #finish()}, which is what marks a pending one. */
    private static final class Entry {
        private final String sectionId;
        private final ECPluginData pluginData;
        private final String backendName;
        private final boolean account;
        private final long startNanos;
        private volatile long endNanos = -1L;

        private Entry(String sectionId, ECPluginData pluginData, String backendName,
                      boolean account, long startNanos) {
            this.sectionId = sectionId;
            this.pluginData = pluginData;
            this.backendName = backendName;
            this.account = account;
            this.startNanos = startNanos;
        }

        private void finish() {
            this.endNanos = System.nanoTime();
        }

        private boolean isFinished() {
            return endNanos >= 0;
        }

        /** How long the load took, or how long it has been running when it never finished. */
        private long elapsedNanos() {
            return (isFinished() ? endNanos : System.nanoTime()) - startNanos;
        }

        private String pluginName() {
            return PlayerController.pluginNameOf(pluginData);
        }

        private String author() {
            if (pluginData == null) return "-";
            IPluginMetaInfo metaInfo = pluginData.getMetaInfo();
            String author = metaInfo == null ? null : metaInfo.getAuthor();
            return author == null || author.isEmpty() ? "-" : author;
        }
    }
}
