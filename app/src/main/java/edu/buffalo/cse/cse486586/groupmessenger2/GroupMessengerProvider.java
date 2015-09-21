package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    private static final String TAG = GroupMessengerProvider.class.getName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        String filename = values.getAsString(KEY_FIELD);

        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(getContext().openFileOutput(filename, Context.MODE_PRIVATE)));
            bw.write(values.getAsString(VALUE_FIELD));
            bw.close();
        }
        catch (IOException e) {
            Log.e(TAG, "IOException on key file write");
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        String values[] = new String[2];
        values[0] = selection;

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getContext().openFileInput(selection)));
            values[1] = br.readLine();
            br.close();
        }
        catch (FileNotFoundException e) {

        }
        catch (IOException e) {
            Log.e(TAG, "IOException on key file read");
        }

        MatrixCursor cur = new MatrixCursor(new String[]{KEY_FIELD, VALUE_FIELD}, 1);
        cur.addRow(values);

        return cur;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }
}