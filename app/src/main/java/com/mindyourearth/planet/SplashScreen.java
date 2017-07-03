package com.mindyourearth.planet;

import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;

public class SplashScreen extends AppCompatActivity
{

    AnimatedVectorDrawableCompat animLogo;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        AppCompatImageView logo = (AppCompatImageView) findViewById(R.id.logo);
        animLogo = AnimatedVectorDrawableCompat.create(this, R.drawable.logo_anim);
        logo.setImageDrawable(animLogo);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        animLogo.start();

        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                startActivity(new Intent(SplashScreen.this, IntroActivity.class));
                finish();
            }
        }, 2500);
    }
}
