package com.mindyourearth.planet;

import android.app.Activity;
import android.app.Dialog;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.mindyourearth.planet.data.TrashPointsContract;
import com.mindyourearth.planet.data.TrashPointsDbHelper;
import com.mindyourearth.planet.pojos.TrashPoint;

/**
 * Created by Rishab on 31-05-2017.
 */

public class TrashDetailDialog extends AppCompatDialogFragment implements View.OnClickListener
{
    //userVote values
    //-2 : app didn't read user vote yet
    //-1 : user didn't vote this trashpoint
    //0 : user voted trash point dirty
    //1 : user voted trash point clean

    AlertDialog alertDialog;
    String trashKey;
    ValueEventListener trashDetailListener;
    DatabaseReference trashDetailReference;
    MutableLiveData<Integer> userVoteLD;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
        {
            trashKey = savedInstanceState.getString("trashKey");
        }
    }

    @Override
    public void onStop()
    {
        if (trashDetailReference != null)
            trashDetailReference.removeEventListener(trashDetailListener);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putString("trashKey", trashKey);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        final Bundle args = getArguments();
        if (trashKey == null) trashKey = args.getString("trashKey");
        Activity activity = getActivity();
        Drawable icon = ((TrashMapActivity) getActivity()).getTrashDrawable(args.getString("trashType"));
        final View trashDetailView = activity.getLayoutInflater().inflate(R.layout.dialog_details_trash_point, null);
        final View userVoteClean = trashDetailView.findViewById(R.id.user_vote_clean);
        final View userVoteDirty = trashDetailView.findViewById(R.id.user_vote_dirty);
        if (!args.getBoolean("canUserVote"))
        {
            View[] views = {userVoteClean, userVoteDirty,
            trashDetailView.findViewById(R.id.textView8),
            trashDetailView.findViewById(R.id.space),
            trashDetailView.findViewById(R.id.space2),
            trashDetailView.findViewById(R.id.you_say),
            trashDetailView.findViewById(R.id.textView9)};
            for (View view: views)
                view.setVisibility(View.GONE);
        }

        //observing user's vote
        userVoteLD = ViewModelProviders.of(getActivity()).get(MyViewModel.class).getUserVoteLD();
        userVoteLD.observe((LifecycleOwner) getActivity(), new Observer<Integer>()
        {
            @Override
            public void onChanged(@Nullable Integer userVote)
            {
                if (userVote == 0)
                {
                    userVoteClean.setAlpha(0.3f);
                    userVoteDirty.setAlpha(1f);
                }
                else if (userVote == 1)
                {
                    userVoteClean.setAlpha(1f);
                    userVoteDirty.setAlpha(0.3f);
                }
                else if (userVote == -1)
                {
                    userVoteClean.setAlpha(0.3f);
                    userVoteDirty.setAlpha(0.3f);
                }
                if (userVote > -2)
                {
                    userVoteClean.setOnClickListener(TrashDetailDialog.this);
                    userVoteDirty.setOnClickListener(TrashDetailDialog.this);
                }
            }
        });

        //fetching trash point data from firebase
        trashDetailListener = new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                //hiding progress bar
                trashDetailView.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                if (args.getBoolean("canUserVote"))
                    trashDetailView.findViewById(R.id.you_say).setVisibility(View.VISIBLE);

                //setting values
                ((TextView) trashDetailView.findViewById(R.id.votes_clean_count))
                        .setText(dataSnapshot.child("clean").getValue().toString());
                ((TextView) trashDetailView.findViewById(R.id.votes_dirty_count))
                        .setText(dataSnapshot.child("dirty").getValue().toString());

                //get vote initally
                getInitialUserVote();
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {
                trashDetailView.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                TextView youSayText = (TextView) trashDetailView.findViewById(R.id.you_say);
                youSayText.setVisibility(View.VISIBLE);
                youSayText.setText(R.string.failed_to_get_votes);
            }
        };
        trashDetailReference = FirebaseDatabase.getInstance().getReference("poi/" + trashKey);
        trashDetailReference.addValueEventListener(trashDetailListener);

        //dialog to show trashpoint details
        alertDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.dumping_land)
                .setView(trashDetailView)
                .setIcon(icon)
                .create();

        return alertDialog;
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.user_vote_clean:
            {
                if (userVoteLD.getValue() == 1) break;
                vote(false);
                break;
            }
            case R.id.user_vote_dirty:
            {
                if (userVoteLD.getValue() == 0) break;
                vote(true);
                break;
            }
            default:
                break;
        }
    }

    //when user votes
    private void vote(final boolean isDirty)
    {
        final int oldUserVote = userVoteLD.getValue();
        if (isDirty) userVoteLD.setValue(0);
        else userVoteLD.setValue(1);

        //checking connectivity
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected())
        {
            new Handler().postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    userVoteLD.setValue(oldUserVote);
                }
            }, 300);
            return;
        }

        //sending trnsaaction
        DatabaseReference trashPointRef = FirebaseDatabase.getInstance().getReference("poi/" + trashKey);
        trashPointRef.runTransaction(new Transaction.Handler()
        {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData)
            {
                TrashPoint trashPoint = mutableData.getValue(TrashPoint.class);
                if (trashPoint == null)
                    return Transaction.success(mutableData);
                else if (isDirty)
                {
                    trashPoint.setDirty(trashPoint.getDirty() + 1);
                    if(oldUserVote>-1) //user did vote then
                        trashPoint.setClean(trashPoint.getClean() - 1);
                }
                else
                {
                    trashPoint.setClean(trashPoint.getClean() + 1);
                    if (oldUserVote > -1) //user did vote then
                        trashPoint.setDirty(trashPoint.getDirty() - 1);
                }
                mutableData.setValue(trashPoint);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean b, DataSnapshot dataSnapshot)
            {
                if (databaseError != null)
                {
                    userVoteLD.setValue(oldUserVote);
                    return;
                }
                //when operation completeS successfully
                Runnable runnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        //saving data to local data
                        TrashPointsDbHelper dbHelper = new TrashPointsDbHelper(getActivity());
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        ContentValues values = new ContentValues();
                        if (isDirty)
                            values.put(TrashPointsContract.TrashEntry.COLUMN_VOTE, 0);
                        else
                            values.put(TrashPointsContract.TrashEntry.COLUMN_VOTE, 1);
                        if (oldUserVote > -1)
                        {
                            db.update(TrashPointsContract.TrashEntry.TABLE_NAME, values,
                                    TrashPointsContract.TrashEntry._ID + " = ?",
                                    new String[]{trashKey});
                        }
                        else if (oldUserVote == -1)
                        {
                            values.put(TrashPointsContract.TrashEntry._ID, trashKey);
                            values.put(TrashPointsContract.TrashEntry.COLUMN_USER_ADDED, 0);
                            db.insert(TrashPointsContract.TrashEntry.TABLE_NAME, null, values);
                        }
                        db.close();
                        dbHelper.close();
                    }
                };
                //running thread on which database will be accessed
                Thread thread = new Thread(runnable);
                thread.start();
            }
        });
    }

    //method to get user vote when no dialog is first opened
    public void getInitialUserVote()
    {
        //checking if user voted on the trashPoint
        if (userVoteLD.getValue() == -2)
        {
            final Handler handler = new Handler();
            Runnable runnable = new Runnable()
            {
                @Override
                public void run()
                {
                    final TrashPointsDbHelper dbHelper = new TrashPointsDbHelper(getActivity());
                    final SQLiteDatabase db = dbHelper.getReadableDatabase();
                    String projection[] = {TrashPointsContract.TrashEntry.COLUMN_VOTE};
                    String selection = TrashPointsContract.TrashEntry._ID + " = ?";
                    String[] selectionArgs = {trashKey};

                    //querying database
                    final Cursor cursor = db.query(
                            TrashPointsContract.TrashEntry.TABLE_NAME,
                            projection,
                            selection,
                            selectionArgs,
                            null,
                            null,
                            null
                    );
                    //setting user vote
                    handler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (cursor.moveToNext())
                                userVoteLD.setValue(cursor.getInt(0));
                            else
                                userVoteLD.setValue(-1);
                            cursor.close();
                            db.close();
                            dbHelper.close();
                        }
                    });
                }
            };
            Thread thread = new Thread(runnable);
            thread.start();
        }
    }
}
