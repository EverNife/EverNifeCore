package br.com.finalcraft.evernifecore.title;

import br.com.finalcraft.evernifecore.EverNifeCore;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.PriorityQueue;

public class PlayerTitleManager extends BukkitRunnable {

    private final Player player;
    private final PriorityQueue<TitleMessage.SentTitleMessage> PRIORITY_QUEUE = new PriorityQueue<>(3, Comparator.comparingInt(TitleMessage::getPriority).reversed());
    private transient boolean isRunning;
    private transient boolean terminated = false;
    private transient long intenalTickCount = 0;
    private transient TitleMessage.SentTitleMessage lastSentTitle;

    public PlayerTitleManager(Player player) {
        this.player = player;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void terminate(){
        terminated = true;
        this.cancel();
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void start(){
        isRunning = true;
        this.runTaskTimerAsynchronously(EverNifeCore.instance, 0, 10);
    }

    public void addMessage(TitleMessage message){
        PRIORITY_QUEUE.removeIf(innerMessage -> innerMessage.getId().equals(message.getId()));//Remove existing ActionBarMessages with the same ID
        PRIORITY_QUEUE.offer(new TitleMessage.SentTitleMessage(message, intenalTickCount));//Add this new message to the priority queue

        if (!isRunning){
            start();//Start async runnable
        }else {
            run();//Enforce send of TitleMessage now, but uncount the 'intenalTickCount'
            intenalTickCount-=10;
        }
    }

    @Override
    public void run() {
        try {
            if (!player.isOnline()){
                this.terminate();
                return;
            }

            while (PRIORITY_QUEUE.peek() != null && PRIORITY_QUEUE.peek().isTerminated(this.intenalTickCount)){
                PRIORITY_QUEUE.poll();
            }

            TitleMessage.SentTitleMessage titleMessage = PRIORITY_QUEUE.peek();

            if (titleMessage == null){//No action remaining
                player.resetTitle();
                this.terminate();
                return;
            }

            if (lastSentTitle == titleMessage){
                //No need to send the same title again, it will break the fadeIn effect
                return;
            }
            this.lastSentTitle = titleMessage;

            long initialPassedTicks;
            long passedTicks = initialPassedTicks = this.intenalTickCount - titleMessage.getStartTickCount(); //The amount of ticks passed since this

            int fadeInTicks = (int) Math.max(0,titleMessage.getFadeIn() - passedTicks);
            passedTicks -= titleMessage.getFadeIn();
            int stayTicks = (int) Math.max(0,titleMessage.getStay() - passedTicks);
            passedTicks -= titleMessage.getStay();
            int fadeOutTicks = (int) Math.max(0,titleMessage.getFadeOut() - passedTicks);

            class Data{
                long passedTicks;
                int fadeInTicks;
                int stayTicks;
                int fadeOutTicks;

                public Data(long passedTicks, int fadeInTicks, int stayTicks, int fadeOutTicks) {
                    this.passedTicks = passedTicks;
                    this.fadeInTicks = fadeInTicks;
                    this.stayTicks = stayTicks;
                    this.fadeOutTicks = fadeOutTicks;
                }

                @Override
                public String toString() {
                    return "Data{" +
                            "passedTicks=" + passedTicks +
                            ", fadeInTicks=" + fadeInTicks +
                            ", stayTicks=" + stayTicks +
                            ", fadeOutTicks=" + fadeOutTicks +
                            '}';
                }
            }

            player.sendMessage(new Data(initialPassedTicks, fadeInTicks, stayTicks, fadeOutTicks).toString());

            //Set high priority title
            player.sendTitle(
                    titleMessage.getTitleText(),
                    titleMessage.getSubTitleText(),
                    fadeInTicks,
                    stayTicks,
                    fadeOutTicks
            );
        }finally {
            this.intenalTickCount +=10;
        }
    }
}
