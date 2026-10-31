package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private ECPluginData plugin;

    @ScreenLayout(title = "Reloadable", rows = 3)
    public static class ReloadableLayout extends Layout {

        @IconData(slot = {0})
        public Icon BUTTON = Icon.of(ItemSpec.of("minecraft:paper", 1));
    }

    @ScreenLayout(title = "Optional", rows = 3)
    public static class ModdedLayout extends Layout {

        @IconData(slot = {0})
        public Icon MOD_ITEM = Icon.of(ItemSpec.of("minecraft:paper", 1));
    }

    @ScreenLayout(title = "Left", rows = 3)
    public static class LeftLayout extends Layout {

        @IconData(slot = {0})
        public Icon SHARED = Layouts.of(RightLayout.class).getIcon("SHARED");
    }

    @ScreenLayout(title = "Right", rows = 3)
    public static class RightLayout extends Layout {

        @IconData(slot = {0})
        public Icon SHARED = Layouts.of(LeftLayout.class).getIcon("SHARED");
    }

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        plugin = world.getOwner();
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
        Screen<?> screen = Screen.of(3).addComponent(component -> component.render(
                slots -> slots.icon(0, Layouts.of(ReloadableLayout.class).getIcon("BUTTON"))));

        ScreenView view = world.open(screen, world.viewer("Steve"));
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(1);
        assertEquals("minecraft:paper", surface.getSlot(0).getItem().getMaterialId());
        surface.forgetApplies();

        Path file = tempDir.resolve("guis/ReloadableLayout.yml");
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(content.contains("material:minecraft:paper"), content);
        Files.write(file, content.replace("material:minecraft:paper", "material:minecraft:diamond")
                .getBytes(StandardCharsets.UTF_8));

        int refreshed = Layouts.reload(ReloadableLayout.class);
        world.advanceTicks(1);

        assertEquals(1, refreshed, "nobody has to be kicked out of a menu to see a change");
        assertEquals("minecraft:diamond", Layouts.of(ReloadableLayout.class).getIcon("BUTTON")
                .getItem().getMaterialId(), "the cached copy was dropped and the file read again");
        assertEquals("minecraft:diamond", surface.getSlot(0).getItem().getMaterialId());
        assertEquals(1, writesTo(surface, 0), "and the redraw costs the one slot that changed");

        view.close();
        world.advanceTicks(2);
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

    /** How many patches since the last {@code forgetApplies} wrote {@code slot}. */
    private static int writesTo(RecordingSurface surface, int slot) {
        int writes = 0;
        for (List<ElementPatch> apply : surface.getApplies()) {
            for (ElementPatch patch : apply) {
                if (patch.getSlot() == slot) {
                    writes++;
                }
            }
        }
        return writes;
    }

}
