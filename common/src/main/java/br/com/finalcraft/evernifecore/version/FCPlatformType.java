package br.com.finalcraft.evernifecore.version;

import org.apache.commons.lang3.StringUtils;

public enum FCPlatformType {
    MINECRAFT,
    HYTALE;

    private static FCPlatformType CURRENT_PLATFORM = null;

    private final String name;

    FCPlatformType() {
        this.name = StringUtils.capitalize(this.name().toLowerCase());
    }

    public String getName() {
        return name;
    }

    public static FCPlatformType getCurrent() {
        if (CURRENT_PLATFORM == null) {
            CURRENT_PLATFORM = detectCurrentPlatform();
        }

        return CURRENT_PLATFORM;
    }

    public static boolean isMinecraft(){
        return getCurrent() == MINECRAFT;
    }

    public static boolean isHytale(){
        return getCurrent() == HYTALE;
    }

    private static FCPlatformType detectCurrentPlatform() {
        if (ClassLoader.getSystemResource("org/bukkit/Bukkit") != null){
            return MINECRAFT;
        }else {
            return HYTALE;
        }
    }

}
