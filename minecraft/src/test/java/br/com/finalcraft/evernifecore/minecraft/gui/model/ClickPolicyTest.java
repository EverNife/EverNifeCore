package br.com.finalcraft.evernifecore.minecraft.gui.model;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

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
    void creativeCloningStaysShutUntilItIsNamedOnItsOwn() {
        assertFalse(ClickPolicy.builder().allowEverything().build().allows("MIDDLE", "CLONE_STACK"),
                "allowEverything opens what a chest does, and duplicating an item is not that");
        assertTrue(ClickPolicy.builder().allow(ClickKind.CLONE).build().allows("MIDDLE", "CLONE_STACK"));
    }

}
