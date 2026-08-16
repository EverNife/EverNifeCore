package br.com.finalcraft.evernifecore.testing.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Installs an EverNifeCore platform for the annotated test class and uninstalls it afterwards.
 *
 * <pre>{@code
 * @ECoreTest
 * class MyTest {
 *     @Test
 *     void something(ECoreTestWorld world) { ... }
 * }
 * }</pre>
 *
 * <p>The uninstall is the point: without it the platform outlives the class and the next test
 * class in the same JVM inherits it. A test method may take an {@code ECoreTestWorld} or a
 * {@code TestPlatform} parameter to assert on what the platform captured.</p>
 *
 * <p>For the length of the class the global {@code ECEventBus} runs a {@code FailingExceptionHandler}:
 * a subscriber or watch callback that breaks fails the test at the post that drove it. A test that
 * breaks one on purpose uses a scoped bus with a {@code RecordingExceptionHandler}.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ECoreTestExtension.class)
public @interface ECoreTest {

    /** Which double to install. Defaults to {@link Mode#LENIENT}, the pre-existing no-op behaviour. */
    Mode value() default Mode.LENIENT;

    enum Mode {
        /** Refuses every question it was not taught to answer. */
        STRICT,
        /** The old no-op answers: empty players, no plugins, inline main thread, recorded shutdowns. */
        LENIENT,
        /** Lenient plus command capture and a neutral chat adapter. */
        COMMAND_CAPTURE
    }
}
