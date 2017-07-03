package com.mindyourearth.planet.pojos;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Created by Rishab on 26-05-2017.
 */

@IgnoreExtraProperties
public class TrashPoint
{
    private Long time, dirty, clean;
    private Double lat, longt;
    private String type;
    @Exclude
    private String key;

    public TrashPoint()
    {
    }

    public TrashPoint(LatLng latLng, String trashType)
    {
        time = System.currentTimeMillis();
        lat = latLng.latitude;
        longt = latLng.longitude;
        type = trashType;
        dirty = 1L;
        clean = 0L;
    }

    public Long getTime()
    {
        return time;
    }

    public Long getDirty()
    {
        return dirty;
    }

    public void setDirty(long dirty)
    {
        if (dirty < 0)
            this.dirty = 0L;
        else
            this.dirty = dirty;
    }

    public Long getClean()
    {
        return clean;
    }

    public void setClean(long clean)
    {
        if (clean < 0)
            this.clean = 0L;
        else
            this.clean = clean;
    }

    public Double getLat()
    {
        return lat;
    }

    public Double getLongt()
    {
        return longt;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getType()
    {
        return type;
    }


    @Exclude
    public String getKey()
    {
        return key;
    }

    @Exclude
    public void setKey(String key)
    {
        this.key = key;
    }

    @Exclude
    public LatLng getPostion()
    {
        return new LatLng(lat, longt);
    }
}
