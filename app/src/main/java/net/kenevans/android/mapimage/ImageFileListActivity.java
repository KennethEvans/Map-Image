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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class ImageFileListActivity extends AppCompatActivity implements IConstants {
    /**
     * Holds the list of files.
     */
    private static File[] mFiles;
    private List<String> mFileNameList = new ArrayList<>();
    private ListView mListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_view);
        mListView = findViewById(R.id.mainListView);

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
     * @return The image directory.
     */
    public static File getImageDirectory(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        String imageDirName = prefs.getString(PREF_IMAGE_DIRECTORY, null);
        File imageDir = null;
        if (imageDirName != null) {
            imageDir = new File(imageDirName);
        } else {
            File sdCardRoot = Environment.getExternalStorageDirectory();
            if (sdCardRoot != null) {
                imageDir = new File(sdCardRoot, SD_CARD_MAP_IMAGE_DIRECTORY);
                // Change the stored value (even if it is null)
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString(PREF_IMAGE_DIRECTORY, imageDir.getPath());
                editor.apply();
            }
        }
        if (imageDir == null) {
            Utils.errMsg(context, "Image directory is null");
            return null;
        }
        if (!imageDir.exists()) {
            Utils.errMsg(context, "Cannot find directory: " + imageDir);
            return null;
        }
        return imageDir;
    }

    /**
     * Sets the current image directory
     */
    private void setImageDirectory() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Set Image Directory");
        alert.setMessage("Image Directory (Leave blank for default):");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        // Set it with the current value
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
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
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(ImageFileListActivity.this)
                        .edit();
                editor.putString(PREF_IMAGE_DIRECTORY, value);
                editor.apply();
                reset();
            }
        });

        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int
                            whichButton) {
                        // Do nothing
                    }
                });

        alert.show();
    }

    public void onListItemClick(ListView lv, View view, int position, long id) {
        if (position < 0 || position >= mFiles.length) {
            return;
        }
        // Create the result Intent and include the fileName
        Intent intent = new Intent();
        String path = mFileNameList.get(position);
        intent.putExtra(EXTRA_OPEN_FILE_PATH, path);

        // Set result and finish this Activity
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    /**
     * Resets the file list.
     */
    private void reset() {
        Log.d(TAG, this.getClass().getSimpleName() + ": reset: "
                + "mListView=" + mListView);
        // Get the available image files
        try {
            // Clear the current list
            mFileNameList.clear();
            File dir = getImageDirectory(this);
            if (dir != null) {
                File[] files = dir.listFiles();
                List<File> fileList = new ArrayList<>();
                for (File file : files) {
                    if (!file.isDirectory()) {
                        String ext = Utils.getExtension(file);
                        if (ext.toLowerCase().equals("jpg")
                                || ext.toLowerCase().equals("jpeg")
                                || ext.toLowerCase().equals("png")
                                || ext.toLowerCase().equals("gif")) {
                            fileList.add(file);
                            mFileNameList.add(file.getName());
                        }
                    }
                }
                mFiles = new File[fileList.size()];
                fileList.toArray(mFiles);
            } else {
                mFiles = new File[0];
            }
            Collections.sort(mFileNameList);
        } catch (Exception ex) {
            Utils.excMsg(this, "Failed to get list of available files", ex);
        }

        // Set the ListAdapter
        ArrayAdapter<String> fileList = new ArrayAdapter<>(this,
                R.layout.row, mFileNameList);
        mListView.setAdapter(fileList);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos,
                                    long id) {
                if (pos < 0 || pos >= mFiles.length) {
                    return;
                }
                // Create the result Intent and include the fileName
                Intent intent = new Intent();
                File file =
                        new File(getImageDirectory(ImageFileListActivity.this),
                                mFileNameList.get(pos));
                intent.putExtra(EXTRA_OPEN_FILE_PATH, file.getPath());

                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });
    }

}
