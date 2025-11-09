package com.project.chatapplication

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.project.chatapplication.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.registerTextView.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        // Make login logo clickable to let user choose an app logo image (stored in app files)
        val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveLogoToFiles(it) }
        }

        binding.loginLogoImage.setOnClickListener {
            pickLogo.launch("image/*")
        }
    }

    private fun saveLogoToFiles(uri: Uri) {
        try {
            // copy selected image to internal files (app-specific) as "app_logo.png"
            val input = contentResolver.openInputStream(uri) ?: return
            val outFile = java.io.File(filesDir, "app_logo.png")
            input.use { inp ->
                outFile.outputStream().use { out ->
                    inp.copyTo(out)
                }
            }

            // update UI image
            binding.loginLogoImage.setImageURI(android.net.Uri.fromFile(outFile))

            // persist path in prefs for later use
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("app_logo_path", outFile.absolutePath).apply()

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save logo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}