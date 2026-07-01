package br.com.finalcraft.evernifecore.minecraft.dependencies;

import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import net.byteflux.libby.Library;
import net.byteflux.libby.relocation.Relocation;

import java.util.ArrayList;
import java.util.List;

/**
 * EveryDatabase runtime dependencies, downloaded via libby and RELOCATED at download-time
 * to {@code br.com.finalcraft.everydatabase.libs.*}.
 *
 * <p>SYNC RULES (do not break):</p>
 * <ul>
 *   <li>The coordinates/versions below mirror {@code EveryDatabaseDependencies} of the
 *       EveryDatabase project (its gradle/libs.versions.toml). Update them TOGETHER with
 *       every everydatabase-core upgrade in common/build.gradle.</li>
 *   <li>The relocation pairs below must be IDENTICAL to the {@code relocate ...} entries of
 *       the shadowJar in minecraft/build.gradle (which rewrites the REFS of the embedded
 *       everydatabase-core classes to these same coordinates).</li>
 *   <li>{@code jackson-annotations} is NEVER relocated: annotations match by class
 *       identity (the plugins' POJO {@code @JsonProperty}/{@code @JsonIgnore} must be
 *       the same classes that the relocated databind sees).</li>
 *   <li>All patterns and groupIds use {@code {}} in place of {@code .} so that ECore's own
 *       shadowJar relocation does not rewrite these strings (libby converts
 *       {@code {}} to {@code .} at runtime).</li>
 * </ul>
 */
public final class EDBDependencies {

    private static final String JACKSON_VERSION = "2.22.0"; // aligned to everydatabase-core 1.0.6

    private static final String LIBS_PREFIX = "br{}com{}finalcraft{}everydatabase{}libs{}";

    private static final Relocation REL_JACKSON_CORE       = rel("com{}fasterxml{}jackson{}core",       "jackson{}core");
    private static final Relocation REL_JACKSON_DATABIND   = rel("com{}fasterxml{}jackson{}databind",   "jackson{}databind");
    private static final Relocation REL_JACKSON_DATAFORMAT = rel("com{}fasterxml{}jackson{}dataformat", "jackson{}dataformat");
    private static final Relocation REL_JACKSON_DATATYPE   = rel("com{}fasterxml{}jackson{}datatype",   "jackson{}datatype");
    private static final Relocation REL_SNAKEYAML          = rel("org{}yaml{}snakeyaml",                "snakeyaml");
    private static final Relocation REL_HIKARI             = rel("com{}zaxxer{}hikari",                 "hikari");
    private static final Relocation REL_SLF4J              = rel("org{}slf4j",                          "slf4j");
    private static final Relocation REL_H2                 = rel("org{}h2",                             "h2");
    private static final Relocation REL_MYSQL              = rel("com{}mysql",                          "mysql");
    private static final Relocation REL_POSTGRESQL         = rel("org{}postgresql",                     "postgresql");
    private static final Relocation REL_MONGODB            = rel("com{}mongodb",                        "mongodb");
    private static final Relocation REL_BSON               = rel("org{}bson",                           "bson");

    private EDBDependencies() {
    }

    /** Jackson JSON+YAML stack - ALWAYS required (EveryDatabase codecs). */
    public static void loadJacksonYaml(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("com{}fasterxml{}jackson{}core", "jackson-core", JACKSON_VERSION,
                REL_JACKSON_CORE));
        // jackson-annotations dropped the patch component at 2.20 (jackson-bom's
        // jackson.version.annotations), so it is "2.22", not "2.22.0". NO relocation - annotation identity.
        libs.add(lib("com{}fasterxml{}jackson{}core", "jackson-annotations", "2.22"
                /* NO relocation - annotation identity */));
        libs.add(lib("com{}fasterxml{}jackson{}core", "jackson-databind", JACKSON_VERSION,
                REL_JACKSON_CORE, REL_JACKSON_DATABIND));
        // Datatype modules that everydatabase-core's JacksonConfig registers into the default codec
        // mappers (jsr310 = java.time, jdk8 = Optional). Omitting them breaks any persisted entity
        // field of those types at runtime.
        libs.add(lib("com{}fasterxml{}jackson{}datatype", "jackson-datatype-jsr310", JACKSON_VERSION,
                REL_JACKSON_CORE, REL_JACKSON_DATABIND, REL_JACKSON_DATATYPE));
        libs.add(lib("com{}fasterxml{}jackson{}datatype", "jackson-datatype-jdk8", JACKSON_VERSION,
                REL_JACKSON_CORE, REL_JACKSON_DATABIND, REL_JACKSON_DATATYPE));
        libs.add(lib("com{}fasterxml{}jackson{}dataformat", "jackson-dataformat-yaml", JACKSON_VERSION,
                REL_JACKSON_CORE, REL_JACKSON_DATABIND, REL_JACKSON_DATAFORMAT, REL_SNAKEYAML));
        libs.add(lib("org{}yaml", "snakeyaml", "2.6",
                REL_SNAKEYAML));
        manager.loadLibrary(libs);
    }

    /** SQL pool shared by sql/postgresql/h2 (HikariCP requires slf4j-api at class-init). */
    public static void loadSqlPool(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("com{}zaxxer", "HikariCP", "4.0.3",
                REL_HIKARI, REL_SLF4J));
        libs.add(lib("org{}slf4j", "slf4j-api", "1.7.36",
                REL_SLF4J));
        manager.loadLibrary(libs);
    }

    /** Embedded H2 engine (JDBC driver bundled in it). Requires loadSqlPool before/alongside. */
    public static void loadH2(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("com{}h2database", "h2", "1.4.200",
                REL_H2));
        manager.loadLibrary(libs);
    }

    /** MySQL/MariaDB driver. Requires loadSqlPool before/alongside. */
    public static void loadMySqlDriver(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("com{}mysql", "mysql-connector-j", "9.7.0",
                REL_MYSQL));
        manager.loadLibrary(libs);
    }

    /** PostgreSQL driver. Requires loadSqlPool before/alongside. */
    public static void loadPostgresDriver(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("org{}postgresql", "postgresql", "42.7.12",
                REL_POSTGRESQL));
        manager.loadLibrary(libs);
    }

    /** Synchronous Mongo driver + flat tree (libby does not resolve transitives). */
    public static void loadMongo(DependencyManager manager) {
        List<Library> libs = new ArrayList<>();
        libs.add(lib("org{}mongodb", "mongodb-driver-sync", "5.8.0",
                REL_MONGODB, REL_BSON, REL_SLF4J));
        libs.add(lib("org{}mongodb", "mongodb-driver-core", "5.8.0",
                REL_MONGODB, REL_BSON, REL_SLF4J));
        libs.add(lib("org{}mongodb", "bson", "5.8.0",
                REL_BSON));
        libs.add(lib("org{}mongodb", "bson-record-codec", "5.8.0",
                REL_BSON));
        manager.loadLibrary(libs);
    }

    private static Relocation rel(String pattern, String relocatedSuffix) {
        return new Relocation(pattern, LIBS_PREFIX + relocatedSuffix);
    }

    private static Library lib(String groupId, String artifactId, String version, Relocation... relocations) {
        Library.Builder builder = Library.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version);
        for (Relocation relocation : relocations) {
            builder.relocate(relocation);
        }
        return builder.build();
    }
}
