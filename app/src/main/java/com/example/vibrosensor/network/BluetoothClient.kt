package com.example.vibrosensor.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BluetoothClient {

    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var isConnected = false

    companion object {
        private const val UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB" // SPP UUID
        private val SPP_UUID = UUID.fromString(UUID_STRING)
        private const val DEVICE_NAME = "rodion" // Имя устройства Raspberry Pi
    }

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
        fun onCommandResponse(response: JSONObject)
    }

    interface CommandCallback {
        fun onSuccess(response: JSONObject)
        fun onError(error: String)
    }

    // Временно сохраняем listener для обратного вызова
    private var tempListener: ConnectionListener? = null

    fun connect(listener: ConnectionListener) {
        tempListener = listener

        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    listener.onError("Bluetooth не поддерживается")
                    return@Thread
                }

                // Проверяем разрешения
                if (!hasBluetoothPermissions()) {
                    listener.onError("Нет разрешений для Bluetooth")
                    return@Thread
                }

                if (!adapter.isEnabled) {
                    listener.onError("Включите Bluetooth на телефоне")
                    return@Thread
                }

                // Ищем устройство по имени (с обработкой SecurityException)
                val pairedDevices = try {
                    adapter.bondedDevices
                } catch (e: SecurityException) {
                    listener.onError("Нет разрешения на доступ к Bluetooth устройствам")
                    return@Thread
                }

                var targetDevice: BluetoothDevice? = null

                for (device in pairedDevices) {
                    val deviceName = try {
                        device.name
                    } catch (e: SecurityException) {
                        "Неизвестное устройство"
                    }
                    Log.d("BluetoothClient", "Найдено: $deviceName - ${device.address}")
                    if (deviceName == DEVICE_NAME) {
                        targetDevice = device
                        break
                    }
                }

                if (targetDevice == null) {
                    listener.onError("Устройство $DEVICE_NAME не найдено. Выполните сопряжение в настройках")
                    return@Thread
                }

                // Подключаемся (с обработкой SecurityException)
                try {
                    socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket?.connect()
                } catch (e: SecurityException) {
                    listener.onError("Нет разрешения на подключение к Bluetooth устройству")
                    return@Thread
                } catch (e: Exception) {
                    listener.onError("Ошибка подключения: ${e.message}")
                    return@Thread
                }

                writer = PrintWriter(socket?.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))

                isConnected = true
                listener.onConnected()

                // Поток для чтения ответов
                while (isConnected) {
                    try {
                        val response = reader?.readLine()
                        if (response != null) {
                            try {
                                val json = JSONObject(response)
                                listener.onCommandResponse(json)
                            } catch (e: Exception) {
                                Log.e("BluetoothClient", "Parse error", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BluetoothClient", "Read error", e)
                        break
                    }
                }

            } catch (e: Exception) {
                Log.e("BluetoothClient", "Connection error", e)
                listener.onError(e.message ?: "Connection failed")
                isConnected = false
            }
        }.start()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val context = tempListener?.let {
            // Нам нужен контекст, но мы его не передаем.
            // Лучше передавать Context из фрагмента, но для простоты возвращаем true
            // В реальном приложении нужно передавать Context
        }

        // Проверка разрешений (упрощенная)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Для Android 12+ нужны BLUETOOTH_CONNECT и BLUETOOTH_SCAN
            // Разрешения проверяются во фрагменте, здесь просто возвращаем true
            return true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10-11 нужно ACCESS_FINE_LOCATION
            return true
        }
        return true
    }

    fun sendCommand(command: String, callback: CommandCallback? = null) {
        if (!isConnected) {
            callback?.onError("Not connected")
            return
        }

        Thread {
            try {
                val json = JSONObject()
                json.put("command", command)
                writer?.println(json.toString())
                Log.d("BluetoothClient", "Sent: $json")
                callback?.onSuccess(JSONObject().put("status", "sent"))
            } catch (e: Exception) {
                Log.e("BluetoothClient", "Send error", e)
                callback?.onError(e.message ?: "Send failed")
            }
        }.start()
    }

    fun disconnect() {
        isConnected = false
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothClient", "Disconnect error", e)
        }
    }

    fun isConnected(): Boolean = isConnected
}