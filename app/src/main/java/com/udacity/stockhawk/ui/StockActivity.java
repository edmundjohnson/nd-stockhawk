package com.udacity.stockhawk.ui;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.db.chart.model.LineSet;
import com.db.chart.view.LineChartView;
import com.udacity.stockhawk.R;
import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.widget.ListWidgetProvider;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * The activity class for the stock screen.
 * @author Edmund Johnson
 */
public class StockActivity extends AppCompatActivity {

    private static final int WEEKS_ON_CHART_DEFAULT = 52;

    private Uri stockUri;

    private static final DecimalFormat dollarFormatWithPlus;
    private static final DecimalFormat dollarFormat;
    private static final DecimalFormat percentageFormat;

    @BindView(R.id.symbol)
    TextView symbol;

    @BindView(R.id.price)
    TextView price;

    @BindView(R.id.change)
    TextView change;

    @BindView(R.id.stockChart)
    LineChartView stockChart;

    /** The maximum value of the stock price in the period displayed. */
    private float mMaxPrice;
    /** The minimum value of the stock price in the period displayed. */
    private float mMinPrice;

//    @BindView(R.id.history)
//    TextView history;

    static {
        dollarFormat = StockAdapter.getDollarFormat();
        dollarFormatWithPlus = StockAdapter.getDollarFormatWithPlus();
        percentageFormat = StockAdapter.getPercentageFormat();
    }

    //---------------------------------------------------------------------------
    // Activity Lifecycle Methods

    /**
     * Perform initialisation on activity creation.
     * @param savedInstanceState a Bundle containing saved state
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);

        stockUri = getIntent().getData();

        ButterKnife.bind(this, findViewById(R.id.stockView));
    }

    /**
     * Perform UI processing for resumption of the activity.
     */
    @Override
    public void onResume() {
        super.onResume();

        displayStockInfo(stockUri);
    }

