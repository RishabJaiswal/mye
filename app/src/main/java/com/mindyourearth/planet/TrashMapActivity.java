package com.mindyourearth.planet;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LifecycleActivity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatTextView;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mindyourearth.planet.data.TrashPointsContract;
import com.mindyourearth.planet.data.TrashPointsDbHelper;
import com.mindyourearth.planet.pojos.TrashPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TrashMapActivity extends LifecycleActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener, View.OnClickListener, GoogleMap.OnMarkerClickListener,
        AgreementDialog.AgreementAcceptCallback, TrashDumpingSelectDialog.TrashTypeSelectListener,
        RetainableProgressDialog.ProgressableActivity
{
    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_RESULTION = 2;
    int maxDistance = 3;
    private String tagForMarkerBitmap;
    private boolean isSelectingTrashPoint, isTrashSelectDialogVisible, isPermissionsRequested;
    private long trashCount;

    private MyViewModel myViewModel;
    private Observer<Location> locationObserver;
    private LocationLiveData locationLD;

    private GoogleMap googleMap;
    private Marker userMarker, trashPointMarker;
    private RetainableProgressDialog currProgressDialog;
    View tapOnMap, locateMe;
    FloatingActionButton saveTrashPointFab, undoTrashMarkingFab;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash_map);
        tapOnMap = findViewById(R.id.tap_on_map);
        locateMe = findViewById(R.id.locate_me);
        saveTrashPointFab = (FloatingActionButton) findViewById(R.id.save_trash_point);
        undoTrashMarkingFab = (FloatingActionButton) findViewById(R.id.cancel_marking_trash_point);
        locateMe.setOnClickListener(this);
        saveTrashPointFab.setOnClickListener(this);
        undoTrashMarkingFab.setOnClickListener(this);
        myViewModel = ViewModelProviders.of(this).get(MyViewModel.class);

        //checking if location is being observed on configuration changes too
        if (savedInstanceState != null)
        {
            isSelectingTrashPoint = savedInstanceState.getBoolean("isSelectingTrashPoint");
            tagForMarkerBitmap = savedInstanceState.getString("tagForMarker");
            isTrashSelectDialogVisible = savedInstanceState.getBoolean("isTrashSelecting");

            if (isSelectingTrashPoint)
            {
                findViewById(R.id.add_trash_point_button).setVisibility(View.GONE);
                if (tagForMarkerBitmap != null)
                {
                    tapOnMap.setVisibility(View.VISIBLE);
                    locateMe.setVisibility(View.VISIBLE);
                }
            }
        }
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //setting add trash button
        AppCompatTextView addTrashButton = (AppCompatTextView) findViewById(R.id.add_trash_point_button);
        Drawable leftDrawable = VectorDrawableCompat.create(getResources(), R.drawable.marker_land_dumping, getTheme());
        addTrashButton.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, null, null);
        addTrashButton.setOnClickListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isSelectingTrashPoint", isSelectingTrashPoint);
        outState.putString("tagForMarker", tagForMarkerBitmap);
        outState.putBoolean("isTrashSelecting", isTrashSelectDialogVisible);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        this.googleMap.setOnMarkerClickListener(this);

        //observing changes in trash point
        myViewModel.getTrashPointsSnapShotLD()
                .observe(this, new Observer<List<Object>>()
                {
                    @Override
                    public void onChanged(@Nullable List<Object> trashPointsOrError)
                    {
                        if (trashPointsOrError.size() == 0)
                        {
                            //todo: check for null trashPointsOrError too
                            //todo: show progress dialog
                        }
                        else if (trashPointsOrError.get(0) != null)
                        {
                            //adding markers on the google map
                            DataSnapshot dataSnapshot = (DataSnapshot) trashPointsOrError.get(0);
                            for (DataSnapshot trashSnapshot : dataSnapshot.getChildren())
                            {
                                TrashPoint trashPoint = trashSnapshot.getValue(TrashPoint.class);
                                trashPoint.setKey(trashSnapshot.getKey());
                                Marker marker = googleMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(trashPoint.getLat(), trashPoint.getLongt()))
                                        .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(getTrashDrawable(trashPoint.getType())))));
                                marker.setTag(trashPoint);
                                trashCount++;
                            }
                            ((TextView) findViewById(R.id.trash_count)).setText("" + trashCount + " " + "trash points");
                        }
                        else
                        {
                            //todo: show error
                        }

                    }
                });

        //checking permissions and observing locations
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)
        {
            askRequiredPermissions();
        }
        else
            startObservingLocation();

        //checking if user has already selected
        //a trash type point to add
        if (tagForMarkerBitmap != null)
            this.googleMap.setOnMapClickListener(this);
    }

    @Override
    public void onMapClick(LatLng latLng)
    {
        if(userMarker == null)
        {
            try
            {
                locationLD.getResolutionLD().getValue().startResolutionForResult(this, REQUEST_RESULTION);
            } catch (IntentSender.SendIntentException e)
            {
            }
        }

        //checking if distance is greater than 5 kms
        else if (getDistanceInKms(latLng, userMarker.getPosition()) > 2)
        {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.invalid_location)
                    .setMessage(R.string.invalid_location_msg)
                    .create();
            alertDialog.show();
        }
        else if (tagForMarkerBitmap != null && trashPointMarker == null)
        {
            //getting marker Bitmap
            //add trash point to map
            trashPointMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(getTrashDrawable(tagForMarkerBitmap))))
                    .title("added trash point"));
            showSaveMarkerFabs();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(trashPointMarker.getPosition(), 16f));
        }
    }

    private void showSaveMarkerFabs()
    {
        //showing FABs
        saveTrashPointFab.setTranslationY(0f);
        undoTrashMarkingFab.setTranslationY(0f);
        ((TextView) findViewById(R.id.mark_trash_context_text)).setText(R.string.save_trash_context_text);
    }

    //marker click callback
    @Override
    public boolean onMarkerClick(Marker marker)
    {
        //getting trashpoint pojo object from marker tag
        TrashPoint trashPoint = (TrashPoint) marker.getTag();
        try
        {
            if (userMarker==null)
            {
                try
                {
                    locationLD.getResolutionLD().getValue().startResolutionForResult(this, REQUEST_RESULTION);
                } catch (IntentSender.SendIntentException e)
                {
                    return  true;
                }
            }
            Bundle args = new Bundle();
            args.putString("trashType", trashPoint.getType());
            args.putString("trashKey", trashPoint.getKey());
            if (getDistanceInKms(userMarker.getPosition(), trashPoint.getPostion()) < maxDistance)
                args.putBoolean("canUserVote", true);
            else args.putBoolean("canUserVote", false);
            myViewModel.getUserVoteLD().setValue(-2);

            //showing dialog
            TrashDetailDialog trashDetailDialog = new TrashDetailDialog();
            trashDetailDialog.setArguments(args);
            trashDetailDialog.show(getSupportFragmentManager(), "trashDetail");
        } catch (NullPointerException npe)
        {
        }
        return true;
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.add_trash_point_button:
            {
                AgreementDialog agreementDialog = new AgreementDialog();
                agreementDialog.show(getSupportFragmentManager(), "agreement");
                break;
            }
            case R.id.locate_me:
            {
                if (userMarker != null)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.getPosition(), 16f));
                break;
            }
            case R.id.save_trash_point:
            {
                pushTrashPointToFirebaseDB(trashPointMarker, tagForMarkerBitmap);
                break;
            }
            case R.id.cancel_marking_trash_point:
            {
                trashPointMarker.remove();
                trashPointMarker = null;
                hideSaveMarkerFabs();
                break;
            }
        }
    }

    private void hideSaveMarkerFabs()
    {
        //todo: calculate proper DP values
        saveTrashPointFab.setTranslationY(280f);
        undoTrashMarkingFab.setTranslationY(280f);
        ((TextView) findViewById(R.id.mark_trash_context_text)).setText(R.string.mark_trash_context_text);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_PERMISSIONS)
        {
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                startObservingLocation();
            else
            {
                //todo: show error prompts
            }
        }
        else if (requestCode == REQUEST_RESULTION && resultCode == RESULT_OK)
        {
            locationLD.startLocationRequest();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED)
        {
            //start listening location updates
            startObservingLocation();
        }
    }

    //when agreement is accepted
    @Override
    public void onAccept()
    {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        String keyCount = getString(R.string.pref_count);
        String keyLastMarkerTimeStamp = getString(R.string.pref_last_marker_timestamp);
        int markerCount = getPreferences(Context.MODE_PRIVATE).getInt(keyCount, -1);
        long lastMarkerTimestamp = preferences.getLong(keyLastMarkerTimeStamp, 1);

        //checking is markers are expired
        if (markerCount == 0 && DateUtils.isToday(lastMarkerTimestamp))
        {
            //todo: handle this when all markers exhausted
            //show rewarded ad to ad markers
            ShowRewVideoDialog showRewVideoDialog = new ShowRewVideoDialog();
            showRewVideoDialog.show(getSupportFragmentManager(), "showRewardedVideo");
            findViewById(R.id.add_trash_point_button).setVisibility(View.GONE);
            return;
        }
        //this is where initial count of markers is set
        else if (markerCount == -1 || !DateUtils.isToday(lastMarkerTimestamp))
            preferences.edit().putInt(keyCount, 1000).apply();

        //show dialog to choose type of pollution
        TrashDumpingSelectDialog trashDumpingSelectDialog = new TrashDumpingSelectDialog();
        trashDumpingSelectDialog.show(getSupportFragmentManager(), "selectTrashType");
        isTrashSelectDialogVisible = true;
        isSelectingTrashPoint = true;
    }

    //on dialog's trash type select
    @Override
    public void onTrashTypeSelected(String tag)
    {
        //progress dialog
        if (userMarker == null)
        {
            RetainableProgressDialog progressDialog = new RetainableProgressDialog();
            Bundle args = new Bundle();
            args.putInt("msg", R.string.fetching_location);
            progressDialog.setArguments(args);
            progressDialog.show(getSupportFragmentManager(), "gettingLocation");
            setProgressDialog(progressDialog);
            //location resolution
            try
            {
                locationLD.getResolutionLD().getValue().startResolutionForResult(this, REQUEST_RESULTION);
            } catch (IntentSender.SendIntentException e)
            {

            }
        }
        else
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.getPosition(), 16f));

        tagForMarkerBitmap = tag;
        googleMap.setOnMapClickListener(this);
        isTrashSelectDialogVisible = false;
        tapOnMap.setVisibility(View.VISIBLE);
        locateMe.setVisibility(View.VISIBLE);
    }

    @Override
    public void setProgressDialog(RetainableProgressDialog progressDialog)
    {
        currProgressDialog = progressDialog;
    }

    @Override
    public RetainableProgressDialog getProgressDialog()
    {
        return currProgressDialog;
    }

    //getting drwable for trash types
    public Drawable getTrashDrawable(String trashType)
    {
        Drawable drawable = null;
        switch (trashType)
        {
            case "land":
                drawable = VectorDrawableCompat.create(getResources(), R.drawable.marker_land_dumping, getTheme());
                break;
            case "water":
                drawable = VectorDrawableCompat.create(getResources(), R.drawable.marker_water_dumping, getTheme());
                break;
            default:
                drawable = VectorDrawableCompat.create(getResources(), R.drawable.marker_air_dumping, getTheme());
                break;
        }
        return drawable;
    }

    //drawable to bitmap
    private Bitmap getMarkerBitmap(Drawable drawable)
    {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getMinimumWidth(), drawable.getMinimumHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    //get distance between two geopoints
    private float getDistanceInKms(LatLng src, LatLng dest)
    {
        Location source = new Location("");
        source.setLatitude(src.latitude);
        source.setLongitude(src.longitude);

        Location destination = new Location("");
        destination.setLatitude(dest.latitude);
        destination.setLongitude(dest.longitude);

        return source.distanceTo(destination) / 1000;
    }

    //sending data to firebase database
    private void pushTrashPointToFirebaseDB(final Marker trashPointMarker, String trashType)
    {
        tapOnMap.setVisibility(View.GONE);
        hideSaveMarkerFabs();
        //showing progress
        Bundle args = new Bundle();
        args.putInt("msg", R.string.saving_data);
        RetainableProgressDialog progressDialog = new RetainableProgressDialog();
        progressDialog.setArguments(args);
        progressDialog.show(getSupportFragmentManager(), "progressDialog");

        //saving data
        final TrashPoint trashPoint = new TrashPoint(trashPointMarker.getPosition(), trashType);
        final DatabaseReference trashPointRef = FirebaseDatabase.getInstance().getReference("poi").push();
        final String trashKey = trashPointRef.getKey();
        trashPoint.setKey(trashKey);
        trashPointRef.setValue(trashPoint, new DatabaseReference.CompletionListener()
        {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference)
            {
                stopSelectingTrashPoint();
                currProgressDialog.dismiss();
                if (databaseError == null)
                {
                    Toast.makeText(TrashMapActivity.this, R.string.success_add_trash_point, Toast.LENGTH_LONG).show();
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(trashPointMarker.getPosition(), 16f));

                    //saving to local db
                    saveTrashToDB(trashKey);

                    //saving count and time stamp in shared preferences
                    SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    String keyCount = getString(R.string.pref_count);
                    editor.putInt(keyCount, preferences.getInt(keyCount, 0) - 1);
                    editor.putLong(getString(R.string.pref_last_marker_timestamp), new Date().getTime());
                    editor.apply();

                    //add tag to trashmarker
                    trashPointMarker.setTag(trashPoint);
                }
                //failed to save trshPoint
                else
                {
                    trashPointMarker.remove();
                    Toast.makeText(TrashMapActivity.this, R.string.failed_to_add_trash_point, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //saving trashpoint to local database
    private void saveTrashToDB(final String trashId)
    {
        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                TrashPointsDbHelper trashPointsDbHelper = new TrashPointsDbHelper(TrashMapActivity.this);
                SQLiteDatabase db = trashPointsDbHelper.getWritableDatabase();

                //values to put
                ContentValues values = new ContentValues();
                values.put(TrashPointsContract.TrashEntry._ID, trashId);
                values.put(TrashPointsContract.TrashEntry.COLUMN_VOTE, 0);
                values.put(TrashPointsContract.TrashEntry.COLUMN_USER_ADDED, 1);

                //inserting row
                db.insert(TrashPointsContract.TrashEntry.TABLE_NAME, null, values);
                db.close();
                trashPointsDbHelper.close();
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    //stop observing location
    public void stopSelectingTrashPoint()
    {
        findViewById(R.id.add_trash_point_button).setVisibility(View.VISIBLE);
        isSelectingTrashPoint = false;
        isTrashSelectDialogVisible = false;
        googleMap.setOnMapClickListener(null);
        tagForMarkerBitmap = null;
        trashPointMarker = null;
    }

    //setting location observer
    private void startObservingLocation()
    {
        if (locationObserver == null)
        {
            locationObserver = new Observer<Location>()
            {
                @Override
                public void onChanged(@Nullable Location location)
                {
                    if (currProgressDialog != null)
                        currProgressDialog.dismiss();
                    if (location != null)
                    {
                        LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        if (userMarker == null)
                        {
                            userMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(newLatLng)
                                    .title("My location"));
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 16f));
                            locateMe.setVisibility(View.VISIBLE);
                        }
                        else
                            userMarker.setPosition(newLatLng);
                    }
                    //google api connection error
                    else
                    {
                        Toast.makeText(TrashMapActivity.this, R.string.location_error, Toast.LENGTH_LONG).show();
                        //todo: handle connection error
                    }
                }
            };

            //start observing location
            locationLD = myViewModel.getUserLocation();
            locationLD.observe(TrashMapActivity.this, locationObserver);

            //checking a check on resolution
            locationLD.getResolutionLD().observe(this, new Observer<Status>()
            {
                @Override
                public void onChanged(@Nullable Status status)
                {
                    if (status != null)
                    {
                        locateMe.setVisibility(View.GONE);
                        if(userMarker!=null)
                        {
                            userMarker.remove();
                            userMarker = null;
                        }
                        try
                        {
                            status.startResolutionForResult(TrashMapActivity.this, REQUEST_RESULTION);
                        } catch (IntentSender.SendIntentException e)
                        {
                        }
                    }
                }
            });
        }
    }

    //requesting permissions
    private void askRequiredPermissions()
    {
        if (isPermissionsRequested && shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION))
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSIONS);
            isPermissionsRequested = true;
        }
        else if(isPermissionsRequested && !shouldShowRequestPermissionRationale((android.Manifest.permission.ACCESS_FINE_LOCATION)))
        {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_request_title)
                    .setMessage(R.string.msg_permissions_needed)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivityForResult(intent, REQUEST_PERMISSIONS);
                        }
                    })
                    .create();
            alertDialog.show();
        }
        else
        {
            ActivityCompat.requestPermissions(TrashMapActivity.this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSIONS);
            isPermissionsRequested = true;
        }
    }
}

