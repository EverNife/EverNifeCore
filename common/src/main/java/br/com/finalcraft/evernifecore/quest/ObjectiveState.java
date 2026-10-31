package br.com.finalcraft.evernifecore.quest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

/** One objective's progress: the count and, for a type that keeps more, its typed state. A Jackson POJO; the engine mutates it under the owner's lock. */
public final class ObjectiveState {

    private long count;
    private boolean done;
    private @Nullable JsonNode data;

    public ObjectiveState() {
        //Jackson
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    /** Whether it reached its goal (or its state held). */
    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    /** What a type keeps beyond the count; null for none. */
    public @Nullable JsonNode getData() {
        return data;
    }

    public void setData(@Nullable JsonNode data) {
        this.data = data;
    }

    ObjectiveState copy() {
        ObjectiveState copy = new ObjectiveState();
        copy.count = count;
        copy.done = done;
        copy.data = data == null ? null : data.deepCopy();
        return copy;
    }

    @Override
    public String toString() {
        return count + (done ? " done" : "");
    }
}
