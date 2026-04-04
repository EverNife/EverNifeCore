package br.com.finalcraft.evernifecore.util;

import com.google.gson.Gson;

public class FCJsonUtil {

    private static final Gson gson = new Gson();

    public static Gson getGson(){
        return gson;
    }

}
