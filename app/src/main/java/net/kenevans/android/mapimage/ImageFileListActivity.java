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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class ImageFileListActivity extends AppCompatActivity implements IConstants {
    /**
     * Holds the list of files.
     */
    private List<UriData> mUriList = new ArrayList<>();
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

    /**
     * Get the list of available image files.
     *
     * @param context The context.
     * @return The list.
     */
    public static List<UriData> getUriList(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        SharedPreferences prefs = context.getSharedPreferences(
                "MapImageActivity", MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(context, "There is no tree Uri set");
            return null;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri));
        List<UriData> uriList = new ArrayList<>();
        String lastSeg;
        try (Cursor cursor = contentResolver.query(childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                },
                null,
                null,
                null)) {
            String documentId;
            Uri documentUri;
            String displayName;
            while (cursor.moveToNext()) {
                documentId = cursor.getString(0);
                documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                documentId);
                if (documentUri == null) continue;
                displayName = cursor.getString(1);
                String lastPathSegment = documentUri.getLastPathSegment();
                if (lastPathSegment == null) continue;
                lastSeg = lastPathSegment.toLowerCase();

                if (lastSeg.endsWith("jpg") ||
                        lastSeg.endsWith("jpeg") ||
                        lastSeg.endsWith("png") ||
                        lastSeg.endsWith("gif")
                ) uriList.add(new UriData(documentUri, displayName));
            }
        }
        // Do nothing
        return uriList;
    }

    /**
     * Resets the file list.
     */
    private void reset() {
        Log.d(TAG, this.getClass().getSimpleName() + ": reset: "
                + "mListView=" + mListView);
        // Get the available image files
        try {
            mUriList = getUriList(this);
            // Sort them by display name
            Collections.sort(mUriList, new Comparator<UriData>() {
                public int compare(UriData data1, UriData data2) {
                    return data1.displayName.compareTo(data2.displayName);
                }
            });
        } catch (Exception ex) {
            Utils.excMsg(this, "Failed to get list of available files", ex);
        }

        // Set the ListAdapter
        ArrayAdapter<UriData> fileList = new ArrayAdapter<>(this,
                R.layout.row, mUriList);
        mListView.setAdapter(fileList);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos,
                                    long id) {
                if (pos < 0 || pos >= mUriList.size()) {
                    return;
                }
                // Create the result Intent and include the fileName
                Intent intent = new Intent();
                intent.putExtra(EXTRA_IMAGE_URI,
                        mUriList.get(pos).uri.toString());
                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });
    }

    /**
     * Convenience class for managing Uri information.
     */
    public static class UriData {
        final public Uri uri;
        final public String displayName;

        UriData(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }

        @androidx.annotation.NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }
}
