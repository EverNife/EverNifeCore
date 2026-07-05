package br.com.finalcraft.evernifecore.minecraft.dependencies;

import br.com.finalcraft.evernifecore.dependencies.DependencyManager;

import java.io.File;

public class ECoreDependencies {

    public static DependencyManager dependencyManager;

    public static void initialize(){
        dependencyManager = new DependencyManager(
                "EverNifeCore",
                new File("plugins/EverNifeCore"),
                "libs"
        );
        dependencyManager.addJitPack();
        dependencyManager.addJCenter();
        dependencyManager.addMavenCentral();
        dependencyManager.addSonatype();
        dependencyManager.addRepository("https://maven.petrus.dev/public");

        // EveryDatabase runtime deps: download every backend's dependencies up front, all
        // relocated to br.com.finalcraft.everydatabase.libs.* at download-time. The admin can
        // switch a backend in storage.yml at any time without a missing-dependency surprise.
        EDBDependencies.loadJacksonYaml(dependencyManager);
        EDBDependencies.loadSqlPool(dependencyManager);
        EDBDependencies.loadH2(dependencyManager);
        EDBDependencies.loadMySqlDriver(dependencyManager);
        EDBDependencies.loadPostgresDriver(dependencyManager);
        EDBDependencies.loadMongo(dependencyManager);
    }
}
