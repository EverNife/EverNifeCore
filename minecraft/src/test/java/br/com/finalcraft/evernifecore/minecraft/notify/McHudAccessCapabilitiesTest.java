package br.com.finalcraft.evernifecore.minecraft.notify;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.notify.IHudAccess.Capability;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Bukkit screen answers per server version - the table the in-server HudSuite reads back. */
class McHudAccessCapabilitiesTest {

    private static Set<Capability> answers(MCDetailedVersion version, boolean necroTempus) {
        McHudAccess hud = new McHudAccess(version, necroTempus);
        Set<Capability> supported = EnumSet.noneOf(Capability.class);
        for (Capability capability : Capability.values()) {
            if (hud.supports(capability)) {
                supported.add(capability);
            }
        }
        return supported;
    }

    @Test
    void crucibleWithoutNecroTempusHasOnlyTheScoreboardSoundAndCompass() {
        assertEquals(EnumSet.of(Capability.SIDEBAR, Capability.TRACKER, Capability.SOUND, Capability.COMPASS),
                answers(MCDetailedVersion.v1_7_R4, false));
    }

    @Test
    void necroTempusBringsActionBarAndTitleTo1710ButNoBossBarOrToast() {
        Set<Capability> supported = answers(MCDetailedVersion.v1_7_R4, true);

        assertTrue(supported.containsAll(Arrays.asList(Capability.ACTION_BAR, Capability.TITLE)), supported.toString());
        assertFalse(supported.contains(Capability.BOSS_BAR), supported.toString());
        assertFalse(supported.contains(Capability.TOAST), supported.toString());
    }

    @Test
    void from1122EveryCapabilityIsThere() {
        for (MCDetailedVersion version : Arrays.asList(MCDetailedVersion.v1_12_R1, MCDetailedVersion.v1_16_R3, MCDetailedVersion.v1_21_R1)) {
            assertEquals(EnumSet.allOf(Capability.class), answers(version, false), version.name());
        }
    }

    @Test
    void theBossBarArrivesAt19AndTheToastAt112() {
        assertFalse(answers(MCDetailedVersion.v1_8_R3, false).contains(Capability.BOSS_BAR));
        assertTrue(answers(MCDetailedVersion.v1_9_R1, false).contains(Capability.BOSS_BAR));
        assertFalse(answers(MCDetailedVersion.v1_11_R1, false).contains(Capability.TOAST));
        assertTrue(answers(MCDetailedVersion.v1_12_R1, false).contains(Capability.TOAST));
    }

    @Test
    void anUnsupportedCapabilityRefusesBeforeReadingThePlayer() {
        McHudAccess crucible = new McHudAccess(MCDetailedVersion.v1_7_R4, false);

        UnsupportedOperationException refused = assertThrows(UnsupportedOperationException.class,
                () -> crucible.sendActionBar(null, FancyText.of("mana")));
        assertTrue(refused.getMessage().contains("ACTION_BAR"), refused.getMessage());
    }

    @Test
    void theToastFrameFollowsTheStyleAndTheIconKeyFollowsTheEra() {
        assertEquals("task", McHudAccess.frameOf(ToastStyle.INFO));
        assertEquals("task", McHudAccess.frameOf(ToastStyle.WARNING));
        assertEquals("goal", McHudAccess.frameOf(ToastStyle.SUCCESS));
        assertEquals("challenge", McHudAccess.frameOf(ToastStyle.DANGER));

        String legacy = new McHudAccess(MCDetailedVersion.v1_12_R1, false).toastJson(FancyText.of("t"), FancyText.of("d"), ToastStyle.SUCCESS);
        String modern = new McHudAccess(MCDetailedVersion.v1_21_R1, false).toastJson(FancyText.of("t"), FancyText.of("d"), ToastStyle.SUCCESS);
        assertTrue(legacy.contains("\"icon\":{\"item\":\"minecraft:book\"}") && legacy.contains("\"frame\":\"goal\""), legacy);
        assertTrue(modern.contains("\"icon\":{\"id\":\"minecraft:book\"}"), modern);
    }
}
