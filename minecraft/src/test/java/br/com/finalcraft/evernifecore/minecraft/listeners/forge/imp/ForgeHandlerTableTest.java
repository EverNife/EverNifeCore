package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table the Mohist adapter posts from: a registration's handlers leave it, and a post that is already
 * iterating is not disturbed by a removal made meanwhile.
 */
class ForgeHandlerTableTest {

    private static Map<Class<?>, List<Consumer<?>>> one(Class<?> type, Consumer<?> handler) {
        Map<Class<?>, List<Consumer<?>>> map = new LinkedHashMap<>();
        map.put(type, new ArrayList<>(Collections.singletonList(handler)));
        return map;
    }

    @Test
    void removedHandlersStopReceivingAndTheOthersStay() {
        ForgeHandlerTable table = new ForgeHandlerTable();
        List<String> received = new ArrayList<>();
        Consumer<Object> mine = event -> received.add("mine");
        Consumer<Object> theirs = event -> received.add("theirs");
        table.add(CharSequence.class, mine);
        table.add(Object.class, theirs);

        table.remove(one(CharSequence.class, mine));

        for (Consumer consumer : table.handlersFor(String.class)) {
            consumer.accept("event");
        }
        assertEquals(Collections.singletonList("theirs"), received);
    }

    @Test
    void aRemovalDuringAPostDoesNotBreakThePost() {
        ForgeHandlerTable table = new ForgeHandlerTable();
        List<String> received = new ArrayList<>();
        Consumer<Object> second = event -> received.add("second");
        Consumer<Object> first = event -> {
            received.add("first");
            table.remove(one(Object.class, second));
        };
        table.add(Object.class, first);
        table.add(Object.class, second);

        for (Consumer consumer : table.handlersFor(String.class)) {
            consumer.accept("event");
        }

        assertEquals(List.of("first", "second"), received, "the post that started keeps its table");
        assertTrue(table.handlersFor(String.class).size() == 1, "the next post no longer sees it");
    }
}
