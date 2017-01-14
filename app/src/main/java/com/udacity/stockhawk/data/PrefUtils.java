package com.udacity.stockhawk.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.udacity.stockhawk.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class PrefUtils {

    private PrefUtils() {
    }

    public static Set<String> getStocks(Context context) {
        String stocksKey = context.getString(R.string.pref_stocks_key);
        String initializedKey = context.getString(R.string.pref_stocks_initialized_key);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean initialized = prefs.getBoolean(initializedKey, false);

        // The first time (only) that this method is called, return the default list of stocks.
        if (!initialized) {
            String[] defaultStocksList = context.getResources().getStringArray(R.array.default_stocks);
            HashSet<String> defaultStocks = new HashSet<>(Arrays.asList(defaultStocksList));

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(initializedKey, true);
            editor.putStringSet(stocksKey, defaultStocks);
            editor.apply();
            return defaultStocks;
        }

        return prefs.getStringSet(stocksKey, new HashSet<String>());
    }

    /**
     * Add or remove a stock symbol from the stock list stored in the preferences.
     * @param context the context
     * @param symbol the stock to add or remove
     * @param add if true, the stock is added, if false, the stock is removed
     */
    private static void editStockPref(Context context, String symbol, Boolean add) {
        String stocksKey = context.getString(R.string.pref_stocks_key);
        Set<String> stocks = getStocks(context);

        if (add) {
            stocks.add(symbol);
        } else {
            stocks.remove(symbol);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(stocksKey, stocks);
        editor.apply();
    }

    public static void addStock(Context context, String symbol) {
        editStockPref(context, symbol, true);
    }

    public static void removeStock(Context context, String symbol) {
        editStockPref(context, symbol, false);
    }

    /**
     * Returns the current display mode, which indicates whether the change in stock value is
     * displayed as an absolute currency amount or as a percentage.
     * @param context the context
     * @return the current display mode
     */
    public static String getDisplayMode(Context context) {
        String key = context.getString(R.string.pref_display_mode_key);
        String defaultValue = context.getString(R.string.pref_display_mode_default);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    public static void toggleDisplayMode(Context context) {
        String key = context.getString(R.string.pref_display_mode_key);
        String absoluteKey = context.getString(R.string.pref_display_mode_absolute_key);
        String percentageKey = context.getString(R.string.pref_display_mode_percentage_key);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String displayMode = getDisplayMode(context);

        SharedPreferences.Editor editor = prefs.edit();

        if (displayMode.equals(absoluteKey)) {
            editor.putString(key, percentageKey);
        } else {
            editor.putString(key, absoluteKey);
        }

        editor.apply();
    }

}
