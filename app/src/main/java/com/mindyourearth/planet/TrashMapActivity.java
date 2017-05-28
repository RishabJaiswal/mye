package com.mindyourearth.planet;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LifecycleActivity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatTextView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
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
import com.mindyourearth.planet.pojos.TrashPoint;

import java.util.ArrayList;
import java.util.List;

public class TrashMapActivity extends LifecycleActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener, View.OnClickListener, DialogInterface.OnCancelListener, GoogleMap.OnMarkerClickListener
{
    private GoogleMap googleMap;
    String tagForMarkerBitmap;
    AlertDialog typeOfDumpDialog;
    Marker userMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash_map);

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
    public void onMapReady(GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        getAllTrashPoints();
        this.googleMap.setOnMarkerClickListener(this);
    }

    @Override
    public void onMapClick(LatLng latLng)
    {
        //checking if distance is greater than 5 kms
        if (getDistanceInKms(latLng, userMarker.getPosition()) > 5)
        {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.invalid_location)
                    .setMessage(R.string.invalid_location_msg)
                    .create();
            alertDialog.show();
            return;
        }
        else if(tagForMarkerBitmap!=null)
        {
            //getting marker Bitmap
            //add trash point to map and
            //push it to Firebase database
            Marker trashPointMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(getTrashDrawable(tagForMarkerBitmap))))
                    .title("added trash point"));
            pushTrashPointToFirebaseDB(trashPointMarker, tagForMarkerBitmap);
        }
        googleMap.setOnMapClickListener(null);
        findViewById(R.id.add_trash_point_button).setVisibility(View.VISIBLE);
        tagForMarkerBitmap = null;
    }


    //marker click callback
    @Override
    public boolean onMarkerClick(Marker marker)
    {
        //getting trashpoint pojo object from marker tag
        TrashPoint trashPoint = (TrashPoint) marker.getTag();

        //dialog to show trashpoint details
        final View trashDetailView = getLayoutInflater().inflate(R.layout.dialog_details_trash_point, null);
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dumping_land)
                .setView(trashDetailView)
                .setIcon(getTrashDrawable(trashPoint.getType()))
                .create();
        alertDialog.show();

        //getching trash point data from firebase
        FirebaseDatabase.getInstance().getReference("poi/" + trashPoint.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                    {
                        //hiding progress bar
                        trashDetailView.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                        trashDetailView.findViewById(R.id.you_say).setVisibility(View.VISIBLE);
                        //setting values
                        ((TextView)trashDetailView.findViewById(R.id.votes_clean_count))
                                .setText(dataSnapshot.child("clean").getValue().toString());
                        ((TextView)trashDetailView.findViewById(R.id.votes_dirty_count))
                                .setText(dataSnapshot.child("dirty").getValue().toString());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                    {
                        trashDetailView.findViewById(R.id.progress_bar_votes).setVisibility(View.INVISIBLE);
                        TextView youSayText = (TextView) trashDetailView.findViewById(R.id.you_say);
                        youSayText.setVisibility(View.VISIBLE);
                        youSayText.setText(R.string.failed_to_get_votes);
                    }
                });
        return true;
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.add_trash_point_button:
                showAgreement(view);
                break;
            default:
            {
                tagForMarkerBitmap = (String) view.getTag();
                typeOfDumpDialog.cancel();
            }
        }
    }

    //callback when type of dumping dialog is dismissed
    @Override
    public void onCancel(DialogInterface dialogInterface)
    {
        //set map click listener
        if(tagForMarkerBitmap!=null)
            googleMap.setOnMapClickListener(TrashMapActivity.this);
        else
            findViewById(R.id.add_trash_point_button).setVisibility(View.VISIBLE);
    }
    //show dialog
    public void showAgreement(View view)
    {
        view.setVisibility(View.GONE);
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.title_dialog_agreement)
                .setMessage(R.string.message_dialog_agreement)
                .setPositiveButton(R.string.i_agree, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        //progress dialog
                        final ProgressDialog progressDialog = new ProgressDialog(TrashMapActivity.this);
                        progressDialog.setMessage(getString(R.string.fetching_location));
                        progressDialog.setCancelable(false);
                        progressDialog.show();

                        //observing userLocation
                        MyViewModel myViewModel = ViewModelProviders.of(TrashMapActivity.this).get(MyViewModel.class);
                        myViewModel.getUserLocation().observe(TrashMapActivity.this, new Observer<Location>()
                        {
                            @Override
                            public void onChanged(@Nullable Location location)
                            {
                                progressDialog.dismiss();
                                if (location != null)
                                {
                                    LatLng newLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                    if (userMarker == null)
                                    {
                                        userMarker = googleMap.addMarker(new MarkerOptions()
                                                .position(newLatLng)
                                                .title("My location"));
                                    } else
                                        userMarker.setPosition(newLatLng);
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 14f));

                                    //show dialog to choose type of pollution
                                    if (typeOfDumpDialog == null)
                                    {
                                        View view = getLayoutInflater().inflate(R.layout.dialog_add_trash_point, null);
                                        view.findViewById(R.id.land_dumping).setOnClickListener(TrashMapActivity.this);
                                        view.findViewById(R.id.water_dumping).setOnClickListener(TrashMapActivity.this);
                                        view.findViewById(R.id.air_dumping).setOnClickListener(TrashMapActivity.this);
                                        typeOfDumpDialog = new AlertDialog.Builder(TrashMapActivity.this)
                                                .setTitle(R.string.title_dialog_add_trash_point)
                                                .setView(view)
                                                .create();
                                        typeOfDumpDialog.setOnCancelListener(TrashMapActivity.this);
                                    }
                                    typeOfDumpDialog.show();
                                }
                                //google api connection error
                                else
                                {
                                }
                            }
                        });
                    }
                })
                .create();
        alertDialog.setOnCancelListener(this);
        RetainableAlertDialog dialogFragment = new RetainableAlertDialog();
        dialogFragment.setDialog(alertDialog);
        dialogFragment.setRetainInstance(true);
        dialogFragment.show(getSupportFragmentManager(), "asd");
    }

    //getting drwable for trash types
    private Drawable getTrashDrawable(String trashType)
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
        //showing progress
        final ProgressDialog savingDataDialog = new ProgressDialog(this);
        savingDataDialog.setMessage(getString(R.string.saving_data));
        savingDataDialog.setCancelable(false);
        savingDataDialog.show();

        //saving data
        DatabaseReference trashPointRef = FirebaseDatabase.getInstance().getReference("poi").push();
        trashPointRef.setValue(new TrashPoint(trashPointMarker.getPosition(), trashType), new DatabaseReference.CompletionListener()
        {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference)
            {
                savingDataDialog.dismiss();
                //failed to save trshPoint
                if (databaseError != null)
                {
                    trashPointMarker.remove();
                    Toast.makeText(TrashMapActivity.this, R.string.failed_to_add_trash_point, Toast.LENGTH_LONG).show();
                } else
                    Toast.makeText(TrashMapActivity.this, R.string.success_add_trash_point, Toast.LENGTH_LONG).show();
            }
        });
    }

    //getting all trash points
    private void getAllTrashPoints()
    {
        final List<TrashPoint> trashPoints = new ArrayList<>();
        DatabaseReference poiRef = FirebaseDatabase.getInstance().getReference("poi");
        poiRef.addListenerForSingleValueEvent(new ValueEventListener()
        {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                for (DataSnapshot trashSnapshot : dataSnapshot.getChildren())
                {
                    TrashPoint trashPoint = trashSnapshot.getValue(TrashPoint.class);
                    trashPoint.setKey(trashSnapshot.getKey());
                    trashPoints.add(trashPoint);
                    Marker marker = googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(trashPoint.getLat(), trashPoint.getLongt()))
                            .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmap(getTrashDrawable(trashPoint.getType())))));
                    marker.setTag(trashPoint);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {

            }
        });
    }
}

//view model for this activity
class MyViewModel extends AndroidViewModel
{
    private LocationLiveData locationLiveData;

    public MyViewModel(Application application)
    {
        super(application);
    }

    LocationLiveData getUserLocation()
    {
        if (locationLiveData == null)
            locationLiveData = new LocationLiveData(this.getApplication());
        return locationLiveData;
    }
}

//googleApiClient LiveData for fetching user location
class LocationLiveData extends LiveData<Location> implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks
{
    private GoogleApiClient googleApiClient;
    private Context context;

    public LocationLiveData(Context context)
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
            googleApiClient.connect();
        }
    }

    @Override
    protected void onInactive()
    {
        super.onInactive();
        googleApiClient.disconnect();
    }

    //connection calbacks
    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        @SuppressLint("MissingPermission")
        Location lastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (lastKnownLocation != null)
            setValue(lastKnownLocation);

        //regularly updating user location
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
}
