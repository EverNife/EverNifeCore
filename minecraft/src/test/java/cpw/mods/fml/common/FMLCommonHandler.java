package cpw.mods.fml.common;

/**
 * A test-only target for a lookup that goes by name, like the {@code MinecraftForge} beside it: the
 * 1.7.10 adapter reaches FML's bus through {@code FMLCommonHandler.instance().bus()} by reflection, so
 * proving the read needs a class under that exact name. Nothing compiles against it and it ships in
 * no artifact.
 */
public class FMLCommonHandler {

    public static Object BUS;

    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();

    public static FMLCommonHandler instance() {
        return INSTANCE;
    }

    public Object bus() {
        return BUS;
    }

}
