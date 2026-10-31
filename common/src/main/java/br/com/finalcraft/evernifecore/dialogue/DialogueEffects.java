package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import jakarta.annotation.Nullable;

/** The stock effects {@code start-dialogue} and {@code end-dialogue}: any gate, quest, menu or flag can open a talk. */
public final class DialogueEffects {

    static final String START_DIALOGUE = "start-dialogue";
    static final String END_DIALOGUE = "end-dialogue";

    private DialogueEffects() {
    }

    /** Registers both under {@code core}, the EverNifeCore plugin data. */
    public static void register(ECPluginData core) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:" + START_DIALOGUE)
                .describe("Starts a registered dialogue for the player; the caller of the context becomes the session's origin and an"
                        + " npc attribute of the context its NPC. A dialogue the player was in ends as replaced.")
                .param(ParamSpec.required("dialogue", ParamSpec.Kind.ID, "The dialogue, owner:name - e.g. finalcolonies:tomas."))
                .param(ParamSpec.optional("node", ParamSpec.Kind.STRING, null, "The node to start on instead of the dialogue's entry."))
                .factory(params -> new StartDialogue(DialogueId.parse(params.getString("dialogue")), params.getString("node")))
                .build());
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:" + END_DIALOGUE)
                .describe("Ends the dialogue the player is in, if any.")
                .factory(params -> new EndDialogue())
                .build());
    }

    static final class StartDialogue implements Effect {

        private final DialogueId dialogue;
        private final @Nullable String node;

        StartDialogue(DialogueId dialogue, @Nullable String node) {
            this.dialogue = dialogue;
            this.node = node;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            FPlayer player = ctx.getPlayer();
            if (player == null) {
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, this + ": no player to talk to");
                return;
            }
            //resolved now, not at parse: the plugin that owns the dialogue may load after whoever wrote this effect
            Dialogue found = Dialogues.find(dialogue).orElse(null);
            if (found == null) {
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, this + ": no dialogue " + dialogue + " is registered");
                return;
            }
            NpcRef npc = ctx.getAttribute(DialogueContext.NPC, NpcRef.class);
            Dialogues.start(player, found, ctx.getCaller(), context -> context.node(node).npc(npc));
        }

        @Override
        public String toString() {
            return START_DIALOGUE + "(" + dialogue + (node == null ? "" : ", " + node) + ")";
        }
    }

    static final class EndDialogue implements Effect {

        @Override
        public void apply(ExecutionContext ctx) {
            FPlayer player = ctx.getPlayer();
            DialogueSession session = player == null ? null : Dialogues.sessionOf(player);
            if (session != null) {
                session.end(DialogueEndCause.API);
            }
        }

        @Override
        public String toString() {
            return END_DIALOGUE;
        }
    }
}
