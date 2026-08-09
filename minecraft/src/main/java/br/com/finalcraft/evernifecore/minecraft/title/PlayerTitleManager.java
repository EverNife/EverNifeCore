package br.com.finalcraft.evernifecore.minecraft.title;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.PriorityQueue;

public class PlayerTitleManager extends BukkitRunnable {

    private final Player player;
    private final PriorityQueue<TitleMessage.SentTitleMessage> PRIORITY_QUEUE = new PriorityQueue<>(3, Comparator.comparingInt(TitleMessage::getPriority).reversed());
    // Serializes the queue and the tick counters against the async tick; the packet send stays outside it.
    private final Object lock = new Object();
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
        this.runTaskTimerAsynchronously(EverNifeCoreBukkitPlugin.instance, 0, 10);
    }

    public void addMessage(TitleMessage message){
        synchronized (lock) {
            PRIORITY_QUEUE.removeIf(innerMessage -> innerMessage.getId().equals(message.getId()));//Remove existing ActionBarMessages with the same ID
            PRIORITY_QUEUE.offer(new TitleMessage.SentTitleMessage(message, intenalTickCount));//Add this new message to the priority queue

            if (!isRunning){
                start();//Start async runnable
            }else {
                run();//Enforce send of TitleMessage now, but uncount the 'intenalTickCount'
                intenalTickCount-=10;
            }
        }
    }

    @Override
    public void run() {
        boolean doReset = false;
        boolean doSend = false;
        String titleText = null;
        String subTitleText = null;
        int fadeInTicks = 0;
        int stayTicks = 0;
        int fadeOutTicks = 0;

        synchronized (lock) {
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
                    doReset = true;
                    this.terminate();
                } else if (lastSentTitle == titleMessage){
                    //No need to send the same title again, it will break the fadeIn effect
                } else {
                    this.lastSentTitle = titleMessage;

                    long passedTicks = this.intenalTickCount - titleMessage.getStartTickCount(); //The amount of ticks passed since this

                    fadeInTicks = (int) Math.max(0,titleMessage.getFadeIn() - passedTicks);
                    passedTicks -= titleMessage.getFadeIn();
                    stayTicks = (int) Math.max(0,titleMessage.getStay() - passedTicks);
                    passedTicks -= titleMessage.getStay();
                    fadeOutTicks = (int) Math.max(0,titleMessage.getFadeOut() - passedTicks);

                    titleText = titleMessage.getTitleText();
                    subTitleText = titleMessage.getSubTitleText();
                    doSend = true;
                }
            }finally {
                this.intenalTickCount +=10;
            }
        }

        // Packet writes (title/reset) are safe off the main thread; kept OUTSIDE the lock.
        if (doReset){
            player.resetTitle();
        } else if (doSend){
            if (MCVersion.isLower(MCDetailedVersion.v1_11_R1)) {
                //the timed form only arrived in 1.11; before that the server takes the pair alone
                player.sendTitle(titleText, subTitleText);
            } else {
                player.sendTitle(titleText, subTitleText, fadeInTicks, stayTicks, fadeOutTicks);
            }
        }
    }
}
