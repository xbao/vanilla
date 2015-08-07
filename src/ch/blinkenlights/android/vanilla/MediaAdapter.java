/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.LruCache;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.lang.StringBuilder;

/**
 * MediaAdapter provides an adapter backed by a MediaStore content provider.
 * It generates simple one- or two-line text views to display each media
 * element.
 *
 * Filtering is supported, as is a more specific type of filtering referred to
 * as limiting. Limiting is separate from filtering; a new filter will not
 * erase an active filter. Limiting is intended to allow only media belonging
 * to a specific group to be displayed, e.g. only songs from a certain artist.
 * See getLimiter and setLimiter for details.
 */
public class MediaAdapter
	extends BaseAdapter
	implements LibraryAdapter
	         , View.OnClickListener
{
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");

	private static final String SORT_MAGIC_PLAYCOUNT = "__PLAYCOUNT_SORT";

	/**
	 * A context to use.
	 */
	private final LibraryActivity mActivity;
	/**
	 * A LayoutInflater to use.
	 */
	private final LayoutInflater mInflater;
	/**
	 * The current data.
	 */
	private Cursor mCursor;
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * MediaUtils.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	private final int mType;
	/**
	 * The URI of the content provider backing this adapter.
	 */
	private Uri mStore;
	/**
	 * The fields to use from the content provider. The last field will be
	 * displayed in the MediaView, as will the first field if there are
	 * multiple fields. Other fields will be used for searching.
	 */
	private String[] mFields;
	/**
	 * The collation keys corresponding to each field. If provided, these are
	 * used to speed up sorting and filtering.
	 */
	private String[] mFieldKeys;
	/**
	 * The columns to query from the content provider.
	 */
	private String[] mProjection;
	/**
	 * A limiter is used for filtering. The intention is to restrict items
	 * displayed in the list to only those of a specific artist or album, as
	 * selected through an expander arrow in a broader MediaAdapter list.
	 */
	private Limiter mLimiter;
	/**
	 * The constraint used for filtering, set by the search box.
	 */
	private String mConstraint;
	/**
	 * The sort order for use with buildSongQuery().
	 */
	private String mSongSort;
	/**
	 * The human-readable descriptions for each sort mode.
	 */
	private int[] mSortEntries;
	/**
	 * An array ORDER BY expressions for each sort mode. %1$s is replaced by
	 * ASC or DESC as appropriate before being passed to the query.
	 */
	private String[] mSortValues;
	/**
	 * The index of the current of the current sort mode in mSortValues, or
	 * the inverse of the index (in which case sort should be descending
	 * instead of ascending).
	 */
	private int mSortMode;
	/**
	 * If true, show the expander button on each row.
	 */
	private boolean mExpandable;
	/**
	 * If true, return views with covers and fire callbacks
	 */
	private boolean mHasCoverArt;
	/**
	 * The drawable cache instance.
	 */
	private static DrawableCache sDrawableCache = null;

	/**
	 * Callback Interface to notify about missing cover elements
	 */
	public interface Callback {
		/**
		 * Called when a view is missing its cover
		 */
		public void onNeedArtworkUpdate(int type, ViewHolder holder);
	}

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param activity The LibraryActivity that will contain this adapter.
	 * @param type The type of media to represent. Must be one of the
	 * Song.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param limiter An initial limiter to use
	 */
	public MediaAdapter(LibraryActivity activity, int type, Limiter limiter)
	{
		mActivity = activity;
		mType = type;
		mLimiter = limiter;
		mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (sDrawableCache == null) {
			sDrawableCache = new DrawableCache(6 * 1024 * 1024);
		}

		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			mStore = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Artists.ARTIST };
			mFieldKeys = new String[] { MediaStore.Audio.Artists.ARTIST_KEY };
			mSongSort = MediaUtils.DEFAULT_SORT;
			mSortEntries = new int[] { R.string.name, R.string.number_of_tracks };
			mSortValues = new String[] { "artist_key %1$s", "number_of_tracks %1$s,artist_key %1$s" };
			break;
		case MediaUtils.TYPE_ALBUM:
			mStore = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
			// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
			mFieldKeys = new String[] { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
			mSongSort = MediaUtils.ALBUM_SORT;
			mSortEntries = new int[] { R.string.name, R.string.artist_album, R.string.year, R.string.number_of_tracks, R.string.date_added };
			mSortValues = new String[] { "album_key %1$s", "artist_key %1$s,album_key %1$s", "minyear %1$s,album_key %1$s", "numsongs %1$s,album_key %1$s", "_id %1$s" };
			mHasCoverArt = true;
			break;
		case MediaUtils.TYPE_SONG:
			mStore = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE };
			mFieldKeys = new String[] { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY };
			mSortEntries = new int[] { R.string.name, R.string.artist_album_track, R.string.artist_album_title,
			                           R.string.artist_year, R.string.album_track, R.string.year, R.string.date_added, R.string.song_playcount };
			mSortValues = new String[] { "title_key %1$s", "artist_key %1$s,album_key %1$s,track %1$s", "artist_key %1$s,album_key %1$s,title_key %1$s",
			                             "artist_key %1$s,year %1$s,track %1$s", "album_key %1$s,track %1s", "year %1$s,title_key %1$s", "_id %1$s", SORT_MAGIC_PLAYCOUNT };
			mHasCoverArt = true;
			break;
		case MediaUtils.TYPE_PLAYLIST:
			mStore = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Playlists.NAME };
			mFieldKeys = null;
			mSortEntries = new int[] { R.string.name, R.string.date_added };
			mSortValues = new String[] { "name %1$s", "date_added %1$s" };
			mExpandable = true;
			break;
		case MediaUtils.TYPE_GENRE:
			mStore = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Genres.NAME };
			mFieldKeys = null;
			mSortEntries = new int[] { R.string.name };
			mSortValues = new String[] { "name %1$s" };
			break;
		default:
			throw new IllegalArgumentException("Invalid value for type: " + type);
		}

		if (mFields.length == 1)
			mProjection = new String[] { BaseColumns._ID, mFields[0] };
		else
			mProjection = new String[] { BaseColumns._ID, mFields[mFields.length - 1], mFields[0] };
	}

	/**
	 * Set whether or not the expander button should be shown in each row.
	 * Defaults to true for playlist adapter and false for all others.
	 *
	 * @param expandable True to show expander, false to hide.
	 */
	public void setExpandable(boolean expandable)
	{
		if (expandable != mExpandable) {
			mExpandable = expandable;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setFilter(String filter)
	{
		mConstraint = filter;
	}

	/**
	 * Build the query to be run with runQuery().
	 *
	 * @param projection The columns to query.
	 * @param forceMusicCheck Force the is_music check to be added to the
	 * selection.
	 */
	private QueryTask buildQuery(String[] projection, boolean forceMusicCheck)
	{
		String constraint = mConstraint;
		Limiter limiter = mLimiter;

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs = null;

		int mode = mSortMode;
		String sortDir;
		if (mode < 0) {
			mode = ~mode;
			sortDir = "DESC";
		} else {
			sortDir = "ASC";
		}

		String sortStringRaw = mSortValues[mode];

		// Magic sort mode: sort by playcount
		if (sortStringRaw == SORT_MAGIC_PLAYCOUNT) {
			ArrayList<Long> topSongs = (new PlayCountsHelper(mActivity)).getTopSongs();
			int sortWeight = -1 * topSongs.size(); // Sort mode is actually reversed (default: mostplayed -> leastplayed)

			StringBuilder sb = new StringBuilder("CASE WHEN _id=0 THEN 0"); // include dummy statement in initial string -> topSongs may be empty
			for (Long id : topSongs) {
				sb.append(" WHEN _id="+id+" THEN "+sortWeight);
				sortWeight++;
			}
			sb.append(" ELSE 0 END %1s");
			sortStringRaw = sb.toString();
		}

		String sort = String.format(sortStringRaw, sortDir);

		if (mType == MediaUtils.TYPE_SONG || forceMusicCheck)
			selection.append("is_music AND length(_data)");

		if (constraint != null && constraint.length() != 0) {
			String[] needles;
			String[] keySource;

			// If we are using sorting keys, we need to change our constraint
			// into a list of collation keys. Otherwise, just split the
			// constraint with no modification.
			if (mFieldKeys != null) {
				String colKey = MediaStore.Audio.keyFor(constraint);
				String spaceColKey = DatabaseUtils.getCollationKey(" ");
				needles = colKey.split(spaceColKey);
				keySource = mFieldKeys;
			} else {
				needles = SPACE_SPLIT.split(constraint);
				keySource = mFields;
			}

			int size = needles.length;
			selectionArgs = new String[size];

			StringBuilder keys = new StringBuilder(20);
			keys.append(keySource[0]);
			for (int j = 1; j != keySource.length; ++j) {
				keys.append("||");
				keys.append(keySource[j]);
			}

			for (int j = 0; j != needles.length; ++j) {
				selectionArgs[j] = '%' + needles[j] + '%';

				// If we have something in the selection args (i.e. j > 0), we
				// must have something in the selection, so we can skip the more
				// costly direct check of the selection length.
				if (j != 0 || selection.length() != 0)
					selection.append(" AND ");
				selection.append(keys);
				selection.append(" LIKE ?");
			}
		}

		if (limiter != null && limiter.type == MediaUtils.TYPE_GENRE) {
			// Genre is not standard metadata for MediaStore.Audio.Media.
			// We have to query it through a separate provider. : /
			return MediaUtils.buildGenreQuery((Long)limiter.data, projection,  selection.toString(), selectionArgs, sort);
		} else {
			if (limiter != null) {
				if (selection.length() != 0)
					selection.append(" AND ");
				selection.append(limiter.data);
			}

			return new QueryTask(mStore, projection, selection.toString(), selectionArgs, sort);
		}
	}

	@Override
	public Object query()
	{
		return buildQuery(mProjection, false).runQuery(mActivity.getContentResolver());
	}

	@Override
	public void commitQuery(Object data)
	{
		changeCursor((Cursor)data);
	}

	/**
	 * Build a query for all the songs represented by this adapter, for adding
	 * to the timeline.
	 *
	 * @param projection The columns to query.
	 */
	public QueryTask buildSongQuery(String[] projection)
	{
		QueryTask query = buildQuery(projection, true);
		query.type = mType;
		if (mType != MediaUtils.TYPE_SONG) {
			query.uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			// Would be better to match the sort order in the adapter. This
			// is likely to require significantly more work though.
			query.sortOrder = mSongSort;
		}
		return query;
	}

	@Override
	public void clear()
	{
		changeCursor(null);
	}

	@Override
	public int getMediaType()
	{
		return mType;
	}

	@Override
	public void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	@Override
	public Limiter buildLimiter(long id)
	{
		String[] fields;
		Object data;

		Cursor cursor = mCursor;
		if (cursor == null)
			return null;
		for (int i = 0, count = cursor.getCount(); i != count; ++i) {
			cursor.moveToPosition(i);
			if (cursor.getLong(0) == id)
				break;
		}

		switch (mType) {
		case MediaUtils.TYPE_ARTIST:
			fields = new String[] { cursor.getString(1) };
			data = String.format("%s=%d", MediaStore.Audio.Media.ARTIST_ID, id);
			break;
		case MediaUtils.TYPE_ALBUM:
			fields = new String[] { cursor.getString(2), cursor.getString(1) };
			data = String.format("%s=%d",  MediaStore.Audio.Media.ALBUM_ID, id);
			break;
		case MediaUtils.TYPE_GENRE:
			fields = new String[] { cursor.getString(1) };
			data = id;
			break;
		default:
			throw new IllegalStateException("getLimiter() is not supported for media type: " + mType);
		}

		return new Limiter(mType, fields, data);
	}

	/**
	 * Set a new cursor for this adapter. The old cursor will be closed.
	 *
	 * @param cursor The new cursor.
	 */
	public void changeCursor(Cursor cursor)
	{
		Cursor old = mCursor;
		mCursor = cursor;
		if (cursor == null) {
			notifyDataSetInvalidated();
		} else {
			notifyDataSetChanged();
		}
		if (old != null) {
			old.close();
		}
	}

	@Override
	public View getView(int position, View view, ViewGroup parent)
	{
		ViewHolder holder;

		if (view == null) {
			// We must create a new view if we're not given a recycle view or
			// if the recycle view has the wrong layout.

			view = mInflater.inflate(R.layout.library_row_expandable, null);
			holder = new ViewHolder();
			view.setTag(holder);

			holder.text = (TextView)view.findViewById(R.id.text);
			holder.arrow = (ImageView)view.findViewById(R.id.arrow);
			holder.cover = (ImageView)view.findViewById(R.id.cover);
			holder.text.setOnClickListener(this);
			holder.arrow.setOnClickListener(this);
			holder.cover.setOnClickListener(this);

			holder.arrow.setVisibility(mExpandable ? View.VISIBLE : View.GONE);
			holder.cover.setVisibility(mHasCoverArt ? View.VISIBLE : View.GONE);

		} else {
			holder = (ViewHolder)view.getTag();

			synchronized(holder) {
				if (holder.coverCacheable) {
					// This cover has been 'created': save it into the cache
					sDrawableCache.put(holder.cacheKey, holder.cover.getDrawable());
				}
			}
		}

		Cursor cursor = mCursor;
		cursor.moveToPosition(position);
		holder.id = cursor.getLong(0);
		holder.cacheKey = mType+"//"+holder.id;
		if (mFields.length > 1) {
			String line1 = cursor.getString(1);
			String line2 = cursor.getString(2);
			if(line1 == null) { line1 = "???"; }
			if(line2 == null) { line2 = "???"; }
			SpannableStringBuilder sb = new SpannableStringBuilder(line1);
			sb.append('\n');
			sb.append(line2);
			sb.setSpan(new ForegroundColorSpan(Color.GRAY), line1.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			holder.text.setText(sb);
			holder.title = line1;
		} else {
			String title = cursor.getString(1);
			if(title == null) { title = "???"; }
			holder.text.setText(title);
			holder.title = title;
		}

		if (mHasCoverArt) {
			Drawable cachedCover = sDrawableCache.get(holder.cacheKey);
			if (cachedCover == null) {
				holder.coverCacheable = false;
				holder.cover.setImageResource(R.drawable.fallback_cover);
				mActivity.onNeedArtworkUpdate(mType, holder);
			} else {
				holder.coverCacheable = true; // read from cache-> we can re-cache it
				holder.cover.setImageDrawable(cachedCover);
			}
		}

		return view;
	}

	/**
	 * Returns the type of the current limiter.
	 *
	 * @return One of MediaUtils.TYPE_, or MediaUtils.TYPE_INVALID if there is
	 * no limiter set.
	 */
	public int getLimiterType()
	{
		Limiter limiter = mLimiter;
		if (limiter != null)
			return limiter.type;
		return MediaUtils.TYPE_INVALID;
	}

	/**
	 * Return the available sort modes for this adapter.
	 *
	 * @return An array containing the resource ids of the sort mode strings.
	 */
	public int[] getSortEntries()
	{
		return mSortEntries;
	}

	/**
	 * Set the sorting mode. The adapter should be re-queried after changing
	 * this.
	 *
	 * @param i The index of the sort mode in the sort entries array. If this
	 * is negative, the inverse of the index will be used and sort order will
	 * be reversed.
	 */
	public void setSortMode(int i)
	{
		mSortMode = i;
	}

	/**
	 * Returns the sort mode that should be used if no preference is saved. This
	 * may very based on the active limiter.
	 */
	public int getDefaultSortMode()
	{
		int type = mType;
		if (type == MediaUtils.TYPE_ALBUM || type == MediaUtils.TYPE_SONG)
			return 1; // aritst,album,track
		return 0;
	}

	/**
	 * Return the current sort mode set on this adapter.
	 */
	public int getSortMode()
	{
		return mSortMode;
	}

	@Override
	public Intent createData(View view)
	{
		ViewHolder holder = (ViewHolder)view.getTag();
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_TYPE, mType);
		intent.putExtra(LibraryAdapter.DATA_ID, holder.id);
		intent.putExtra(LibraryAdapter.DATA_TITLE, holder.title);
		intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, mExpandable);
		return intent;
	}

	@Override
	public void onClick(View view)
	{
		int id = view.getId();
		view = (View)view.getParent(); // get view of linear layout, not the click consumer
		Intent intent = createData(view);
		if (id == R.id.arrow) {
			mActivity.onItemExpanded(intent);
		} else {
			mActivity.onItemClicked(intent);
		}
	}

	@Override
	public int getCount()
	{
		Cursor cursor = mCursor;
		if (cursor == null)
			return 0;
		return cursor.getCount();
	}

	@Override
	public Object getItem(int position)
	{
		return null;
	}

	@Override
	public long getItemId(int position)
	{
		Cursor cursor = mCursor;
		if (cursor == null || cursor.getCount() == 0)
			return 0;
		cursor.moveToPosition(position);
		return cursor.getLong(0);
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	private class DrawableCache extends LruCache<String, Drawable> {
		public DrawableCache(int size) {
			super(size);
		}
	}

}
