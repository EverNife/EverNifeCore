package br.com.finalcraft.evernifecore.pageviwer;

import lombok.Getter;

@Getter
public class PageVizualization {

    private final int pageStart;
    private final int pageEnd;
    private final boolean showAll;

    public PageVizualization(int pageStart, int pageEnd, boolean showAll) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.showAll = showAll;
    }
}
