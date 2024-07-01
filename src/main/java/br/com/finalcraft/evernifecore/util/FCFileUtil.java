package br.com.finalcraft.evernifecore.util;

import java.io.File;

public class FCFileUtil {

    public static boolean isEmpty(File folder) {
        if (folder == null || !folder.exists()) {
            return true;
        }

        File[] files = folder.listFiles();

        if (files == null || files.length == 0) {
            return true;
        }

        return false;
    }

}
