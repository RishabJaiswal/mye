package com.mindyourearth.planet;

import android.app.Activity;
import android.app.Dialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mindyourearth.planet.data.TrashPointsContract;
import com.mindyourearth.planet.data.TrashPointsDbHelper;
import com.mindyourearth.planet.pojos.TrashPoint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Rishab on 16-06-2017.
 */

public class UserHistoryDialog extends AppCompatDialogFragment
{

    UserHistoryAdapter userHistoryAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        final Activity activity = getActivity();
        View view = activity.getLayoutInflater().inflate(R.layout.frag_user_trash_points, null);
        userHistoryAdapter = new UserHistoryAdapter(null, (TrashMapActivity) activity);
        RecyclerView trashHistoryRecycler = (RecyclerView) view.findViewById(R.id.reycler_user_trash_points);
        trashHistoryRecycler.setLayoutManager(new LinearLayoutManager(activity));
        trashHistoryRecycler.setAdapter(userHistoryAdapter);

        new AsyncTask<Void, Void, Cursor>()
        {
            SQLiteDatabase db;
            TrashPointsDbHelper dbHelper;

            @Override
            protected Cursor doInBackground(Void... voids)
            {
                dbHelper = new TrashPointsDbHelper(activity);
                db = dbHelper.getReadableDatabase();
                Cursor cursor = db.query(TrashPointsContract.TrashEntry.TABLE_NAME,
                        null, null, null, null, null,
                        TrashPointsContract.TrashEntry.COLUMN_TIME + " DESC");

                return cursor;
            }

            @Override
            protected void onPostExecute(Cursor cursor)
            {
                userHistoryAdapter.setData(cursor);
            }
        }.execute();

        return new AlertDialog.Builder(activity)
                .setView(view)
                .create();
    }


    //RecylerView adapter
    class UserHistoryAdapter extends RecyclerView.Adapter<TrashHistoryHolder>
    {
        Cursor historyCursor;
        int COL_TIME, COL_KEY, COL_USER_ADDED, COL_VOTE, COL_TYPE;
        SimpleDateFormat sdf;
        TrashMapActivity trashMapActivity;

        UserHistoryAdapter(Cursor historyCursor, TrashMapActivity trashMapActivity)
        {
            this.historyCursor = historyCursor;
            this.trashMapActivity = trashMapActivity;
            sdf = new SimpleDateFormat("dd MMM yyyy \t hh:mm aa", Locale.getDefault());
        }

        @Override
        public TrashHistoryHolder onCreateViewHolder(ViewGroup viewGroup, int i)
        {
            View view = LayoutInflater.from(getActivity())
                    .inflate(R.layout.recycler_item_trash_summary, null);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new TrashHistoryHolder(view, trashMapActivity);
        }


        @Override
        public void onBindViewHolder(TrashHistoryHolder viewHolder, int position)
        {
            if (historyCursor == null || !historyCursor.moveToNext())
                return;

            //setting drawable
            viewHolder.trashIcon.setImageDrawable(trashMapActivity.getTrashDrawable(historyCursor.getString(COL_TYPE)));

            //setting history title
            //and check vote
            if (historyCursor.getInt(COL_USER_ADDED) == 1)
                viewHolder.title.setText(R.string.added_trash_point);
            else
            {
                viewHolder.title.setText(R.string.voted_trash_point);
                if (historyCursor.getInt(COL_VOTE) == 0)
                    viewHolder.checkVotes.setText(R.string.your_vote_dirty);
                else
                    viewHolder.checkVotes.setText(R.string.your_vote_clean);
            }

            //setting Date and time
            Calendar calendar = GregorianCalendar.getInstance();
            calendar.setTimeInMillis(historyCursor.getLong(COL_TIME));
            viewHolder.dateTime.setText(sdf.format(calendar.getTime()));

            ///setting tag
            TrashPoint trashPoint = new TrashPoint();
            trashPoint.setKey(historyCursor.getString(COL_KEY));
            trashPoint.setType(historyCursor.getString(COL_TYPE));
            viewHolder.itemView.setTag(trashPoint);
        }

        @Override
        public int getItemCount()
        {
            if (historyCursor == null)
                return 0;
            return historyCursor.getCount();
        }

        public void setData(Cursor cursor)
        {
            historyCursor = cursor;
            if (this.historyCursor.getCount() > 0)
            {
                COL_KEY = historyCursor.getColumnIndex(TrashPointsContract.TrashEntry._ID);
                COL_TYPE = historyCursor.getColumnIndex(TrashPointsContract.TrashEntry.COLUMN_TYPE);
                COL_TIME = historyCursor.getColumnIndex(TrashPointsContract.TrashEntry.COLUMN_TIME);
                COL_VOTE = historyCursor.getColumnIndex(TrashPointsContract.TrashEntry.COLUMN_VOTE);
                COL_USER_ADDED = historyCursor.getColumnIndex(TrashPointsContract.TrashEntry.COLUMN_USER_ADDED);
            }
            notifyDataSetChanged();
        }
    }

    //view holder for trash history
    class TrashHistoryHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        AppCompatImageView trashIcon;
        TextView title, dateTime, checkVotes;
        private HistoryItemClickListener historyItemClickListener;

        TrashHistoryHolder(View itemView, HistoryItemClickListener historyItemClickListener)
        {
            super(itemView);
            trashIcon = (AppCompatImageView) itemView.findViewById(R.id.trashIcon);
            title = (TextView) itemView.findViewById(R.id.historty_title);
            dateTime = (TextView) itemView.findViewById(R.id.date_time);
            checkVotes = (TextView) itemView.findViewById(R.id.check_votes);
            this.historyItemClickListener = historyItemClickListener;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v)
        {
            historyItemClickListener.onHistoryItemClicked((TrashPoint) v.getTag());
        }
    }

    public interface HistoryItemClickListener
    {
        void onHistoryItemClicked(TrashPoint trashPoint);
    }
}
