package br.com.finalcraft.evernifecore.minecraft.version;

import org.bukkit.Bukkit;

/**
 * The Minecraft versions this core knows about, each declaring the lowest release it covers.
 *
 * <p>A constant spans a range of releases - {@code v1_20_R3} is 1.20.3 and 1.20.4, {@code v1_20_R4}
 * is 1.20.5 and 1.20.6 - and a range ends where the next one begins, so the declared release is all
 * the table needs. That declared release is also what orders the table and what {@link #resolve}
 * matches a reported version against.
 *
 * <p>Constants carry two naming schemes, because a server reports two different things. Up to
 * 1.20.4 the server package named the CraftBukkit revision, so those constants are named after it
 * ({@code v1_16_R3}) and {@link #resolve} can match the package outright. From 1.20.5 the package
 * is a plain {@code org.bukkit.craftbukkit} and the reported release is the only evidence left;
 * once Minecraft moved to year-based releases the constants are named after the release itself
 * ({@code v26_2}), since a revision in the name would claim something no such server reports.
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
    v26_1("26.1"),
    v26_1_1("26.1.1"),
    v26_1_2("26.1.2"),
    v26_2("26.2"),
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
     * The newest constant whose range starts at or below the reported release. A patch above every
     * known range still answers the newest constant of that family, which is the closest the table
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
    private final String releaseFamily;
    private final int major;
    private final int minor;
    private final int lowestPatch;

    MCDetailedVersion(String lowestRelease) {
        int[] floor = releaseOf(lowestRelease);
        if (floor == null) {
            throw new IllegalArgumentException(String.format(
                    "%s declares \"%s\" as the lowest release it covers, but that does not read as a "
                            + "Minecraft release. Declare the first release this constant covers, as the "
                            + "server reports it - \"1.20.5\", \"26.2\".",
                    name(), lowestRelease
            ));
        }
        this.major = floor[0];
        this.minor = floor[1];
        this.lowestPatch = floor[2];
        this.releaseFamily = major + "." + minor;
        requireNameSaysWhichReleaseItCovers(lowestRelease);
    }

    private void requireNameSaysWhichReleaseItCovers(String lowestRelease) {
        String familyPrefix = "v" + major + "_" + minor + "_R";
        boolean namedAfterRevision = name().startsWith(familyPrefix)
                && isDigits(name().substring(familyPrefix.length()));

        if (namedAfterRevision || name().equals("v" + lowestRelease.replace('.', '_'))) {
            return;
        }

        throw new IllegalArgumentException(String.format(
                "%s declares \"%s\" as the lowest release it covers, but its name says otherwise. A "
                        + "constant is named either after the CraftBukkit revision its server package "
                        + "reports, which here would be %s<n> - v1_20_R4 covers 1.20.5 - or, when the "
                        + "package no longer carries a revision, after the release itself, which here "
                        + "would be %s. Rename it, or fix the release it declares.",
                name(), lowestRelease, familyPrefix, "v" + lowestRelease.replace('.', '_')
        ));
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    /** The release family this covers, written the way a server reports it: {@code 1.21}, {@code 26.2}. */
    public String getReleaseFamily() {
        return releaseFamily;
    }

    /** The lowest release this covers, the one it was declared with: {@code 1.20.5}, {@code 26.1.2}. */
    public String getLowestRelease() {
        return lowestPatch == 0 ? releaseFamily : releaseFamily + "." + lowestPatch;
    }

    /**
     * Where a server of this version keeps a CraftBukkit class, most likely first - the caller takes
     * the first that loads.
     *
     * <p>Up to 1.20.4 CraftBukkit was relocated into a package carrying the revision, so those
     * versions offer the relocated name and then the plain one, because a fork can report a version
     * whose layout it does not follow. From 1.20.5 the package is plain and there is nothing to
     * relocate into: the revision only ever existed as a package segment, so building one for a
     * version that never had it would name a class no server has.
     *
     * @param craftBukkitClass the name below the CraftBukkit package, such as {@code entity.CraftPlayer}
     */
    public String[] getCraftBukkitClassNames(String craftBukkitClass) {
        String plain = "org.bukkit.craftbukkit." + craftBukkitClass;
        if (isHigherEquals(v1_20_R4)) {
            return new String[]{plain};
        }
        return new String[]{"org.bukkit.craftbukkit." + name() + "." + craftBukkitClass, plain};
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
        return compareRelease(otherVersion) < 0;
    }

    public boolean isLowerEquals(MCDetailedVersion otherVersion) {
        return compareRelease(otherVersion) <= 0;
    }

    public boolean isEqual(MCDetailedVersion otherVersion) {
        return compareRelease(otherVersion) == 0;
    }

    public boolean isHigher(MCDetailedVersion otherVersion) {
        return compareRelease(otherVersion) > 0;
    }

    public boolean isHigherEquals(MCDetailedVersion otherVersion) {
        return compareRelease(otherVersion) >= 0;
    }

    /**
     * Orders two constants by the release each declares, one digit group at a time.
     *
     * <p>Folding a release into a single number is what stops working the moment the scheme changes:
     * 26.2 comes after 1.21.11, yet nothing binds a number built out of {@code 26} and {@code 2} to
     * land above one built out of {@code 1} and {@code 21}. Comparing the groups needs no such luck -
     * 26 is simply greater than 1.
     */
    private int compareRelease(MCDetailedVersion other) {
        if (major != other.major) return Integer.compare(major, other.major);
        if (minor != other.minor) return Integer.compare(minor, other.minor);
        return Integer.compare(lowestPatch, other.lowestPatch);
    }

    /** The same order, blind to the patch, so every constant of one family answers as one version. */
    int compareFamily(MCDetailedVersion other) {
        if (major != other.major) return Integer.compare(major, other.major);
        return Integer.compare(minor, other.minor);
    }

}
