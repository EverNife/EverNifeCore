package br.com.finalcraft.evernifecore.minecraft.gui.model;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a viewer may do inside a screen, decided from the NAMES the platform reports.
 *
 * <p>Names instead of constants is not a stylistic choice: a class that mentions a click type the
 * running server does not have dies in its initializer and takes the framework with it. So the two
 * things worth proving are that an unknown name is denied rather than crashed on, and that naming a
 * modern click type costs nothing on a server that never sends it.</p>
 */
class ClickPolicyTest {

    /** No version of the game has ever sent this - it stands in for a name from the future. */
    private static final String ABSENT_CLICK_TYPE = "SWAP_WITH_SECOND_OFFHAND";

    /** One action name per kind, so a policy can be asked about a kind the way a click asks. */
    private static final Map<ClickKind, String> AN_ACTION_OF_EACH_KIND = new EnumMap<>(ClickKind.class);

    static {
        AN_ACTION_OF_EACH_KIND.put(ClickKind.NOTHING, "NOTHING");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.TAKE, "PICKUP_ALL");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.PLACE, "PLACE_ALL");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.SWAP, "SWAP_WITH_CURSOR");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.DROP, "DROP_ONE_SLOT");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.CLONE, "CLONE_STACK");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.HOTBAR, "HOTBAR_SWAP");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.MOVE_TO_OTHER_INVENTORY, "MOVE_TO_OTHER_INVENTORY");
        AN_ACTION_OF_EACH_KIND.put(ClickKind.COLLECT_TO_CURSOR, "COLLECT_TO_CURSOR");
    }

    @Test
    void theDefaultDeniesEverything() {
        assertTrue(ClickPolicy.DENY_ALL.isDenyAll());
        assertFalse(ClickPolicy.DENY_ALL.allows("LEFT", "PICKUP_ALL"));
        assertFalse(ClickPolicy.DENY_ALL.allows("SHIFT_LEFT", "MOVE_TO_OTHER_INVENTORY"));
        assertFalse(ClickPolicy.DENY_ALL.allows("DOUBLE_CLICK", "COLLECT_TO_CURSOR"));
        assertFalse(ClickPolicy.DENY_ALL.allowsDrag());
    }

    @Test
    void whatIsOpenedUpIsOpenedUpAndNothingElseIs() {
        ClickPolicy takeOnly = ClickPolicy.builder().allowTake().build();

        assertTrue(takeOnly.allows("LEFT", "PICKUP_ALL"));
        assertTrue(takeOnly.allows("RIGHT", "PICKUP_HALF"));
        assertFalse(takeOnly.allows("LEFT", "PLACE_ONE"), "placing was never opened up");
        assertFalse(takeOnly.allows("SHIFT_LEFT", "MOVE_TO_OTHER_INVENTORY"));
        assertFalse(takeOnly.isDenyAll());
    }

    @Test
    void denyTakesBackWhatAllowGave() {
        ClickPolicy policy = ClickPolicy.builder().allowEverything().denyPlace().denyDrag().build();

        assertTrue(policy.allows("LEFT", "PICKUP_ALL"));
        assertFalse(policy.allows("LEFT", "PLACE_ALL"));
        assertFalse(policy.allowsDrag());
    }

    @Test
    void anActionNameThisFrameworkDoesNotRecogniseIsDenied() {
        ClickPolicy everything = ClickPolicy.builder().allowEverything().build();

        assertEquals(ClickKind.UNKNOWN, ClickKind.ofAction("SOME_ACTION_FROM_THE_FUTURE"));
        assertFalse(everything.allows("LEFT", "SOME_ACTION_FROM_THE_FUTURE"));
        assertFalse(everything.allows("LEFT", null));
    }

    @Test
    void aClickTypeNameThisFrameworkDoesNotKnowIsDenied() {
        ClickPolicy takeOnly = ClickPolicy.builder().allowTake().build();

        assertFalse(takeOnly.allows("SWAP_OFFHAND", "PICKUP_ALL"),
                "a click type the framework never listed stays denied even for an allowed kind");
        assertFalse(takeOnly.allows(ABSENT_CLICK_TYPE, "PICKUP_ALL"));
        assertFalse(takeOnly.allows(null, "PICKUP_ALL"));
    }

    @Test
    void namingAModernClickTypeCostsNothingOnAServerThatNeverSendsIt() {
        assertThrows(IllegalArgumentException.class, () -> ClickType.valueOf(ABSENT_CLICK_TYPE),
                "the premise of this test: no ClickType constant carries this name");

        ClickPolicy policy = ClickPolicy.builder()
                .allowTake()
                .allowIfPresent(ABSENT_CLICK_TYPE)
                .build();

        assertTrue(policy.allows(ABSENT_CLICK_TYPE, "PICKUP_ALL"), "honoured by name when it does arrive");
        assertTrue(policy.allows("LEFT", "PICKUP_ALL"), "and nothing else changed");
        assertFalse(policy.allows(ABSENT_CLICK_TYPE, "PLACE_ONE"), "the kind axis still applies");
    }

    @Test
    void anEmptyOrBlankNameIsIgnoredRatherThanStored() {
        ClickPolicy policy = ClickPolicy.builder().allowTake().allowIfPresent("  ").allowIfPresent(null).build();

        assertFalse(policy.allows("  ", "PICKUP_ALL"));
        assertTrue(policy.allows("LEFT", "PICKUP_ALL"));
    }

    @Test
    void unknownCannotBeAllowedIntoExistence() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ClickPolicy.builder().allow(ClickKind.UNKNOWN));
        assertTrue(error.getMessage().contains("denying"), error.getMessage());
    }

    @Test
    void everyActionNameOfTheFloorVersionIsClassified() {
        assertEquals(ClickKind.NOTHING, ClickKind.ofAction("NOTHING"));
        assertEquals(ClickKind.TAKE, ClickKind.ofAction("PICKUP_ALL"));
        assertEquals(ClickKind.TAKE, ClickKind.ofAction("PICKUP_SOME"));
        assertEquals(ClickKind.PLACE, ClickKind.ofAction("PLACE_ALL"));
        assertEquals(ClickKind.SWAP, ClickKind.ofAction("SWAP_WITH_CURSOR"));
        assertEquals(ClickKind.DROP, ClickKind.ofAction("DROP_ONE_SLOT"));
        assertEquals(ClickKind.DROP, ClickKind.ofAction("DROP_ALL_CURSOR"));
        assertEquals(ClickKind.CLONE, ClickKind.ofAction("CLONE_STACK"));
        assertEquals(ClickKind.HOTBAR, ClickKind.ofAction("HOTBAR_SWAP"));
        assertEquals(ClickKind.HOTBAR, ClickKind.ofAction("HOTBAR_MOVE_AND_READD"));
        assertEquals(ClickKind.MOVE_TO_OTHER_INVENTORY, ClickKind.ofAction("MOVE_TO_OTHER_INVENTORY"));
        assertEquals(ClickKind.COLLECT_TO_CURSOR, ClickKind.ofAction("COLLECT_TO_CURSOR"));
    }

    @Test
    void allowEverythingLeavesNothingOut() {
        ClickPolicy everything = ClickPolicy.builder().allowEverything().build();

        assertTrue(everything.allows("MIDDLE", "CLONE_STACK"),
                "everything means everything - a name that opens all but one kind would be a lie");
        assertTrue(everything.allowsDrag());
        for (Map.Entry<ClickKind, String> entry : AN_ACTION_OF_EACH_KIND.entrySet()) {
            assertTrue(everything.allows("LEFT", entry.getValue()),
                    entry.getKey() + " was left out of allowEverything");
        }
    }

    @Test
    void theSweepAboveKnowsEveryKindThereIs() {
        for (ClickKind kind : ClickKind.values()) {
            if (kind == ClickKind.UNKNOWN || kind == ClickKind.DRAG) {
                continue;//neither is reachable from an action name: one is the refusal, the other a gesture
            }
            assertTrue(AN_ACTION_OF_EACH_KIND.containsKey(kind), kind + " is a new kind with no action "
                    + "name here, so the sweep over allowEverything silently stopped covering it");
        }
        for (Map.Entry<ClickKind, String> entry : AN_ACTION_OF_EACH_KIND.entrySet()) {
            assertEquals(entry.getKey(), ClickKind.ofAction(entry.getValue()));
        }
    }

    @Test
    void eachCallEditsWhatTheOneBeforeItLeft() {
        ClickPolicy allButCloning = ClickPolicy.builder().allowEverything().denyCreativeClone().build();

        assertFalse(allButCloning.allows("MIDDLE", "CLONE_STACK"));
        assertTrue(allButCloning.allows("LEFT", "PICKUP_ALL"));
        assertTrue(allButCloning.allows("LEFT", "PLACE_ALL"));
        assertTrue(allButCloning.allowsDrag());

        ClickPolicy cloningOnly = ClickPolicy.builder()
                .allowEverything()
                .denyEverything()
                .allowCreativeClone()
                .build();

        assertTrue(cloningOnly.allows("MIDDLE", "CLONE_STACK"), "the last call is what stands");
        assertFalse(cloningOnly.allows("LEFT", "PICKUP_ALL"), "denyEverything wiped what allowEverything gave");
        assertFalse(cloningOnly.allows("LEFT", "PLACE_ALL"));
        assertFalse(cloningOnly.allowsDrag());
        assertFalse(cloningOnly.isDenyAll());
    }

    @Test
    void denyEverythingShutsAPolicyBackDown() {
        ClickPolicy policy = ClickPolicy.builder().allowEverything().denyEverything().build();

        assertTrue(policy.isDenyAll());
        assertFalse(policy.allows("LEFT", "PICKUP_ALL"));
        assertFalse(policy.allowsDrag());
    }

}
