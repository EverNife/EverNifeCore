package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link TempDir} JUnit is told not to delete: {@link TestTempFolders} does it, after the tests.
 *
 * <p>Some writers of a plugin's data folder finish off the test's thread by design, such as the
 * cooldown file saved asynchronously, and on Windows a file still being written cannot be
 * removed: the default cleanup then fails the test that just passed. A test that drives such a writer
 * takes this form of temp dir.</p>
 *
 * <p>The folder lives in the JVM's root of {@link TestTempFolders}, which goes when the JVM exits, or with a later
 * JVM when a writer still held a file. A build needs no setting for this.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@TempDir(factory = TestTempFolders.Factory.class, cleanup = CleanupMode.NEVER)
public @interface TempDirNobodyCleans {

}
