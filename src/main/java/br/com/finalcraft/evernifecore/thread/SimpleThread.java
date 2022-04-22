package br.com.finalcraft.evernifecore.thread;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class SimpleThread {

    protected final JavaPlugin javaPlugin = JavaPlugin.getProvidingPlugin(this.getClass());
    protected transient Thread thread;

    protected Thread getOrCreateThread(){
        if (thread == null || !thread.isAlive()){
            thread = new Thread(){
                @Override
                public void run() {
                    try {
                        SimpleThread.this.javaPlugin.getLogger().info("Starting Thread: " + this.getName());
                        SimpleThread.this.onStart();
                        SimpleThread.this.run();
                    } catch (InterruptedException exception) {
                        SimpleThread.this.onShutdown();
                        SimpleThread.this.javaPlugin.getLogger().info("Thread Shutdown: " + this.getName());
                    }
                }
            };
        }
        return thread;
    }

    public SimpleThread() {
        getOrCreateThread().setName("[" + this.getClass().getSimpleName() + "]");
        getOrCreateThread().setDaemon(true);
    }

    public SimpleThread(String name) {
        getOrCreateThread().setName(name);
        getOrCreateThread().setDaemon(true);
    }

    public void start(){
        if (!getOrCreateThread().isAlive()){
            getOrCreateThread().start();
        } else if (allowRestart()){
            shutdown();
            start();
        }
    }

    public void shutdown(){
        if (getOrCreateThread().isAlive()){
            getOrCreateThread().interrupt();
        }
    }

    protected abstract void run() throws InterruptedException;

    protected void onShutdown(){

    }

    protected void onStart(){

    }

    protected boolean allowRestart(){
        return false;
    }

}
