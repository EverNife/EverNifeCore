package br.com.finalcraft.evernifecore.conformance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * {@code <dataFolder>/reports/<runId>.ndjson}: the same events as the markers, one per line. An I/O failure costs
 * the report and nothing else - the run goes on and its markers still reach the log.
 */
final class NdjsonReport {

    private final String relativePath;
    private Writer writer;

    NdjsonReport(File dataFolder, String runId) {
        this.relativePath = "reports/" + runId + ".ndjson";
        try {
            File file = new File(dataFolder, relativePath);
            file.getParentFile().mkdirs();
            this.writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            this.writer = null;
        }
    }

    /** Relative to the data folder, or null once a write failed - the value {@code suite_end.report} carries. */
    String getRelativePath() {
        return writer == null ? null : relativePath;
    }

    void write(String json) {
        if (writer == null) return;
        try {
            writer.write(json);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            close();
        }
    }

    void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
            //the lines already flushed are all the report will ever hold
        }
        writer = null;
    }
}
