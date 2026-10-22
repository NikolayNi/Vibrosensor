package com.example.vibrosensor.ui.match

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.vibrosensor.R
import com.example.vibrosensor.data.Athlete
import com.example.vibrosensor.network.WebSocketClient
import com.example.vibrosensor.ui.settings.SettingsFragment
import org.json.JSONObject
import android.util.Log

class MatchFragment : Fragment() {

    private var redAthlete: Athlete? = null
    private var blueAthlete: Athlete? = null

    private var webSocketClient: WebSocketClient? = null
    private var isConnected = false
    private var isConnecting = false
    private var isReconnecting = false
    private var isAutoConnecting = false
    private var clientCount = 0

    private var isVibrating = false
    private val handler = Handler(Looper.getMainLooper())
    private var vibrationRunnable: Runnable? = null

    private lateinit var vibrator: Vibrator

    // UI элементы
    private lateinit var btnConnect: Button
    private lateinit var shortVibeButton: Button
    private lateinit var longVibeButton: Button
    private lateinit var holdVibeButton: Button
    private lateinit var connectionLed: ImageView
    private lateinit var connectionText: TextView
    private lateinit var deviceLed: ImageView
    private lateinit var deviceText: TextView
    private lateinit var lastCommand: TextView
    private lateinit var redName: TextView
    private lateinit var blueName: TextView
    private lateinit var redStatus: TextView
    private lateinit var blueStatus: TextView
    private lateinit var redPanel: View
    private lateinit var bluePanel: View
    private lateinit var redEditArea: View
    private lateinit var blueEditArea: View
    private lateinit var menuButton: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_match, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vibrator = requireActivity().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        btnConnect = view.findViewById(R.id.btnConnect)
        shortVibeButton = view.findViewById(R.id.shortVibeButton)
        longVibeButton = view.findViewById(R.id.longVibeButton)
        holdVibeButton = view.findViewById(R.id.holdVibeButton)
        connectionLed = view.findViewById(R.id.connectionLed)
        connectionText = view.findViewById(R.id.connectionText)
        deviceLed = view.findViewById(R.id.deviceLed)
        deviceText = view.findViewById(R.id.deviceText)
        lastCommand = view.findViewById(R.id.lastCommand)
        redName = view.findViewById(R.id.redName)
        blueName = view.findViewById(R.id.blueName)
        redStatus = view.findViewById(R.id.redStatus)
        blueStatus = view.findViewById(R.id.blueStatus)
        redPanel = view.findViewById(R.id.redPanel)
        bluePanel = view.findViewById(R.id.bluePanel)
        redEditArea = view.findViewById(R.id.redEditArea)
        blueEditArea = view.findViewById(R.id.blueEditArea)
        menuButton = view.findViewById(R.id.menuButton)

        loadTestAthletes()
        setupClickListeners()
        updateConnectionStatus(false)
        updateDeviceStatus()

        parentFragmentManager.setFragmentResultListener("settings_updated", this) { _, _ ->
            if (!isConnected && !isConnecting) {
                autoConnect()
            }
        }

