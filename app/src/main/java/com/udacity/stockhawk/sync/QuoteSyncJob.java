package com.udacity.stockhawk.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.widget.ListWidgetProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;
import yahoofinance.quotes.stock.StockQuote;

public final class QuoteSyncJob {

    private static final int ONE_OFF_ID = 2;
    private static final int PERIOD = 300000;
    private static final int INITIAL_BACKOFF = 10000;
    private static final int PERIODIC_ID = 1;
    private static final int YEARS_OF_HISTORY = 2;

    private QuoteSyncJob() {
    }

    static void getQuotes(Context context) {

        Timber.d("Running sync job");

        Calendar from = Calendar.getInstance();
        Calendar to = Calendar.getInstance();
        from.add(Calendar.YEAR, -YEARS_OF_HISTORY);

        try {

            Set<String> stockPref = PrefUtils.getStocks(context);
            Set<String> stockCopy = new HashSet<>();
            stockCopy.addAll(stockPref);
            String[] stockArray = stockPref.toArray(new String[stockPref.size()]);

            Timber.d(stockCopy.toString());

            if (stockArray.length == 0) {
                return;
            }
            Map<String, Stock> quotes = YahooFinance.get(stockArray);
            Timber.d(quotes.toString());

            Set<String> symbolsToRemove = null;
            ArrayList<ContentValues> quoteCVs = new ArrayList<>();

            // The stock preferences may have been updated in a different thread,
            // while obtaining the stock info from Yahoo Finance,
            // so get the latest preference values here
            Set<String> stockPrefNew = PrefUtils.getStocks(context);

            for (String symbol : stockCopy) {

                // do nothing if the stock was removed from preferences while its data was
                // being fetched from Yahoo Finance
                if (!stockPrefNew.contains(symbol)) {
                    continue;
                }

                Stock stock = quotes.get(symbol);
                StockQuote quote = stock.getQuote();

                if (quote.getPrice() != null
                        && quote.getChange() != null
                        && quote.getChangeInPercent() != null) {

                    float price = quote.getPrice().floatValue();
                    float change = quote.getChange().floatValue();
                    float percentChange = quote.getChangeInPercent().floatValue();

                    // WARNING! Don't request historical data for a stock that doesn't exist!
                    // The request will hang forever X_x
                    List<HistoricalQuote> history = stock.getHistory(from, to, Interval.WEEKLY);

                    StringBuilder historyBuilder = new StringBuilder();

                    if (history != null) {
                        for (HistoricalQuote it : history) {
                            historyBuilder.append(it.getDate().getTimeInMillis());
                            historyBuilder.append(", ");
                            historyBuilder.append(it.getClose());
                            historyBuilder.append("\n");
                        }
                    }

                    ContentValues quoteCV = new ContentValues();
                    quoteCV.put(Contract.Quote.COLUMN_SYMBOL, symbol);
                    quoteCV.put(Contract.Quote.COLUMN_PRICE, price);
                    quoteCV.put(Contract.Quote.COLUMN_PERCENTAGE_CHANGE, percentChange);
                    quoteCV.put(Contract.Quote.COLUMN_ABSOLUTE_CHANGE, change);

                    quoteCV.put(Contract.Quote.COLUMN_HISTORY, historyBuilder.toString());

                    quoteCVs.add(quoteCV);
                } else {
                    // If no stock data was returned for the symbol (i.e. we are here) AND the
                    // stock symbol is not in the database (i.e. there has never been stock data
                    // for the stock), it is almost certain that the stock symbol is not a valid
                    // stock symbol. (If the symbol is in the database, there has been stock info
                    // for it in the past and the stock info may just be temporarily unavailable.)
                    // In this case, add the symbol to a list of stocks to be removed from preferences.

                    Set<String> symbolsInDatabase = getSymbolsInDatabase(context);
                    Timber.d("symbolsInDatabase: " + symbolsInDatabase.toString());
                    if (!symbolsInDatabase.contains(symbol)) {
                        if (symbolsToRemove == null) {
                            symbolsToRemove = new HashSet<>();
                        }
                        symbolsToRemove.add(symbol);
                    }
                }
            }

            context.getContentResolver()
                    .bulkInsert(
                            Contract.Quote.URI,
                            quoteCVs.toArray(new ContentValues[quoteCVs.size()]));

            // Update any widgets with the latest stock info
            ListWidgetProvider.updateWidgets(context);

            // Remove any symbols which are not valid stocks from the preferences
            if (symbolsToRemove != null) {
                for (String symbolToRemove : symbolsToRemove) {
                    Timber.d("Removing symbol from preferences: " + symbolToRemove);
                    PrefUtils.removeStock(context, symbolToRemove);
                }
            }

        } catch (Exception exception) {
            Timber.e(exception, "Error fetching stock quotes");
        }
    }

    private static void schedulePeriodic(Context context) {
        Timber.d("Scheduling a periodic task");


        JobInfo.Builder builder = new JobInfo.Builder(PERIODIC_ID, new ComponentName(context, QuoteJobService.class));


        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD)
                .setBackoffCriteria(INITIAL_BACKOFF, JobInfo.BACKOFF_POLICY_EXPONENTIAL);

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        scheduler.schedule(builder.build());
    }


    public static synchronized void initialize(final Context context) {

        schedulePeriodic(context);
        syncImmediately(context);

    }

    public static synchronized void syncImmediately(Context context) {

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
            Intent nowIntent = new Intent(context, QuoteIntentService.class);
            context.startService(nowIntent);
        } else {

            JobInfo.Builder builder = new JobInfo.Builder(ONE_OFF_ID, new ComponentName(context, QuoteJobService.class));

            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(INITIAL_BACKOFF, JobInfo.BACKOFF_POLICY_EXPONENTIAL);

            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            scheduler.schedule(builder.build());
        }
    }

    private static Set<String> getSymbolsInDatabase(Context context) {
        Cursor cursor = null;
        Set<String> symbols = null;

        try {
            cursor = context.getContentResolver().query(
                    Contract.Quote.URI,
                    Contract.Quote.QUOTE_COLUMNS,
                    null,
                    null,
                    null);

            if (cursor != null && cursor.getCount() > 0) {
                symbols = new HashSet<>();
                while (cursor.moveToNext()) {
                    symbols.add(cursor.getString(Contract.Quote.POSITION_SYMBOL));
                }
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // do this check here rather than returning earlier to ensure the cursor gets closed
        if (symbols == null) {
            return Collections.emptySet();
        }
        return symbols;
    }

}
