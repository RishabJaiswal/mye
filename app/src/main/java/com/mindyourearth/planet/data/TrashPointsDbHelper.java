package com.mindyourearth.planet.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Rishab on 31-05-2017.
 */

public class TrashPointsDbHelper extends SQLiteOpenHelper
{
    public static final int DATABASE_VERIOSN = 1;
    public static final String DATABASE_NAME = "TrashPoints.db";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TrashPointsContract.TrashEntry.TABLE_NAME + " (" +
                    TrashPointsContract.TrashEntry._ID + " TEXT PRIMARY KEY," +
                    TrashPointsContract.TrashEntry.COLUMN_VOTE + " INTEGER," +
                    TrashPointsContract.TrashEntry.COLUMN_USER_ADDED + " INTEGER)";

    public TrashPointsDbHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERIOSN);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1)
    {
        //todo: create upgrade database scenarios
    }
}
