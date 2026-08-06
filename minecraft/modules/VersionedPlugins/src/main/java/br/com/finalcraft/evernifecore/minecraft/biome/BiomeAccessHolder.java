package br.com.finalcraft.evernifecore.minecraft.biome;

import org.bukkit.block.Biome;

final class BiomeAccessHolder {

    static final BiomeAccess INSTANCE = load();

    private BiomeAccessHolder() {
    }

    private static BiomeAccess load() {
        //Asking the running server whether Biome is an interface beats guessing from a version
        //number: the shape is exactly what decides which artifact's call opcodes will link here.
        String era = Biome.class.isInterface() ? "modern" : "legacy";
        String className = "br.com.finalcraft.evernifecore.minecraft.compat." + era + ".biome.ImpBiomeAccess";
        try {
            return (BiomeAccess) Class.forName(className).newInstance();
        } catch (Throwable e) {
            throw new IllegalStateException("This EverNifeCore jar carries no biome support for a "
                    + era + " server: [" + className + "] is not in it. Rebuild the shadow jar with"
                    + " every minecraft/modules/* artifact bundled - dropping one leaves the server"
                    + " era it covers unserved.", e);
        }
    }
}
