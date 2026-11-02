package br.com.finalcraft.evernifecore.quest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One owner's instance of one quest. A Jackson POJO stored as is (in the plugin's {@link QuestSection} for a player,
 * in its storage otherwise); the engine is its only writer and mutates it under the owner's lock. What events and
 * readers get is a {@link #copy() snapshot}. Times are epoch millis, 0 for "never".
 */
public final class QuestState {

    private String questId;
    private QuestStatus status = QuestStatus.AVAILABLE;
    private int definitionVersion;
    private @Nullable String stageId;
    private long stageStartedAt;
    private boolean stageClosed;
    private Map<String, ObjectiveState> objectives = new LinkedHashMap<>();
    private int times;
    private long startedAt;
    private long completedAt;
    private long lastCompletedAt;
    private Map<String, String> params = new LinkedHashMap<>();
    private @Nullable FailCause failCause;
    private Map<String, QuestContribution> contributions = new LinkedHashMap<>();
    private @Nullable String lastActor;

    public QuestState() {
        //Jackson
    }

    QuestState(QuestId questId) {
        this.questId = questId.toString();
    }

    /** {@code owner:name} of the quest. */
    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    /** The definition version the instance follows. */
    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(int definitionVersion) {
        this.definitionVersion = definitionVersion;
    }

    /** The current stage while active; null otherwise. */
    public @Nullable String getStageId() {
        return stageId;
    }

    public void setStageId(@Nullable String stageId) {
        this.stageId = stageId;
    }

    public long getStageStartedAt() {
        return stageStartedAt;
    }

    public void setStageStartedAt(long stageStartedAt) {
        this.stageStartedAt = stageStartedAt;
    }

    /**
     * Whether the current stage already closed - its on-complete effects ran - and only the move after it is pending (a
     * cancelled completion is retried without closing the stage again).
     */
    public boolean isStageClosed() {
        return stageClosed;
    }

    public void setStageClosed(boolean stageClosed) {
        this.stageClosed = stageClosed;
    }

    /** By objective id; an objective with no entry has made no progress. */
    public Map<String, ObjectiveState> getObjectives() {
        return objectives;
    }

    public void setObjectives(Map<String, ObjectiveState> objectives) {
        this.objectives = objectives == null ? new LinkedHashMap<String, ObjectiveState>() : objectives;
    }

    /** The objective's state, created empty. */
    ObjectiveState objective(String objectiveId) {
        return objectives.computeIfAbsent(objectiveId, id -> new ObjectiveState());
    }

    /** The count of {@code objectiveId}; 0 with no progress. */
    public long getCount(String objectiveId) {
        ObjectiveState state = objectives.get(objectiveId);
        return state == null ? 0 : state.getCount();
    }

    /** How many times the owner completed it. */
    public int getTimes() {
        return times;
    }

    public void setTimes(int times) {
        this.times = times;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public long getLastCompletedAt() {
        return lastCompletedAt;
    }

    public void setLastCompletedAt(long lastCompletedAt) {
        this.lastCompletedAt = lastCompletedAt;
    }

    /** The param values frozen at the start. */
    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? new LinkedHashMap<String, String>() : params;
    }

    public @Nullable FailCause getFailCause() {
        return failCause;
    }

    public void setFailCause(@Nullable FailCause failCause) {
        this.failCause = failCause;
    }

    /** By member uuid, for an owner that is not a player; empty for a player. */
    public Map<String, QuestContribution> getContributions() {
        return contributions;
    }

    public void setContributions(Map<String, QuestContribution> contributions) {
        this.contributions = contributions == null ? new LinkedHashMap<String, QuestContribution>() : contributions;
    }

    /** The member whose action closed the latest objective (owner not a player); null when none did. */
    @JsonIgnore
    public @Nullable UUID getLastActorId() {
        return lastActor == null ? null : UUID.fromString(lastActor);
    }

    public @Nullable String getLastActor() {
        return lastActor;
    }

    public void setLastActor(@Nullable String lastActor) {
        this.lastActor = lastActor;
    }

    /** A deep copy: what an event or a reader holds. */
    public QuestState copy() {
        QuestState copy = new QuestState();
        copy.questId = questId;
        copy.status = status;
        copy.definitionVersion = definitionVersion;
        copy.stageId = stageId;
        copy.stageStartedAt = stageStartedAt;
        copy.stageClosed = stageClosed;
        for (Map.Entry<String, ObjectiveState> entry : objectives.entrySet()) {
            copy.objectives.put(entry.getKey(), entry.getValue().copy());
        }
        copy.times = times;
        copy.startedAt = startedAt;
        copy.completedAt = completedAt;
        copy.lastCompletedAt = lastCompletedAt;
        copy.params = new LinkedHashMap<>(params);
        copy.failCause = failCause;
        for (Map.Entry<String, QuestContribution> entry : contributions.entrySet()) {
            copy.contributions.put(entry.getKey(), entry.getValue().copy());
        }
        copy.lastActor = lastActor;
        return copy;
    }

    @Override
    public String toString() {
        return questId + " " + status + (stageId == null ? "" : " @" + stageId) + " " + objectives;
    }
}
