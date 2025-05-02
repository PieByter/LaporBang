package com.xeraphion.laporbang

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.databinding.ActivitySplashScreenBinding
import com.xeraphion.laporbang.ui.login.LoginActivity
import kotlinx.coroutines.launch


@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding
    private lateinit var userPreference: UserPreference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userPreference = UserPreference.getInstance(this)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.splashLogo.startAnimation(fadeIn)
        binding.splashName.startAnimation(fadeIn)

        // Navigate to LoginActivity after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            checkTokenAndNavigate()
        }, 2000)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkTokenAndNavigate() {
        lifecycleScope.launch {
            val token = userPreference.getToken()
            if (token.isNullOrEmpty()) {
                // No token, navigate to LoginActivity
                navigateToLogin()
            } else {
                // Validate token
                try {
                    val response = ApiConfig.getApiService(token).validateToken()
                    if (response.isSuccessful) {
                        // Token is valid, navigate to MainActivity
                        navigateToMain()
                    } else {
                        // Token is invalid, clear it and navigate to LoginActivity
                        userPreference.clearToken()
                        navigateToLogin()
                    }
                } catch (_: Exception) {
                    // Handle API call failure (e.g., network error)
                    userPreference.clearToken()
                    navigateToLogin()
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this@SplashScreenActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this@SplashScreenActivity, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}