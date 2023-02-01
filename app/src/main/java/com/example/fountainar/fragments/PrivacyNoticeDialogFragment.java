package com.example.fountainar.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.fountainar.R;

/**
 * A DialogFragment for the Privacy Notice Dialog Box.
 */
public class PrivacyNoticeDialogFragment extends DialogFragment {

    NoticeDialogListener noticeDialogListener;

    public static PrivacyNoticeDialogFragment createDialog() {
        return new PrivacyNoticeDialogFragment();
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
                .setTitle(R.string.share_experience_title)
                .setMessage(R.string.share_experience_message)
                .setPositiveButton(
                        R.string.agree_to_share,
                        (dialog, id) ->
                                noticeDialogListener
                                        .onDialogPositiveClick(PrivacyNoticeDialogFragment.this))
                .setNegativeButton(
                        R.string.learn_more,
                        (dialog, id) -> {
                            Intent browserIntent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(getString(R.string.learn_more_url)));
                            requireActivity().startActivity(browserIntent);
                        });
        return builder.create();
    }

    /**
     * Listener for a privacy notice response.
     */
    public interface NoticeDialogListener {

        /**
         * Invoked when the user accepts sharing experience.
         */
        void onDialogPositiveClick(DialogFragment dialog);
    }
}