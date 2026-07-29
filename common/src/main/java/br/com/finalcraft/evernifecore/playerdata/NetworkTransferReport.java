package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.transfer.TransferReport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * What a network transfer moved, what it could not, and the per-collection results. The whole family
 * travels as one operation, so a single {@link TransferReport} could not describe it.
 */
public final class NetworkTransferReport {

    private final String sourceBackend;
    private final String targetBackend;
    private final boolean success;
    private final List<String> moved;
    private final List<String> notCopyable;
    private final List<TransferReport> reports;

    NetworkTransferReport(String sourceBackend, String targetBackend, boolean success,
                          Collection<String> moved, List<String> notCopyable, List<TransferReport> reports) {
        this.sourceBackend = sourceBackend;
        this.targetBackend = targetBackend;
        this.success = success;
        this.moved = Collections.unmodifiableList(new ArrayList<>(moved));
        this.notCopyable = Collections.unmodifiableList(new ArrayList<>(notCopyable));
        this.reports = Collections.unmodifiableList(new ArrayList<>(reports));
    }

    public String getSourceBackend() {
        return sourceBackend;
    }

    public String getTargetBackend() {
        return targetBackend;
    }

    public boolean success() {
        return success;
    }

    /** The collections that travelled, in the order they were copied. */
    public List<String> moved() {
        return moved;
    }

    /**
     * Claimed on the source backend but copied by nothing, because whoever claimed them recorded no
     * descriptor. Naming them is the point: silence here reads as "everything moved".
     */
    public List<String> notCopyable() {
        return notCopyable;
    }

    /** One report per collection that was attempted, in the same order as {@link #moved()}. */
    public List<TransferReport> reports() {
        return reports;
    }

    public long totalEntities() {
        long total = 0;
        for (TransferReport report : reports) {
            total += report.totalEntities();
        }
        return total;
    }
}
