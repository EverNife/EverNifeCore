package br.com.finalcraft.evernifecore.actionbar;

public class ActionBarMessage {

    private final String actionBarID;
    private final String actionBarText;
    private final int priority;
    private final long timeToEnd;

    public int getPriority() {
        return priority;
    }

    public ActionBarMessage(String actionBarId, String actionBarText, long timeToEnd, int priority) {
        this.actionBarID = actionBarId;
        this.actionBarText = actionBarText;
        this.timeToEnd = timeToEnd;
        this.priority = priority;
    }

    public boolean isTerminated(){
        return System.currentTimeMillis() >= timeToEnd;
    }

    public String getActionBarID() {
        return actionBarID;
    }

    public String getActionBarText() {
        return actionBarText;
    }

    //------------------------------------------------------------------------------------------------------------------
    // Builder Code
    //------------------------------------------------------------------------------------------------------------------

    public static ActionBarMessage.Builder of(String actionBarText){
        return new ActionBarMessage.Builder(actionBarText);
    }

    public static class Builder{
        private String actionBarID = "";
        private final String actionBarText;
        private int priority = 0;
        private int seconds = 3;

        private Builder(String actionBarText) {
            this.actionBarText = actionBarText;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder setSeconds(int seconds) {
            this.seconds = seconds;
            return this;
        }

        public Builder setBarID(String actionBarID) {
            this.actionBarID = actionBarID;
            return this;
        }

        public ActionBarMessage build(){
            return new ActionBarMessage(this.actionBarID, this.actionBarText, System.currentTimeMillis() + 1000 * this.seconds, this.priority);
        }
    }

}