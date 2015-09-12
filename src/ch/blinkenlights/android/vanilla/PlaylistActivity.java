/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;

import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.SwipeDismissItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager;
import com.mobeta.android.dslv.DragSortListView;

/**
 * The playlist activity where playlist songs can be viewed and reordered.
 */
public class PlaylistActivity extends Activity
	implements View.OnClickListener
	         , DialogInterface.OnClickListener
	         , DragSortListView.DropListener
	         , DragSortListView.RemoveListener
{
	/**
	 * The SongTimeline play mode corresponding to each
	 * LibraryActivity.ACTION_*
	 */
	private static final int[] MODE_FOR_ACTION =
		{ SongTimeline.MODE_PLAY, SongTimeline.MODE_ENQUEUE, -1,
		  SongTimeline.MODE_PLAY_POS_FIRST, SongTimeline.MODE_ENQUEUE_POS_FIRST };

	/**
	 * An event loop running on a worker thread.
	 */
	private Looper mLooper;
	private RecyclerView mListView;
	private PlaylistAdapter mAdapter;

	/**
	 * The id of the playlist this activity is currently viewing.
	 */
	private long mPlaylistId;
	/**
	 * The name of the playlist this activity is currently viewing.
	 */
	private String mPlaylistName;
	/**
	 * If true, then playlists songs can be dragged to reorder.
	 */
	private boolean mEditing;

	/**
	 * The item click action specified in the preferences.
	 */
	private int mDefaultAction;
	/**
	 * The last action used from the context menu, used to implement
	 * LAST_USED_ACTION action.
	 */
	private int mLastAction = LibraryActivity.ACTION_PLAY;

	private Button mEditButton;
	private Button mDeleteButton;

	@Override
	public void onCreate(Bundle state)
	{
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(state);

		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();

		setContentView(R.layout.playlist_activity);


		mLooper = thread.getLooper();
        mListView = (RecyclerView)findViewById(R.id.list);

        initialiseListView();

		onNewIntent(getIntent());
	}

    private void initialiseListView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(layoutManager);

        //Swipe
        RecyclerViewSwipeManager swipeManager = new RecyclerViewSwipeManager();


        mAdapter = new PlaylistAdapter(this, mLooper);
        mAdapter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onItemClick(v);
            }
        });
        RecyclerView.Adapter wrappedAdapter = swipeManager.createWrappedAdapter(mAdapter);
        mListView.setAdapter(wrappedAdapter);
        mListView.setItemAnimator(createAnimator());

        swipeManager.attachRecyclerView(mListView);
    }

    private RecyclerView.ItemAnimator createAnimator() {
        final GeneralItemAnimator animator = new SwipeDismissItemAnimator();
        animator.setSupportsChangeAnimations(false);
        return animator;
    }

    private View createHeader() {
        View header = LayoutInflater.from(this).inflate(R.layout.playlist_buttons, null);
        mEditButton = (Button)header.findViewById(R.id.edit);
        mEditButton.setOnClickListener(this);
        mDeleteButton = (Button)header.findViewById(R.id.delete);
        mDeleteButton.setOnClickListener(this);
        return header;
    }

	@Override
	public void onStart()
	{
		super.onStart();
		SharedPreferences settings = PlaybackService.getSettings(this);
		mDefaultAction = Integer.parseInt(settings.getString(PrefKeys.DEFAULT_PLAYLIST_ACTION, "0"));
	}

	@Override
	public void onDestroy()
	{
		mLooper.quit();
		super.onDestroy();
	}

	@Override
	public void onNewIntent(Intent intent)
	{
		long id = intent.getLongExtra("playlist", 0);
		String title = intent.getStringExtra("title");
		mAdapter.setPlaylistId(id);
		setTitle(title);
		mPlaylistId = id;
		mPlaylistName = title;
	}

	/**
	 * Enable or disable edit mode, which allows songs to be reordered and
	 * removed.
	 *
	 * @param editing True to enable edit mode.
	 */
	public void setEditing(boolean editing)
	{
        setDragEnabled(editing);
		mAdapter.setEditable(editing);
		int visible = editing ? View.GONE : View.VISIBLE;
		mDeleteButton.setVisibility(visible);
		mEditButton.setText(editing ? R.string.done : R.string.edit);
		mEditing = editing;
	}

    private void setDragEnabled(boolean dragEnabled) {
        //mListView.setDragEnabled(editing);
    }

	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.edit:
			setEditing(!mEditing);
			break;
		case R.id.delete: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			String message = getResources().getString(R.string.delete_playlist, mPlaylistName);
			builder.setMessage(message);
			builder.setPositiveButton(R.string.delete, this);
			builder.setNegativeButton(R.string.cancel, this);
			builder.show();
			break;
		}
		}
	}

	private static final int MENU_PLAY = LibraryActivity.ACTION_PLAY;
	private static final int MENU_PLAY_ALL = LibraryActivity.ACTION_PLAY_ALL;
	private static final int MENU_ENQUEUE = LibraryActivity.ACTION_ENQUEUE;
	private static final int MENU_ENQUEUE_ALL = LibraryActivity.ACTION_ENQUEUE_ALL;
	private static final int MENU_REMOVE = -1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View listView, ContextMenu.ContextMenuInfo absInfo)
	{
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)absInfo;
		Intent intent = new Intent();
		intent.putExtra("id", info.id);
		intent.putExtra("position", info.position);
		intent.putExtra("audioId", (Long)info.targetView.findViewById(R.id.text).getTag());

		menu.add(0, MENU_PLAY, 0, R.string.play).setIntent(intent);
		menu.add(0, MENU_PLAY_ALL, 0, R.string.play_all).setIntent(intent);
		menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue).setIntent(intent);
		menu.add(0, MENU_ENQUEUE_ALL, 0, R.string.enqueue_all).setIntent(intent);
		menu.add(0, MENU_REMOVE, 0, R.string.remove).setIntent(intent);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		int itemId = item.getItemId();
		Intent intent = item.getIntent();
		int pos = intent.getIntExtra("position", -1);

		if (itemId == MENU_REMOVE) {
			mAdapter.removeItem(pos - getHeaderViewsCount());
		} else {
			performAction(itemId, pos, intent.getLongExtra("audioId", -1));
		}

		return true;
	}

	/**
	 * Perform the specified action on the adapter row with the given id and
	 * position.
	 *
	 * @param action One of LibraryActivity.ACTION_*.
	 * @param position The position in the adapter.
	 * @param audioId The id of the selected song, for PLAY/ENQUEUE.
	 */
	private void performAction(int action, int position, long audioId)
	{
		if (action == LibraryActivity.ACTION_LAST_USED)
			action = mLastAction;

		switch (action) {
		case LibraryActivity.ACTION_PLAY:
		case LibraryActivity.ACTION_ENQUEUE: {
			QueryTask query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, audioId, Song.FILLED_PROJECTION, null);
			query.mode = MODE_FOR_ACTION[action];
			PlaybackService.get(this).addSongs(query);
			break;
		}
		case LibraryActivity.ACTION_PLAY_ALL:
		case LibraryActivity.ACTION_ENQUEUE_ALL: {
			QueryTask query = MediaUtils.buildPlaylistQuery(mPlaylistId, Song.FILLED_PLAYLIST_PROJECTION, null);
			query.mode = MODE_FOR_ACTION[action];
			query.data = position - getHeaderViewsCount();
			PlaybackService.get(this).addSongs(query);
			break;
		}
		}

		mLastAction = action;
	}

    private int getHeaderViewsCount() {
        return 0;
    }

	public void onItemClick(View item)
	{
		if (!mEditing && mDefaultAction != LibraryActivity.ACTION_DO_NOTHING) {
            int position = mListView.getChildAdapterPosition(item);
			// fixme: this is butt ugly: the adapter should probably already set this on view (its parent)
			// setting this on the textarea is hacky
			performAction(mDefaultAction, position, (Long)item.findViewById(R.id.text).getTag());
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (which == DialogInterface.BUTTON_POSITIVE) {
			Playlist.deletePlaylist(getContentResolver(), mPlaylistId);
			finish();
		}
		dialog.dismiss();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Fired from adapter listview  if user moved an item
	 * @param from the item index that was dragged
	 * @param to the index where the item was dropped
	 */
	@Override
	public void drop(int from, int to) {
		mAdapter.moveItem(from, to);
	}

	/**
	 * Fired from adapter listview if user fling-removed an item
	 * @param position The position of the removed item
	 */
	@Override
	public void remove(int position) {
		mAdapter.removeItem(position);
	}

}
