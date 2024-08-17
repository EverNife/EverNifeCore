package br.com.finalcraft.evernifecore.version;

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

    private static final MCDetailedVersion currentVersion;

    static {
        String[] svPackage = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        String svPackageVersionName = svPackage[svPackage.length - 1];

        MCDetailedVersion mcDetailedVersion = Arrays.stream(MCDetailedVersion.values())
                .filter(version -> version.name().equalsIgnoreCase(svPackageVersionName))
                .findFirst()
                .orElse(null);

        if (mcDetailedVersion == null){
            mcDetailedVersion = Arrays.asList(MCDetailedVersion.values()).get(MCDetailedVersion.values().length - 1); //Assume it's a newer version!
            System.out.println(String.format(
                    "[EverNifeCore] Failed to find out what is the MCVersion of this server when looking for the package name '%s'. Defaulting it to latest known MCVersion: (%s)",
                    svPackageVersionName,
                    mcDetailedVersion
            ));
        }

        currentVersion = mcDetailedVersion;
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

    public static MCDetailedVersion getCurrent() {
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