    //---------------------------------------------------------------------------
    // Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_settings, menu);
        MenuItem item = menu.findItem(R.id.action_change_units);
        toggleDisplayModeMenuItemAppearance(item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_change_units) {
            PrefUtils.toggleDisplayMode(this);
            toggleDisplayModeMenuItemAppearance(item);
            // The widget is best updated here immediately, as the change in
            // price change format does not require an internet connection.
            ListWidgetProvider.updateWidgets(this);
            displayStockInfo(stockUri);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleDisplayModeMenuItemAppearance(MenuItem item) {
        if (PrefUtils.getDisplayMode(this).equals(getString(R.string.pref_display_mode_absolute_key))) {
            item.setIcon(R.drawable.ic_percentage);
            item.setTitle(R.string.action_change_units_to_percentage);
        } else {
            item.setIcon(R.drawable.ic_dollar);
            item.setTitle(R.string.action_change_units_to_dollar);
        }
    }

    //---------------------------------------------------------------------------

    private void displayStockInfo(Uri uri) {
        Timber.d("displayStockInfo: %s", uri);
        if (uri == null) {
            return;
        }

        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    uri,
                    Contract.Quote.QUOTE_COLUMNS,
                    null,
                    null,
                    null);

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();

                // Display the current stock price and today's change

                symbol.setText(cursor.getString(Contract.Quote.POSITION_SYMBOL));
                price.setText(dollarFormat.format(cursor.getFloat(Contract.Quote.POSITION_PRICE)));

                float rawAbsoluteChange = cursor.getFloat(Contract.Quote.POSITION_ABSOLUTE_CHANGE);
                float rawPercentageChange = cursor.getFloat(Contract.Quote.POSITION_PERCENTAGE_CHANGE);

                if (rawAbsoluteChange > 0) {
                    change.setBackgroundResource(R.drawable.percent_change_pill_green);
                } else {
                    change.setBackgroundResource(R.drawable.percent_change_pill_red);
                }

                String absoluteChange = dollarFormatWithPlus.format(rawAbsoluteChange);
                String percentageChange = percentageFormat.format(rawPercentageChange / 100);

                if (PrefUtils.getDisplayMode(this)
                        .equals(this.getString(R.string.pref_display_mode_absolute_key))) {
                    change.setText(absoluteChange);
                } else {
                    change.setText(percentageChange);
                }

                // Display the stock price history
                String strHistory = cursor.getString(Contract.Quote.POSITION_HISTORY);
                LineSet historicPriceData = getHistoricPriceData(strHistory);
                stockChart.addData(historicPriceData);

                // Set y axis range to the range of values
                int yAxisMin = (int) Math.floor(mMinPrice);
                int yAxisMax = (int) Math.ceil(mMaxPrice);
                // yAxisStep is the value difference between labels,
                // calculate a value which gives us 8 or 9 labels
                int yAxisStep = Math.max((8 + yAxisMax - yAxisMin) / 8, 1);
                // max must be an exact number of steps greater than min
                int numberOfLabels = (yAxisMax - yAxisMin) / yAxisStep;
                yAxisMax = yAxisMin + (yAxisStep * (numberOfLabels + 1));
                stockChart.setAxisBorderValues(yAxisMin, yAxisMax, yAxisStep);

                stockChart.show();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Returns a LineSet of historic price data corresponding to a stock's history as stored in the
     * database as a String
     * @param history the history as a String
     * @return a list of historic prices corresponding to the history
     */
    private LineSet getHistoricPriceData(String history) {

        LineSet historicPriceData = new LineSet();
        historicPriceData.setColor(getResources().getColor(R.color.chartLine));

        if (history == null || history.length() == 0) {
            return historicPriceData;
        }

        String[] strDataPoints = history.split("\n");
        String[] strDataPointSplit;
        int dataPointsOnChart = Math.min(strDataPoints.length, WEEKS_ON_CHART_DEFAULT);
        // The history is most-recent first, so the order must be reversed
        for (int i = dataPointsOnChart - 1; i >= 0; i--) {
            String strDataPoint = strDataPoints[i];
            strDataPointSplit = strDataPoint.split(", ");
            if (strDataPointSplit.length == 2) {
                String strMillis = strDataPointSplit[0];
                String strPrice = strDataPointSplit[1];

                try {
                    String label = getLabel(Long.valueOf(strMillis));
                    float value = Float.valueOf(strPrice);

                    // Update the max and min value if they are breached
                    if (i == dataPointsOnChart - 1) {
                        mMaxPrice = value;
                        mMinPrice = value;
                    } else {
                        if (mMaxPrice < value) {
                            mMaxPrice = value;
                        }
                        if (mMinPrice > value) {
                            mMinPrice = value;
                        }
                    }

                    historicPriceData.addPoint(label, value);
                } catch (Exception e) {
                    Timber.d("Error creating HistoricPrice", e);
                }
            }
        }

        return historicPriceData;
    }

    private String getLabel(long millis) {
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(millis);

        //noinspection WrongConstant
        if (isInFirstWeekOfMonth(cal)) {
            return cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
        } else {
            return "";
        }
    }

    private boolean isInFirstWeekOfMonth(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return 1 <= day && day <= 7;
    }

//    /**
//     * Returns a list of HistoricPrices corresponding to a stock's history as stored in the
//     * database as a String
//     * @param history the history as a String
//     * @return a list of historic prices corresponding to the history
//     */
//    private List<HistoricPrice> getHistoricPrices(String history) {
//        if (history == null) {
//            return Collections.emptyList();
//        }
//        List<HistoricPrice> historicPrices = new ArrayList<>();
//
//        String[] strDataPoints = history.split("\n");
//        String[] strDataPointSplit;
//        for (String strDataPoint : strDataPoints) {
//            strDataPointSplit = strDataPoint.split(", ");
//            if (strDataPointSplit.length == 2) {
//                String strMillis = strDataPointSplit[0];
//                String strPrice = strDataPointSplit[1];
//
//                try {
//                    HistoricPrice historicPrice = new HistoricPrice(
//                            Long.valueOf(strMillis),
//                            new BigDecimal(strPrice)
//                    );
//                    historicPrices.add(historicPrice);
//                } catch (Exception e) {
//                    Timber.d("Error creating HistoricPrice", e);
//                }
//            }
//        }
//
//        return historicPrices;
//    }

//    private class HistoricPrice {
//        // see GregorianCalendar.getTimeInMillis()
//        long timeInMillis;
//        BigDecimal price;
//
//        public HistoricPrice(long timeInMillis, BigDecimal price) {
//            this.timeInMillis = timeInMillis;
//            this.price = price;
//        }
//
//        // Getters
//
//        public long getTimeInMillis() {
//            return timeInMillis;
//        }
//
//        public BigDecimal getPrice() {
//            return price;
//        }
//
//    }

}
