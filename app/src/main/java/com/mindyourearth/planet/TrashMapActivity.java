package com.mindyourearth.planet;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LifecycleActivity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.mindyourearth.planet.data.TrashPointsContract;
import com.mindyourearth.planet.data.TrashPointsDbHelper;
import com.mindyourearth.planet.pojos.TrashPoint;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class TrashMapActivity extends LifecycleActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener, View.OnClickListener, GoogleMap.OnMarkerClickListener,
        AgreementDialog.AgreementAcceptCallback, TrashDumpingSelectDialog.TrashTypeSelectListener,
        RetainableProgressDialog.ProgressableActivity, RewardedVideoAdListener,
        ShowRewVideoDialog.ShowRewVideoListener, UserHistoryDialog.HistoryItemClickListener,
        SensorEventListener
{
    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_RESULTION = 2;
    private static final int REQUEST_SELECT_IMAGE = 3;
    private static final int MAX_MARKER_COUNT = 3;
    int maxDistance = 2;
    int minVotesToRemove = 100;
    float[] accelerometerReadings = new float[3];
    float[] geomagnetometerReadings = new float[3];
    private String tagForMarkerBitmap, trashKey, trashType;    // this trashkey represts the id of trashpoint
    // whose details are currently open in dialog
    private boolean isSelectingTrashPoint, isTrashSelectDialogVisible;

    private MyViewModel myViewModel;
    private Observer<Location> locationObserver;
    private LocationLiveData locationLD;

    private GoogleMap googleMap;
    Marker userMarker, trashPointMarker;
    private RetainableProgressDialog currProgressDialog;
    View tapOnMap, locateMe, stats, trashHistoryBtn;
    FloatingActionButton saveTrashPointFab, undoTrashMarkingFab;
    private RewardedVideoAd rewardedVideoAd;
    private SharedPreferences permissionStatus;
    private TextView stats_then, stats_now;
    private SensorManager sensorManager;
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    private float alpha = 0.97f;
    private AppCompatButton addTrashButton;
    private SupportMapFragment mapFragment;
    private Marker dummyMarker;
    private Uri sharedImageUri;

    @Override
    protected void attachBaseContext(Context newBase)
    {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash_map);
        MobileAds.initialize(this, getString(R.string.ad_app_id));
        rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this);
        rewardedVideoAd.setRewardedVideoAdListener(this);

        tapOnMap = findViewById(R.id.tap_on_map);
        locateMe = findViewById(R.id.locate_me);
        stats = findViewById(R.id.stats);
        trashHistoryBtn = findViewById(R.id.trash_history);
        stats_then = (TextView) findViewById(R.id.stat_then);
        stats_now = (TextView) findViewById(R.id.stat_now);
        saveTrashPointFab = (FloatingActionButton) findViewById(R.id.save_trash_point);
        undoTrashMarkingFab = (FloatingActionButton) findViewById(R.id.cancel_marking_trash_point);
        //click listeners
        locateMe.setOnClickListener(this);
        saveTrashPointFab.setOnClickListener(this);
        undoTrashMarkingFab.setOnClickListener(this);
        findViewById(R.id.trash_history).setOnClickListener(this);
        myViewModel = ViewModelProviders.of(this).get(MyViewModel.class);

        //checking if location is being observed on configuration changes too
        if (savedInstanceState != null)
        {
            isSelectingTrashPoint = savedInstanceState.getBoolean("isSelectingTrashPoint");
            tagForMarkerBitmap = savedInstanceState.getString("tagForMarker");
            isTrashSelectDialogVisible = savedInstanceState.getBoolean("isTrashSelecting");

            if (isSelectingTrashPoint)
            {
                addTrashButton.setVisibility(View.GONE);
                stats.setVisibility(View.GONE);
                trashHistoryBtn.setVisibility(View.GONE);
                if (tagForMarkerBitmap != null)
                {
                    tapOnMap.setVisibility(View.VISIBLE);
                    locateMe.setVisibility(View.VISIBLE);
                }
            }
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        loadMap();

        //setting add trash button
        addTrashButton = (AppCompatButton) findViewById(R.id.add_trash_point_button);
        Drawable leftDrawable = VectorDrawableCompat.create(getResources(), R.drawable.marker_land_dumping, getTheme());
        addTrashButton.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, null, null);
        addTrashButton.setOnClickListener(this);

        //setting then trashpoint count
        long trashCount = getPreferences(MODE_PRIVATE).getLong(getString(R.string.pref_trash_count), -1);
        if (trashCount > -1)
            stats_then.setText(getString(R.string.stats_then) + " " + trashCount);

        //getting minimum votes required to remove trashPoint
        FirebaseDatabase.getInstance().getReference("minVotes").addListenerForSingleValueEvent(new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                minVotesToRemove = ((Long) dataSnapshot.getValue()).intValue();
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {

            }
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    private void loadMap()
    {
        //checking network connectivity
        if (hasInternetConnection())
        {
            createProgressDialog(R.string.loading_map);
            mapFragment.getMapAsync(this);
        }
        else
        {
            new AlertDialog.Builder(this)
                    .setView(R.layout.dialog_no_internet)
                    .setPositiveButton(R.string.retry, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            loadMap();
                        }
                    })
                    .create()
                    .show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putBoolean("isSelectingTrashPoint", isSelectingTrashPoint);
        outState.putString("tagForMarker", tagForMarkerBitmap);
        outState.putBoolean("isTrashSelecting", isTrashSelectDialogVisible);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause()
    {
        rewardedVideoAd.pause(this);
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume()
    {
        rewardedVideoAd.resume(this);
        super.onResume();

        //when sharing trash Image
        if (sharedImageUri != null)
        {
            createProgressDialog(R.string.building_link);
            new DynamicLinkTask(myViewModel.getShareDynamicLinkLD()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, trashKey, trashType);
        }

        //registering sensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (userMarker != null)
            getSharedDynamicLink();
    }

    @Override
    public void onBackPressed()
    {
        if (tagForMarkerBitmap != null && isSelectingTrashPoint)
            stopSelectingTrashPoint();
        else
            super.onBackPressed();
    }

    @Override
    public void onDetachedFromWindow()
    {
        rewardedVideoAd.destroy(this);
        super.onDetachedFromWindow();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(final GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        //checking if user needs to get tutorial done
        if (!getPreferences(MODE_PRIVATE).getBoolean(getString(R.string.pref_tutorial_done), false))
        {
            startTutorial();
            setProgressDialog(null);
            return;
        }

        this.googleMap.setOnMarkerClickListener(this);
        final MutableLiveData<Long> trashCountLD = myViewModel.getTrashCountLD();
        //showing progress dialog
        if (currProgressDialog != null)
            currProgressDialog.progressDialog.setMessage(getString(R.string.getting_trash_points));
        else
            createProgressDialog(R.string.getting_trash_points);

        //observing changes in trash point
        myViewModel.getTrashPointsSnapShotLD()
                .observe(this, new Observer<List<Object>>()
                {
                    @Override
                    public void onChanged(@Nullable List<Object> trashPointsOrError)
                    {
                        long trashPointsCount = 0L;
                        if (trashPointsOrError == null || trashPointsOrError.size() == 0)
                        {
                            //todo: check for null trashPointsOrError too
                            setProgressDialog(null);
                            Toast.makeText(TrashMapActivity.this, R.string.fail_to_get_trashpoints, Toast.LENGTH_SHORT).show();
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
                                trashPointsCount++;
                            }
                            setProgressDialog(null);
                        }
                        else
                        {
                            setProgressDialog(null);
                            Toast.makeText(TrashMapActivity.this, R.string.fail_to_get_trashpoints, Toast.LENGTH_SHORT).show();
                        }

                        if (trashPointsCount != 0)
                        {
                            trashCountLD.setValue(trashPointsCount);
                            setProgressDialog(null);
                        }
                    }
                });

        //updating total value of trashPoint counts
        trashCountLD.observe(this, new Observer<Long>()
        {
            @Override
            public void onChanged(@Nullable Long trashCount)
            {
                if (trashCount != null && trashCount > 0)
                {
                    stats_now.setText(getString(R.string.stats_now) + " " + trashCount);
                    getPreferences(MODE_PRIVATE)
                            .edit()
                            .putLong(getString(R.string.pref_trash_count), trashCount)
                            .apply();
                }
            }
        });

        //checking permissions and observing locations
        if (!hasRequiredPermissions())
            askRequiredPermissions();
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
        if (userMarker == null)
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
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(trashPointMarker.getPosition(), 19f));
        }
    }

    //marker click callback
    @Override
    public boolean onMarkerClick(Marker marker)
    {
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 18f));
        return showTrashDetails(marker, null);
    }

    //dialog to show trash details
    private boolean showTrashDetails(Marker marker, TrashPoint trashPoint)
    {
        //getting trashpoint pojo object from marker tag
        if (marker != null)
            trashPoint = (TrashPoint) marker.getTag();
        try
        {
            if (userMarker == null)
            {
                try
                {
                    locationLD.getResolutionLD().getValue().startResolutionForResult(this, REQUEST_RESULTION);
                } catch (IntentSender.SendIntentException e)
                {
                    return true;
                }
            }
            trashKey = trashPoint.getKey();
            trashType = trashPoint.getType();
            Bundle args = new Bundle();
            args.putString("trashType", trashType);
            args.putString("trashKey", trashKey);

            myViewModel.getUserVoteLD().setValue(-2);
            observeShareDynamicLink();

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
                if (!hasInternetConnection())
                {
                    new AlertDialog.Builder(this)
                            .setView(R.layout.dialog_no_internet)
                            .create()
                            .show();
                }
                else if (hasRequiredPermissions())
                {
                    AgreementDialog agreementDialog = new AgreementDialog();
                    agreementDialog.show(getSupportFragmentManager(), "agreement");
                }
                else
                    askRequiredPermissions();
                break;
            }
            case R.id.locate_me:
            {
                if (!hasInternetConnection())
                {
                    new AlertDialog.Builder(this)
                            .setView(R.layout.dialog_no_internet)
                            .create()
                            .show();
                }
                else if (userMarker != null)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.getPosition(), 16f));
                else if (!hasRequiredPermissions())
                {
                    askRequiredPermissions();
                }
                else if (locationObserver != null)
                {
                    Status status = locationLD.getResolutionLD().getValue();
                    if (status != null)
                    {
                        try
                        {
                            status.startResolutionForResult(TrashMapActivity.this, REQUEST_RESULTION);
                        } catch (IntentSender.SendIntentException e)
                        {
                            Toast.makeText(TrashMapActivity.this, R.string.location_error, Toast.LENGTH_LONG).show();
                        }
                    }
                    else
                        Toast.makeText(TrashMapActivity.this, R.string.location_error, Toast.LENGTH_LONG).show();

                }
                else
                    startObservingLocation();
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
            case R.id.trash_history:
            {
                UserHistoryDialog historyDialog = new UserHistoryDialog();
                historyDialog.show(getSupportFragmentManager(), "userHistory");
                break;
            }
            case R.id.stats:
            {
                showStatsDialog();
                break;
            }
            case R.id.skip:
            {
                stopTutorial();
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_PERMISSIONS:
            {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                {
                    startObservingLocation();
                    permissionStatus.edit().putInt(android.Manifest.permission.ACCESS_FINE_LOCATION, 1).apply();
                }
                else
                {
                    //todo: show error prompts
                }
                break;
            }
            case REQUEST_RESULTION:
            {
                if (resultCode == RESULT_OK)
                    locationLD.startLocationRequest();
                break;
            }
            case REQUEST_SELECT_IMAGE:
            {
                //building link and sharing image
                if (resultCode == RESULT_OK)
                {
                    sharedImageUri = data.getData();
                }
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            //start listening location updates
            startObservingLocation();
            permissionStatus.edit().putInt(android.Manifest.permission.ACCESS_FINE_LOCATION, 1).apply();
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

        //setting up rewarded video ads
        if (!rewardedVideoAd.isLoaded() || markerCount == 1)
        {
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .build();
            rewardedVideoAd.loadAd(getString(R.string.ad_rewarded_adunit_id), adRequest);
        }

        //checking if markers are expired
        if (markerCount == 0 && DateUtils.isToday(lastMarkerTimestamp))
        {
            //todo: handle this when all markers exhausted
            //show rewarded ad to ad markers
            ShowRewVideoDialog showRewVideoDialog = new ShowRewVideoDialog();
            showRewVideoDialog.show(getSupportFragmentManager(), "showRewardedVideo");
            addTrashButton.setVisibility(View.GONE);
            return;
        }
        //this is where initial count of markers is set
        else if (markerCount == -1 || !DateUtils.isToday(lastMarkerTimestamp))
            preferences.edit().putInt(keyCount, MAX_MARKER_COUNT).apply();

        //show dialog to choose type of pollution
        TrashDumpingSelectDialog trashDumpingSelectDialog = new TrashDumpingSelectDialog();
        trashDumpingSelectDialog.show(getSupportFragmentManager(), "selectTrashType");
        stats.setVisibility(View.GONE);
        trashHistoryBtn.setVisibility(View.GONE);
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
            createProgressDialog(R.string.fetching_location);
            //location resolution
            try
            {
                locationLD.getResolutionLD().getValue().startResolutionForResult(this, REQUEST_RESULTION);
            } catch (IntentSender.SendIntentException e)
            {

            }
        }
        else
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.getPosition(), 19f));

        tagForMarkerBitmap = tag;
        googleMap.setOnMapClickListener(this);
        isTrashSelectDialogVisible = false;
        tapOnMap.setVisibility(View.VISIBLE);
        locateMe.setVisibility(View.VISIBLE);
    }

    @Override
    public void setProgressDialog(RetainableProgressDialog progressDialog)
    {
        if (progressDialog == null && currProgressDialog != null)
            currProgressDialog.dismiss();
        currProgressDialog = progressDialog;
    }

    @Override
    public RetainableProgressDialog getProgressDialog()
    {
        return currProgressDialog;
    }

    //checking if user has given required permissions
    private boolean hasRequiredPermissions()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    //getting drwable for trash types
    public Drawable getTrashDrawable(String trashType)
    {
        int drawableId = -1;
        switch (trashType)
        {
            case "land":
                drawableId = R.drawable.marker_land_dumping;
                break;
            case "water":
                drawableId = R.drawable.marker_water_dumping;
                break;
            default:
                drawableId = R.drawable.marker_air_dumping;
                break;
        }
        return VectorDrawableCompat.create(getResources(), drawableId, getTheme());
    }

    //getting title for trash types
    public String getTrashTitle(String trashType)
    {
        int stringResId = -1;
        switch (trashType)
        {
            case "land":
                stringResId = R.string.dumping_land;
                break;
            case "water":
                stringResId = R.string.dumping_water;
                break;
            default:
                stringResId = R.string.dumping_air;
                break;
        }
        return getString(stringResId);
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
    float getDistanceInKms(LatLng src, LatLng dest)
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
    private void pushTrashPointToFirebaseDB(final Marker trashPointMarker, final String trashType)
    {
        tapOnMap.setVisibility(View.GONE);
        //showing progress
        createProgressDialog(R.string.saving_data);

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
                try
                {
                    stopSelectingTrashPoint();
                    if (databaseError == null)
                    {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(trashPointMarker.getPosition(), 20f));

                        //saving to local db
                        saveTrashToDB(trashKey, trashType);

                        //saving count and time stamp in shared preferences
                        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        String keyCount = getString(R.string.pref_count);
                        editor.putInt(keyCount, preferences.getInt(keyCount, 0) - 1);
                        editor.putLong(getString(R.string.pref_last_marker_timestamp), new Date().getTime());
                        editor.apply();

                        //add tag to trashmarker
                        trashPointMarker.setTag(trashPoint);
                        setProgressDialog(null);

                        //dialog to show success
                        final AlertDialog alertDialog = new AlertDialog.Builder(TrashMapActivity.this)
                                .setView(R.layout.dialog_trash_point_saved)
                                .create();
                        alertDialog.show();
                        new Handler().postDelayed(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                alertDialog.dismiss();
                            }
                        }, 1800);

                        //updating stats
                        MutableLiveData<Long> trashCount = myViewModel.getTrashCountLD();
                        trashCount.setValue(trashCount.getValue() + 1);
                    }
                    //failed to save trshPoint
                    else
                    {
                        setProgressDialog(null);
                        trashPointMarker.remove();
                        Toast.makeText(TrashMapActivity.this, R.string.failed_to_add_trash_point, Toast.LENGTH_LONG).show();
                    }
                } catch (NullPointerException npe)
                {
                    stopSelectingTrashPoint();
                    setProgressDialog(null);
                    trashPointMarker.remove();
                    Toast.makeText(TrashMapActivity.this, R.string.failed_to_add_trash_point, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //saving trashpoint to local database
    private void saveTrashToDB(final String trashId, final String trashType)
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
                values.put(TrashPointsContract.TrashEntry.COLUMN_TIME, System.currentTimeMillis());
                values.put(TrashPointsContract.TrashEntry.COLUMN_TYPE, trashType);
                values.put(TrashPointsContract.TrashEntry.COLUMN_VOTE, 0);
                values.put(TrashPointsContract.TrashEntry.COLUMN_USER_ADDED, 1);

                //inserting row
                db.insert(TrashPointsContract.TrashEntry.TABLE_NAME, null, values);
                db.close();
                trashPointsDbHelper.close();
            }
        };
        new Thread(runnable).start();
    }

    //stop observing location
    public void stopSelectingTrashPoint()
    {
        addTrashButton.setVisibility(View.VISIBLE);
        stats.setVisibility(View.VISIBLE);
        trashHistoryBtn.setVisibility(View.VISIBLE);
        isSelectingTrashPoint = false;
        isTrashSelectDialogVisible = false;
        googleMap.setOnMapClickListener(null);
        tagForMarkerBitmap = null;
        trashPointMarker = null;

        if (tapOnMap.getVisibility() == View.VISIBLE)
            tapOnMap.setVisibility(View.GONE);
        if (saveTrashPointFab.getTranslationY() == 0)
            hideSaveMarkerFabs();
    }

    //setting location observer
    @SuppressLint("MissingPermission")
    private void startObservingLocation()
    {
        if (locationObserver == null)
        {
            locationObserver = new Observer<Location>()
            {
                @Override
                public void onChanged(@Nullable Location location)
                {
                    if (currProgressDialog!= null && currProgressDialog.getCustomTag().equals(getString(R.string.fetching_location)))
                    {
                        currProgressDialog.dismiss();
                        currProgressDialog = null;
                    }
                    if (location != null)
                    {
                        LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        if (googleMap == null)
                        {
                            mapFragment.getMapAsync(TrashMapActivity.this);
                            return;
                        }
                        if (userMarker == null)
                        {
                            userMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(newLatLng)
                                    .anchor(0.5f, 0.6f)
                                    .flat(true)
                                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(VectorDrawableCompat
                                            .create(getResources(), R.drawable.marker_user, null))))
                                    .title("Your location"));
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 16f));
                            getSharedDynamicLink();
                        }
                        else
                        {
                            userMarker.setPosition(newLatLng);
                        }
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
            locationLD = myViewModel.getUserLocation(getApplicationContext());
            //checking a check on resolution
            locationLD.getResolutionLD().observe(this, new Observer<Status>()
            {
                @Override
                public void onChanged(@Nullable Status status)
                {
                    if (status != null)
                    {
                        if (userMarker != null)
                        {
                            userMarker.remove();
                            userMarker = null;
                        }
                        try
                        {
                            status.startResolutionForResult(TrashMapActivity.this, REQUEST_RESULTION);
                        } catch (IntentSender.SendIntentException e)
                        {
                            Toast.makeText(TrashMapActivity.this, R.string.location_error, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
            locationLD.observe(TrashMapActivity.this, locationObserver);
        }
    }

    //checking internet connectivity
    private boolean hasInternetConnection()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        return false;
    }

    //requesting permissions
    private void askRequiredPermissions()
    {
        permissionStatus = getPreferences(MODE_PRIVATE);
        if (permissionStatus.getInt(android.Manifest.permission.ACCESS_FINE_LOCATION, -1) == -1)
        {
            ActivityCompat.requestPermissions(TrashMapActivity.this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSIONS);
            permissionStatus.edit().putInt(android.Manifest.permission.ACCESS_FINE_LOCATION, 0).apply();

        }
        else if (ActivityCompat.shouldShowRequestPermissionRationale(TrashMapActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION))
        {

            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_request_title)
                    .setMessage(R.string.msg_permissions_needed)
                    .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            ActivityCompat.requestPermissions(TrashMapActivity.this,
                                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                                    REQUEST_PERMISSIONS);
                            permissionStatus.edit().putInt(android.Manifest.permission.ACCESS_FINE_LOCATION, 0).apply();
                        }
                    })
                    .create();
            alertDialog.show();
        }
        else
        {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_request_title)
                    .setMessage(R.string.msg_permissions_needed)
                    .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivityForResult(intent, REQUEST_PERMISSIONS);
                            permissionStatus.edit().putInt(android.Manifest.permission.ACCESS_FINE_LOCATION, 0).apply();
                        }
                    })
                    .create();
            alertDialog.show();
        }
    }

    //getting sharedDynamicLink when user opens the link
    private void getSharedDynamicLink()
    {
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, new OnSuccessListener<PendingDynamicLinkData>()
                {
                    @Override
                    public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData)
                    {
                        // Get deep link from result (may be null if no link is found)
                        if (pendingDynamicLinkData != null)
                        {
                            TrashPoint trashPoint = new TrashPoint();
                            trashPoint.setKey(pendingDynamicLinkData.getLink().getQueryParameter("id"));
                            trashPoint.setType(pendingDynamicLinkData.getLink().getQueryParameter("type"));
                            showTrashDetails(null, trashPoint);
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener()
                {
                    @Override
                    public void onFailure(@NonNull Exception e)
                    {
                        Log.w("DL ", "getDynamicLink:onFailure", e);
                    }
                });
    }

    //watching changes on dynamic link
    private void observeShareDynamicLink()
    {
        final MutableLiveData<String> dynamicLinkLD = myViewModel.getShareDynamicLinkLD();
        dynamicLinkLD.removeObservers(this);
        dynamicLinkLD.observe(this, new Observer<String>()
        {
            @Override
            public void onChanged(@Nullable String dynamicLink)
            {
                if (currProgressDialog == null && dynamicLink != null)
                {
                    if (dynamicLink.equals(""))
                    {
                        createProgressDialog(R.string.building_link);
                        new DynamicLinkTask(dynamicLinkLD).execute(trashKey, trashType);
                    }
                    else if (dynamicLink.equals(" "))
                    {
                        Toast.makeText(TrashMapActivity.this, R.string.please_wait, Toast.LENGTH_SHORT).show();
                        //dynamicLinkLD.removeObserver(this);
                        dynamicLinkLD.setValue(null);
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_image)), REQUEST_SELECT_IMAGE);
                    }
                }
                //if building link dialog is visible
                else if (currProgressDialog != null)
                {
                    dynamicLinkLD.removeObserver(this);
                    if (dynamicLink == null)
                    {
                        setProgressDialog(null);
                        Toast.makeText(TrashMapActivity.this, R.string.try_again, Toast.LENGTH_SHORT).show();
                        sharedImageUri = null;
                    }
                    else if (sharedImageUri != null)
                    {
                        Intent shareImageIntent = ShareCompat.IntentBuilder.from(TrashMapActivity.this)
                                .setType("image/*")
                                .setStream(sharedImageUri)
                                .setText(getString(R.string.trash_share_text_one) + " " + dynamicLink)
                                .createChooserIntent();
                        startActivity(Intent.createChooser(shareImageIntent, getString(R.string.share_via)));

                        //copying text to clipboard
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clipData = ClipData.newPlainText("trash url", dynamicLink);
                        clipboardManager.setPrimaryClip(clipData);
                        Toast.makeText(TrashMapActivity.this, R.string.trash_link_to_clipboard, Toast.LENGTH_LONG).show();

                        setProgressDialog(null);
                        dynamicLinkLD.setValue(null);
                        sharedImageUri = null;
                    }
                    else
                    {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.trash_share_text_one) + " " + dynamicLink +
                                "\n" + getString(R.string.trash_share_text_two));
                        sendIntent.setType("text/plain");
                        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.share_trash_point)));

                        setProgressDialog(null);
                        dynamicLinkLD.setValue(null);
                    }
                }
            }
        });
    }

    //creating custom retainable progress dialog
    private void createProgressDialog(int msgId)
    {
        Bundle args = new Bundle();
        args.putInt("msg", msgId);
        RetainableProgressDialog progressDialog = new RetainableProgressDialog();
        progressDialog.setArguments(args);
        progressDialog.setCustomTag(getString(msgId));
        progressDialog.show(getSupportFragmentManager(), "progressDialog");
        setProgressDialog(progressDialog);
    }

    //showing/hiding FABs
    private void showSaveMarkerFabs()
    {
        //showing FABs
        saveTrashPointFab.setTranslationY(0f);
        undoTrashMarkingFab.setTranslationY(0f);
        ((TextView) findViewById(R.id.mark_trash_context_text)).setText(R.string.save_trash_context_text);
        ((AppCompatImageView) findViewById(R.id.tap_save_img))
                .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_save_trash_point, null));
    }

    private void hideSaveMarkerFabs()
    {
        //todo: calculate proper DP values
        saveTrashPointFab.setTranslationY(280f);
        undoTrashMarkingFab.setTranslationY(280f);
        ((TextView) findViewById(R.id.mark_trash_context_text)).setText(R.string.mark_trash_context_text);
        ((AppCompatImageView) findViewById(R.id.tap_save_img))
                .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_tap, null));
    }

    //start tutorial
    private void startTutorial()
    {
        View views[] = {locateMe, stats, trashHistoryBtn, addTrashButton};
        for (View view : views) view.setVisibility(View.INVISIBLE);
        findViewById(R.id.tutorial).setVisibility(View.VISIBLE);

        //adding dummy marker
        LatLng dummyLatLng = new LatLng(18.584449, 73.735446);
        dummyMarker = googleMap.addMarker(new MarkerOptions()
                .position(dummyLatLng)
                .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(getTrashDrawable("land")))));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(dummyLatLng, 18f));
        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener()
        {
            @Override
            public boolean onMarkerClick(Marker marker)
            {
                //dummy trash details
                View view = getLayoutInflater().inflate(R.layout.dialog_details_trash_point, null);
                ((TextView) view.findViewById(R.id.votes_dirty_count)).setText("88");
                ((TextView) view.findViewById(R.id.votes_clean_count)).setText("10");
                ((AppCompatTextView)view.findViewById(R.id.title)).setText(R.string.sample_title);
                ((AppCompatTextView)view.findViewById(R.id.date)).setText(R.string.sample_more);
                view.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                view.findViewById(R.id.you_say).setVisibility(View.VISIBLE);
                view.findViewById(R.id.native_ad).setVisibility(View.GONE);
                new AlertDialog.Builder(TrashMapActivity.this)
                        .setView(view)
                        .setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                //final dialog
                                new AlertDialog.Builder(TrashMapActivity.this)
                                        .setTitle(R.string.thats_it)
                                        .setMessage(R.string.tutorial_finish_msg)
                                        .setPositiveButton(R.string.done, new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                stopTutorial();
                                                getPreferences(MODE_PRIVATE).edit()
                                                        .putBoolean(getString(R.string.pref_tutorial_done), true).apply();
                                            }
                                        })
                                        .create()
                                        .show();
                            }
                        })
                        .create()
                        .show();
                return true;
            }
        });

        //shwoing tutorial dialogs
        new AlertDialog.Builder(this)
                .setTitle(R.string.hey_user)
                .setMessage(R.string.tutorial_msg)
                .setPositiveButton(R.string.next, null)
                .create()
                .show();

    }

    private void stopTutorial()
    {
        findViewById(R.id.tutorial).setVisibility(View.GONE);
        View views[] = {locateMe, stats, trashHistoryBtn, addTrashButton};
        for (View view : views) view.setVisibility(View.VISIBLE);
        getPreferences(MODE_PRIVATE).edit()
                .putBoolean(getString(R.string.pref_tutorial_done), true).apply();

        if (dummyMarker != null)
            dummyMarker.remove();

        onMapReady(googleMap);
    }

    //showing stats dialog
    private void showStatsDialog()
    {
        View view = getLayoutInflater().inflate(R.layout.dialog_stats, null);
        //since_last_visit label
        ((AppCompatTextView) view.findViewById(R.id.last_visit_label))
                .setCompoundDrawablesWithIntrinsicBounds(VectorDrawableCompat.create(getResources(), R.drawable.ic_stats, null),
                        null, null, null);
        //then
        AppCompatTextView then = ((AppCompatTextView) view.findViewById(R.id.then));
        then.setText(stats_then.getText() + " " + getString(R.string.trash_points));
        then.setCompoundDrawablesWithIntrinsicBounds(VectorDrawableCompat.create(getResources(), R.drawable.ic_then, null),
                null, null, null);
        //now
        AppCompatTextView now = ((AppCompatTextView) view.findViewById(R.id.now));
        now.setText(stats_now.getText() + " " + getString(R.string.trash_points));
        now.setCompoundDrawablesWithIntrinsicBounds(VectorDrawableCompat.create(getResources(), R.drawable.ic_now, null),
                null, null, null);
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        alertDialog.show();
    }

    //Rewarded videoAdListener Events
    @Override
    public void onRewardedVideoAdLoaded()
    {
        if (currProgressDialog != null && currProgressDialog.getCustomTag().equals(getString(R.string.loading_video)))
        {
            currProgressDialog.dismiss();
            currProgressDialog = null;
            rewardedVideoAd.show();
        }
    }

    @Override
    public void onRewardedVideoAdOpened()
    {
    }

    @Override
    public void onRewardedVideoStarted()
    {
    }

    @Override
    public void onRewardedVideoAdClosed()
    {
        onAccept();
    }

    @Override
    public void onRewarded(RewardItem rewardItem)
    {
        getPreferences(MODE_PRIVATE).edit().putInt(getString(R.string.pref_count), 3).commit();
    }

    @Override
    public void onRewardedVideoAdLeftApplication()
    {
    }

    @Override
    public void onRewardedVideoAdFailedToLoad(int i)
    {
        if (currProgressDialog != null)
        {
            currProgressDialog.dismiss();
            currProgressDialog = null;
            addTrashButton.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, R.string.failed_to_load_ad, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showRewVideoAd()
    {
        if (rewardedVideoAd.isLoaded())
            rewardedVideoAd.show();
        else
            createProgressDialog(R.string.loading_video);
    }

    @Override
    public void onHistoryItemClicked(TrashPoint trashPoint)
    {
        showTrashDetails(null, trashPoint);
    }

    //sensor callbacks
    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            accelerometerReadings[0] = alpha * accelerometerReadings[0] + (1 - alpha)
                    * event.values[0];
            accelerometerReadings[1] = alpha * accelerometerReadings[1] + (1 - alpha)
                    * event.values[1];
            accelerometerReadings[2] = alpha * accelerometerReadings[2] + (1 - alpha)
                    * event.values[2];
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            geomagnetometerReadings[0] = alpha * geomagnetometerReadings[0] + (1 - alpha)
                    * event.values[0];
            geomagnetometerReadings[1] = alpha * geomagnetometerReadings[1] + (1 - alpha)
                    * event.values[1];
            geomagnetometerReadings[2] = alpha * geomagnetometerReadings[2] + (1 - alpha)
                    * event.values[2];
        }

        if (accelerometerReadings != null && geomagnetometerReadings != null)
        {
            // orientation contains azimut, pitch and roll
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReadings, geomagnetometerReadings))
            {
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                float azimuth = (float) Math.toDegrees(orientationAngles[0]);;
                float rotation = (azimuth + 360) % 360;
                if (userMarker != null)
                    userMarker.setRotation(rotation);
                //Log.i("Direction", String.valueOf(azimut) + " " + rotation);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }
}

