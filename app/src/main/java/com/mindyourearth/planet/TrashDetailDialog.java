package com.mindyourearth.planet;

import android.app.Dialog;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.NativeExpressAdView;
import com.google.android.gms.maps.model.LatLng;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Rishab on 31-05-2017.
 */

//userVote values
//-2 : app didn't read user vote yet
//-1 : user didn't vote this trashpoint
//0 : user voted trash point dirty
//1 : user voted trash point clean

/*There are 2 conditions to remove a trash point.
Either one or both should satisfy
1. User added this trash point and it was added today
2. Trash point votes are minimum of required votes and clean votes are 70%*/

public class TrashDetailDialog extends AppCompatDialogFragment implements View.OnClickListener, PopupMenu.OnMenuItemClickListener
{
    TrashPoint trashPoint; //represents this trashpoint
    AlertDialog alertDialog;
    String trashKey;
    ValueEventListener trashDetailListener;
    DatabaseReference trashDetailReference;
    MutableLiveData<Integer> userVoteLD;
    MutableLiveData<String> dynamicLinkLD;
    AdRequest adRequest;
    NativeExpressAdView nativeAdView;
    private boolean hasFirstVoteDone, hasInitialSetupDone;
    private PopupMenu popupMenu;
    View dialogView;
    private boolean canUserVote = true, hasUserAddedThis;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
        {
            trashKey = savedInstanceState.getString("trashKey");
        }
        if (adRequest == null)
        {
            adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .build();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putString("trashKey", trashKey);
    }

    @Override
    public void onPause()
    {
        nativeAdView.pause();
        super.onPause();
    }

    @Override
    public void onResume()
    {
        nativeAdView.resume();
        super.onResume();
    }


