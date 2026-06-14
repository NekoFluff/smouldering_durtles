/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smouldering_durtles.wk.util;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.cursoradapter.widget.CursorAdapter;

import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.db.model.Subject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smouldering_durtles.wk.util.ObjectSupport.runAsync;

/**
 * Manages toolbar search suggestions: attaches a custom suggestion adapter to a SearchView,
 * queries subjects on a background thread, and cancels stale results when the query changes.
 */
public final class SearchSuggestionsManager {
    private final Context context;
    private final LayoutInflater inflater;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger queryGeneration = new AtomicInteger(0);

    public SearchSuggestionsManager(final Context context, final LayoutInflater inflater) {
        this.context = context;
        this.inflater = inflater;
    }

    public void attach(final SearchView searchView, final String subjectInfoUriBase,
            final ComponentName searchActivity) {
        final CursorAdapter suggestAdapter = new CursorAdapter(context, null, false) {
            @Override
            public View newView(final Context ctx, final Cursor cursor, final ViewGroup parent) {
                return inflater.inflate(R.layout.search_suggestion_item, parent, false);
            }

            @Override
            public void bindView(final View view, final Context ctx, final Cursor cursor) {
                final TextView t1 = view.findViewById(android.R.id.text1);
                final TextView t2 = view.findViewById(android.R.id.text2);
                final int c1 = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1);
                final int c2 = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2);
                if (t1 != null && c1 >= 0) t1.setText(cursor.getString(c1));
                if (t2 != null && c2 >= 0) t2.setText(cursor.getString(c2));
            }
        };

        searchView.setSuggestionsAdapter(suggestAdapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                if (query != null && !query.trim().isEmpty()) {
                    final Intent intent = new Intent(Intent.ACTION_SEARCH);
                    intent.setComponent(searchActivity);
                    intent.putExtra(SearchManager.QUERY, query.trim());
                    context.startActivity(intent);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                if (newText != null && newText.trim().length() >= 3) {
                    final String trimmed = newText.trim();
                    final int generation = queryGeneration.incrementAndGet();
                    runAsync(() -> {
                        final List<Subject> subjects = SearchUtil.searchSubjectSuggestions(trimmed);
                        if (queryGeneration.get() != generation) {
                            return;
                        }
                        final MatrixCursor mc = new MatrixCursor(new String[]{
                                BaseColumns._ID,
                                SearchManager.SUGGEST_COLUMN_TEXT_1,
                                SearchManager.SUGGEST_COLUMN_TEXT_2,
                                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID});
                        for (final Subject s : subjects) {
                            mc.addRow(new Object[]{
                                    s.getId(),
                                    s.getSearchSuggestionText(),
                                    s.getMeaningRichText("").toString(),
                                    s.getId()});
                        }
                        mainHandler.post(() -> {
                            if (queryGeneration.get() == generation) {
                                suggestAdapter.changeCursor(mc);
                            }
                        });
                    });
                } else {
                    queryGeneration.incrementAndGet();
                    suggestAdapter.changeCursor(null);
                }
                return false;
            }
        });

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(final int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(final int position) {
                final Cursor cursor = suggestAdapter.getCursor();
                if (cursor != null && cursor.moveToPosition(position)) {
                    final long id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
                    final Uri uri = Uri.parse(subjectInfoUriBase + "/" + id);
                    context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
                return true;
            }
        });
    }
}
