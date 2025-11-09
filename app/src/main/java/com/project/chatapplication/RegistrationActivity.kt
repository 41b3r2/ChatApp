package com.project.chatapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.project.chatapplication.databinding.ActivityRegistrationBinding

class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var progressDialog: ModernProgressDialog
    private val TAG = "RegistrationActivity"
    private val DB_URL = "https://chatapp-9c53c-default-rtdb.firebaseio.com/"

    // image selection removed to keep registration minimal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        progressDialog = ModernProgressDialog(this).create()

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.registerButton.setOnClickListener {
            val fname = binding.fnameEditText.text.toString().trim()
            val mname = binding.mnameEditText.text.toString().trim()
            val lname = binding.lnameEditText.text.toString().trim()
            val username = binding.usernameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            val confirm = binding.confirmPasswordEditText.text.toString().trim()

            if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressDialog.setMessage("🔐 Creating Your Account...").show()

            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                val uid = task.result?.user?.uid ?: ""
                saveUser(uid, fname, mname, lname, username, email)
            }
        }
    }

    private fun saveUser(uid: String, fname: String, mname: String, lname: String, username: String, email: String) {
        progressDialog.setMessage("💾 Saving Your Profile...")

        val user = User(
            firstName = fname,
            middleName = if (mname.isBlank()) null else mname,
            lastName = lname,
            email = email,
            username = if (username.isBlank()) null else username,
            uid = uid,
            profileImageUrl = "",
            name = listOf(fname, mname, lname).filter { it.isNotBlank() }.joinToString(" ")
        )

        val ref = FirebaseDatabase.getInstance(DB_URL).getReference("/users/$uid")
        ref.setValue(user).addOnSuccessListener {
            progressDialog.dismiss()
            // After registration go back to login per requirement
            goToLogin()
        }.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "❌ Failed to save user to database: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun goToMainActivity() {
        // After registration we send the user back to the Login screen per requirement
        progressDialog.dismiss()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToLogin(){
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}