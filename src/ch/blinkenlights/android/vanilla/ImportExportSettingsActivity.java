/*
 * Copyright (C) 2015-2016 Xiao Bao Clark <xiao@xbc.nz>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * An Activity that allows the user to
 * A) save their current settings (aka preferences) to a file
 * B) load a file of previously saved settings, replacing their current settings
 * C) check whether the settings stored in a file are the same as their current settings
 */
public class ImportExportSettingsActivity extends Activity {

	private SharedPreferences mPreferences;

	public static final String ACTION_SETTINGS_IMPORTED =
			"ch.blinkenlights.android.vanilla.action.SETTINGS_IMPORTED";
	private final File mSettingsFile = new File(Environment.getExternalStorageDirectory(),
			"vanilla_settings");
	private View mImportButton;
	private View mExportButton;
	private SettingsFileState mSettingsFileState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_export_settings_activity);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		TextView filePathTextView = (TextView) findViewById(R.id.file_path);
		filePathTextView.setText(mSettingsFile.getPath());

		mImportButton = findViewById(R.id.import_settings);
		mExportButton = findViewById(R.id.export_settings);

		mImportButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				performOperationWithConfirm(Operation.IMPORT);
			}
		});
		mExportButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				performOperationWithConfirm(Operation.EXPORT);
			}
		});

		updateFileState();
	}

	/**
	 * Handles press on the top-left home/back button
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void postFinish() {
		new Handler(getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				finish();
			}
		});
	}

	/**
	 * Calls {@link #setFileState(SettingsFileState)} with the current state of the file.
	 *
	 * @see SettingsFileState#from(File, SharedPreferences)
	 */
	private void updateFileState() {

		// Compare current settings to saved
		final SettingsFileState settingsFileState;
		try {
			settingsFileState = SettingsFileState.from(mSettingsFile, mPreferences);
		} catch (IOException e) {
			// This is generic IO error (we didn't attempt any packing/unpacking)
			e.printStackTrace();
			return;
		}
		setFileState(settingsFileState);
	}

	/**
	 * Sets {@link #mSettingsFileState}, enabling or disabling the import/export buttons as
	 * appropriate
	 *
	 * @param newState The new file state. Must not be null
	 */
	private void setFileState(SettingsFileState newState) {
		if (mSettingsFileState == newState) {
			return;
		}
		mSettingsFileState = newState;
		// User can import only if the file exists and it is not the same as their current
		// preferences
		mImportButton.setEnabled(newState == SettingsFileState.DIFFERS_FROM_CURRENT);

		// User can export if there's no existing file or the file differs from their current
		// preferences
		boolean userCanExport = newState == SettingsFileState.DOES_NOT_EXIST ||
				newState == SettingsFileState.DIFFERS_FROM_CURRENT;
		mExportButton.setEnabled(userCanExport);
	}

	/**
	 * Replaces the Activity's view with an error message and a stack trace
	 *
	 * @param operation The {@link ch.blinkenlights.android.vanilla
	 * .ImportExportSettingsActivity.Operation} that failed.
	 * @param error The throwable that caused the error.
	 */
	private void onTerribleFailure(Operation operation, Throwable error) {
		CharSequence message = getText(operation.errorId);
		final ViewGroup rootView = (ViewGroup) findViewById(R.id.settings_root);

		rootView.removeAllViews();
		LayoutInflater.from(this).inflate(R.layout.import_export_settings_error, rootView, true);

		TextView errorMessageView = (TextView) rootView.findViewById(R.id.error_message);
		errorMessageView.setText(message);

		TextView errorDetailsView = (TextView) rootView.findViewById(R.id.error_details);
		errorDetailsView.setText(Log.getStackTraceString(error));
	}

	/**
	 * Runs a given import/export operation. If the file is different to their current settings, we
	 * first ask the user to confirm that they want to overwrite their existing file/preferences
	 *
	 * @param operation The operation to run if the user confirms the overwrite or if nothing will
	 * be overwritten
	 * @see Operation#confirmDialogTitleId
	 * @see #performOperation(Operation)
	 */
	private void performOperationWithConfirm(final Operation operation) {
		if (mSettingsFileState == SettingsFileState.DIFFERS_FROM_CURRENT) {
			new AlertDialog.Builder(ImportExportSettingsActivity.this)
					.setTitle(operation.confirmDialogTitleId)
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog, final int which) {
							performOperation(operation);
						}
					})
					.setNegativeButton(R.string.cancel, null)
					.show();
		} else {
			performOperation(operation);
		}
	}

	/**
	 * Runs an import/export operation then updates the settings file state.
	 *
	 * Failures are handled by {@link #onTerribleFailure(Operation, Throwable)}.
	 *
	 * On success we show a Toast. In addition, after importing settings we
	 * broadcast an intent with {@link #ACTION_SETTINGS_IMPORTED} and finish this activity.
	 *
	 * @param operation The operation to run.
	 * @see #onTerribleFailure(Operation, Throwable)
	 * @see Operation#toastTextId
	 * @see PreferencesActivity#mSettingsImportedReceiver
	 * @see OperationImpls
	 */
	private void performOperation(Operation operation) {
		boolean operationSucceeded = false;
		try {
			operation.run(mPreferences, mSettingsFile);
			operationSucceeded = true;
		} catch (IOException e) {
			onTerribleFailure(operation, e);
		}

		if (operationSucceeded) {
			Toast.makeText(ImportExportSettingsActivity.this,
					operation.toastTextId, Toast.LENGTH_SHORT).show();
			if (operation == Operation.IMPORT) {
				final Intent settingsImportedIntent = new Intent(ACTION_SETTINGS_IMPORTED);
				settingsImportedIntent.setPackage(getPackageName());
				sendBroadcast(settingsImportedIntent);
				postFinish();
			}
		}
		updateFileState();
	}

	/**
	 * An enum that encapsulates static differences between the import and export operations
	 */
	private enum Operation {
		EXPORT(R.string.export_settings_toast,
				R.string.export_settings_error,
				R.string.export_confirm_overwrite_dialog_title) {
			@Override
			void run(final SharedPreferences preferences, final File settingsFile)
					throws IOException {
				OperationImpls.exportPreferences(preferences, new FileOutputStream(settingsFile));
			}
		},
		IMPORT(R.string.import_settings_toast,
				R.string.import_settings_error,
				R.string.import_confirm_overwrite_dialog_title) {
			@Override
			void run(final SharedPreferences preferences, final File settingsFile)
					throws IOException {
				OperationImpls.importPreferences(preferences, new FileInputStream(settingsFile));
			}
		};
		public final int toastTextId;
		public final int errorId;
		public final int confirmDialogTitleId;

		Operation(final int toastTextId, final int errorId, final int confirmDialogTitleId) {
			this.toastTextId = toastTextId;
			this.errorId = errorId;
			this.confirmDialogTitleId = confirmDialogTitleId;
		}

		/**
		 * Does the import or export
		 *
		 * @param preferences The preferences object to import into/export from
		 * @param settingsFile The file to import from/export to
		 * @throws IOException if an IO error occurred during the operation
		 * @see OperationImpls#importPreferences(SharedPreferences, InputStream)
		 * @see OperationImpls#exportPreferences(SharedPreferences, OutputStream)
		 */
		abstract void run(SharedPreferences preferences, File settingsFile) throws IOException;
	}

	/**
	 * The possible states of the settings file
	 */
	private enum SettingsFileState {
		/**
		 * The settings file doesn't exist
		 */
		DOES_NOT_EXIST,
		/**
		 * The settings file exists and contains settings that are identical to the current user's
		 * settings
		 */
		SAME_AS_CURRENT,
		/**
		 * The settings file exists and contains settings that are different from the current user's
		 * settings
		 */
		DIFFERS_FROM_CURRENT;

		private static SettingsFileState from(File file, SharedPreferences current)
				throws IOException {
			if (!file.exists()) {
				return DOES_NOT_EXIST;
			}

			final Map<String, ?> stored = OperationImpls.readPreferenceMap(new FileInputStream
					(file));

			if (stored.equals(current.getAll())) {
				return SAME_AS_CURRENT;
			} else {
				return DIFFERS_FROM_CURRENT;
			}
		}
	}

	/**
	 * Container for the code that does the actual importing/exporting
	 */
	private static class OperationImpls {

		/**
		 * Serialises a {@link SharedPreferences} object and writes it to an {@link OutputStream}.
		 * The written preferences can be read back by
		 * {@link #importPreferences(SharedPreferences, InputStream)}
		 *
		 * @param prefs The preferences that will be written
		 * @param outputStream The stream that {@code prefs} will be written to. We close the stream
		 * before this function returns
		 * @throws IOException if an exception occurs
		 * @see #importPreferences(SharedPreferences, InputStream)
		 */
		public static void exportPreferences(SharedPreferences prefs, OutputStream outputStream)
				throws IOException {
			ObjectOutputStream output = null;
			try {
				output = new ObjectOutputStream(outputStream);
				output.writeObject(prefs.getAll());
			} finally {
				if (output != null) {
					output.close();
				}
			}
		}

		/**
		 * Reads a {@link SharedPreferences} object that was previously serialised with {@link
		 * #exportPreferences(SharedPreferences, OutputStream)}
		 *
		 * @param prefs The {@link SharedPreferences} object that will be populated with the
		 * values read from {@code inputStream}
		 * @param inputStream An {@link InputStream} that the preferences will be read from. The
		 * stream will be closed when this function returns
		 * @throws IOException if an IO error occurs
		 * @see #exportPreferences(SharedPreferences, OutputStream)
		 */
		public static void importPreferences(SharedPreferences prefs, InputStream inputStream)
				throws IOException {
			SharedPreferences.Editor prefEdit = prefs.edit();
			prefEdit.clear();
			Map<String, ?> entries = readPreferenceMap(inputStream);
			for (Map.Entry<String, ?> entry : entries.entrySet()) {
				Object v = entry.getValue();
				String key = entry.getKey();

				if (v instanceof Boolean)
					prefEdit.putBoolean(key, (Boolean) v);
				else if (v instanceof Float)
					prefEdit.putFloat(key, (Float) v);
				else if (v instanceof Integer)
					prefEdit.putInt(key, (Integer) v);
				else if (v instanceof Long)
					prefEdit.putLong(key, (Long) v);
				else if (v instanceof String)
					prefEdit.putString(key, ((String) v));
			}
			prefEdit.commit();

		}

		/**
		 * Deserialises a String->Object map that was previously serialised by
		 * {@link #exportPreferences(SharedPreferences, OutputStream)}.
		 *
		 * @param inputStream The input stream that the map will be deserialised from. It will be
		 * closed when this function returns
		 * @return The deserialised map
		 * @throws IOException if the map could not be deserialised from the input stream
		 * @see #exportPreferences(SharedPreferences, OutputStream)
		 */
		@SuppressWarnings("unchecked")
		public static Map<String, Object> readPreferenceMap(InputStream inputStream) throws
				IOException {

			ObjectInputStream input = null;
			try {
				input = new ObjectInputStream(inputStream);
				Object entriesRaw = input.readObject();
				return (Map<String, Object>) entriesRaw;
			} catch (ClassCastException e) {
				throw new IOException(e);
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			} finally {
				closeNoThrow(input);
			}
		}

		/**
		 * Helper function, closes a {@link Closeable} ignoring any IOExceptions thrown
		 *
		 * @param closeable The closeable to close. Can be null
		 */
		private static void closeNoThrow(Closeable closeable) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (IOException e) {
					// No throw
				}
			}
		}
	}
}
