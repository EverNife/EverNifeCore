package br.com.finalcraft.evernifecore.minecraft.version;

import org.bukkit.Bukkit;

import java.util.Arrays;

public enum MCDetailedVersion {

    v1_7_R1(171, "v1_7"),
    v1_7_R2(172, "v1_7"),
    v1_7_R3(173, "v1_7"),
    v1_7_R4(174, "v1_7"),
    v1_8_R1(181, "v1_8"),
    v1_8_R2(182, "v1_8"),
    v1_8_R3(183, "v1_8"),
    v1_9_R1(191, "v1_9"),
    v1_9_R2(192, "v1_9"),
    v1_10_R1(1101, "v1_10"),
    v1_11_R1(1111, "v1_11"),
    v1_11_R2(1112, "v1_11"),
    v1_12_R1(1121, "v1_12"),
    v1_12_R2(1122, "v1_12"),
    v1_13_R1(1131, "v1_13"),
    v1_13_R2(1132, "v1_13"),
    v1_14_R1(1141, "v1_14"),
    v1_14_R2(1142, "v1_14"),
    v1_15_R1(1151, "v1_15"),
    v1_15_R2(1152, "v1_15"),
    v1_16_R1(1161, "v1_16"),
    v1_16_R2(1162, "v1_16"),
    v1_16_R3(1163, "v1_16"),
    v1_17_R1(1171, "v1_17"),
    v1_18_R1(1181, "v1_18"),
    v1_18_R2(1182, "v1_18"),
    v1_19_R1(1191, "v1_19"),
    v1_19_R2(1192, "v1_19"),
    v1_19_R3(1193, "v1_19"),
    v1_20_R1(1201, "v1_20"),
    v1_20_R2(1202, "v1_20"),
    v1_20_R3(1203, "v1_20"),
    v1_21_R1(1211, "v1_21"),
    ;

    private static MCDetailedVersion currentVersion;

    /**
     * Resolves the running server's version, preferring the CraftBukkit revision package and falling
     * back to the release it reports.
     *
     * <p>The package is the sharper answer - it names the revision (R1/R2/...) directly - but it only
     * exists while the server relocates CraftBukkit per version. From 1.20.5 onwards the package is a
     * plain {@code org.bukkit.craftbukkit}, and reading it alone silently reported whatever the newest
     * known version happened to be.
     *
     * @return {@code null} when neither the package nor the release names anything known.
     */
    static MCDetailedVersion resolve(String serverPackageName, String bukkitVersion) {
        String revision = serverPackageName.substring(serverPackageName.lastIndexOf('.') + 1);

        MCDetailedVersion byRevision = Arrays.stream(MCDetailedVersion.values())
                .filter(version -> version.name().equalsIgnoreCase(revision))
                .findFirst()
                .orElse(null);

        if (byRevision != null) {
            return byRevision;
        }

        return newestOf(shortVersionOf(bukkitVersion));
    }

    /** {@code "1.21.1-R0.1-SNAPSHOT"} to {@code "v1_21"}, or {@code null} if it does not parse. */
    private static String shortVersionOf(String bukkitVersion) {
        if (bukkitVersion == null) return null;

        String[] parts = bukkitVersion.split("[.\\-]");
        if (parts.length < 2) return null;

        try {
            return "v" + Integer.parseInt(parts[0]) + "_" + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The highest revision known for a release, since the release alone does not name one. */
    private static MCDetailedVersion newestOf(String shortVersion) {
        MCDetailedVersion newest = null;
        for (MCDetailedVersion version : values()) {
            if (version.getShortVersion().equals(shortVersion)) {
                newest = version;
            }
        }
        return newest;
    }

    // Operations
    private int value;
    private int shortValue;
    private String shortVersion;

    MCDetailedVersion(int value, String shortVersion) {
        this.value = value;
        this.shortValue = Integer.parseInt(shortVersion.replace("v", "").replace("_", ""));
        this.shortVersion = shortVersion;
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
