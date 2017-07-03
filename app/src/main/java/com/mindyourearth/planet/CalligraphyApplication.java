package com.mindyourearth.planet;

import android.app.Application;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by Rishab on 20-06-2017.
 */

public class CalligraphyApplication extends Application
{
    @Override
    public void onCreate() {
        super.onCreate();
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/regular.otf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );
    }
}
