package br.com.finalcraft.evernifecore.thread;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class SimpleThread {

    protected final JavaPlugin javaPlugin = JavaPlugin.getProvidingPlugin(this.getClass());
    protected transient Thread thread;
    protected final String threadName;

    private transient boolean executeOnShutdown = true;

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
                        if (executeOnShutdown){
                            SimpleThread.this.onShutdown();
                        }
                        SimpleThread.this.javaPlugin.getLogger().info("Thread Shutdown: " + this.getName());
                    }
                }
            };
            thread.setName(this.getThreadName());
            thread.setDaemon(true);
        }
        return thread;
    }

    public SimpleThread(String threadName) {
        this.threadName = threadName;
    }

    public SimpleThread() {
        this.threadName = "[" + this.getClass().getSimpleName() + "]";
    }

    public String getThreadName() {
        return threadName;
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
        shutdown(true);
    }

    public void shutdown(boolean executeOnShutdown){
        this.executeOnShutdown = executeOnShutdown;
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
