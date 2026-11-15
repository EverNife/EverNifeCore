package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.plugin.RegionSettings;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import br.com.finalcraft.evernifecore.region.visual.RegionVisualizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the region verbs take from the settings of the node's manager: how long a view lasts, what a selection leaves unset. */
class RegionNodeSettingsTest extends RegionCommandFixture {

    @BeforeEach
    void grant() {
        prot.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        steve.grant("fp.region");
    }

    @AfterEach
    void visualizerOff() {
        RegionVisualizer.get().hide(steve);
        ECSettings.REGIONS_VISUALIZER_ENABLED = false;
    }

    private FinalCMDPluginCommand mount(SelectionSpec spec) {
        return harness.register(new Fp_Cmd(new RegionCommandNode(prot.getManager(), spec)));
    }

    private void advance(Duration by) {
        prot.advance(by);
        prot.runDue();
    }

    @Test
    void aViewLastsTheViewTimeOfTheNodesManager() {
        ECSettings.REGIONS_VISUALIZER_ENABLED = true;
        RegionSettings settings = new RegionSettings();
        settings.setViewTime(Duration.ofMillis(1500));
        prot.getManager().setSettings(settings);

        harness.dispatch(mount(SelectionSpec.cuboid()), steve, "region view arena");
        assertTrue(RegionVisualizer.get().isShowing(steve), steve.getMessages().toString());

        advance(Duration.ofSeconds(1));
        assertTrue(RegionVisualizer.get().isShowing(steve));
        advance(Duration.ofSeconds(1));
        assertFalse(RegionVisualizer.get().isShowing(steve), "1.5 s, not the 10 s built in");
    }

    @Test
    void aSelectionTakesWhatItsSpecLeavesUnsetFromTheManager() {
        RegionSettings settings = new RegionSettings();
        settings.getSelection().setIdleTime(Duration.ofMinutes(1));
        settings.getSelection().setLivePreview(true);
        prot.getManager().setSettings(settings);
        FinalCMDPluginCommand fp = mount(SelectionSpec.cuboid().withTimeout(Duration.ofMinutes(3)));

        harness.dispatch(fp, steve, "region select");
        SelectionSession session = SelectionTool.getSession(steve);
        assertNotNull(session, steve.getMessages().toString());
        assertEquals(Duration.ofMinutes(3), session.getSpec().getTimeout(), "the spec's own value stays");
        assertTrue(session.getSpec().isLivePreview(), "unset in the spec, on in the manager");

        harness.dispatch(fp, steve, "region select polygon");
        SelectionSpec polygon = SelectionTool.getSession(steve).getSpec();
        assertEquals(ShapeType.POLYGON, polygon.getShapeType());
        assertEquals(Duration.ofMinutes(3), polygon.getTimeout());
        assertTrue(polygon.isLivePreview());
    }

    @Test
    void aSpecWithNothingSetRunsOnTheManagersSelection() {
        RegionSettings settings = new RegionSettings();
        settings.getSelection().setIdleTime(Duration.ofSeconds(30));
        prot.getManager().setSettings(settings);

        harness.dispatch(mount(SelectionSpec.cuboid()), steve, "region select");

        assertEquals(Duration.ofSeconds(30), SelectionTool.getSession(steve).getSpec().getTimeout());
        assertFalse(SelectionTool.getSession(steve).getSpec().isLivePreview());
    }
}
