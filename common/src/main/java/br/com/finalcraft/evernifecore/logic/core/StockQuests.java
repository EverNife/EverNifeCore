package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.quest.FailCause;
import br.com.finalcraft.evernifecore.quest.ObjectiveTypes;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.quest.core.CoreObjectiveTypes;
import br.com.finalcraft.evernifecore.quest.ObjectiveType;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/** The stock logic over quests - the {@code quest-status} condition, the {@code quest-times} variable, the quest effects - and the stock objective types. */
final class StockQuests {

    private StockQuests() {
    }

    static void register(ECPluginData core) {
        for (ObjectiveType<?> type : CoreObjectiveTypes.all()) {
            ObjectiveTypes.register(core, type);
        }
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:quest-status")
                .describe("Whether the owner stands at a status of a quest: AVAILABLE, ACTIVE, COMPLETED...")
                .param(questParam())
                .param(ParamSpec.required("is", ParamSpec.Kind.STRING, "The status.").oneOf(statuses()))
                .factory(params -> {
                    QuestId quest = quest(params);
                    QuestStatus wanted = QuestStatus.valueOf(params.getString("is").trim().toUpperCase(Locale.ROOT));
                    return ctx -> {
                        QuestEngine engine = QuestEngine.find(quest.getOwner());
                        QuestStatus status = engine == null ? null : engine.getStatus(ctx.getOwner(), quest);
                        return ConditionResult.of(status == wanted, FancyText.of("The quest " + quest + " is " + status + ", not " + wanted + "."));
                    };
                })
                .build());
        Variables.registry().register(core, TypeDescriptor.<Variable<?>>of("evernifecore:quest-times")
                .describe("How many times the owner completed a quest.")
                .param(questParam())
                .factory(params -> {
                    QuestId quest = quest(params);
                    return new Variable<BigDecimal>() {
                        @Override
                        public String getId() {
                            return "quest-times";
                        }

                        @Override
                        public Class<BigDecimal> getType() {
                            return BigDecimal.class;
                        }

                        @Override
                        public BigDecimal read(ExecutionContext ctx) {
                            QuestEngine engine = QuestEngine.find(quest.getOwner());
                            Optional<QuestState> state = engine == null ? Optional.<QuestState>empty() : engine.getState(ctx.getOwner(), quest);
                            return BigDecimal.valueOf(state.map(QuestState::getTimes).orElse(0));
                        }
                    };
                })
                .build());
        effect(core, "start-quest", "Starts a quest for the context's owner.",
                (engine, ctx, quest) -> engine.start(ctx.getOwner(), quest, QuestParams.NONE));
        effect(core, "offer-quest", "Offers a quest to the context's owner.",
                (engine, ctx, quest) -> engine.offer(ctx.getOwner(), quest));
        effect(core, "complete-quest", "Completes the context's owner's active quest.",
                (engine, ctx, quest) -> engine.complete(ctx.getOwner(), quest));
        effect(core, "fail-quest", "Fails the context's owner's active quest.",
                (engine, ctx, quest) -> engine.fail(ctx.getOwner(), quest, FailCause.EFFECT));
        effect(core, "track-quest", "Makes the player track a quest.", (engine, ctx, quest) -> {
            FPlayer player = ctx.getPlayer();
            if (player != null) {
                engine.track(player, quest);
            }
        });
    }

    private interface QuestAction {
        void run(QuestEngine engine, ExecutionContext ctx, QuestId quest);
    }

    private static void effect(ECPluginData core, String name, String description, QuestAction action) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:" + name)
                .describe(description)
                .param(questParam())
                .factory(params -> {
                    QuestId quest = quest(params);
                    return new Effect() {
                        @Override
                        public void apply(ExecutionContext ctx) {
                            QuestEngine engine = QuestEngine.find(quest.getOwner());
                            if (engine == null) {
                                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, name + ": no quest engine of " + quest.getOwner() + " is open");
                                return;
                            }
                            action.run(engine, ctx, quest);
                        }

                        @Override
                        public String toString() {
                            return name + "(" + quest + ")";
                        }
                    };
                })
                .build());
    }

    private static ParamSpec questParam() {
        return ParamSpec.required("quest", ParamSpec.Kind.ID, "The quest, owner:name (a bare name is the caller's own).");
    }

    private static QuestId quest(ParsedParams params) {
        return QuestId.parse(params.getString("quest"), params.getCaller().getMetaInfo().getName());
    }

    private static String[] statuses() {
        QuestStatus[] values = QuestStatus.values();
        String[] names = new String[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
            names[values.length + i] = values[i].name().toLowerCase(Locale.ROOT);
        }
        return names;
    }
}
