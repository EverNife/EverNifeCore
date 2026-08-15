package br.com.finalcraft.evernifecore.listeners.forge.imp;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The by-name access the Forge adapters run on: it finds what is there, whatever type it is declared
 * as, and whenever it cannot it refuses in a catchable way that names what the route wanted.
 *
 * <p>The type-agnostic half is the whole point of the design. A {@code getstatic} compiled against
 * one era's bus type dies with {@code NoSuchFieldError} on an era that declares another - a read that
 * only knows the name cannot, because no type ever reaches the lookup.</p>
 *
 * <p>The refusal has two sources and both are covered below: a lookup that comes back empty, and a
 * lookup that throws. The second one is the one a hybrid actually produces, and its original failure
 * has to survive into the cause - a message that reads well and eats the stack trades one problem
 * for another.</p>
 */
class ForgeReflectionTest {

    private static final String UNREADABLE_METHOD = "register";

    /**
     * A class this server has whose members cannot be read: its only method takes a type that is on no
     * classpath, so building the {@code Method} object fails even though the class itself loaded fine.
     * That is the shape a hybrid produced in the field - the owner resolves, and reading its members
     * throws a {@code NoClassDefFoundError} naming a type nobody asked for.
     *
     * <p>It is assembled instead of compiled because a source file cannot name a type that must not
     * exist. {@code MethodHandles.lookup().defineClass} puts it in this test's own package and
     * classloader, which is what lets production code reach it by name.</p>
     */
    private static final Class<?> UNREADABLE_MEMBERS = defineOwnerWhoseMembersCannotBeRead();

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
    void aClassThatIsOnThisServerAndStillCannotBeLoadedIsRefusedTheSameWay() throws Exception {
        String className = PresentAndUnlinkable.class.getName();
        assertNotNull(Class.forName(className, false, ForgeReflectionTest.class.getClassLoader()),
                "loading it without initializing is what proves the class is on this server at all -"
                        + " otherwise this would just be the missing-class case again");

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForgeReflection.requireClass(className));

        assertTrue(refusal.getMessage().contains(className),
                "present and unusable is a refusal that names the class, not an Error escaping from"
                        + " whoever asked: " + refusal.getMessage());
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
    void aLookupThatThrowsIsRefusedByNameAndKeepsWhatTheServerThrew() {
        String owner = UNREADABLE_MEMBERS.getName();

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> ForgeReflection.method(owner, UNREADABLE_METHOD, 1));

        assertTrue(refusal.getMessage().contains(owner) && refusal.getMessage().contains(UNREADABLE_METHOD),
                "the refusal names the lookup that failed; the raw error names only the type the server"
                        + " tripped on, which sends the reader after the wrong class: " + refusal.getMessage());

        NoClassDefFoundError cause = assertInstanceOf(NoClassDefFoundError.class, refusal.getCause(),
                "and the original failure is chained, not replaced - a LinkageError is not an Exception,"
                        + " so it escapes any guard that only catches Exception");
        assertTrue(String.valueOf(cause.getMessage()).contains("NotOnThisServer"),
                "it is the server's own failure that survives, unedited: " + cause);
    }

    @Test
    void aBusIsModernExactlyWhenItCarriesTheInterfaceThatNamesTheEra() {
        assertTrue(ForgeReflection.isModernEventBus(new ModernEraBus()),
                "1.16.5 on hands out an IEventBus, so the question has to be able to answer yes - a name"
                        + " that resolves to nothing answers no to everything and every test still passes");
        assertFalse(ForgeReflection.isModernEventBus(new Object()),
                "a bus of any other type - a 1.7.10 one, say - is not one of those");
        assertFalse(ForgeReflection.isModernEventBus(null),
                "and the question survives a null bus instead of throwing on it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  stand-ins
    // -----------------------------------------------------------------------------------------------------------------

    /** A bus from the eras where implementing that interface is what makes a bus the modern kind. */
    static class ModernEraBus implements IEventBus {

    }

    /**
     * On this server and unloadable anyway: initializing it throws, so every by-name attempt to reach it
     * comes back a {@code LinkageError} - "on it but failing to link", the half of {@code requireClass}
     * that no missing class can stand in for.
     */
    static class PresentAndUnlinkable {

        static final Object NEVER = refuseToInitialize();

        private static Object refuseToInitialize() {
            throw new UnsupportedOperationException("a stand-in that exists to fail its own initialization");
        }
    }

    private static Class<?> defineOwnerWhoseMembersCannotBeRead() {
        String here = ForgeReflectionTest.class.getPackageName();
        ClassDesc owner = ClassDesc.of(here, "UnreadableMembers");
        ClassDesc absent = ClassDesc.of(here, "NotOnThisServer");

        byte[] bytecode = ClassFile.of().build(owner, classFile -> classFile
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                .withMethod(UNREADABLE_METHOD, MethodTypeDesc.of(ConstantDescs.CD_void, absent),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        method -> method.withCode(code -> code.return_())));
        try {
            return MethodHandles.lookup().defineClass(bytecode);
        } catch (IllegalAccessException lookupCannotDefineHere) {
            throw new AssertionError(lookupCannotDefineHere);
        }
    }

}
