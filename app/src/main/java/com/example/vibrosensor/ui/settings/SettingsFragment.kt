package com.example.vibrosensor.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.vibrosensor.R

class SettingsFragment : Fragment() {

    private lateinit var sharedPref: SharedPreferences
    private lateinit var etRPiIP: EditText
    private lateinit var etPort: EditText
    private lateinit var vibrationSwitch: Switch
    private lateinit var themeSwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    private val DEFAULT_IP = "10.3.141.1"
    private val DEFAULT_PORT = 8765
    private val DEFAULT_VIBRATION = true
    private val DEFAULT_DARK_THEME = false

    // Временные переменные для хранения изменений до сохранения
    private var tempVibrationEnabled = DEFAULT_VIBRATION
    private var tempDarkThemeEnabled = DEFAULT_DARK_THEME

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPref = requireActivity().getSharedPreferences("vibrosensor_settings", Context.MODE_PRIVATE)

        etRPiIP = view.findViewById(R.id.etRPiIP)
        etPort = view.findViewById(R.id.etPort)
        vibrationSwitch = view.findViewById(R.id.vibrationSwitch)
        themeSwitch = view.findViewById(R.id.themeSwitch)
        saveButton = view.findViewById(R.id.saveButton)
        resetButton = view.findViewById(R.id.resetButton)

        loadSettings()

        // Только сохраняем временные значения, тему не меняем
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            tempVibrationEnabled = isChecked
        }

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            tempDarkThemeEnabled = isChecked
        }

        saveButton.setOnClickListener {
            saveSettings()
        }

        resetButton.setOnClickListener {
            showResetDialog()
        }
    }

    private fun applyTheme(darkTheme: Boolean) {
        if (darkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun loadSettings() {
        val savedIP = sharedPref.getString("rpi_ip", DEFAULT_IP)
        val savedPort = sharedPref.getInt("rpi_port", DEFAULT_PORT)
        val vibrationEnabled = sharedPref.getBoolean("vibration", DEFAULT_VIBRATION)
        val darkThemeEnabled = sharedPref.getBoolean("dark_theme", DEFAULT_DARK_THEME)

        etRPiIP.setText(savedIP)
        etPort.setText(savedPort.toString())

        vibrationSwitch.isChecked = vibrationEnabled
        themeSwitch.isChecked = darkThemeEnabled

        tempVibrationEnabled = vibrationEnabled
        tempDarkThemeEnabled = darkThemeEnabled
    }

    private fun saveSettings() {
        val ip = etRPiIP.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull()

        if (ip.isEmpty()) {
            Toast.makeText(requireContext(), "Введите IP адрес", Toast.LENGTH_SHORT).show()
            return
        }

        if (port == null || port <= 0 || port > 65535) {
            Toast.makeText(requireContext(), "Введите корректный порт (1-65535)", Toast.LENGTH_SHORT).show()
            return
        }

        // Сохраняем все настройки
        sharedPref.edit().apply {
            putString("rpi_ip", ip)
            putInt("rpi_port", port)
            putBoolean("vibration", tempVibrationEnabled)
            putBoolean("dark_theme", tempDarkThemeEnabled)
            apply()
        }

        // Применяем тему
        applyTheme(tempDarkThemeEnabled)

        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show()
        parentFragmentManager.setFragmentResult("settings_updated", Bundle())
    }

    private fun resetToDefault() {
        tempVibrationEnabled = DEFAULT_VIBRATION
        tempDarkThemeEnabled = DEFAULT_DARK_THEME

        etRPiIP.setText(DEFAULT_IP)
        etPort.setText(DEFAULT_PORT.toString())
        vibrationSwitch.isChecked = DEFAULT_VIBRATION
        themeSwitch.isChecked = DEFAULT_DARK_THEME

        // Сохраняем и применяем
        sharedPref.edit().apply {
            putString("rpi_ip", DEFAULT_IP)
            putInt("rpi_port", DEFAULT_PORT)
            putBoolean("vibration", DEFAULT_VIBRATION)
            putBoolean("dark_theme", DEFAULT_DARK_THEME)
            apply()
        }

        applyTheme(DEFAULT_DARK_THEME)

        Toast.makeText(requireContext(), "Настройки сброшены к заводским", Toast.LENGTH_SHORT).show()
        parentFragmentManager.setFragmentResult("settings_updated", Bundle())
    }

    private fun showResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Сброс настроек")
            .setMessage("Вы уверены, что хотите сбросить все настройки к значениям по умолчанию?\n\nIP: $DEFAULT_IP\nПорт: $DEFAULT_PORT\nВибрация: ${if (DEFAULT_VIBRATION) "Вкл" else "Выкл"}\nТёмная тема: ${if (DEFAULT_DARK_THEME) "Вкл" else "Выкл"}")
            .setPositiveButton("Сбросить") { _, _ ->
                resetToDefault()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}