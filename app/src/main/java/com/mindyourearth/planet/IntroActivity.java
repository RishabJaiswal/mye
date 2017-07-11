package com.mindyourearth.planet;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.graphics.drawable.Animatable2Compat;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class IntroActivity extends AppCompatActivity
{

    @Override
    protected void attachBaseContext(Context newBase)
    {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        AppCompatImageView happyEarth = (AppCompatImageView) findViewById(R.id.happy_earth);
        final AnimatedVectorDrawableCompat happyEarthAnim = AnimatedVectorDrawableCompat.create(this, R.drawable.happyearth_anim);
        happyEarth.setImageDrawable(happyEarthAnim);

        //rotate Earth(enterance)
        final AnimatorSet animatorSet = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.earth_rotate);
        animatorSet.setInterpolator(new FastOutSlowInInterpolator());
        animatorSet.setTarget(happyEarth);
        animatorSet.start();

        //start blinking after 0.5s
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                happyEarthAnim.start();
            }
        },500);

        //eye blinking
        happyEarthAnim.registerAnimationCallback(new Animatable2Compat.AnimationCallback()
        {
            @Override
            public void onAnimationEnd(Drawable drawable)
            {
                handler.postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        happyEarthAnim.start();
                    }
                }, 1000);
            }
        });

        //rotating sun
        ObjectAnimator animator = ObjectAnimator.ofFloat(findViewById(R.id.sun),"rotation", 0f,360f);
        animator.setDuration(14000);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();

        //fading in text title
        ObjectAnimator animator2 = ObjectAnimator.ofFloat((View)findViewById(R.id.msg_title),"alpha", 0f,1f);
        animator2.setStartDelay(800);
        animator2.setDuration(1000);
        animator2.start();
    }

    public void begin(View v)
    {
        startActivity(new Intent(this, TrashMapActivity.class));
        getSharedPreferences(getString(R.string.shared_pref_user), MODE_PRIVATE).edit()
                .putBoolean(getString(R.string.pref_new_user), false).apply();
        finish();
    }
}