//view model for this activity
class MyViewModel extends AndroidViewModel
{
    //user's Location data
    private LocationLiveData locationLiveData;
    private MutableLiveData<List<Object>> trashPointsSnapShotOrErrorLD = new MutableLiveData<>();
    private MutableLiveData<Integer> userVoteLD = new MutableLiveData<>();

    public MyViewModel(Application application)
    {
        super(application);
        userVoteLD.setValue(-2);
    }

    //user location
    public LocationLiveData getUserLocation()
    {
        if (locationLiveData == null)
            locationLiveData = new LocationLiveData(this.getApplication());
        return locationLiveData;
    }

    //getting all trash points
    public MutableLiveData<List<Object>> getTrashPointsSnapShotLD()
    {
        //this list represents success and error elemets
        //if datasnapshot is null ;i.e; failure
        final List<Object> trashPointsOrError = new ArrayList<>();
        DatabaseReference poiRef = FirebaseDatabase.getInstance().getReference("poi");
        poiRef.addListenerForSingleValueEvent(new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                trashPointsOrError.add(0, dataSnapshot);
                trashPointsSnapShotOrErrorLD.setValue(trashPointsOrError);
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {
                trashPointsOrError.add(0, null);
                trashPointsSnapShotOrErrorLD.setValue(trashPointsOrError);
            }
        });
        return trashPointsSnapShotOrErrorLD;
    }

    public MutableLiveData<Integer> getUserVoteLD()
    {
        return userVoteLD;
    }
}

