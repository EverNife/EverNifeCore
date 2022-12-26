package br.com.finalcraft.evernifecore.dependencies.util;

import net.byteflux.libby.Library;

import java.util.regex.Pattern;

public class LibraryFactory {

    public static Library of(String fullLibraryName){
        String[] split = fullLibraryName.split(Pattern.quote(":"));
        Library.Builder builder = Library.builder()
                .groupId(split[0])
                .artifactId(split[1])
                .version(split[2]);

        if (split.length > 3){
            builder.checksum(split[3]);
        }

        return builder.build();
    }

}
