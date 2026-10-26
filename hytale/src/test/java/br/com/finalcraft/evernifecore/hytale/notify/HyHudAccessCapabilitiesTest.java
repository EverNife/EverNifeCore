package br.com.finalcraft.evernifecore.hytale.notify;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.HudSounds;
import br.com.finalcraft.evernifecore.notify.IHudAccess.Capability;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Hytale screen answers: title, toast, tracker and sound; nothing else. */
class HyHudAccessCapabilitiesTest {

    private final HyHudAccess hud = new HyHudAccess();

    @Test
    void hytaleHasTitleToastTrackerAndSoundOnly() {
        Set<Capability> supported = EnumSet.noneOf(Capability.class);
        for (Capability capability : Capability.values()) {
            if (hud.supports(capability)) {
                supported.add(capability);
            }
        }

        assertEquals(EnumSet.of(Capability.TITLE, Capability.TOAST, Capability.TRACKER, Capability.SOUND), supported);
        assertEquals(EnumSet.of(Capability.ACTION_BAR, Capability.BOSS_BAR, Capability.SIDEBAR, Capability.COMPASS),
                EnumSet.copyOf(hud.getUnsupported()));
    }

    @Test
    void whatHytaleLacksRefusesBeforeReadingThePlayer() {
        assertThrows(UnsupportedOperationException.class, () -> hud.sendActionBar(null, FancyText.of("mana")));
        assertThrows(UnsupportedOperationException.class, () -> hud.resetCompassTarget(null));
    }

    @Test
    void ticksBecomeSecondsAndEachToastStyleHasItsNotificationStyle() {
        assertEquals(2.5f, HyHudAccess.seconds(50));
        assertEquals(0f, HyHudAccess.seconds(-5));
        assertEquals(NotificationStyle.Default, HyHudAccess.styleOf(ToastStyle.INFO));
        assertEquals(NotificationStyle.Success, HyHudAccess.styleOf(ToastStyle.SUCCESS));
        assertEquals(NotificationStyle.Warning, HyHudAccess.styleOf(ToastStyle.WARNING));
        assertEquals(NotificationStyle.Danger, HyHudAccess.styleOf(ToastStyle.DANGER));
    }

    @Test
    void aTrackerIsTheSameObjectiveForTheSamePlayerAndId() {
        UUID steve = UUID.randomUUID();

        assertEquals(HyHudAccess.objectiveId(steve, "quest"), HyHudAccess.objectiveId(steve, "quest"));
        assertNotEquals(HyHudAccess.objectiveId(steve, "quest"), HyHudAccess.objectiveId(steve, "other"));
        assertNotEquals(HyHudAccess.objectiveId(steve, "quest"), HyHudAccess.objectiveId(UUID.randomUUID(), "quest"));
    }

    @Test
    void everySoundAliasNamesAShippedSoundEvent() {
        assertEquals(HudSounds.ALL.size(), HyHudAccess.ALIASES.size());
        for (String alias : HudSounds.ALL) {
            assertTrue(HyHudAccess.ALIASES.get(alias).startsWith("SFX_"), alias + " -> " + HyHudAccess.ALIASES.get(alias));
        }
    }
}
