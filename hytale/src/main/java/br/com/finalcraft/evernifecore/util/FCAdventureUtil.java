package br.com.finalcraft.evernifecore.util;

import com.hypixel.hytale.server.core.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class FCAdventureUtil {

    public static Message toHytaleMessage(Component component) {
        if (component instanceof TextComponent text) {
            Message message = Message.raw(text.content());
            
            TextColor color = text.color();
            if (color != null) {
                message.color(color.asHexString());
            }

            TextDecoration.State bold = text.decoration(TextDecoration.BOLD);
            if (bold != TextDecoration.State.NOT_SET) {
                message.bold(bold == TextDecoration.State.TRUE);
            }

            TextDecoration.State italic = text.decoration(TextDecoration.ITALIC);
            if (italic != TextDecoration.State.NOT_SET) {
                message.italic(italic == TextDecoration.State.TRUE);
            }

//            TextDecoration.State underlined = text.decoration(TextDecoration.UNDERLINED);
//            if (underlined != TextDecoration.State.NOT_SET) {
//                message.underline(underlined == TextDecoration.State.TRUE);
//            }
//
//            TextDecoration.State strikethrough = text.decoration(TextDecoration.STRIKETHROUGH);
//            if (strikethrough != TextDecoration.State.NOT_SET) {
//                message.strikethrough(strikethrough == TextDecoration.State.TRUE);
//            }

            ClickEvent clickEvent = text.clickEvent();
            if (clickEvent != null) {
                if (clickEvent.action() == ClickEvent.Action.OPEN_URL) {
                    message.link(clickEvent.value());
                }
            }

//            HoverEvent<?> hoverEvent = text.hoverEvent();
//            if (hoverEvent != null && hoverEvent.action() == HoverEvent.Action.SHOW_TEXT) {
//                Object value = hoverEvent.value();
//                if (value instanceof Component hoverComponent) {
//                    message.hover(toHytaleMessage(hoverComponent));
//                }
//            }

            message.insertAll(text.children().stream().map(FCAdventureUtil::toHytaleMessage).toList());
            return message;
        } else {
            throw new UnsupportedOperationException("Unsupported component type: " + component.getClass());
        }
    }
}
