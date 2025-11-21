package br.com.finalcraft.evernifecore.argumento;

import org.junit.jupiter.api.Test;

public class MultiArgumentosTest {

    @Test
    public void testeMultiArgumentos() {
        MultiArgumentos argumentos = new MultiArgumentos("give 30.2".split(" "));

        assert argumentos.get(0).equalsIgnoreCase("GiVe") == true;
        assert argumentos.get(1).getDouble() == 30.2D;
        assert argumentos.get(1).getInteger() == null;

        argumentos = new MultiArgumentos("hello -teste:1 my -testeString:'Here Comes The Mighty Duster!' --teste:2 friend ---teste:3".split(" "));

        System.out.println("Before Taking the first Flag, we have:");
        for (Argumento arg : argumentos.getArgs()) {
            System.out.println(" > " + arg);
        }

        //When getting the firt flag, the MultiArgumentos is rearanged to remove all flags from its body
        assert argumentos.getFlag("teste").getInteger() == argumentos.getFlag("-teste").getInteger();

        assert argumentos.getFlag("-teste").getInteger() == 1;
        assert argumentos.getFlag("--teste").getInteger() == 2;
        assert argumentos.getFlag("---teste").getInteger() == 3;
        assert argumentos.getFlag("-testeString").equals("Here Comes The Mighty Duster!");

        //After flagify, argumentos should be in proper order
        assert argumentos.get(0).equals("hello") == true;
        assert argumentos.get(1).equals("my") == true;
        assert argumentos.get(2).equals("friend") == true;

        System.out.println("\nAfter taking any flag we have:");
        for (Argumento arg : argumentos.getArgs()) {
            System.out.println(" > " + arg);
        }

        for (FlagedArgumento flag : argumentos.getFlags()) {
            System.out.println(" \\--> " + flag);
        }

    }

}
