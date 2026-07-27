package br.com.finalcraft.evernifecore.pageviewer;

import lombok.Getter;

@Getter
public class PageVisualization {

    private final int pageStart;
    private final int pageEnd;
    private final boolean showAll;

    public PageVisualization(int pageStart, int pageEnd, boolean showAll) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.showAll = showAll;
    }
}
