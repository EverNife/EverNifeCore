package br.com.finalcraft.evernifecore.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class EverNifeCoreReloadEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
