package br.com.finalcraft.evernifecore.actionbar;

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
    private transient boolean hasStarted;
    private transient boolean terminated = false;

    private transient ScheduledFuture<?> scheduledFuture;

    public PlayerActionBarManager(FPlayer player) {
        this.player = player;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void terminate(){
        terminated = true;
        if (scheduledFuture != null){
            scheduledFuture.cancel(false);
            scheduledFuture = null;
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
        ACTION_BARS_PRIORITY_QUEUE.removeIf(innerMessage -> innerMessage.getActionBarID().equals(message.getActionBarID()));//Remove existing ActionBarMessages with the same ID
        ACTION_BARS_PRIORITY_QUEUE.offer(message);//Add this new message to the priority queue

        if (hasStarted() == false){
            start();//Start async runnable
        }else {
            run();//Enforce send of ActionBarMessage now
        }
    }

    @Override
    public void run() {
        if (!player.isOnline()){
            this.terminate();
            return;
        }

        while (ACTION_BARS_PRIORITY_QUEUE.peek() != null && ACTION_BARS_PRIORITY_QUEUE.peek().isTerminated()){
            ACTION_BARS_PRIORITY_QUEUE.poll();
        }

        ActionBarMessage actionBarMessage = ACTION_BARS_PRIORITY_QUEUE.peek();

        if (actionBarMessage == null){//No action bar remaining
            sendActionBarMessage(player, FancyText.of());
            this.terminate();
            return;
        }

        //Set high priority action bar
        FancyText fancyText = FancyText.of(actionBarMessage.getActionBarText());
        sendActionBarMessage(player, fancyText);
    }

    public static void sendActionBarMessage(FPlayer player, FancyText fancyText){
        //Logic to actually send the message here
    }
}
