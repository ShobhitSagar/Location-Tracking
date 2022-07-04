package com.shobhitsagar.locationtracker.utils

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.shobhitsagar.locationtracker.R

class LoadingDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val view = requireActivity().layoutInflater.inflate(R.layout.loading_dialog_layout, null)

        return activity?.let {
            builder.setView(view).setCancelable(false)
//                .setTitle("Please wait")

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    companion object {
        const val LOADING_DIALOG = "Loading dialog"
    }
}