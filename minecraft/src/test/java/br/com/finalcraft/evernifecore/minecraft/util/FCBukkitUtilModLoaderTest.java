package br.com.finalcraft.evernifecore.minecraft.util;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which mod loader each era's server is asked through, and what a fake player looks like to it -
 * posed with stand-in classes under the real names, since no JVM running this suite carries Forge.
 */
class FCBukkitUtilModLoaderTest {

    private static final String NEOFORGE_MOD_LIST = "net.neoforged.fml.ModList";
    private static final String FORGE_MOD_LIST = "net.minecraftforge.fml.ModList";
    private static final String LEGACY_LOADER = "net.minecraftforge.fml.common.Loader";

    @Test
    void aNeoForgeServerIsAskedThroughItsOwnModList() {
        Function<String, Boolean> isModLoaded = FCBukkitUtil.detectModLoader(runtimeWith(NEOFORGE_MOD_LIST, StandInModList.class));

        assertNotNull(isModLoaded, "NeoForge renamed the package, and a server running it is still a modded one");
        assertTrue(isModLoaded.apply("somemod"));
        assertFalse(isModLoaded.apply("othermod"));
    }

    @Test
    void aModernForgeServerIsAskedThroughTheForgeModList() {
        Function<String, Boolean> isModLoaded = FCBukkitUtil.detectModLoader(runtimeWith(FORGE_MOD_LIST, StandInModList.class));

        assertNotNull(isModLoaded);
        assertTrue(isModLoaded.apply("somemod"));
    }

    @Test
    void aLegacyForgeServerIsAskedThroughTheStaticLoader() {
        Function<String, Boolean> isModLoaded = FCBukkitUtil.detectModLoader(runtimeWith(LEGACY_LOADER, StandInStaticLoader.class));

        assertNotNull(isModLoaded);
        assertTrue(isModLoaded.apply("somemod"));
        assertFalse(isModLoaded.apply("othermod"));
    }

    @Test
    void aServerWithNoLoaderIsNotModded() {
        assertNull(FCBukkitUtil.detectModLoader(name -> null));
    }

    @Test
    void aLoaderThatCannotBeAskedIsReportedNotThrown() {
        assertNull(FCBukkitUtil.detectModLoader(runtimeWith(NEOFORGE_MOD_LIST, Object.class)),
                "a ModList with neither get() nor isLoaded() leaves the server unmodded instead of failing the class");
    }

    @Test
    void theFakePlayerBaseIsFoundUnderEitherVocabulary() {
        List<Class<?>> types = FCBukkitUtil.resolveFakePlayerTypes(
                runtimeWith("net.neoforged.neoforge.common.util.FakePlayer", StandInFakePlayer.class));

        assertEquals(Collections.singletonList(StandInFakePlayer.class), types);
        assertTrue(FCBukkitUtil.resolveFakePlayerTypes(name -> null).isEmpty());
    }

    @Test
    void aPlayerIsFakeWhenTheEntityBehindItExtendsTheFakePlayerBase() {
        List<Class<?>> types = Collections.singletonList(StandInFakePlayer.class);

        assertTrue(FCBukkitUtil.isFakePlayer(playerBackedBy(new StandInQuarry()), types));
        assertFalse(FCBukkitUtil.isFakePlayer(playerBackedBy(new Object()), types), "a connected player's entity");
        assertFalse(FCBukkitUtil.isFakePlayer(playerBackedBy(new StandInQuarry()), Collections.emptyList()),
                "with no loader there is no base class to extend");
    }

    private static Function<String, Class<?>> runtimeWith(String name, Class<?> type) {
        Map<String, Class<?>> classes = new HashMap<>();
        classes.put(name, type);
        return classes::get;
    }

    private static Player playerBackedBy(Object handle) {
        return (Player) Proxy.newProxyInstance(ServerBackedPlayer.class.getClassLoader(),
                new Class<?>[]{ServerBackedPlayer.class},
                (proxy, method, args) -> method.getName().equals("getHandle") ? handle : null);
    }

    /** A Bukkit player with CraftBukkit's door to the entity behind it. Package-private on purpose: the
     *  proxy class then lives in this package, where reflection may call it. */
    interface ServerBackedPlayer extends Player {
        Object getHandle();
    }

    public static class StandInModList {
        private static final StandInModList INSTANCE = new StandInModList();

        public static StandInModList get() {
            return INSTANCE;
        }

        public boolean isLoaded(String modId) {
            return "somemod".equals(modId);
        }
    }

    public static class StandInStaticLoader {
        public static boolean isModLoaded(String modId) {
            return "somemod".equals(modId);
        }
    }

    public static class StandInFakePlayer {
    }

    public static class StandInQuarry extends StandInFakePlayer {
    }
}
