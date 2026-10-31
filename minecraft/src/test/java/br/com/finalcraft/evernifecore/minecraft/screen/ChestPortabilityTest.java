package br.com.finalcraft.evernifecore.minecraft.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.7.10 floor of the chest renderer, which it keeps by discipline rather than by abstraction and which only a scan
 * can keep honest: naming a click type or an inventory action that an old server does not carry kills the class in its
 * initializer and takes the renderer with it. It is read from the sources, because it is about what was written, and
 * the fix is to compare the name as text instead.
 *
 * <p>The platform-free half of the screen framework needs no scan: it lives in {@code :common}, which cannot see
 * Bukkit at all, so the compiler keeps that boundary.</p>
 */
class ChestPortabilityTest {

    private static final Set<String> CLICK_TYPES_OF_THE_FLOOR = new HashSet<>(Arrays.asList(
            "LEFT", "SHIFT_LEFT", "RIGHT", "SHIFT_RIGHT",
            "WINDOW_BORDER_LEFT", "WINDOW_BORDER_RIGHT",
            "MIDDLE", "NUMBER_KEY", "DOUBLE_CLICK",
            "DROP", "CONTROL_DROP", "CREATIVE", "UNKNOWN"
    ));

    private static final Set<String> INVENTORY_ACTIONS_OF_THE_FLOOR = new HashSet<>(Arrays.asList(
            "NOTHING", "PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE",
            "PLACE_ALL", "PLACE_SOME", "PLACE_ONE", "SWAP_WITH_CURSOR",
            "DROP_ALL_CURSOR", "DROP_ONE_CURSOR", "DROP_ALL_SLOT", "DROP_ONE_SLOT",
            "MOVE_TO_OTHER_INVENTORY", "HOTBAR_MOVE_AND_READD", "HOTBAR_SWAP",
            "CLONE_STACK", "COLLECT_TO_CURSOR", "UNKNOWN"
    ));

    private static final Pattern PLATFORM_CONSTANT =
            Pattern.compile("\\b(ClickType|InventoryAction)\\.([A-Z][A-Z0-9_]*)\\b");

    private static final Pattern PLATFORM_ENUM_STATIC_IMPORT =
            Pattern.compile("import\\s+static\\s+\\S*\\b(ClickType|InventoryAction)\\.\\S+;");

    // -----------------------------------------------------------------------------------------------------------------
    //  The 1.7.10 floor, read from the sources
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void noSourceNamesAConstantTheOldestSupportedServerLacks() throws IOException {
        Path sources = chestSources();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                scanned++;
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (String staticImport : platformEnumStaticImportsIn(text)) {
                    offenders.add(sources.relativize(file) + " -> " + staticImport);
                }
                for (String reference : platformConstantsIn(text)) {
                    String[] parts = reference.split("\\.");
                    Set<String> floor = parts[0].equals("ClickType")
                            ? CLICK_TYPES_OF_THE_FLOOR
                            : INVENTORY_ACTIONS_OF_THE_FLOOR;
                    if (!floor.contains(parts[1])) {
                        offenders.add(sources.relativize(file) + " -> " + reference);
                    }
                }
            }
        }

        assertTrue(scanned >= 9,"only " + scanned + " sources were scanned, and there are more than that - "
                + "a scan that stopped finding them proves nothing");
        assertTrue(offenders.isEmpty(), "a server on the version floor has no such constant, and merely "
                + "mentioning one kills the declaring class in its initializer - compare the name as text "
                + "instead. A static import is refused outright: it lets the constant be written bare, "
                + "where this scan cannot see it: " + offenders);
    }

    // The scan reads names, so the one thing that would hide a constant from it is a spelling that
    // does not carry the enum: a static import lets SWAP_OFFHAND be written bare. Refusing the import
    // is what keeps the qualified form the only form there is.
    @Test
    void theScanWouldCatchAStaticImportThatHidesTheEnumName() {
        assertEquals(1, platformEnumStaticImportsIn(
                        "import static org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;").size(),
                "a bare SWAP_OFFHAND after this import names no enum for the scan to read");
        assertEquals(1, platformEnumStaticImportsIn(
                "import static org.bukkit.event.inventory.InventoryAction.*;").size());
        assertTrue(platformEnumStaticImportsIn("import org.bukkit.event.inventory.ClickType;").isEmpty(),
                "importing the type itself is how every one of these is written");
    }

    @Test
    void theScanWouldCatchAConstantFromALaterVersion() {
        String written = "if (event.getClick() == ClickType.SWAP_OFFHAND) { return; }";
        assertTrue(platformConstantsIn(written).contains("ClickType.SWAP_OFFHAND"));
        assertTrue(platformConstantsIn("ClickType clickType, InventoryAction action").isEmpty(),
                "naming the TYPE is fine - only a constant of it is the hazard");
        assertTrue(platformConstantsIn("{@code SWAP_OFFHAND} arrives on 1.16").isEmpty(),
                "prose about a constant is not a reference to it");
    }

    /** A static import of either enum, which is the one way a constant can be written without its name. */
    private static List<String> platformEnumStaticImportsIn(String source) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLATFORM_ENUM_STATIC_IMPORT.matcher(source);
        while (matcher.find()) {
            found.add(matcher.group().trim());
        }
        return found;
    }

    private static List<String> platformConstantsIn(String source) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLATFORM_CONSTANT.matcher(source);
        while (matcher.find()) {
            if (!matcher.group(2).equals("class")) {
                found.add(matcher.group(1) + "." + matcher.group(2));
            }
        }
        return found;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Finding the tree
    // -----------------------------------------------------------------------------------------------------------------

    private static Path chestSources() {
        String suffix = "src/main/java/br/com/finalcraft/evernifecore/minecraft/screen";
        List<String> candidates = Arrays.asList(suffix, "minecraft/" + suffix, "../minecraft/" + suffix);
        for (String candidate : candidates) {
            Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("The chest renderer sources were not found from [" + System.getProperty("user.dir")
                + "]; tried " + Collections.unmodifiableList(candidates));
    }

}
