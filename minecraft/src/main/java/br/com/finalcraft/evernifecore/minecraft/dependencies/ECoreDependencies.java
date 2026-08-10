package br.com.finalcraft.evernifecore.minecraft.dependencies;

import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import net.byteflux.libby.Library;

import java.io.File;

public class ECoreDependencies {

    public static DependencyManager dependencyManager;

    public static void initialize(){
        dependencyManager = new DependencyManager(
                "EverNifeCore",
                new File("plugins/EverNifeCore"),
                "libs"
        );
        if (!dependencyManager.canInjectLibraries()) {
            //Downloading here would spend the boot fetching jars nothing can load afterwards. The
            //manager already said why on the console; what follows fails later, on the class that
            //needed the library, naming it.
            return;
        }

        dependencyManager.addJitPack();
        dependencyManager.addJCenter();
        dependencyManager.addMavenCentral();
        dependencyManager.addSonatype();
        dependencyManager.addRepository("https://maven.petrus.dev/public");

        // Apache commons-lang3 is a compileOnly dependency: most servers already have it on the
        // plugin classpath (legacy Spigot bundles it; modern Paper exposes it through its plugin
        // library-loader). Paper 1.16.5, however, does NOT embed commons-lang3 nor expose it to
        // plugins, so every class that references org.apache.commons.lang3.*
        if (!isClassPresent("org.apache.commons.lang3.Validate")) {
            dependencyManager.loadLibrary(
                    Library.builder()
                            .groupId("org.apache.commons")
                            .artifactId("commons-lang3")
                            .version("3.15.0")
                            .build()
            );
        }

        // EveryDatabase runtime deps: download every backend's dependencies up front, all
        // relocated to br.com.finalcraft.everydatabase.libs.* at download-time. The admin can
        // switch a backend in storage.yml at any time without a missing-dependency surprise.
        EDBDependencies.loadJacksonStack(dependencyManager);
        EDBDependencies.loadSqlPool(dependencyManager);
        EDBDependencies.loadH2(dependencyManager);
        EDBDependencies.loadMySqlDriver(dependencyManager);
        EDBDependencies.loadPostgresDriver(dependencyManager);
        EDBDependencies.loadMongo(dependencyManager);
    }

    private static boolean isClassPresent(String className){
        try {
            Class.forName(className, false, ECoreDependencies.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
