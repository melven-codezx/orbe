package com.orbe.browser;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;

/** Utilitários de UI compartilhados (densidade e tipografia Outfit). */
public final class Ui {

    private static final HashMap<String, Typeface> CACHE = new HashMap<>();

    private Ui() {
    }

    public static float dp(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().density;
    }

    public static float sp(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().scaledDensity;
    }

    public static Typeface font(Context c, boolean semibold) {
        String name = semibold ? "fonts/Outfit-SemiBold.ttf" : "fonts/Outfit-Regular.ttf";
        Typeface tf = CACHE.get(name);
        if (tf == null) {
            try {
                tf = Typeface.createFromAsset(c.getAssets(), name);
            } catch (Throwable t) {
                tf = semibold ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        : Typeface.create("sans-serif", Typeface.NORMAL);
            }
            CACHE.put(name, tf);
        }
        return tf;
    }
}
