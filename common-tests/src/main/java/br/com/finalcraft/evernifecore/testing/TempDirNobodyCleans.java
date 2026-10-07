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
 * <p>Some writers of a plugin's data folder finish off the test's thread by design, such as the
 * cooldown file saved asynchronously, and on Windows a file still being written cannot be
 * removed: the default cleanup then fails the test that just passed, over a directory the operating
 * system will clear anyway. A test that drives such a writer takes this form of temp dir.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@TempDir(cleanup = CleanupMode.NEVER)
public @interface TempDirNobodyCleans {

}
