package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a layout comes from, when it is read again, and what it does instead of failing.
 *
 * <p>Each layout loads on its own, the first time it is asked for. That buys a framework with no
 * initialization order to get right, and it costs exactly one thing: two layouts that read each other
 * would never finish. That refusal is named here, and so is its opposite - a screen this server has no
 * business showing is absent rather than broken.</p>
 */
class LayoutsTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private ECPluginData plugin;

    @GuiLayout(title = "Reloadable", rows = 3)
    public static class ReloadableLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon BUTTON = Icon.of(new ItemStack(Material.PAPER));
    }

    @GuiLayout(title = "Optional", rows = 3)
    public static class ModdedLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon MOD_ITEM = Icon.of(new ItemStack(Material.PAPER));
    }

    @GuiLayout(title = "Left", rows = 3)
    public static class LeftLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon SHARED = Layouts.of(RightLayout.class).getIcon("SHARED");
    }

    @GuiLayout(title = "Right", rows = 3)
    public static class RightLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon SHARED = Layouts.of(LeftLayout.class).getIcon("SHARED");
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        plugin = ECPluginManager.getOrCreateECorePluginData(new Object());
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        if (world != null) {
            world.close();
        }
    }

    @Test
    void aLayoutIsReadOnceAndHandedOutAgainFromThenOn() {
        ReloadableLayout first = Layouts.of(ReloadableLayout.class);

        assertSame(first, Layouts.of(ReloadableLayout.class));
        assertTrue(Layouts.getRegistered(plugin).contains(ReloadableLayout.class));
        assertEquals(Optional.of(ReloadableLayout.class),
                Layouts.findRegistered(plugin, "reloadablelayout"),
                "the name a command takes is the class name, case and all being the admin's business");
    }

    @Test
    void reloadingDropsTheCachedCopyAndRedrawsTheScreensThatAreOpen() throws IOException {
        //a screen that reads the layout while rendering is what makes a reload visible without reopening
        Gui<?> gui = Gui.of(3).component(component -> component.render(
                slots -> slots.icon(0, Layouts.of(ReloadableLayout.class).getIcon("BUTTON"))));

        world.openDetachedAndRegistered(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);
        assertEquals(Material.PAPER, surface.getItem(0).getType());
        surface.forgetWrites();

        Path file = tempDir.resolve("guis/ReloadableLayout.yml");
        Files.write(file, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("type:PAPER", "type:DIAMOND").getBytes(StandardCharsets.UTF_8));

        int refreshed = Layouts.reload(ReloadableLayout.class);
        world.advanceTicks(1);

        assertEquals(1, refreshed, "nobody has to be kicked out of a menu to see a change");
        assertEquals(Material.DIAMOND, Layouts.of(ReloadableLayout.class).getIcon("BUTTON")
                .getItemStack().getType(), "the cached copy was dropped and the file read again");
        assertEquals(Material.DIAMOND, surface.getItem(0).getType());
        assertEquals(1, surface.getWriteCount(0), "and the redraw costs the one slot that changed");
    }

    @Test
    void aLayoutThisServerHasNoBusinessShowingIsAbsentRatherThanBroken() {
        Optional<ModdedLayout> absent = Layouts.ifAvailable(ModdedLayout.class, () -> false);

        assertFalse(absent.isPresent(), "empty is an answer the caller has to handle");
        assertFalse(Files.exists(tempDir.resolve("guis/ModdedLayout.yml")),
                "a layout that was never asked for is not read, so it seeds no file either");
        assertFalse(Layouts.getRegistered(plugin).contains(ModdedLayout.class));

        assertTrue(Layouts.ifAvailable(ModdedLayout.class, () -> true).isPresent(),
                "and the same call with the condition met reads it normally");
    }

    @Test
    void twoLayoutsThatReadEachOtherAreRefusedByNameInsteadOfSpinningForever() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> Layouts.of(LeftLayout.class));

        //the cycle itself, not a wrapper around it: whoever reads the first line has to read the reason
        String explanation = refusal.getMessage();
        assertTrue(explanation.contains(LeftLayout.class.getName()), explanation);
        assertTrue(explanation.contains(RightLayout.class.getName()), explanation);
        assertTrue(explanation.contains("read each other while loading"), explanation);
        assertTrue(explanation.contains("Share icons through inheritance"),
                "and it has to name the way out: " + explanation);
    }

}
