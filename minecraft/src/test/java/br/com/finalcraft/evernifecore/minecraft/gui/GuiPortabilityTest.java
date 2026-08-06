package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two boundaries the gui framework keeps by discipline rather than by abstraction, and which only a
 * scan can keep honest.
 *
 * <p>The first is the <b>platform-free core</b>: {@code model}, {@code state} and {@code nav} may not
 * touch Bukkit. Reading the imports would not prove it - a fully qualified name in the middle of a
 * method has no import - so what is read here is the compiled constant pool, where every type a class
 * mentions is spelled out.</p>
 *
 * <p>The second is the <b>1.7.10 floor</b>: naming a click type or an inventory action that an old
 * server does not carry kills the class in its initializer and takes the framework with it. That one
 * is read from the sources, because it is about what was written, and the fix is to compare the name
 * as text instead.</p>
 */
class GuiPortabilityTest {

    /** Packages the whole point of which is that a Hytale port could compile them unchanged. */
    private static final List<String> PLATFORM_FREE_PACKAGES = Arrays.asList("model", "state", "nav");

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

    // -----------------------------------------------------------------------------------------------------------------
    //  The platform-free core, read from the bytecode
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theCoreMentionsBukkitNowhere() throws IOException {
        Path guiClasses = compiledGuiPackage();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (String packageName : PLATFORM_FREE_PACKAGES) {
            Path directory = guiClasses.resolve(packageName);
            assertTrue(Files.isDirectory(directory), "gui/" + packageName + " was not compiled to " + directory);
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                    scanned++;
                    if (mentionsBukkit(Files.readAllBytes(file))) {
                        offenders.add(guiClasses.relativize(file).toString());
                    }
                }
            }
        }

        assertTrue(scanned >= 12, "only " + scanned + " classes were scanned; the scan found nothing to check");
        assertTrue(offenders.isEmpty(), "these classes name a Bukkit type, which is what keeps the gui core "
                + "portable: " + offenders);
    }

    /** The constant pool spells every type out in ASCII, whether it came from an import or not. */
    private static boolean mentionsBukkit(byte[] classFile) {
        byte[] needle = "org/bukkit".getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int start = 0; start <= classFile.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (classFile[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The 1.7.10 floor, read from the sources
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void noSourceNamesAConstantTheOldestSupportedServerLacks() throws IOException {
        Path sources = guiSources();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                scanned++;
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
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

        assertTrue(scanned >= 20, "only " + scanned + " sources were scanned; the scan found nothing to check");
        assertTrue(offenders.isEmpty(), "a server on the version floor has no such constant, and merely "
                + "mentioning one kills the declaring class in its initializer - compare the name as text "
                + "instead: " + offenders);
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

    private static Path compiledGuiPackage() {
        URL marker = SlotSet.class.getResource("SlotSet.class");
        assertTrue(marker != null, "the gui classes are not on the test classpath as files");
        try {
            //.../gui/model/SlotSet.class -> .../gui
            return Paths.get(marker.toURI()).getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The compiled gui package could not be located at " + marker, e);
        }
    }

    private static Path guiSources() {
        String suffix = "src/main/java/br/com/finalcraft/evernifecore/minecraft/gui";
        List<String> candidates = Arrays.asList(suffix, "minecraft/" + suffix, "../minecraft/" + suffix);
        for (String candidate : candidates) {
            Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("The gui sources were not found from [" + System.getProperty("user.dir")
                + "]; tried " + Collections.unmodifiableList(candidates));
    }

}
