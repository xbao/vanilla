/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.LogPrinter;
import android.util.Printer;

import java.util.Arrays;

/**
 * Represents a pending query.
 */
public class QueryTask {
	public Uri uri;
	public final String[] projection;
	public final String selection;
	public final String[] selectionArgs;
	public String sortOrder;

	/**
	 * Used for {@link SongTimeline#addSongs(android.content.Context, QueryTask)}.
	 * One of SongTimeline.MODE_*.
	 */
	public int mode;

	/**
	 * Type of the group being query. One of MediaUtils.TYPE_*.
	 */
	public int type;

	/**
	 * Data. Required value depends on value of mode. See individual mode
	 * documentation for details.
	 */
	public long data;

	/**
	 * Create the tasks. All arguments are passed directly to
	 * ContentResolver.query().
	 */
	public QueryTask(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		this.uri = uri;
		this.projection = projection;
		this.selection = selection;
		this.selectionArgs = selectionArgs;
		this.sortOrder = sortOrder;
	}

	/**
	 * Run the query. Should be called on a background thread.
	 *
	 * @param resolver The ContentResolver to query with.
	 */
	public Cursor runQuery(ContentResolver resolver)
	{
		if (BuildConfig.DEBUG) {
			dumpQuery(new LogPrinter(Log.VERBOSE, "VanillaMusic"));
			logAndClose(resolver.query(uri, projection, selection, selectionArgs, sortOrder));
		}
		return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
	}

	public void dumpQuery(Printer printer) {
		printer.println("uri=" + uri + "," +
				"projection=" + Arrays.toString(projection) + "," +
				"selection=" + selection + "," +
				"selectionArgs=" + Arrays.toString(selectionArgs) + "," +
				"sortOrder=" + sortOrder);
	}

	private static void logAndClose(Cursor cursor) {
		if (cursor == null) {
			Log.d("VanillaMusic", "null cursor");
			return;
		}
		try {
			logCursor(cursor);
		} finally {
			cursor.close();
		}

	}

	private static void logCursor(Cursor cursor) {
		if (cursor.getCount() == 0) {
			Log.d("VanillaMusic", "empty cursor");
			return;
		}
		Log.d("VanillaMusic", "Count: " + cursor.getCount());
		final String[] columnNames = cursor.getColumnNames();
		Log.d("VanillaMusic", "Columns: " + TextUtils.join(",", columnNames));
		for (int i = 0; i < columnNames.length; i++) {
			int dotIdx = columnNames[i].lastIndexOf('.');
			if (dotIdx != -1) {
				columnNames[i] = columnNames[i].substring(dotIdx + 1);
			}
		}
		while (cursor.moveToNext()) {
			StringBuilder builder = new StringBuilder();
			builder.append("[").append(cursor.getPosition()).append("] ");
			for (String column : columnNames) {
				builder.append(column).append(":").append(getString(cursor, column));
				if (column != columnNames[columnNames.length - 1]) {
					builder.append(", ");
				}
			}
			Log.d("VanillaMusic", builder.toString());
		}
	}

	private static String getString(Cursor cursor, String columnName) {
		int index = cursor.getColumnIndex(columnName);
		int type = cursor.getType(index);
		switch (type) {
			case Cursor.FIELD_TYPE_NULL:
				return null;
			case Cursor.FIELD_TYPE_BLOB:
				return "<blob>";
			case Cursor.FIELD_TYPE_FLOAT:
				return String.format("%f", cursor.getFloat(index));
			case Cursor.FIELD_TYPE_INTEGER:
				return String.format("%d", cursor.getInt(index));
			case Cursor.FIELD_TYPE_STRING:
				return cursor.getString(index);
		}
		throw new IllegalArgumentException("type not valid. cursor=" + cursor.toString() + ", " +
				"column=" + columnName);
	}
}
