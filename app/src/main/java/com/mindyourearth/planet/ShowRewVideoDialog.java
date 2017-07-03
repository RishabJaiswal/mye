package com.mindyourearth.planet;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;

/**
 * Created by Rishab on 31-05-2017.
 */

public class ShowRewVideoDialog extends AppCompatDialogFragment implements View.OnClickListener
{
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_show_rewvideo, null);
        view.findViewById(R.id.showRewVideoAd).setOnClickListener(this);
        return new AlertDialog.Builder(getActivity())
                .setView(view)
                .setTitle(R.string.get_three_markers)
                .create();
    }

    @Override
    public void onCancel(DialogInterface dialog)
    {
        super.onCancel(dialog);
        getActivity().findViewById(R.id.add_trash_point_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View view)
    {
        //not checking via instanceOf
        dismiss();
        ((ShowRewVideoListener) getActivity()).showRewVideoAd();
    }

    interface ShowRewVideoListener
    {
        void showRewVideoAd();
    }
}
