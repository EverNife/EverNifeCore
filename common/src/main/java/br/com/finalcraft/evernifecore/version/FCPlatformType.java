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

    //The resource path a Bukkit server is recognized by. It MUST carry the .class suffix: a jar
    //holds no entry for the bare binary name, so a suffix-less probe never matches and every
    //server, Bukkit included, gets detected as Hytale.
    static final String BUKKIT_MARKER = "org/bukkit/Bukkit.class";

    private static FCPlatformType detectCurrentPlatform() {
        return detectPlatform(FCPlatformType.class.getClassLoader());
    }

    static FCPlatformType detectPlatform(ClassLoader loader) {
        //Our own loader is asked first: it delegates up to whichever loader holds the server
        //classes, which a server booted through a custom launcher keeps off the system classpath.
        boolean bukkitPresent = (loader != null && loader.getResource(BUKKIT_MARKER) != null)
                || ClassLoader.getSystemResource(BUKKIT_MARKER) != null;

        return bukkitPresent ? MINECRAFT : HYTALE;
    }

}
