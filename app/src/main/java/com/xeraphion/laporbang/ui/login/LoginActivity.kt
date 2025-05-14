package com.xeraphion.laporbang.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.xeraphion.laporbang.MainActivity
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.databinding.ActivityLoginBinding
import com.xeraphion.laporbang.ui.register.RegisterActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private lateinit var userPreference: UserPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreference = UserPreference(this)

        val factory = LoginViewModelFactory(LoginRepository())
        viewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]

        lifecycleScope.launch {
            val token = userPreference.getToken()
            if (!token.isNullOrEmpty()) {
                validateToken(token)
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmailLogin.text.toString()
            val password = binding.etPasswordLogin.text.toString()

            if (!validateEmail(email)) {
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "Password harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                loginUser(email, password)
            }
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.etEmailLogin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validateEmail(s.toString())
            }
        })
    }

    private suspend fun validateToken(token: String) {
        try {
            val response = viewModel.validateToken(token)
            if (response.isSuccessful) {
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } else {
                userPreference.clearToken()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateEmail(email: String): Boolean {
        return if (email.isEmpty()) {
            binding.textInputEmailLogin.error = "Email tidak boleh kosong!"
            false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmailLogin.error = "Format email tidak valid!"
            false
        } else {
            binding.textInputEmailLogin.error = null
            true
        }
    }

    private suspend fun loginUser(email: String, password: String) {
        try {
            val response = viewModel.login(email, password)
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!.token
                val id = response.body()!!.user?.id
                val role = response.body()!!.user?.role
                userPreference.saveToken(token!!)
                userPreference.saveUserId(id!!)
                userPreference.saveIsAdmin(role!!)
                Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    JSONObject(errorBody ?: "").optString("error", response.message())
                } catch (e: Exception) {
                    response.message()
                }
                Toast.makeText(this, "Login gagal: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