        handler.postDelayed({
            if (!isConnected && !isConnecting) {
                autoConnect()
            }
        }, 500)
    }

    private fun getSavedIP(): String {
        val sharedPref = requireActivity().getSharedPreferences("vibrosensor_settings", Context.MODE_PRIVATE)
        return sharedPref.getString("rpi_ip", "10.3.141.1") ?: "10.3.141.1"
    }

    private fun getSavedPort(): Int {
        val sharedPref = requireActivity().getSharedPreferences("vibrosensor_settings", Context.MODE_PRIVATE)
        return sharedPref.getInt("rpi_port", 8765)
    }

    private fun saveAthleteNames(redNameStr: String, blueNameStr: String) {
        val sharedPref = requireActivity().getSharedPreferences("rpi_settings", 0)
        with(sharedPref.edit()) {
            putString("red_name", redNameStr)
            putString("blue_name", blueNameStr)
            apply()
        }
    }

    private fun showEditNameDialog(color: String, currentName: String) {
        val input = android.widget.EditText(requireContext())
        input.setText(currentName)
        input.hint = "Введите имя спортсмена"

        AlertDialog.Builder(requireContext())
            .setTitle("Редактировать имя (${if (color == "red") "Красный угол" else "Синий угол"})")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString().trim().uppercase()
                if (newName.isNotEmpty()) {
                    if (color == "red") {
                        redName.text = newName
                    } else {
                        blueName.text = newName
                    }
                    saveAthleteNames(redName.text.toString(), blueName.text.toString())
                    Toast.makeText(requireContext(), "Имя сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun autoConnect() {
        if (isConnected || isConnecting || isAutoConnecting) return
        isAutoConnecting = true

        val ip = getSavedIP()
        val port = getSavedPort()

        if (ip.isNotEmpty()) {
            connectionText.text = "Автоподключение..."
            connectToRPi(ip, port, silent = true)
        }
        isAutoConnecting = false
    }

    private fun vibratePhone() {
        val vibrationEnabled = requireActivity().getSharedPreferences("vibrosensor_settings", Context.MODE_PRIVATE)
            .getBoolean("vibration", true)

        if (vibrationEnabled) {
            try {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.VIBRATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    vibrator.vibrate(50)
                }
            } catch (e: Exception) {
                Log.e("MatchFragment", "Vibration error", e)
            }
        }
    }

    private fun startContinuousVibration() {
        if (isVibrating) return
        if (!isConnected) return

        isVibrating = true
        sendVibrationCommand("VIBRATE_START", "ВИБРАЦИЯ")

        vibrationRunnable = object : Runnable {
            override fun run() {
                if (isVibrating) {
                    sendVibrationCommand("VIBRATE_CONTINUE", "ВИБРАЦИЯ")
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(vibrationRunnable!!)
    }

    private fun stopContinuousVibration() {
        if (!isVibrating) return
        isVibrating = false
        vibrationRunnable?.let { handler.removeCallbacks(it) }
        sendVibrationCommand("VIBRATE_STOP", "ВИБРАЦИЯ ОСТАНОВЛЕНА")
    }

    private fun sendVibrationCommand(command: String, displayName: String) {
        if (!isConnected) return
        webSocketClient?.sendCommand(command) { _ ->
            requireActivity().runOnUiThread {
                lastCommand.text = "Команда: $displayName"
                flashPanel(redPanel)
                flashPanel(bluePanel)
            }
        }
    }

    private fun updateDeviceStatus() {
        val isDeviceConnected = clientCount >= 2
        if (isDeviceConnected) {
            deviceLed.setImageResource(R.drawable.ic_led_green)
            deviceText.text = "Подключено"
        } else {
            deviceLed.setImageResource(R.drawable.ic_led_gray)
            deviceText.text = "Не подключено"
        }
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            if (isConnected) {
                Toast.makeText(requireContext(), "Уже подключено", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ip = getSavedIP()
            val port = getSavedPort()
            connectionText.text = "Подключение..."
            connectToRPi(ip, port, silent = false)
        }

        shortVibeButton.setOnClickListener {
            if (!isConnected) return@setOnClickListener
            vibratePhone()
            sendCommand("VIBRATE_SHORT", "Короткая вибрация")
        }

        longVibeButton.setOnClickListener {
            if (!isConnected) return@setOnClickListener
            vibratePhone()
            sendCommand("VIBRATE_LONG", "Длинная вибрация")
        }

        holdVibeButton.setOnLongClickListener {
            if (!isConnected) return@setOnLongClickListener false
            startContinuousVibration()
            true
        }

        holdVibeButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    stopContinuousVibration()
                }
            }
            false
        }

        redEditArea.setOnClickListener {
            showEditNameDialog("red", redName.text.toString())
        }

        blueEditArea.setOnClickListener {
            showEditNameDialog("blue", blueName.text.toString())
        }

        menuButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.bottom_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_settings -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, SettingsFragment())
                            .addToBackStack(null)
                            .commit()
                        true
                    }
                    R.id.action_about -> {
                        Toast.makeText(requireContext(), "VibroSensor v1.0\nДля управления вибрацией", Toast.LENGTH_LONG).show()
                        true
                    }
                    R.id.action_disconnect -> {
                        webSocketClient?.close()
                        updateConnectionStatus(false)
                        clientCount = 0
                        updateDeviceStatus()
                        connectionText.text = "Нет подключения"
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun connectToRPi(ip: String, port: Int, silent: Boolean = false) {
        if (isConnecting) return
        isConnecting = true
        updateConnectionStatus(false)
        clientCount = 0
        updateDeviceStatus()

        connectionText.text = if (silent) "Автоподключение..." else "Подключение..."

        val serverUrl = "ws://$ip:$port"
        webSocketClient = WebSocketClient(serverUrl)

        webSocketClient?.setListener(object : WebSocketClient.ConnectionListener {
            override fun onConnected() {
                requireActivity().runOnUiThread {
                    isConnecting = false
                    updateConnectionStatus(true)
                    connectionText.text = "Подключено"
                    if (!silent) {
                        Toast.makeText(requireContext(), "Подключено!", Toast.LENGTH_SHORT).show()
                    }
                    sendCommand("GET_CLIENTS_COUNT", "Запрос клиентов")
                }
            }

            override fun onDisconnected() {
                requireActivity().runOnUiThread {
                    isConnecting = false
                    updateConnectionStatus(false)
                    clientCount = 0
                    updateDeviceStatus()
                    stopContinuousVibration()
                    connectionText.text = "Нет подключения"

                    if (!isReconnecting) {
                        isReconnecting = true
                        handler.postDelayed({
                            isReconnecting = false
                            if (!isConnected && !isConnecting) {
                                autoConnect()
                            }
                        }, 5000)
                    }
                }
            }

            override fun onError(message: String) {
                requireActivity().runOnUiThread {
                    isConnecting = false
                    updateConnectionStatus(false)
                    connectionText.text = "Нет подключения"
                    if (!silent && message != "Попытка переподключения...") {
                        Toast.makeText(requireContext(), "Ошибка: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onMessage(response: JSONObject) {
                Log.d("WebSocket", "Response: $response")
                val clients = response.optInt("clients", -1)
                if (clients >= 0) {
                    requireActivity().runOnUiThread {
                        clientCount = clients
                        updateDeviceStatus()
                    }
                }
            }
        })

        webSocketClient?.connect()
    }

    private fun sendCommand(command: String, displayName: String) {
        if (!isConnected) return

        webSocketClient?.sendCommand(command) { _ ->
            requireActivity().runOnUiThread {
                lastCommand.text = "Команда: $displayName"
                flashPanel(redPanel)
                flashPanel(bluePanel)
                vibratePhone()
            }
        }
    }

    private fun loadTestAthletes() {
        val sharedPref = requireActivity().getSharedPreferences("rpi_settings", 0)
        val savedRedName = sharedPref.getString("red_name", "ИВАНОВ") ?: "ИВАНОВ"
        val savedBlueName = sharedPref.getString("blue_name", "ПЕТРОВ") ?: "ПЕТРОВ"

        redName.text = savedRedName
        blueName.text = savedBlueName

        redAthlete = Athlete(1, savedRedName, "до 73 кг", "red", "AA:BB:CC:DD:EE:01")
        blueAthlete = Athlete(2, savedBlueName, "до 73 кг", "blue", "AA:BB:CC:DD:EE:02")

        redStatus.text = "Активен"
        blueStatus.text = "Активен"
    }

    private fun flashPanel(panel: View) {
        panel.alpha = 0.5f
        view?.postDelayed({ panel.alpha = 1.0f }, 150)
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        if (connected) {
            connectionLed.setImageResource(R.drawable.ic_led_green)
            btnConnect.isEnabled = false
            btnConnect.alpha = 0.5f
            shortVibeButton.isEnabled = true
            longVibeButton.isEnabled = true
            holdVibeButton.isEnabled = true
            shortVibeButton.alpha = 1.0f
            longVibeButton.alpha = 1.0f
            holdVibeButton.alpha = 1.0f
        } else {
            connectionLed.setImageResource(R.drawable.ic_led_gray)
            btnConnect.isEnabled = true
            btnConnect.alpha = 1.0f
            shortVibeButton.isEnabled = false
            longVibeButton.isEnabled = false
            holdVibeButton.isEnabled = false
            shortVibeButton.alpha = 0.5f
            longVibeButton.alpha = 0.5f
            holdVibeButton.alpha = 0.5f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousVibration()
        webSocketClient?.close()
    }
}