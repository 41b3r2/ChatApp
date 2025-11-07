package com.project.chatapplication

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.project.chatapplication.databinding.ProgressDialogBinding

class ModernProgressDialog(context: Context) {

    private val binding: ProgressDialogBinding = ProgressDialogBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog = AlertDialog.Builder(context)
        .setView(binding.root)
        .setCancelable(false)
        .create()

    fun create(): ModernProgressDialog {
        return this
    }

    fun setMessage(message: String): ModernProgressDialog {
        binding.messageTextView.text = message
        return this
    }

    fun setProgress(progress: Int): ModernProgressDialog {
        binding.progressBar.progress = progress
        return this
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}