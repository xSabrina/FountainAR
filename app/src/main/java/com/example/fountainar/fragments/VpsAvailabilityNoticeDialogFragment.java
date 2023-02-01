package com.example.fountainar.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.fountainar.R;

/**
 * A DialogFragment for the VPS availability Notice Dialog Box.
 */
public class VpsAvailabilityNoticeDialogFragment extends DialogFragment {

    NoticeDialogListener noticeDialogListener;

    public static VpsAvailabilityNoticeDialogFragment createDialog() {
        return new VpsAvailabilityNoticeDialogFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        try {
            noticeDialogListener = (NoticeDialogListener) context;
        } catch (ClassCastException e) {
            throw new AssertionError("Must implement NoticeDialogListener", e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        noticeDialogListener = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                R.style.AlertDialogCustom);
        builder
                .setTitle(R.string.vps_unavailable_title)
                .setMessage(R.string.vps_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(
                        R.string.continue_button,
                        (dialog, id) -> noticeDialogListener.onDialogContinueClick(
                                VpsAvailabilityNoticeDialogFragment.this));
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Listener for a VPS availability notice response.
     */
    public interface NoticeDialogListener {

        /**
         * Invoked when the user accepts sharing experience.
         */
        void onDialogContinueClick(DialogFragment dialog);
    }
}