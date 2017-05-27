package com.mindyourearth.planet.pojos;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Rishab on 26-05-2017.
 */

@IgnoreExtraProperties
public class TrashPoint
{
    private Long time, dirty, clean;
    private Double lat, longt;
    private String type;

    public TrashPoint()
    {
    }

    public TrashPoint(LatLng latLng, String trashType)
    {
        time = System.currentTimeMillis();
        lat = latLng.latitude;
        longt = latLng.longitude;
        type = trashType;
        dirty = 0L; clean =0L;
    }

    public Long getTime()
    {
        return time;
    }

    public Long getDirty()
    {
        return dirty;
    }

    public Long getClean()
    {
        return clean;
    }

    public Double getLat()
    {
        return lat;
    }

    public Double getLongt()
    {
        return longt;
    }

    public String getType()
    {
        return type;
    }
}
