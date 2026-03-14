package br.com.finalcraft.evernifecore.util;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nonnull;

public class FCStringUtil {

    /**
     * This method uses a region to check case-insensitive equality. This
     * means the internal array does not need to be copied like a
     * toLowerCase() call would.
     *
     * @param string String to check
     * @param prefix Prefix of string to compare
     * @return true if provided string starts with, ignoring case, the prefix
     *     provided
     * @throws NullPointerException if prefix is null
     * @throws IllegalArgumentException if string is null
     */
    public static boolean startsWithIgnoreCase(@Nonnull final String string, @Nonnull final String prefix) throws IllegalArgumentException, NullPointerException {
        Preconditions.checkArgument(string != null, "Cannot check a null string for a match");
        if (string.length() < prefix.length()) {
            return false;
        }
        return string.regionMatches(true, 0, prefix, 0, prefix.length());
    }

}
