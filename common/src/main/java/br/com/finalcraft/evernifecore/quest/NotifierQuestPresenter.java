package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.notify.HudSounds;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.channel.ActionBarChannel;
import br.com.finalcraft.evernifecore.notify.channel.BossBarChannel;
import br.com.finalcraft.evernifecore.notify.channel.TitleChannel;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointException;
import br.com.finalcraft.evernifecore.waypoint.WaypointId;
import br.com.finalcraft.evernifecore.waypoint.WaypointTarget;
import br.com.finalcraft.evernifecore.waypoint.Waypoints;

/**
 * Every engine's default {@link QuestPresenter}, over the {@link Notifier}: progress as a {@link NotifyKind#PROGRESS}
 * notification (the shipped chain starts at the {@link ActionBarChannel}), completion as {@link NotifyKind#COMPLETE}
 * with the level-up sound (the {@link TitleChannel} plus sound), and the tracked quest as a {@link NotifyKind#TRACKED}
 * notification carrying the stage's progress (the {@link BossBarChannel} first), kept up to date while it moves. The
 * admin's {@code Settings.Notify.chains} decide the channels; the presenter only says what kind of moment it is.
 *
 * <p>For the tracked quest, every objective of the current stage with a place ({@link ObjectiveType#targetOf}) gets a
 * {@link Waypoint}; it goes when the objective, the stage or the quest ends, or the player stops tracking.</p>
 */
public class NotifierQuestPresenter implements QuestPresenter {

    @Override
    public void onProgress(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
        Notifier.get().notify(viewer, Notification.of(NotifyKind.PROGRESS).id("quest-progress:" + definition.getId())
                .text(definition.getName() + "§7: §f" + label(objective) + " §e" + state.getCount(objective.getId()) + "/" + objective.getGoal())
                .build());
        refreshTracked(viewer, engine, definition, state);
    }

    @Override
    public void onObjectiveComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
        Waypoints.get().hide(viewer, waypointOf(engine, definition, objective));
        refreshTracked(viewer, engine, definition, state);
    }

    @Override
    public void onStageComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Stage stage) {
        hideWaypoints(viewer, engine, definition);
        if (state.getStageId() != null && isTracked(viewer, engine, definition)) {
            showWaypoints(viewer, engine, definition, state);
        }
    }

    @Override
    public void onQuestComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        Notifier.get().notify(viewer, Notification.of(NotifyKind.COMPLETE).id("quest-complete:" + definition.getId())
                .title(definition.getName()).subtitle("§a✔").text("§a✔ " + definition.getName()).sound(HudSounds.LEVEL_UP).build());
        end(viewer, engine, definition);
    }

    @Override
    public void onQuestFail(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        Notifier.get().notify(viewer, Notification.of(NotifyKind.DENY).id("quest-fail:" + definition.getId())
                .text("§c✘ " + definition.getName()).build());
        end(viewer, engine, definition);
    }

    @Override
    public void onQuestAbandon(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        end(viewer, engine, definition);
    }

    @Override
    public void onTrack(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        Notifier.get().notify(viewer, tracked(definition, state));
        hideWaypoints(viewer, engine, definition);
        showWaypoints(viewer, engine, definition, state);
    }

    @Override
    public void onUntrack(FPlayer viewer, QuestEngine engine, QuestId quest) {
        Notifier.get().clear(viewer, trackedId(quest));
        QuestDefinition definition = engine.get(quest);
        if (definition != null) {
            hideWaypoints(viewer, engine, definition);
        }
    }

    // ------------------------------------------------------------------

    private void end(FPlayer viewer, QuestEngine engine, QuestDefinition definition) {
        if (isTracked(viewer, engine, definition)) {
            Notifier.get().clear(viewer, trackedId(definition.getId()));
        }
        hideWaypoints(viewer, engine, definition);
    }

    private void refreshTracked(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        if (isTracked(viewer, engine, definition)) {
            Notifier.get().notify(viewer, tracked(definition, state));
        }
    }

    private static boolean isTracked(FPlayer viewer, QuestEngine engine, QuestDefinition definition) {
        return definition.getId().equals(engine.getTracked(viewer.getUniqueId()));
    }

    private static Notification tracked(QuestDefinition definition, QuestState state) {
        return Notification.of(NotifyKind.TRACKED).id(trackedId(definition.getId())).title(definition.getName())
                .text(definition.getName()).progress((float) definition.getStageProgress(state)).build();
    }

    private static String trackedId(QuestId quest) {
        return "quest-tracked:" + quest;
    }

    private static String label(Objective objective) {
        return objective.getDescription().isEmpty() ? objective.getId() : objective.getDescription();
    }

    private static void showWaypoints(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        Stage stage = state.getStageId() == null ? null : definition.getStage(state.getStageId());
        if (stage == null) {
            return;
        }
        for (Objective objective : stage.getObjectives().getObjectives()) {
            ObjectiveState progress = state.getObjectives().get(objective.getId());
            ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
            WorldLocPos target = filter == null || (progress != null && progress.isDone()) ? null : filter.targetOf();
            if (target == null) {
                continue;
            }
            try {
                Waypoints.get().show(viewer, Waypoint.of(waypointOf(engine, definition, objective), WaypointTarget.at(target))
                        .label(label(objective)).build());
            } catch (WaypointException noChannelHere) {
                return; //this platform draws no waypoint at all: the quest goes on without one
            }
        }
    }

    private static void hideWaypoints(FPlayer viewer, QuestEngine engine, QuestDefinition definition) {
        for (Objective objective : definition.getObjectives()) {
            Waypoints.get().hide(viewer, waypointOf(engine, definition, objective));
        }
    }

    private static WaypointId waypointOf(QuestEngine engine, QuestDefinition definition, Objective objective) {
        return WaypointId.of(engine.getOwner(), "quest-" + definition.getId().getName() + "-" + objective.getId());
    }
}
