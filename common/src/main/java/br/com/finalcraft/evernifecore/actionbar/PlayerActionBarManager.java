package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlayerActionBarManager implements Runnable {

    private final FPlayer player;
    private final PriorityQueue<ActionBarMessage> ACTION_BARS_PRIORITY_QUEUE = new PriorityQueue<>(3, Comparator.comparingInt(ActionBarMessage::getPriority).reversed());
    //The queue is mutated from the caller thread (addMessage) and the async tick (run); every
    //access to the queue and the lifecycle flags is guarded by this monitor. The packet write
    //(sendActionBarMessage) is done OUTSIDE the lock - it must never hold the monitor.
    private final Object lock = new Object();
    private transient volatile boolean hasStarted;
    private transient volatile boolean terminated = false;

    private transient ScheduledFuture<?> scheduledFuture;

    public PlayerActionBarManager(FPlayer player) {
        this.player = player;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void terminate(){
        synchronized (lock) {
            terminated = true;
            if (scheduledFuture != null){
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
        }
    }

    public boolean hasStarted() {
        return hasStarted;
    }

    private void start(){
        hasStarted = true;
        this.scheduledFuture = FCScheduler.getScheduler()
                .scheduleAtFixedRate(this, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void addMessage(ActionBarMessage message){
        boolean invokeNow;
        synchronized (lock) {
            ACTION_BARS_PRIORITY_QUEUE.removeIf(innerMessage -> innerMessage.getActionBarID().equals(message.getActionBarID()));//Remove existing ActionBarMessages with the same ID
            ACTION_BARS_PRIORITY_QUEUE.offer(message);//Add this new message to the priority queue

            if (hasStarted() == false){
                start();//Start async runnable (initial delay 0 -> the tick emits immediately)
                invokeNow = false;
            }else {
                invokeNow = true;
            }
        }
        if (invokeNow){
            run();//Enforce send of ActionBarMessage now, outside the lock
        }
    }

    @Override
    public void run() {
        FancyText textToSend;
        synchronized (lock) {
            if (!player.isOnline()){
                this.terminate();
                return;
            }

            while (ACTION_BARS_PRIORITY_QUEUE.peek() != null && ACTION_BARS_PRIORITY_QUEUE.peek().isTerminated()){
                ACTION_BARS_PRIORITY_QUEUE.poll();
            }

            ActionBarMessage actionBarMessage = ACTION_BARS_PRIORITY_QUEUE.peek();

            if (actionBarMessage == null){//No action bar remaining
                this.terminate();
                textToSend = FancyText.of();
            }else {
                //Set high priority action bar
                textToSend = FancyText.of(actionBarMessage.getActionBarText());
            }
        }

        //Send the packet outside the lock - it is a network write, not a queue mutation
        sendActionBarMessage(player, textToSend);
    }

    public static void sendActionBarMessage(FPlayer player, FancyText fancyText){
        EverNifeCore.getPlatform().sendActionBarMessage(player, fancyText);
    }
}
