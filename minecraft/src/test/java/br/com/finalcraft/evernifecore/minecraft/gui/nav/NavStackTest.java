package br.com.finalcraft.evernifecore.minecraft.gui.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The back stack: as deep as it was pushed, and able to hand a value down as it unwinds. */
class NavStackTest {

    @Test
    void poppingRevealsTheScreenUnderneath() {
        NavStack<String> stack = new NavStack<>();
        stack.push("menu");
        stack.push("shop");
        stack.push("confirm");

        assertEquals(3, stack.size());
        assertEquals("confirm", stack.peek());

        NavResult<String> result = stack.pop();

        assertEquals("shop", result.getScreen());
        assertTrue(result.hasScreen());
        assertNull(result.getValue(), "a plain pop carries nothing back");
        assertEquals(2, stack.size());
    }

    @Test
    void popWithHandsAValueToTheScreenThatComesBack() {
        NavStack<String> stack = new NavStack<>();
        stack.push("menu");
        stack.push("amount");

        NavResult<String> result = stack.popWith(64);

        assertEquals("menu", result.getScreen());
        assertEquals(64, result.getValue());
        assertEquals(64, result.getValue(Integer.class));
        assertNull(result.getValue(String.class), "a value of another type reads as absent, not as a cast error");
    }

    @Test
    void unwindingPastTheLastScreenStillCarriesTheValue() {
        NavStack<String> stack = new NavStack<>();
        stack.push("only");

        assertFalse(stack.pop().hasScreen());
        assertTrue(stack.isEmpty());

        NavResult<String> beyond = stack.popWith("done");
        assertNull(beyond.getScreen());
        assertEquals("done", beyond.getValue());
    }

    @Test
    void aNullScreenIsRefusedBecauseItWouldReadAsAnEmptyStack() {
        NavStack<String> stack = new NavStack<>();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> stack.push(null));
        assertTrue(error.getMessage().contains("empty stack"), error.getMessage());
    }

    @Test
    void clearForgetsEverything() {
        NavStack<String> stack = new NavStack<>();
        stack.push("a");
        stack.push("b");

        stack.clear();

        assertTrue(stack.isEmpty());
        assertNull(stack.peek());
    }

}
