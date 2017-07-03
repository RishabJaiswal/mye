package com.mindyourearth.planet;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;

/**
 * Created by Rishab on 28-05-2017.
 */

public class AgreementDialog extends AppCompatDialogFragment
{
    AlertDialog alertDialog;

    @Override
    public void onCancel(DialogInterface dialog)
    {
        getActivity().findViewById(R.id.add_trash_point_button).setVisibility(View.VISIBLE);
        super.onCancel(dialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        //controlling visibility from fragment
        //to main its state in device rotation
        Activity activity = getActivity();
        activity.findViewById(R.id.add_trash_point_button).setVisibility(View.GONE);
        if(alertDialog!=null)
            return alertDialog;
        alertDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.title_dialog_agreement)
                .setMessage(R.string.message_dialog_agreement)
                .setPositiveButton(R.string.i_agree, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        ((AgreementAcceptCallback) getActivity()).onAccept();
                    }
                })
                .create();
        return alertDialog;
    }

    //call back when user accepts the agreement
    public interface AgreementAcceptCallback
    {
        void onAccept();
    }
}
