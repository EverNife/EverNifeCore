package br.com.finalcraft.evernifecore.util.pageviwer;

import lombok.Getter;

@Getter
public class PageVizualization {

    private final int pageStart;
    private final int pageEnd;
    private boolean showAll;

    public PageVizualization(int pageStart, int pageEnd, boolean showAll) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.showAll = showAll;
    }
}
