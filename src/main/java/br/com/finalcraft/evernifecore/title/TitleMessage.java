package br.com.finalcraft.evernifecore.title;

import org.bukkit.entity.Player;

public class TitleMessage {

    private final String id;
    private final String titleText;
    private final String subTitleText;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;
    private final int priority;

    public TitleMessage(String id, String titleText, String subTitleText, int fadeIn, int stay, int fadeOut, int priority) {
        this.id = id;
        this.titleText = titleText;
        this.subTitleText = subTitleText;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getSubTitleText() {
        return subTitleText;
    }

    public int getFadeIn() {
        return fadeIn;
    }

    public int getStay() {
        return stay;
    }

    public int getFadeOut() {
        return fadeOut;
    }

    public int getPriority() {
        return priority;
    }

    public TitleMessage send(Player... players){
        for (Player player : players) {
            TitleAPI.send(player, this);
        }
        return this;
    }

    public static class SentTitleMessage extends TitleMessage {

        private final long startTickCount;
        private final long endTime;

        public SentTitleMessage(TitleMessage titleMessage, long startTickCount) {
            super(titleMessage.getId(), titleMessage.getTitleText(), titleMessage.getSubTitleText(), titleMessage.getFadeIn(), titleMessage.getStay(), titleMessage.getFadeOut(), titleMessage.getPriority());
            this.startTickCount = startTickCount;
            this.endTime = (startTickCount + this.getFadeIn() + this.getStay() + this.getFadeOut());
        }

        public long getStartTickCount() {
            return startTickCount;
        }

        public boolean isTerminated(long whatTickIsIt){
            return whatTickIsIt >= endTime;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    // Builder Code
    //------------------------------------------------------------------------------------------------------------------

    public static Builder of(String tileText, String subTitleText){
        return new Builder(tileText, subTitleText);
    }

    public static class Builder{
        private String id = "";
        private String titleText = "";
        private String subTitleText = "";
        private int fadeIn = 10;
        private int stay = 70;
        private int fadeOut = 20;
        private int priority = 0;

        public Builder(String titleText, String subTitleText) {
            this.titleText = titleText;
            this.subTitleText = subTitleText;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setTitleText(String titleText) {
            this.titleText = titleText;
            return this;
        }

        public Builder setSubTitleText(String subTitleText) {
            this.subTitleText = subTitleText;
            return this;
        }

        public Builder setFadeIn(int fadeIn) {
            this.fadeIn = fadeIn;
            return this;
        }

        public Builder setStay(int stay) {
            this.stay = stay;
            return this;
        }

        public Builder setFadeOut(int fadeOut) {
            this.fadeOut = fadeOut;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public TitleMessage build(){
            return new TitleMessage(this.id, this.titleText, this.subTitleText, this.fadeIn, this.stay, this.fadeOut, this.priority);
        }

        public TitleMessage send(Player... players){
            return this.build().send(players);
        }
    }

}