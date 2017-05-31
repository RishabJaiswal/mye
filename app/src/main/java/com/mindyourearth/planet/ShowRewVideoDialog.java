package com.mindyourearth.planet;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;

/**
 * Created by Rishab on 31-05-2017.
 */

public class ShowRewVideoDialog extends AppCompatDialogFragment
{
    AlertDialog alertDialog;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        if (alertDialog == null)
        {
            View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_show_rewvideo, null);
            alertDialog = new AlertDialog.Builder(getActivity())
                    .setView(view)
                    .setTitle(R.string.get_three_markers)
                    .create();
        }
        return alertDialog;
    }
}