    @Override
    public void onDestroy()
    {
        if (trashDetailReference != null)
            trashDetailReference.removeEventListener(trashDetailListener);
        nativeAdView.destroy();
        super.onDestroy();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        final TrashMapActivity trashMapActivity = (TrashMapActivity) getActivity();
        final Bundle args = getArguments();
        if (trashKey == null)
            trashKey = args.getString("trashKey");

        final View trashDetailView = trashMapActivity.getLayoutInflater().inflate(R.layout.dialog_details_trash_point, null);
        dialogView = trashDetailView;
        nativeAdView = (NativeExpressAdView) trashDetailView.findViewById(R.id.native_ad);
        nativeAdView.loadAd(adRequest);
        final View userVoteClean = trashDetailView.findViewById(R.id.user_vote_clean);
        final View userVoteCleanTxt = trashDetailView.findViewById(R.id.user_vote_clean_txt);
        final View userVoteDirtyTxt = trashDetailView.findViewById(R.id.user_vote_dirty_txt);
        final AppCompatImageView userVoteDirty = (AppCompatImageView) trashDetailView.findViewById(R.id.user_vote_dirty);
        final TextView votesCleanCount = (TextView) trashDetailView.findViewById(R.id.votes_clean_count);
        final TextView votesDirtyCount = (TextView) trashDetailView.findViewById(R.id.votes_dirty_count);
        View menuIcon = trashDetailView.findViewById(R.id.menu_icon);

        //on click listners
        trashDetailView.findViewById(R.id.share_trash_point).setOnClickListener(this);
        trashDetailView.findViewById(R.id.share_image).setOnClickListener(this);
        trashDetailView.findViewById(R.id.share_link).setOnClickListener(this);
        trashDetailView.findViewById(R.id.cancel_removal_btn).setOnClickListener(this);
        trashDetailView.findViewById(R.id.approve_removal_btn).setOnClickListener(this);
        menuIcon.setOnClickListener(this);
        hasFirstVoteDone = trashMapActivity.getPreferences(Context.MODE_PRIVATE)
                .getBoolean(getString(R.string.pref_first_vote_done), false);

        //setting popup menu
        popupMenu = new PopupMenu(trashMapActivity, menuIcon);
        popupMenu.inflate(R.menu.trash_detail_dialog_menu);
        popupMenu.setOnMenuItemClickListener(this);

        //observing user's vote
        userVoteLD = ViewModelProviders.of(trashMapActivity).get(MyViewModel.class).getUserVoteLD();
        userVoteLD.observe(trashMapActivity, new Observer<Integer>()
        {
            @Override
            public void onChanged(@Nullable Integer userVote)
            {
                if (userVote == 0)
                {
                    userVoteClean.setAlpha(0.3f);
                    userVoteCleanTxt.setAlpha(0.3f);
                    userVoteDirty.setAlpha(1f);
                    userVoteDirtyTxt.setAlpha(1f);
                }
                else if (userVote == 1)
                {
                    userVoteClean.setAlpha(1f);
                    userVoteCleanTxt.setAlpha(1f);
                    userVoteDirty.setAlpha(0.3f);
                    userVoteDirtyTxt.setAlpha(0.3f);
                }
                else if (userVote == -1)
                {
                    userVoteClean.setAlpha(0.3f);
                    userVoteCleanTxt.setAlpha(0.3f);
                    userVoteDirty.setAlpha(0.3f);
                    userVoteDirtyTxt.setAlpha(0.3f);
                }
                if (userVote > -2)
                {
                    userVoteClean.setOnClickListener(TrashDetailDialog.this);
                    userVoteCleanTxt.setOnClickListener(TrashDetailDialog.this);
                    userVoteDirty.setOnClickListener(TrashDetailDialog.this);
                    userVoteDirtyTxt.setOnClickListener(TrashDetailDialog.this);
                }
            }
        });

        //fetching trash point data from firebase
        trashDetailListener = new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                try
                {
                    trashPoint = dataSnapshot.getValue(TrashPoint.class);
                    //setting values
                    votesCleanCount.setText(String.valueOf(trashPoint.getClean()));
                    votesDirtyCount.setText(String.valueOf(trashPoint.getDirty()));

                    //setting trash point removal condition
                    long totalVotes = trashPoint.getClean() + trashPoint.getDirty();
                    int cleanPercent = (int) ((trashPoint.getClean()*100) / totalVotes);
                    if (hasUserAddedThis ||
                            canUserVote && totalVotes >= trashMapActivity.minVotesToRemove && cleanPercent >= 70)
                        popupMenu.getMenu().findItem(R.id.remove_trash_point).setEnabled(true);
                    else
                        popupMenu.getMenu().findItem(R.id.remove_trash_point).setEnabled(false);

                    //this will be executed only once
                    if (hasInitialSetupDone)
                        return;

                    //hiding progress bar and setting initial data of the trash point
                    trashDetailView.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                    canUserVote = trashMapActivity.getDistanceInKms(trashMapActivity.userMarker.getPosition(),
                            trashPoint.getPostion()) <= 2;
                    if (!canUserVote)
                    {
                        View[] views =
                                {
                                        userVoteClean, userVoteDirty,
                                        trashDetailView.findViewById(R.id.user_vote_clean_txt),
                                        trashDetailView.findViewById(R.id.space),
                                        trashDetailView.findViewById(R.id.space2),
                                        trashDetailView.findViewById(R.id.you_say),
                                        trashDetailView.findViewById(R.id.user_vote_dirty_txt)
                                };
                        for (View view : views)
                            view.setVisibility(View.GONE);
                        popupMenu.getMenu().findItem(R.id.remove_trash_point).setEnabled(false);
                    }
                    else
                    {
                        //get vote initally
                        trashDetailView.findViewById(R.id.you_say).setVisibility(View.VISIBLE);
                        if (hasFirstVoteDone)
                        {
                            getInitialUserVote();
                        }
                        else
                        {
                            AppCompatTextView tutorial_vote = (AppCompatTextView) trashDetailView.findViewById(R.id.vote_tutorial_text);
                            tutorial_vote.setCompoundDrawablesWithIntrinsicBounds(
                                    VectorDrawableCompat.create(getResources(), R.drawable.ic_arrow_left, null), null,
                                    VectorDrawableCompat.create(getResources(), R.drawable.ic_arrow_right, null), null);
                            View view = trashDetailView.findViewById(R.id.vote_tutorial);
                            view.setVisibility(View.VISIBLE);
                            view.setOnClickListener(TrashDetailDialog.this);
                            userVoteLD.setValue(-1);
                        }
                    }
                    //setting date
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    Calendar cal = GregorianCalendar.getInstance();
                    cal.setTimeInMillis(trashPoint.getTime());
                    ((AppCompatTextView) trashDetailView.findViewById(R.id.date))
                            .setText(getString(R.string.added_on) + " " + sdf.format(cal.getTime()));

                    hasInitialSetupDone = true;
                } catch (NullPointerException npe)
                {
                    //showing trash removed
                    trashDetailView.findViewById(R.id.progress_bar_removing_tp).setVisibility(View.GONE);
                    trashDetailView.findViewById(R.id.trash_removed).setVisibility(View.VISIBLE);
                    AnimatedVectorDrawableCompat animatedLogo = AnimatedVectorDrawableCompat.create(trashMapActivity, R.drawable.logo_anim);
                    ((AppCompatImageView) trashDetailView.findViewById(R.id.trash_removed_img)).setImageDrawable(animatedLogo);
                    ((AppCompatTextView)trashDetailView.findViewById(R.id.trash_removed_txt)).setText(R.string.trash_point_removed);
                    trashDetailView.findViewById(R.id.cancel_removal_btn).setVisibility(View.GONE);
                    trashDetailView.findViewById(R.id.approve_removal_btn).setVisibility(View.GONE);
                    animatedLogo.start();
                }
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
        Drawable icon = trashMapActivity.getTrashDrawable(args.getString("trashType"));
        userVoteDirty.setImageDrawable(icon);

        //setting icon and title
        ((AppCompatImageView) trashDetailView.findViewById(R.id.trashPointIcon)).setImageDrawable(icon);
        ((AppCompatTextView) trashDetailView.findViewById(R.id.title))
                .setText(trashMapActivity.getTrashTitle(args.getString("trashType")));

        //dialog to show trashpoint details
        alertDialog = new AlertDialog.Builder(trashMapActivity)
                .setView(trashDetailView)
                .create();
        return alertDialog;
    }

