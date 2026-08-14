package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.io.File;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * An audit log of its own file: UTF-8, appended, one flush per line, and messages formatted by the
 * same {@link ECLogFormat} the console logger uses.
 *
 * <pre>
 * FCFileLogger trades = FCFileLogger.of(pluginData, "trades.log").withTimestamps().build();
 * trades.log("{} bought {} for {}", player, item, price);
 * </pre>
 *
 * <p>{@link #of(ECPluginData, String)} writes under {@code &lt;dataFolder&gt;/logs/} and hands the
 * handle to the plugin, which closes it on shutdown - the other two ports have no owner, so whoever
 * opened them closes them (they are {@link AutoCloseable}, so try-with-resources does).</p>
 *
 * <p>It never throws and never stops the caller: a file that cannot be opened or written degrades to
 * a no-op after one {@code severe}, and {@link #close()} may be called as often as one likes.</p>
 */
public class FCFileLogger implements AutoCloseable {

    /** What {@link Builder#withTimestamps()} puts in front of every line. */
    public static final String DEFAULT_TIMESTAMP_PATTERN = "[yyyy-MM-dd HH:mm:ss] ";

    private static final String ARCHIVE_DATE_PATTERN = "yyyy-MM-dd-HH-mm";
    private static final int MAX_ARCHIVE_ATTEMPTS = 10000;

    private final File file;
    private final ECPluginData owner;
    private final SimpleDateFormat timestamps;

    private Writer writer;
    private boolean closed;

    FCFileLogger(Builder builder) {
        this.file = builder.file;
        this.owner = builder.owner;
        this.timestamps = builder.timestampPattern == null ? null : new SimpleDateFormat(builder.timestampPattern);

        if (builder.rolling) {
            //before the handle exists: the point of the roll is that this run starts on an empty file
            rollAside(builder.rollPattern != null ? builder.rollPattern : defaultArchivePattern(file));
        }
        this.writer = openForAppend();
    }

    // ------------------------------------------------------------------
    //  Ports
    // ------------------------------------------------------------------

    /** A log file named exactly {@code logFile}, owned by nobody. */
    public static Builder of(File logFile) {
        return new Builder(logFile, null);
    }

    /** {@code name} inside {@code rootFolder}, owned by nobody. */
    public static Builder of(File rootFolder, String name) {
        return new Builder(new File(rootFolder, name), null);
    }

    /**
     * {@code name} under the plugin's {@code logs/} folder. The handle is tracked by {@code owner}
     * and closed by its shutdown, so a plugin that forgets to close still does not leak it.
     */
    public static Builder of(ECPluginData owner, String name) {
        return new Builder(new File(new File(owner.getMetaInfo().getDataFolder(), "logs"), name), owner);
    }

    // ------------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------------

    /** One line, formatted like any other EverNifeCore log message. Silent once the file went bad. */
    public synchronized void log(String message, Object... params) {
        if (writer == null) {
            return;
        }
        String line = ECLogFormat.format(message, params);
        if (timestamps != null) {
            line = timestamps.format(new Date()) + line;
        }
        try {
            writer.write(line);
            writer.write(System.lineSeparator());
            //an audit line nobody flushed is an audit line nobody has, and this file exists to be read
            //while the server is still running
            writer.flush();
        } catch (Exception failure) {
            report("Could not write to the log file [{}]; it stops being written to.", failure);
            releaseWriter();
        }
    }

    /**
     * Releases the file. Calling it again does nothing, and so does every {@link #log} after it - a
     * shutdown that closes a handle the plugin already closed is the normal case, not an error.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        releaseWriter();
        if (owner != null) {
            owner.untrackOpenFileLogger(this);
        }
    }

    /** Whether {@link #close()} has not run yet. A file that went bad is still open in this sense. */
    public synchronized boolean isOpen() {
        return !closed;
    }

    /** The file being written to - the one the ports resolved, not the one the caller asked for. */
    public File getFile() {
        return file;
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    private Writer openForAppend() {
        try {
            File folder = file.getParentFile();
            if (folder != null) {
                folder.mkdirs();
            }
            return Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) {
            report("Could not open the log file [{}]; every line meant for it is dropped.", failure);
            return null;
        }
    }

    /**
     * Renames the file already at the target out of the way, so this run starts on an empty one.
     * A rename that does not happen costs the roll, never the logger: the run then appends to what
     * was already there.
     */
    private void rollAside(String pattern) {
        if (!file.isFile()) {
            return; //nothing was there, so there is nothing to archive
        }
        File archived = freeArchiveName(pattern);
        if (archived == null || !file.renameTo(archived)) {
            report("Could not roll [{}] aside; this run appends to the file that was already there.");
        }
    }

    /** The first name the pattern yields that no file holds, or {@code null} if there is none. */
    private File freeArchiveName(String pattern) {
        String resolved = pattern.replace("{date}", new SimpleDateFormat(ARCHIVE_DATE_PATTERN).format(new Date()));
        boolean counted = resolved.contains("{n}");
        File folder = file.getParentFile();

        for (int n = 1; n <= MAX_ARCHIVE_ATTEMPTS; n++) {
            //a pattern that never asked for a counter still gets one the moment it would overwrite
            String name = counted ? resolved.replace("{n}", String.valueOf(n))
                    : (n == 1 ? resolved : withCounter(resolved, n));
            File candidate = new File(folder, name);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return null;
    }

    /** {@code audit.log} + 2 -> {@code audit-2.log}; a name with no extension takes the suffix at the end. */
    private static String withCounter(String name, int n) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name + "-" + n : name.substring(0, dot) + "-" + n + name.substring(dot);
    }

    /** {@code latest.log} -> {@code {date}-{n}.log} - the archived name keeps only the extension. */
    private static String defaultArchivePattern(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return "{date}-{n}" + (dot <= 0 ? "" : name.substring(dot));
    }

    private void releaseWriter() {
        Writer releasing = writer;
        writer = null;
        if (releasing == null) {
            return;
        }
        try {
            releasing.close();
        } catch (Exception ignoredOnPurpose) {
            //the handle is gone either way, and a logger that throws while being closed is a logger
            //that takes the shutdown down with it
        }
    }

    /** One line on the owner's console log - or the core's, for a file nobody owns. */
    private void report(String message) {
        logger().severe(message, file.getAbsolutePath());
    }

    private void report(String message, Exception failure) {
        logger().severe(message, file.getAbsolutePath(), failure);
    }

    private ECLogger logger() {
        return owner != null ? owner.getLog() : EverNifeCore.getLog();
    }

    // ------------------------------------------------------------------
    //  Builder
    // ------------------------------------------------------------------

    /** What every {@code of(...)} hands back. Nothing touches the disk until {@link #build()}. */
    public static final class Builder {

        private final File file;
        private final ECPluginData owner;
        private String timestampPattern;
        private boolean rolling;
        private String rollPattern;

        private Builder(File file, ECPluginData owner) {
            this.file = file;
            this.owner = owner;
        }

        /** Stamps every line with {@link FCFileLogger#DEFAULT_TIMESTAMP_PATTERN}. Off by default. */
        public Builder withTimestamps() {
            return withTimestamps(DEFAULT_TIMESTAMP_PATTERN);
        }

        /**
         * Stamps every line with {@code pattern}, a {@link SimpleDateFormat} pattern used whole - it
         * carries its own brackets and trailing space, since it IS the prefix.
         */
        public Builder withTimestamps(String pattern) {
            this.timestampPattern = pattern;
            return this;
        }

        /**
         * Archives the file already at the target before opening, the way a server treats
         * {@code latest.log}. Off by default. The archived name is {@code {date}-{n}.<ext>}, with
         * {@code {date}} as {@code yyyy-MM-dd-HH-mm} and {@code {n}} counting from 1.
         */
        public Builder rollOnOpen() {
            this.rolling = true;
            this.rollPattern = null;
            return this;
        }

        /**
         * As {@link #rollOnOpen()} with a name of your own, over the tokens {@code {date}} and
         * {@code {n}}. A pattern that resolves onto an existing file is counted up either way: the
         * roll never overwrites.
         */
        public Builder rollOnOpen(String pattern) {
            this.rolling = true;
            this.rollPattern = pattern;
            return this;
        }

        /** Opens the file - and, on the port that has an owner, hands it the handle to close. */
        public FCFileLogger build() {
            FCFileLogger logger = new FCFileLogger(this);
            if (owner != null) {
                owner.trackOpenFileLogger(logger);
            }
            return logger;
        }
    }
}
