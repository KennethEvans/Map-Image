//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.mapimage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class ImageFileListActivity extends ListActivity implements IConstants {
	private static final boolean USE_ARRAY_LIST_ADAPTER = true;
	private static File[] mFiles;
	private List<String> mFileNameList = new ArrayList<String>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set result CANCELED in case the user backs out
		setResult(Activity.RESULT_CANCELED);

		// Set the file list
		reset();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.image_file_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
		case R.id.set_image_directory:
			setImageDirectory();
			return true;
		}
		return false;
	}

	/**
	 * Gets the current image directory
	 * 
	 * @return
	 */
	private File getImageDirectory() {
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String imageDirName = prefs.getString(PREF_IMAGE_DIRECTORY, null);
		File imageDir = null;
		if (imageDirName != null) {
			imageDir = new File(imageDirName);
		} else {
			File sdCardRoot = Environment.getExternalStorageDirectory();
			if (sdCardRoot != null) {
				imageDir = new File(sdCardRoot, SD_CARD_MAP_IMAGE_DIRECTORY);
				// Change the stored value (even if it is null)
				SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
						.edit();
				editor.putString(PREF_IMAGE_DIRECTORY, imageDir.getPath());
				editor.commit();
			}
		}
		if (imageDir == null) {
			Utils.errMsg(this, "Image directory is null");
			return null;
		}
		if (!imageDir.exists()) {
			Utils.errMsg(this, "Cannot find directory: " + imageDir);
			return null;
		}
		return imageDir;
	}

	/**
	 * Sets the current image directory
	 * 
	 * @return
	 */
	private void setImageDirectory() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Set Image Directory");
		alert.setMessage("Image Directory (Leave blank for default):");

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		// Set it with the current value
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String imageDirName = prefs.getString(PREF_IMAGE_DIRECTORY, null);
		if (imageDirName != null) {
			input.setText(imageDirName);
		}
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				if (value.length() == 0) {
					value = null;
				}
				SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
						.edit();
				editor.putString(PREF_IMAGE_DIRECTORY, value);
				editor.commit();
				reset();
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Do nothing
					}
				});

		alert.show();
	}

	/**
	 * Resets the file list.
	 */
	private void reset() {
		// Get the available image files
		try {
			// Clear the current list
			mFileNameList.clear();
			File dir = getImageDirectory();
			if (dir != null) {
				File[] files = dir.listFiles();
				List<File> fileList = new ArrayList<File>();
				for (File file : files) {
					if (!file.isDirectory()) {
						String ext = Utils.getExtension(file);
						if (ext.equals("jpg") || ext.equals("jpeg")
								|| ext.equals("png") || ext.equals("gif")) {
							fileList.add(file);
							if (USE_ARRAY_LIST_ADAPTER) {
								mFileNameList.add(file.getPath());
							}
						}
					}
				}
				mFiles = new File[fileList.size()];
				fileList.toArray(mFiles);
			} else {
				mFiles = new File[0];
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Failed to get list of available files", ex);
		}

		// Set the ListAdapter
		if (USE_ARRAY_LIST_ADAPTER) {
			ArrayAdapter<String> fileList = new ArrayAdapter<String>(this,
					R.layout.row, mFileNameList);
			setListAdapter(fileList);
		} else {
			setListAdapter(new FileListAdapter(this));
		}

		ListView lv = getListView();
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,
					long id) {
				if (pos < 0 || pos >= mFiles.length) {
					return;
				}
				// Create the result Intent and include the fileName
				Intent intent = new Intent();
				String path = "";
				if (USE_ARRAY_LIST_ADAPTER) {
					path = mFileNameList.get(pos);
				} else {
					path = mFiles[pos].getPath();
				}
				intent.putExtra(OPEN_FILE_PATH, path);

				// Set result and finish this Activity
				setResult(Activity.RESULT_OK, intent);
				finish();
			}
		});
	}

	/**
	 * @return
	 */
	public static File[] getFiles() {
		return mFiles;
	}

	/**
	 * /** A custom ListView adapter for our implementation. Based on the
	 * efficient list adapter in the SDK APIDemos list14.java.
	 */
	private static class FileListAdapter extends BaseAdapter {
		private LayoutInflater mInflater;

		public FileListAdapter(Context context) {
			// Cache the LayoutInflate to avoid asking for a new one each time.
			mInflater = LayoutInflater.from(context);
		}

		/**
		 * The number of items in the list.
		 * 
		 * @see android.widget.ListAdapter#getCount()
		 */
		public int getCount() {
			return mFiles.length;
		}

		/**
		 * Since the data comes from an array, just returning the index is
		 * sufficient to get at the data. If we were using a more complex data
		 * structure, we would return whatever object represents one row in the
		 * list.
		 * 
		 * @see android.widget.ListAdapter#getItem(int)
		 */
		public Object getItem(int position) {
			return position;
		}

		/**
		 * Use the array index as a unique id.
		 * 
		 * @see android.widget.ListAdapter#getItemId(int)
		 */
		public long getItemId(int position) {
			return position;
		}

		/**
		 * Make a view to hold each row.
		 * 
		 * @see android.widget.ListAdapter#getView(int, android.view.View,
		 *      android.view.ViewGroup)
		 */
		@TargetApi(Build.VERSION_CODES.GINGERBREAD)
		public View getView(int pos, View convertView, ViewGroup parent) {
			// A ViewHolder keeps references to children views to avoid
			// unnecessary calls
			// to findViewById() on each row.
			ViewHolder holder;
			File file = mFiles[pos];
			Log.d(TAG, this.getClass().getSimpleName() + "getView: pos=" + pos
					+ "/" + getCount() + " name=" + file.getName());

			// When convertView is not null, we can reuse it directly, there is
			// no need to reinflate it. We only inflate a new View when the
			// convertView
			// supplied by ListView is null.
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.list_row, null);

				// Creates a ViewHolder and store references to the two children
				// views
				// we want to bind data to.
				holder = new ViewHolder();
				holder.title = (TextView) convertView.findViewById(R.id.title);
				holder.subtitle = (TextView) convertView
						.findViewById(R.id.subtitle);

				convertView.setTag(holder);
			} else {
				// Get the ViewHolder back
				holder = (ViewHolder) convertView.getTag();
			}
			holder.title.setText(file.getName());
			double size = file.getTotalSpace() / 1024. / 1024.;
			holder.subtitle.setText(String.format("Size: %0.3f MB", size));
			return convertView;
		}

		static class ViewHolder {
			TextView title;
			TextView subtitle;
		}

	}

}