//AsyncTask to get short dynamicLink fromm REST API
class DynamicLinkTask extends AsyncTask<String, Void, String>
{
    MutableLiveData<String> dynamicLinkLD;

    DynamicLinkTask(MutableLiveData<String> dynamicLinkLD)
    {
        this.dynamicLinkLD = dynamicLinkLD;
    }

    @Override
    protected String doInBackground(String... trashKeyNtype)
    {
        HttpURLConnection conn = null;

        try
        {
            URL url = new URL("https://firebasedynamiclinks.googleapis.com/v1/shortLinks?key=AIzaSyAXZXsUErx0mnIHbWyLRFp4dEn22VAGsqs");
            String deepLink = URLEncoder.encode("https://mindyourearth.com?id=" + trashKeyNtype[0] + "&type=" + trashKeyNtype[1],
                    "UTF-8");

            JSONObject postDataParams = new JSONObject();
            postDataParams.put("longDynamicLink", "https://t34cg.app.goo.gl/?link=" + deepLink +
                    "&apn=com.mindyourearth.planet" +
                    "&dfl=https://play.google.com/store/apps/details?id=com.mindyourearth.planet");
            JSONObject suffixJson = new JSONObject();
            suffixJson.put("option", "SHORT");
            postDataParams.put("suffix", suffixJson);

            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setReadTimeout(15000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(postDataParams.toString().getBytes("UTF-8"));
            os.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
                StringBuffer sb = new StringBuffer("");
                Scanner sc = new Scanner(conn.getInputStream());
                while (sc.hasNext())
                    sb.append(sc.nextLine());
                JSONObject jsonObject = new JSONObject(sb.toString());
                sc.close();

                if (conn != null)
                    conn.disconnect();
                return jsonObject.getString("shortLink");
            }
            else
            {
                if (conn != null)
                    conn.disconnect();
                return null;
            }
        } catch (Exception e)
        {
            if (conn != null)
                conn.disconnect();
            return null;
        }
    }

    @Override
    protected void onPostExecute(String dl)
    {
        dynamicLinkLD.setValue(dl);
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
