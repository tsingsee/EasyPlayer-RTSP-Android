package org.easydarwin.easyplayer;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.easydarwin.easyplayer.data.EasyDBHelper;
import org.easydarwin.video.Client;

public class TheApp extends Application {

    public static SQLiteDatabase sDB;

    @Override
    public void onCreate() {
        super.onCreate();

        sDB = new EasyDBHelper(this).getWritableDatabase();
    }
}
