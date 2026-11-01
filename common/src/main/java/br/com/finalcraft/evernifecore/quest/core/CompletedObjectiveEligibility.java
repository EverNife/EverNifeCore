package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.reward.Eligibility;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardPiece;
import br.com.finalcraft.evernifecore.logic.reward.Rewards;
import br.com.finalcraft.evernifecore.quest.QuestContribution;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.util.UUID;

/**
 * The reward piece {@code completed-objective: <id>}: eligible is a member who contributed to that objective of the
 * quest being paid - read from the {@link QuestState} the engine hands as the {@code quest} attribute. On a player's
 * own quest, the player once the objective is done. Outside a quest's payment, nobody.
 */
public final class CompletedObjectiveEligibility implements Eligibility {

    public static final String NAME = "completed-objective";

    private final String objectiveId;

    public CompletedObjectiveEligibility(String objectiveId) {
        this.objectiveId = objectiveId;
    }

    /** Registers the piece in {@link Rewards#registry()} under {@code core}. */
    public static void register(ECPluginData core) {
        Rewards.registry().register(core, TypeDescriptor.<RewardPiece>of("evernifecore:" + NAME)
                .describe("The members who contributed to an objective of the quest being paid.")
                .param(ParamSpec.required("id", ParamSpec.Kind.STRING, "The objective's id in the quest, e.g. mobs."))
                .factory(params -> new CompletedObjectiveEligibility(params.getString("id")))
                .build());
    }

    @Override
    public boolean isEligible(UUID member, RewardContext ctx) {
        Object attribute = ctx.getAttribute("quest");
        if (!(attribute instanceof QuestState)) {
            return false;
        }
        QuestState state = (QuestState) attribute;
        QuestContribution contribution = state.getContributions().get(member.toString());
        if (contribution != null && contribution.getCount(objectiveId) > 0) {
            return true;
        }
        ProgressOwner owner = ctx.getOwner();
        return owner.isKind(ProgressOwner.PLAYER) && owner.getId().equals(member.toString()) && state.getObjectives().containsKey(objectiveId)
                && state.getObjectives().get(objectiveId).isDone();
    }

    @Override
    public String toString() {
        return NAME + ": " + objectiveId;
    }
}
