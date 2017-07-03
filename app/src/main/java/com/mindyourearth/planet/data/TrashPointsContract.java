package com.mindyourearth.planet.data;

import android.provider.BaseColumns;

/**
 * Created by Rishab on 31-05-2017.
 */

public final class TrashPointsContract
{
    private TrashPointsContract()
    {
    }
    /* Inner class that defines the table contents */
    public static class TrashEntry implements BaseColumns
    {
        public static final String TABLE_NAME = "trashPoints";
        public static final String COLUMN_VOTE = "vote";
        public static final String COLUMN_USER_ADDED = "userAdded";
        public static final String COLUMN_TIME = "time";
        public static final String COLUMN_TYPE = "type";
    }
}
