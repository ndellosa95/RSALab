package edu.temple.rsalab;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * Created by nickdellosa on 2/8/17.
 */

public class EnterTitleFragment extends DialogFragment {

    private boolean isImport;


    public interface KeyGenerator {
        public void generateKey(String title);
        public void importKey(String key, String title);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        isImport = this.getArguments().getBoolean("isImport");
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.title_prompt1);

        final View v = getActivity().getLayoutInflater().inflate(R.layout.title_alert, null);
        builder.setView(v);
        builder.setPositiveButton("Generate", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // send title back to main activity
                if (isImport) {
                    ((KeyGenerator) getActivity()).importKey(
                            ((EditText)v.findViewById(R.id.importer)).getText().toString(),
                            ((EditText)v.findViewById(R.id.title)).getText().toString()
                    );
                } else {
                    ((KeyGenerator) getActivity()).generateKey(((EditText)v.findViewById(R.id.title)).getText().toString());
                }
                dismiss();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismiss();
            }
        });

        return builder.create();
    }
}
