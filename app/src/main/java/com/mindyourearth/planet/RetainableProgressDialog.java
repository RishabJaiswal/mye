package com.mindyourearth.planet;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDialogFragment;

/**
 * Created by Rishab on 28-05-2017.
 */

public class RetainableProgressDialog extends AppCompatDialogFragment
{
    ProgressDialog progressDialog;

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        if (activity instanceof ProgressableActivity)
            ((ProgressableActivity) activity).setProgressDialog(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (progressDialog != null)
        {
            return progressDialog;
        }
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setCancelable(false);
        progressDialog.setMessage(getString(getArguments().getInt("msg")));
        return progressDialog;
    }

    public interface ProgressableActivity
    {
        void setProgressDialog(RetainableProgressDialog progressDialog);

        RetainableProgressDialog getProgressDialog();
    }
}
