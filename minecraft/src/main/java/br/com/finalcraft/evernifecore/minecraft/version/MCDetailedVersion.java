package br.com.finalcraft.evernifecore.minecraft.version;

import org.bukkit.Bukkit;

/**
 * The CraftBukkit revisions this core knows about, each declaring the lowest Minecraft release it
 * covers.
 *
 * <p>A revision spans a range of releases - {@code v1_20_R3} is 1.20.3 and 1.20.4, {@code v1_20_R4}
 * is 1.20.5 and 1.20.6 - and a range ends where the next one begins, so the declared release is all
 * the table needs. Everything else about a constant (its release family and its revision number) is
 * read off its own name, which is also what {@link #resolve} matches the server package against.
 */
public enum MCDetailedVersion {

    v1_7_R1("1.7"),
    v1_7_R2("1.7.5"),
    v1_7_R3("1.7.8"),
    v1_7_R4("1.7.10"),
    v1_8_R1("1.8"),
    v1_8_R2("1.8.3"),
    v1_8_R3("1.8.8"),
    v1_9_R1("1.9"),
    v1_9_R2("1.9.4"),
    v1_10_R1("1.10"),
    v1_11_R1("1.11"),
    v1_12_R1("1.12"),
    v1_13_R1("1.13"),
    v1_13_R2("1.13.1"),
    v1_14_R1("1.14"),
    v1_15_R1("1.15"),
    v1_16_R1("1.16"),
    v1_16_R2("1.16.2"),
    v1_16_R3("1.16.4"),
    v1_17_R1("1.17"),
    v1_18_R1("1.18"),
    v1_18_R2("1.18.2"),
    v1_19_R1("1.19"),
    v1_19_R2("1.19.3"),
    v1_19_R3("1.19.4"),
    v1_20_R1("1.20"),
    v1_20_R2("1.20.2"),
    v1_20_R3("1.20.3"),
    v1_20_R4("1.20.5"),
    v1_21_R1("1.21"),
    v1_21_R2("1.21.2"),
    v1_21_R3("1.21.4"),
    v1_21_R4("1.21.5"),
    v1_21_R5("1.21.6"),
    v1_21_R6("1.21.9"),
    v1_21_R7("1.21.11"),
    ;

    private static MCDetailedVersion currentVersion;

    /**
     * Resolves the running server's version, preferring the CraftBukkit revision package and falling
     * back to the release it reports.
     *
     * <p>The package is the sharper answer - it names the revision directly - but it only exists while
     * the server relocates CraftBukkit per version. From 1.20.5 onwards the package is a plain
     * {@code org.bukkit.craftbukkit} and the reported release is the only evidence left. That release
     * carries the patch, and the patch is exactly what tells two revisions of the same family apart.
     *
     * @return {@code null} when neither the package nor the release names anything known.
     */
    static MCDetailedVersion resolve(String serverPackageName, String bukkitVersion) {
        String revision = serverPackageName.substring(serverPackageName.lastIndexOf('.') + 1);

        for (MCDetailedVersion version : values()) {
            if (version.name().equalsIgnoreCase(revision)) {
                return version;
            }
        }

        return covering(bukkitVersion);
    }

    /**
     * The newest revision whose range starts at or below the reported release. A patch above every
     * known range still answers the newest revision of that family, which is the closest the table
     * can get to a release published after it was written.
     *
     * @return {@code null} when no known release family matches.
     */
    private static MCDetailedVersion covering(String bukkitVersion) {
        int[] release = releaseOf(bukkitVersion);
        if (release == null) return null;

        MCDetailedVersion covering = null;
        for (MCDetailedVersion version : values()) {
            if (version.major == release[0] && version.minor == release[1] && version.lowestPatch <= release[2]) {
                covering = version;
            }
        }
        return covering;
    }

    /** {@code "1.20.5-R0.1-SNAPSHOT"} to {@code {1, 20, 5}}, or {@code null} if it does not parse. */
    private static int[] releaseOf(String release) {
        if (release == null) return null;

        String[] parts = release.split("[.\\-]");
        if (parts.length < 2) return null;

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new int[]{major, minor, parts.length > 2 ? patchOf(parts[2]) : 0};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A release with no patch puts the API revision in that slot ({@code 1.21-R0.1}), and that is not one. */
    private static int patchOf(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Operations
    private final int value;
    private final int shortValue;
    private final String shortVersion;
    private final int major;
    private final int minor;
    private final int lowestPatch;

    MCDetailedVersion(String lowestRelease) {
        String[] familyAndRevision = name().substring(1).split("_R");
        String[] family = familyAndRevision[0].split("_");

        this.major = Integer.parseInt(family[0]);
        this.minor = Integer.parseInt(family[1]);
        this.shortVersion = "v" + major + "_" + minor;
        this.shortValue = Integer.parseInt("" + major + minor);
        this.value = shortValue * 10 + Integer.parseInt(familyAndRevision[1]);

        int[] floor = releaseOf(lowestRelease);
        if (floor == null || floor[0] != major || floor[1] != minor) {
            throw new IllegalArgumentException(String.format(
                    "%s declares '%s' as the lowest release it covers, but that is not a %s.%s release. "
                            + "Declare the first release the revision shipped on, in its own family - v1_20_R4 "
                            + "shipped on 1.20.5, so it declares \"1.20.5\".",
                    name(), lowestRelease, major, minor
            ));
        }
        this.lowestPatch = floor[2];
    }

    public int getValue() {
        return value;
    }

    public int getShortValue() {
        return shortValue;
    }

    public String getShortVersion() {
        return shortVersion;
    }

    /**
     * Resolved on first use rather than at class load, so nothing forces a Bukkit server to exist
     * merely because this enum was touched.
     */
    public static MCDetailedVersion getCurrent() {
        if (currentVersion == null) {
            String serverPackageName = Bukkit.getServer().getClass().getPackage().getName();
            String bukkitVersion = Bukkit.getBukkitVersion();

            MCDetailedVersion resolved = resolve(serverPackageName, bukkitVersion);

            if (resolved == null) {
                resolved = values()[values().length - 1]; //Assume it's a newer version!
                System.out.println(String.format(
                        "[EverNifeCore] Failed to find out the MCVersion of this server from the package name '%s' or the reported version '%s'. Defaulting it to latest known MCVersion: (%s)",
                        serverPackageName,
                        bukkitVersion,
                        resolved
                ));
            }

            currentVersion = resolved;
        }
        return currentVersion;
    }

    public boolean isLower(MCDetailedVersion otherVersion) {
        return this.getValue() < otherVersion.getValue();
    }

    public boolean isLowerEquals(MCDetailedVersion otherVersion) {
        return this.getValue() <= otherVersion.getValue();
    }

    public boolean isEqual(MCDetailedVersion otherVersion) {
        return this.getValue() == otherVersion.getValue();
    }

    public boolean isHigher(MCDetailedVersion otherVersion) {
        return this.getValue() > otherVersion.getValue();
    }

    public boolean isHigherEquals(MCDetailedVersion otherVersion) {
        return this.getValue() >= otherVersion.getValue();
    }

}
