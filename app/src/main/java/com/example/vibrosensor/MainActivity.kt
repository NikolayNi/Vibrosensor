package com.example.vibrosensor

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.commit
import com.example.vibrosensor.R
import com.example.vibrosensor.ui.match.MatchFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, MatchFragment())
            }
        }
    }

    private fun applySavedTheme() {
        val sharedPref = getSharedPreferences("vibrosensor_settings", Context.MODE_PRIVATE)
        val darkThemeEnabled = sharedPref.getBoolean("dark_theme", false)

        if (darkThemeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}