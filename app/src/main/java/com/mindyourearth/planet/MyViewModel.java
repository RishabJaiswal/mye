package com.mindyourearth.planet;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

//view model for this activity
public class MyViewModel extends ViewModel
{
    //user's Location data
    private LocationLiveData locationLiveData;
    private MutableLiveData<List<Object>> trashPointsSnapShotOrErrorLD = new MutableLiveData<>();
    private MutableLiveData<Integer> userVoteLD;
    private MutableLiveData<Long> trashCountLD;
    private MutableLiveData<String> shareDynamicLinkLD = new MutableLiveData<>();

    public MyViewModel()
    {
    }

    //user location
    public LocationLiveData getUserLocation(Context context)
    {
        if (locationLiveData == null)
            locationLiveData = new LocationLiveData(context);
        return locationLiveData;
    }

    //getting all trash points
    public MutableLiveData<List<Object>> getTrashPointsSnapShotLD()
    {
        //this list represents success and error elements
        //if datasnapshot is null ;i.e; failure
        DatabaseReference poiRef = FirebaseDatabase.getInstance().getReference("poi");
        poiRef.addListenerForSingleValueEvent(new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                List<Object> trashPointsOrError = new ArrayList<>();
                trashPointsOrError.add(0, dataSnapshot);
                trashPointsSnapShotOrErrorLD.setValue(trashPointsOrError);
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {
                List<Object> trashPointsOrError = new ArrayList<>();
                trashPointsOrError.add(0, null);
                trashPointsSnapShotOrErrorLD.setValue(trashPointsOrError);
            }
        });
        return trashPointsSnapShotOrErrorLD;
    }

    //getting user's vote
    public MutableLiveData<Integer> getUserVoteLD()
    {
        if(userVoteLD == null)
        {
            userVoteLD = new MutableLiveData<Integer>();
            userVoteLD.setValue(-2);
        }
        return userVoteLD;
    }

    //shareable link of the trash
    public MutableLiveData<String> getShareDynamicLinkLD()
    {
        return shareDynamicLinkLD;
    }

    //to get number of trash points
    public MutableLiveData<Long> getTrashCountLD()
    {
        if(trashCountLD == null)
        {
            trashCountLD = new MutableLiveData<>();
            trashCountLD.setValue(0L);
        }
        return trashCountLD;
    }
}
