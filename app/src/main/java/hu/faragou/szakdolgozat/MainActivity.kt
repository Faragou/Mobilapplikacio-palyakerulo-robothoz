package hu.faragou.szakdolgozat

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 2
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var device: BluetoothDevice
    private val hc05UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var connectButton: Button
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedTextView: TextView
    private lateinit var voiceButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var saveDataButton: Button
    private lateinit var switchFileButton: Button
    private val address = "98:D3:C1:FD:98:7A"
    private lateinit var dataTextView: TextView
    private lateinit var obsAvoid: Button

    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private var lastCommand: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        connectButton = findViewById(R.id.Connect)
        speedSeekBar = findViewById(R.id.SpeedBar)
        speedTextView = findViewById(R.id.Speed)
        voiceButton = findViewById(R.id.voiceButton)
        saveDataButton = findViewById(R.id.saveDataButton)
        switchFileButton = findViewById(R.id.switchFileButton)
        dataTextView = findViewById(R.id.dataTextView)
        obsAvoid = findViewById(R.id.Labyrinth)


        val upButton: FloatingActionButton = findViewById(R.id.Up)
        val downButton: FloatingActionButton = findViewById(R.id.Down)
        val leftButton: FloatingActionButton = findViewById(R.id.Left)
        val rightButton: FloatingActionButton = findViewById(R.id.Right)


        connectButton.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                connectToBluetooth()
            } else {
                disconnectFromBluetooth()
            }
        }

        upButton.setOnClickListener { sendCommand("U") }
        downButton.setOnClickListener { sendCommand("D") }
        leftButton.setOnClickListener { sendCommand("L") }
        rightButton.setOnClickListener { sendCommand("R") }
        obsAvoid.setOnClickListener { sendCommand("F") }

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedTextView.text = "Sebesseg: $progress"
                sendCommand("S$progress")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            setupVoiceRecognition()
        }

        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        saveDataButton.setOnClickListener {
            saveDataToFile(dataTextView.text.toString())
        }

        switchFileButton.setOnClickListener {
            switchToFile()
        }

        createNewFile()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                setupVoiceRecognition()
            } else {
                Toast.makeText(this, "Audio recording permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToBluetooth() {
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(address)
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show()
            return
        }

        for (device in pairedDevices) {
            if (device.address == address) {
                this.device = device
                break
            }
        }

        if (device != null) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        REQUEST_BLUETOOTH_PERMISSIONS
                    )
                    return
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(hc05UUID)
                bluetoothSocket?.connect()
                Toast.makeText(this, "Connected to HC-06", Toast.LENGTH_SHORT).show()
                connectButton.text = "Lecsatlakozás"
                readDataFromBluetooth()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show()
                bluetoothSocket = null
            }
        }
    }

    private fun disconnectFromBluetooth() {
        try {
            bluetoothSocket?.close()
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            connectButton.text = "Csatlakozás"
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Disconnection Failed", Toast.LENGTH_SHORT).show()
        } finally {
            bluetoothSocket = null
        }
    }

    private fun sendCommand(command: String) {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            try {
                lastCommand = command
                bluetoothSocket!!.outputStream.write(command.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error sending command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readDataFromBluetooth() {
        Thread {
            while (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                try {
                    val inputStream = bluetoothSocket!!.inputStream
                    val buffer = ByteArray(1024)
                    var bytes: Int

                    while (inputStream.read(buffer).also { bytes = it } > 0) {
                        val receivedMessage = String(buffer, 0, bytes)

                        runOnUiThread {
                            dataTextView.append(receivedMessage)
                            saveDataToCurrentFile(receivedMessage)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Error reading data", Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
        }.start()
    }

    private fun saveDataToFile(data: String) {
        val externalStorageState = Environment.getExternalStorageState()
        if (externalStorageState == Environment.MEDIA_MOUNTED) {
            val file = File(getExternalFilesDir(null), "ultrasound_data.txt")
            try {
                val fileOutputStream = FileOutputStream(file, true)
                fileOutputStream.write(data.toByteArray())
                fileOutputStream.close()
                Toast.makeText(this, "Data saved to file", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error saving data to file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "External storage not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNewFile() {
        val externalStorageState = Environment.getExternalStorageState()
        if (externalStorageState == Environment.MEDIA_MOUNTED) {
            val dir = getExternalFilesDir(null)
            val timestamp = System.currentTimeMillis()
            currentFile = File(dir, "ultrasound_data_$timestamp.txt")
            try {
                fileOutputStream = FileOutputStream(currentFile, true)
                Toast.makeText(this, "New file created", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error creating new file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "External storage not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDataToCurrentFile(data: String) {
        if (fileOutputStream != null) {
            try {
                fileOutputStream!!.write(data.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error writing data to file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchToFile() {

        if (fileOutputStream != null) {
            try {
                fileOutputStream!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error closing file", Toast.LENGTH_SHORT).show()
            }
        }

        createNewFile()
    }

    private fun setupVoiceRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Voice recognition error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (match in matches) {
                        when (match.toLowerCase(Locale.getDefault())) {
                            "forward" -> sendCommand("U")
                            "backward" -> sendCommand("D")
                            "left" -> sendCommand("L")
                            "right" -> sendCommand("R")
                            "stop" -> sendCommand("B")
                            "obsticle" -> sendCommand("F")
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command")
        speechRecognizer.startListening(intent)
    }
}
