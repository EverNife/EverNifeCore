package br.com.finalcraft.evernifecore.quest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** What one member of a non-player owner did for a quest instance: a count per objective, and when they joined and left. A Jackson POJO. */
public final class QuestContribution {

    private Map<String, Long> counts = new LinkedHashMap<>();
    private long joinedAt;
    private long leftAt;

    public QuestContribution() {
        //Jackson
    }

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Long> counts) {
        this.counts = counts == null ? new LinkedHashMap<String, Long>() : new LinkedHashMap<>(counts);
    }

    /** The count under {@code objectiveId}; 0 when they did nothing there. */
    public long getCount(String objectiveId) {
        Long count = counts.get(objectiveId);
        return count == null ? 0 : count;
    }

    /** Every objective summed. */
    @JsonIgnore
    public long getTotal() {
        long total = 0;
        for (long count : counts.values()) {
            total += count;
        }
        return total;
    }

    void add(String objectiveId, long delta) {
        counts.merge(objectiveId, delta, Long::sum);
    }

    /** Epoch millis of their first contribution; 0 when unknown. */
    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    /** Epoch millis they left the owner; 0 while they belong to it. */
    public long getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(long leftAt) {
        this.leftAt = leftAt;
    }

    QuestContribution copy() {
        QuestContribution copy = new QuestContribution();
        copy.counts = new LinkedHashMap<>(counts);
        copy.joinedAt = joinedAt;
        copy.leftAt = leftAt;
        return copy;
    }

    @Override
    public String toString() {
        return Collections.unmodifiableMap(counts).toString();
    }
}
