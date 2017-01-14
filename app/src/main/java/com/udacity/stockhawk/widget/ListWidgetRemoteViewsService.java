package com.udacity.stockhawk.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Binder;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.R;
import com.udacity.stockhawk.ui.StockAdapter;

import java.text.DecimalFormat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * RemoteViewsService which controls the data shown in the scrollable stock list widget.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ListWidgetRemoteViewsService extends RemoteViewsService {
//    public final String LOG_TAG = ListWidgetRemoteViewsService.class.getSimpleName();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor cursor = null;

            @Override
            public void onCreate() {
                // Nothing to do
            }

            @Override
            public void onDataSetChanged() {
                if (cursor != null) {
                    cursor.close();
                }
                // This method is called by the app hosting the widget (e.g., the launcher)
                // However, our ContentProvider is not exported so it doesn't have access to the
                // data. Therefore we need to clear (and finally restore) the calling identity so
                // that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();

                cursor = getContentResolver().query(
                        Contract.Quote.URI,
                        Contract.Quote.QUOTE_COLUMNS,
                        null,
                        null,
                        Contract.Quote.COLUMN_SYMBOL);

                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            @Override
            public int getCount() {
                return cursor == null ? 0 : cursor.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        cursor == null || !cursor.moveToPosition(position)) {
                    return null;
                }

                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_list_list_item);

                String symbol = cursor.getString(Contract.Quote.POSITION_SYMBOL);

                // symbol
                views.setTextViewText(R.id.symbol, symbol);

                // price
                DecimalFormat dollarFormat = StockAdapter.getDollarFormat();
                views.setTextViewText(R.id.price,
                        dollarFormat.format(cursor.getFloat(Contract.Quote.POSITION_PRICE)));

                // change
                float rawAbsoluteChange = cursor.getFloat(Contract.Quote.POSITION_ABSOLUTE_CHANGE);
                float percentageChange = cursor.getFloat(Contract.Quote.POSITION_PERCENTAGE_CHANGE);

                //String change = dollarFormatWithPlus.format(rawAbsoluteChange);
                //String percentage = percentageFormat.format(percentageChange / 100);

// TODO: When PrefUtils.getDisplayMode(...) changes, update widget
                Context context = getBaseContext();

                String displayedChange;
                if (PrefUtils.getDisplayMode(context).equals(context.getString(R.string.pref_display_mode_absolute_key))) {
                    DecimalFormat dollarFormatWithPlus = StockAdapter.getDollarFormatWithPlus();
                    displayedChange = dollarFormatWithPlus.format(rawAbsoluteChange);
                } else {
                    DecimalFormat percentageFormat = StockAdapter.getPercentageFormat();
                    displayedChange = percentageFormat.format(percentageChange / 100);
                }

                if (rawAbsoluteChange > 0) {
                    views.setViewVisibility(R.id.changeNegative, GONE);
                    views.setTextViewText(R.id.changeNegative, null);
                    views.setTextViewText(R.id.changePositive, displayedChange);
                    views.setViewVisibility(R.id.changePositive, VISIBLE);
                } else {
                    views.setViewVisibility(R.id.changePositive, GONE);
                    views.setTextViewText(R.id.changePositive, null);
                    views.setTextViewText(R.id.changeNegative, displayedChange);
                    views.setViewVisibility(R.id.changeNegative, VISIBLE);
                }

                // TODO: click on symbol -> detail (history graph?) for symbol
                final Intent fillInIntent = new Intent();
                Uri stockUri = Contract.Quote.makeUriForStock(symbol);
                fillInIntent.setData(stockUri);
                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);
                return views;
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_list_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (cursor.moveToPosition(position)) {
                    return cursor.getLong(Contract.Quote.POSITION_ID);
                }
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
