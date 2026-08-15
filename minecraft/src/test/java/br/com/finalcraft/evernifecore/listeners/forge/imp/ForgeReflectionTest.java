package br.com.finalcraft.evernifecore.listeners.forge.imp;

import net.minecraftforge.common.MinecraftForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The by-name access the Forge adapters run on: it finds what is there, whatever type it is declared
 * as, and it refuses in a catchable way when it is not there.
 *
 * <p>The type-agnostic half is the whole point of the design. A {@code getstatic} compiled against
 * one era's bus type dies with {@code NoSuchFieldError} on an era that declares another - a read that
 * only knows the name cannot, because no type ever reaches the lookup.</p>
 */
class ForgeReflectionTest {

    @AfterEach
    void clearTheDouble() {
        MinecraftForge.EVENT_BUS = null;
    }

    @Test
    void theDefaultBusComesBackWhateverTypeTheServerDeclaredItAs() {
        Object legacyEraBus = "a 1.7.10 cpw.mods bus stands in as a String";
        MinecraftForge.EVENT_BUS = legacyEraBus;
        assertSame(legacyEraBus, ForgeReflection.defaultEventBus(),
                "the field is read by name, so its value comes back untouched");

        Object modernEraBus = new Object();
        MinecraftForge.EVENT_BUS = modernEraBus;
        assertSame(modernEraBus, ForgeReflection.defaultEventBus(),
                "and the very same lookup answers for a value of a completely different type - which is"
                        + " the era difference that used to be a NoSuchFieldError");
    }

    @Test
    void aClassThisServerDoesNotHaveIsRefusedWithAMessageNamingIt() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForgeReflection.requireClass("net.minecraftforge.common.NothingIsHere"));

        assertTrue(refusal.getMessage().contains("net.minecraftforge.common.NothingIsHere"),
                "the reader has to learn which class is missing: " + refusal.getMessage());
    }

    @Test
    void aMemberThisServerDoesNotHaveIsRefusedSeparatelyFromTheClass() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForgeReflection.method("java.lang.String", "thisMethodIsNotThere", 0));

        assertTrue(refusal.getMessage().contains("thisMethodIsNotThere"),
                "a present class with an absent member is its own diagnosis: " + refusal.getMessage());
    }

    @Test
    void aMethodOfTheRightNameAndTheWrongArityIsRefusedBeforeItIsCalled() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForgeReflection.method("java.lang.String", "substring", 7));

        assertTrue(refusal.getMessage().contains("substring"),
                "binding to an overload that takes something else is the failure this guard exists for: "
                        + refusal.getMessage());
    }

    @Test
    void withoutForgeOnTheClasspathNothingIsAModernBusAndAskingIsSafe() {
        assertFalse(ForgeReflection.isModernEventBus(new Object()),
                "no IEventBus interface here, so nothing can be an instance of it");
        assertFalse(ForgeReflection.isModernEventBus(null),
                "and the question survives a null bus instead of throwing on it");
    }

}