    //when user votes
    private void vote(final boolean isDirty)
    {
        final int oldUserVote = userVoteLD.getValue();
        if (isDirty) userVoteLD.setValue(0);
        else userVoteLD.setValue(1);
        //finishing voting tutorial
        if (!hasFirstVoteDone)
        {
            getActivity().getPreferences(Context.MODE_PRIVATE).edit()
                    .putBoolean(getString(R.string.pref_first_vote_done), true).apply();
            hasFirstVoteDone = true;
        }

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
            TrashPoint trashPoint = null;

            @Override
            public Transaction.Result doTransaction(MutableData mutableData)
            {
                trashPoint = mutableData.getValue(TrashPoint.class);
                if (trashPoint == null)
                    return Transaction.success(mutableData);
                else if (isDirty)
                {
                    trashPoint.setDirty(trashPoint.getDirty() + 1);
                    if (oldUserVote > -1) //user did vote then
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
                        values.put(TrashPointsContract.TrashEntry.COLUMN_TIME, System.currentTimeMillis());

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
                            values.put(TrashPointsContract.TrashEntry.COLUMN_TYPE, trashPoint.getType());
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
                    String projection[] =
                            {
                                    TrashPointsContract.TrashEntry.COLUMN_VOTE,
                                    TrashPointsContract.TrashEntry.COLUMN_USER_ADDED
                            };
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
                            {
                                userVoteLD.setValue(cursor.getInt(0));
                                //user has added this trash point
                                if (cursor.getInt(1) == 1)
                                {
                                    popupMenu.getMenu().findItem(R.id.remove_trash_point).setEnabled(true);
                                    hasUserAddedThis = true;
                                }
                            }
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

    @Override
    public boolean onMenuItemClick(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.on_maps:
            {
                if (trashPoint == null)
                    return true;
                LatLng position = trashPoint.getPostion();
                String params = String.valueOf(position.latitude) + "," + String.valueOf(position.longitude);
                Uri gmmIntentUri = Uri.parse("geo:" + params + "?q=" + params + "(Trash point)");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null)
                    startActivity(mapIntent);
                return true;
            }
            case R.id.remove_trash_point:
            {
                dialogView.findViewById(R.id.trash_removed).setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.cancel_removal_btn).setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.approve_removal_btn).setVisibility(View.VISIBLE);
                ((AppCompatTextView) dialogView.findViewById(R.id.trash_removed_txt)).setText(R.string.ask_trash_removal);
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.user_vote_clean:
            case R.id.user_vote_clean_txt:
            {
                if (userVoteLD.getValue() == 1) break;
                vote(false);
                break;
            }
            case R.id.user_vote_dirty:
            case R.id.user_vote_dirty_txt:
            {
                if (userVoteLD.getValue() == 0) break;
                vote(true);
                break;
            }
            case R.id.share_trash_point:
            {
                if (dynamicLinkLD == null)
                    dynamicLinkLD = ViewModelProviders.of(getActivity()).get(MyViewModel.class).getShareDynamicLinkLD();
                getDialog().findViewById(R.id.share_image_link).setVisibility(View.VISIBLE);
                break;
            }
            case R.id.share_link:
            {
                dynamicLinkLD.setValue("");
                dismiss();
                break;
            }
            case R.id.share_image:
            {
                dynamicLinkLD.setValue(" ");
                dismiss();
                break;
            }
            case R.id.vote_tutorial:
            {
                view.setVisibility(View.GONE);
                break;
            }
            case R.id.menu_icon:
            {
                popupMenu.show();
                break;
            }
            case R.id.cancel_removal_btn:
            {
                dialogView.findViewById(R.id.trash_removed).setVisibility(View.GONE);
                break;
            }
            case R.id.approve_removal_btn:
            {
                dialogView.findViewById(R.id.progress_bar_removing_tp).setVisibility(View.VISIBLE);
                trashDetailReference.setValue(null);
                break;
            }
        }
    }
}
