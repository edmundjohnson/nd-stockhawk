package com.udacity.stockhawk.ui;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.udacity.stockhawk.R;
import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.widget.ListWidgetProvider;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

import static android.icu.text.RelativeDateTimeFormatter.RelativeUnit.WEEKS;

/**
 * The activity class for the stock screen.
 * @author Edmund Johnson
 */
public class StockActivity extends AppCompatActivity {

    private static final int WEEKS_PER_YEAR = 52;
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

    @BindView(R.id.history)
    TextView history;

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

                String strHistory = cursor.getString(Contract.Quote.POSITION_HISTORY);
                history.setText(strHistory);

                List<HistoricPrice> historicPrices = getHistoricPrices(strHistory);
                String breakpoint = "s";

            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private List<HistoricPrice> getHistoricPrices(String history) {
        if (history == null) {
            return Collections.emptyList();
        }
        List<HistoricPrice> historicPrices = new ArrayList<>();

        String[] strDataPoints = history.split("\n");
        String[] strDataPointSplit;
        for (String strDataPoint : strDataPoints) {
            strDataPointSplit = strDataPoint.split(", ");
            if (strDataPointSplit.length == 2) {
                String strMillis = strDataPointSplit[0];
                String strPrice = strDataPointSplit[1];

                try {
                    HistoricPrice historicPrice = new HistoricPrice(
                            Long.valueOf(strMillis),
                            new BigDecimal(strPrice)
                    );
                    historicPrices.add(historicPrice);
                } catch (Exception e) {
                    Timber.d("Error creating HistoricPrice", e);
                }
            }
        }

        return historicPrices;
    }

    private class HistoricPrice {
        // see GregorianCalendar.getTimeInMillis()
        long timeInMillis;
        BigDecimal price;

        public HistoricPrice(long timeInMillis, BigDecimal price) {
            this.timeInMillis = timeInMillis;
            this.price = price;
        }

        // Getters

        public long getTimeInMillis() {
            return timeInMillis;
        }

        public BigDecimal getPrice() {
            return price;
        }

    }

}
