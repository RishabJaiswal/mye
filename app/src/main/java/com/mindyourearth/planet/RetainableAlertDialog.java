package com.mindyourearth.planet;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * Created by Rishab on 27-05-2017.
 */

public class RetainableAlertDialog extends DialogFragment
{
    AlertDialog alertDialog;


    public void setDialog(AlertDialog alertDialog)
    {
        this.alertDialog = alertDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        return alertDialog;
    }
}
