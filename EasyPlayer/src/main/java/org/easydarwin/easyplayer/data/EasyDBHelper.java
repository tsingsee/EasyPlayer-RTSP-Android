package org.easydarwin.easyplayer.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * 视频源的数据库
 */
public class EasyDBHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "easydb.db";

    public EasyDBHelper(Context context) {
        super(context, DB_NAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        VideoSource.createTable(sqLiteDatabase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Cursor cursor = db.query(VideoSource.TABLE_NAME, null, null, null, null, null, null);

        ArrayList<ContentValues> cvs = new ArrayList<>();

        for (cursor.moveToFirst();!cursor.isAfterLast();cursor.moveToNext()){
            ContentValues cv = new ContentValues();
            cv.put(VideoSource.URL, cursor.getString(cursor.getColumnIndex(VideoSource.URL)));
            cv.put(VideoSource.SEND_OPTION, 0);
            cv.put(VideoSource.TRANSPORT_MODE, 0);
            cv.put(VideoSource.AUDIENCE_NUMBER, 0);
            cvs.add(cv);
        }

        cursor.close();

        db.execSQL("DROP TABLE "+VideoSource.TABLE_NAME);
        VideoSource.createTable(db);

        for (ContentValues cv : cvs) {
            db.insert(VideoSource.TABLE_NAME,null, cv);
        }
    }
}
