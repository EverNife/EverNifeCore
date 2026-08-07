package br.com.finalcraft.evernifecore.minecraft.chat;

/** What to do with a chat message a caller asked to be notified about. */
public interface IChatAction {

    ActionResult onChat(String message);

    enum ActionResult {
        /** Handled; stop expecting, but let the message reach chat. */
        SUCCESS,
        /** Not what was being waited for; keep expecting the next message. */
        IGNORE_CURRENT_MESSAGE,
        /** Handled; stop expecting and swallow the message so it never reaches chat. */
        SUCCESS_AND_CONSUME,
        /**
         * Not an answer, but the message belongs to this wait: swallow it and keep expecting. It is
         * what a question that asks again on bad input needs, so the rejected attempt does not show
         * up in public chat.
         */
        CONSUME_AND_CONTINUE
    }

}
