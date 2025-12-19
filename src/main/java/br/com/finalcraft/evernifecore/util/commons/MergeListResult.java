package br.com.finalcraft.evernifecore.util.commons;

import lombok.Data;

import java.util.List;

@Data
public class MergeListResult<ENTITY> {

    final List<ENTITY> updated;
    final List<ENTITY> removed;
    final List<ENTITY> added;

}
