/*
 * Copyright (C) 2016 Xiao Bao Clark <xiao@xbc.nz>
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.widget.RadioGroup;

/**
 * Helper class to create a sort dialog and listen for the result
 */
public class SortDialogHelper {

	/**
	 * Wrapper around {@link #createDialog(Context, int, int[], OnSortDialogDismissListener)} that
	 * gets the sort parameters from a {@link MediaAdapter}
	 * @param context The context
	 * @param mediaAdapter The adapter that will provide the sort entries and current sort mode
	 * @param listener The dismiss listener
	 * @return The created {@link AlertDialog}. DO NOT set the
	 * 		{@link android.content.DialogInterface.OnDismissListener} on the returned dialog
	 */
	public static AlertDialog createDialog(Context context, MediaAdapter mediaAdapter,
										   final OnSortDialogDismissListener listener) {
		return createDialog(context, mediaAdapter.getSortMode(), mediaAdapter.getSortEntries(),
				listener);
	}

	/**
	 * Creates an {@link AlertDialog} that allows the user to choose between different sorting
	 * options.
	 *
	 * @param context The context
	 * @param sortMode The current sort mode (used to set initial ascending/descending)
	 * @param sortEntries The string ids of the sorting choices to present
	 * @param listener The {@link OnSortDialogDismissListener} that will be called when the dialog
	 * 		is dismissed
	 * @return The created {@link AlertDialog}. DO NOT set the
	 * 		{@link android.content.DialogInterface.OnDismissListener} on the returned dialog
	 *
	 * 	@see OnSortDialogDismissListener
	 */
	public static AlertDialog createDialog(Context context, int sortMode, int[] sortEntries,
										   final OnSortDialogDismissListener listener) {
		int check;
		if (sortMode < 0) {
			check = R.id.descending;
			sortMode = ~sortMode;
		} else {
			check = R.id.ascending;
		}

		int[] itemIds = sortEntries;
		String[] items = new String[itemIds.length];
		Resources res = context.getResources();
		for (int i = itemIds.length; --i != -1; ) {
			items[i] = res.getString(itemIds[i]);
		}

		RadioGroup header = (RadioGroup) LayoutInflater.from(context)
												 .inflate(R.layout.sort_dialog,null);
		header.check(check);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.sort_by);
		// add 1 for header
		builder.setSingleChoiceItems(items, sortMode + 1, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
			}
		});
		builder.setNeutralButton(R.string.done, null);

		final AlertDialog dialog = builder.create();
		dialog.getListView().addHeaderView(header);
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(final DialogInterface dialogInterface) {
				ListView list = dialog.getListView();
				// subtract 1 for header
				int which = list.getCheckedItemPosition() - 1;

				RadioGroup group = (RadioGroup)list.findViewById(R.id.sort_direction);
				if (group.getCheckedRadioButtonId() == R.id.descending)
					which = ~which;
				listener.onSortDialogDismissed(dialogInterface, which);
			}
		});
		return dialog;
	}

	/**
	 * A listener for when a dialog created with {@link #createDialog} is dismissed
	 */
	public interface OnSortDialogDismissListener {

		/**
		 * Called when the dialog is dismissed
		 * @param dialog The dialog that was dismissed
		 * @param sortMode The index into the array passed to {@link #createDialog}. This will
		 * be positive for if the sort is ascending and negative if the sort is descending.
		 *
		 * @see MediaAdapter#setSortMode
		 */
		void onSortDialogDismissed(DialogInterface dialog, int sortMode);
	}
}
