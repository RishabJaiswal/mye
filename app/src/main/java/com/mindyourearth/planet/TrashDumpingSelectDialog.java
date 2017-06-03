package com.mindyourearth.planet;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.widget.TextView;

/**
 * Created by Rishab on 28-05-2017.
 */

public class TrashDumpingSelectDialog extends AppCompatDialogFragment implements View.OnClickListener
{
    private TrashTypeSelectListener trashTypeSelectListener;

    @Override
    public void onCancel(DialogInterface dialog)
    {
        ((TrashMapActivity)getActivity()).stopSelectingTrashPoint();
        super.onCancel(dialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Activity activity = getActivity();
        trashTypeSelectListener = (TrashTypeSelectListener) activity;
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_add_trash_point, null);
        view.findViewById(R.id.land_dumping).setOnClickListener(this);
        view.findViewById(R.id.water_dumping).setOnClickListener(this);
        view.findViewById(R.id.air_dumping).setOnClickListener(this);

        //setting marker count
        TextView trashMarkerCount = (TextView) view.findViewById(R.id.trash_marker_count);
        SharedPreferences preferences = activity.getPreferences(Context.MODE_PRIVATE);
        String txt = getString(R.string.trash_markers_left) + " " + preferences.getInt(getString(R.string.pref_count), -1);
        trashMarkerCount.setText(txt);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.title_dialog_add_trash_point)
                .setView(view)
                .create();
    }

    @Override
    public void onClick(View view)
    {
        trashTypeSelectListener.onTrashTypeSelected((String) view.getTag());
        dismiss();
    }

    public interface TrashTypeSelectListener
    {
        void onTrashTypeSelected(String tag);
    }
}
