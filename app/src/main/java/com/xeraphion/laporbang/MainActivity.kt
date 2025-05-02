package com.xeraphion.laporbang

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.xeraphion.laporbang.databinding.ActivityMainBinding
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        enableEdgeToEdge()
//        setStatusBarColor(R.color.primary)

        // Setup Window Insets
//        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, -40)
//            insets
//        }

        // Setup Navigation
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Connect BottomNavigationView with NavController
        binding.navView.setupWithNavController(navController)



        // Optional: Jika ingin menangani navigasi secara manual
        binding.navView.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_location -> navController.navigate(R.id.nav_location)
                R.id.nav_home -> navController.navigate(R.id.nav_home)
                R.id.nav_camera -> navController.navigate(R.id.nav_camera)
                R.id.nav_account -> navController.navigate(R.id.nav_account)

//                R.id.nav_report -> navController.navigate(R.id.nav_report)
                else -> false
            }
            true
        }
    }

    private fun setStatusBarColor(color: Int) {
        window.statusBarColor = ContextCompat.getColor(this, color)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }
}