//googleApiClient LiveData for fetching user location
class LocationLiveData extends LiveData<Location> implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks, LocationListener
{
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private Context context;
    private MutableLiveData<Status> resolutionLD = new MutableLiveData<>();

    LocationLiveData(Context context)
    {
        this.context = context;
    }

    @Override
    protected void onActive()
    {
        super.onActive();
        if (googleApiClient == null)
        {
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        googleApiClient.connect();
    }

    @Override
    protected void onInactive()
    {
        super.onInactive();
        if (googleApiClient.isConnected() && locationRequest != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, LocationLiveData.this);
        googleApiClient.disconnect();

    }

    //connection calbacks
    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        //regularly updating user location
        if (locationRequest == null)
        {
            locationRequest = new LocationRequest();
            locationRequest.setInterval(5000);
            locationRequest.setFastestInterval(6000);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        //checking setting
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        PendingResult<LocationSettingsResult> pendingResult = LocationServices.SettingsApi
                .checkLocationSettings(googleApiClient, builder.build());

        //checking result callbacks
        pendingResult.setResultCallback(new ResultCallback<LocationSettingsResult>()
        {
            @SuppressLint("MissingPermission")
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult)
            {
                Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode())
                {
                    case LocationSettingsStatusCodes.SUCCESS:
                    {
                        startLocationRequest();
                        break;
                    }
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                    {
                        resolutionLD.setValue(status);
                        break;
                    }
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                    {
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        setValue(null);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        setValue(null);
    }

    @Override
    public void onLocationChanged(Location location)
    {
        setValue(location);
    }

    MutableLiveData<Status> getResolutionLD()
    {
        return resolutionLD;
    }

    @SuppressLint("MissingPermission")
    void startLocationRequest()
    {
        LocationServices.FusedLocationApi
                .requestLocationUpdates(googleApiClient, locationRequest, LocationLiveData.this);
    }
}
