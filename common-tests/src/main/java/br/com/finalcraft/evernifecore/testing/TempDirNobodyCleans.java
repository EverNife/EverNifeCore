package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link TempDir} JUnit is told not to delete.
 *
 * <p>A locale bootstrap saves asynchronously, and on Windows a file still being written cannot be
 * removed: the default cleanup then fails the test that just passed, over a directory the operating
 * system will clear anyway. Every world this engine builds writes something, so this is the form a
 * temp dir takes here.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@TempDir(cleanup = CleanupMode.NEVER)
public @interface TempDirNobodyCleans {

}
