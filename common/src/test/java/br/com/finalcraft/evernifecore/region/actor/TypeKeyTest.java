package br.com.finalcraft.evernifecore.region.actor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeKeyTest {

    @Test
    void parseSplitsAtTheFirstColon() {
        TypeKey key = TypeKey.parse("minecraft:creeper");
        assertEquals("minecraft", key.getNamespace());
        assertEquals("creeper", key.getPath());
        assertEquals("minecraft:creeper", key.toString());
        assertEquals("a:b:c", TypeKey.parse("a:b:c").getNamespace() + ":" + TypeKey.parse("a:b:c").getPath());
    }

    @Test
    void equalKeysAreTheSameText() {
        assertEquals(TypeKey.of("minecraft", "creeper"), TypeKey.parse("minecraft:creeper"));
        assertEquals(TypeKey.of("minecraft", "creeper").hashCode(), TypeKey.parse("minecraft:creeper").hashCode());
        assertNotEquals(TypeKey.parse("minecraft:creeper"), TypeKey.parse("minecraft:zombie"));
    }

    @Test
    void matchesGlobsOnEitherSideIgnoringCase() {
        TypeKey creeper = TypeKey.parse("minecraft:creeper");
        assertTrue(creeper.matches("minecraft:creeper"));
        assertTrue(creeper.matches("MINECRAFT:Creeper"));
        assertTrue(creeper.matches("minecraft:*"));
        assertTrue(creeper.matches("*:creeper"));
        assertTrue(TypeKey.parse("hytale:Trork_Brute").matches("hytale:trork_*"));
        assertFalse(creeper.matches("pixelmon:*"));
        assertFalse(creeper.matches("creeper"), "a pattern without a namespace names no key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"creeper", ":creeper", "minecraft:", ""})
    void textWithoutBothPartsIsRefusedShowingTheRightForm(String text) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> TypeKey.parse(text));
        assertTrue(refused.getMessage().contains("namespace:path"), refused.getMessage());
        assertTrue(refused.getMessage().contains("minecraft:creeper"), refused.getMessage());
    }

    @Test
    void ofRefusesEmptyPartsAndAColonInTheNamespace() {
        assertThrows(IllegalArgumentException.class, () -> TypeKey.of("", "creeper"));
        assertThrows(IllegalArgumentException.class, () -> TypeKey.of("minecraft", ""));
        assertThrows(IllegalArgumentException.class, () -> TypeKey.of("a:b", "c"));
    }
}
