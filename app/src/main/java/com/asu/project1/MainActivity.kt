package com.asu.project1

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import com.asu.project1.R
import com.asu.project1.databinding.ActivityMainBinding

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

typealias LumaListener = (luma: Double) -> Unit

interface SlowTaskListener {
    fun onTaskCompleted(result: String)
}

class MainActivity : AppCompatActivity() ,SlowTaskListener{
    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recordingPath: String? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor

    private var accelValuesY = mutableListOf(0f)
    private var accelValuesZ = mutableListOf(0f)
    private var accelValuesX = mutableListOf(0f)

    private val handler = Handler()
    private var isCapturingData = false
    private val captureDurationMillis = 5000
    private val cameracaptureduration = 5000
    private var captureStartTimeMillis = 0L
    private var isDataCollectionComplete = false

    private var respiratoryRate: Int? = null
    private var heartRate: Int? = null

    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        viewBinding.btnNextPage.isEnabled = false

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listeners for take photo and video capture buttons
//        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.btnCheckRespirationRate.setOnClickListener { if (!isCapturingData) {
            // Start capturing accelerometer data
            isCapturingData = true
            viewBinding.btnCheckRespirationRate.apply { text = "Reading Data ..."
                isEnabled = true}
            sensorManager.registerListener(
                sensorListener,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            startCapturingData()
        }
        else {
            // Stop capturing accelerometer data
            isCapturingData = false
            viewBinding.btnCheckRespirationRate.apply { text = getString(R.string.start_capture)
                isEnabled = true}
            sensorManager.unregisterListener(sensorListener)
            stopCapturingData()
        } }

        viewBinding.btnNextPage.setOnClickListener {
            val intent = Intent(this, SymptomActivity::class.java)
            intent.putExtra("heartRate", heartRate.toString())
            intent.putExtra("respRate", respiratoryRate.toString())
            println("heartRate at intent "+intent.data)
            startActivity(intent)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .setDurationLimit(45000)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {}
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                            recordingPath = convertMediaUriToPath(recordEvent.outputResults.outputUri);
                        val editText = findViewById<EditText>(R.id.tvHeartRate)
                        editText.setText("Processing HeartRate")

                        calculateHeartRate()
                        recording = null
                        return@start
                    }
                }
            }

    }
    override fun onTaskCompleted(result: String) {
        println("onTaskCompleted: $result")
        Toast.makeText(this, "Heart Rate: $result", Toast.LENGTH_SHORT).show()
        //if datamap has 2 entries, then only enable the symptoms button
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

//            val imageAnalyzer = ImageAnalysis.Builder()
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
//                }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val cam = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
                cam.cameraControl.enableTorch(true)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    fun convertMediaUriToPath(uri: Uri?): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri!!, proj, null, null, null)
        val column_index = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val path = cursor.getString(column_index)
        cursor.close()
        return path
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun calculateHeartRate() {
        val editText = findViewById<EditText>(R.id.tvHeartRate)
        editText.setText("Processing heart rate...")
        var asyncTask = SlowTask(recordingPath.toString(),this)

        asyncTask?.execute()
        asyncTask.setOnPostExecuteListener { result ->
            editText.setText("Heart Rate: "+result)
            heartRate = result.toInt()
        }
        viewBinding.videoCaptureButton.apply {
            text = getString(R.string.start_capture)
            isEnabled = true
        }


        viewBinding.btnNextPage.isEnabled = respiratoryRate != null && heartRate != null


    }

    private fun startCapturingData() {
        isCapturingData = true
        captureStartTimeMillis = System.currentTimeMillis() // Record the start time
        handler.post(captureDataRunnable) // Start capturing data immediately
    }

    //
    private fun stopCapturingData() {
        isCapturingData = false
        sensorManager.unregisterListener(sensorListener)
        handler.removeCallbacks(captureDataRunnable)

        viewBinding.btnCheckRespirationRate.apply { text = "Check Respiration Rate"
            isEnabled = true }
    }

    private val captureDataRunnable = object : Runnable {
        override fun run() {
            if (isCapturingData) {
                val currentTimeMillis = System.currentTimeMillis()
                val elapsedTimeMillis = currentTimeMillis - captureStartTimeMillis

                if (elapsedTimeMillis >= captureDurationMillis) {
                    isCapturingData = false
                    stopCapturingData()
                    respiratoryRate = callRespiratoryCalculator()
                    val editText = findViewById<EditText>(R.id.tvRespirationRate)
                    editText.setText("Respiration Rate: "+respiratoryRate.toString())

                    viewBinding.btnNextPage.isEnabled = respiratoryRate != null && heartRate != null

                } else {
                    // Continue capturing data until the desired duration is reached
                    handler.post(this) // Schedule the next capture immediately
                }
            }
        }

        //
        private fun callRespiratoryCalculator(): Int {
            val editText = findViewById<EditText>(R.id.tvRespirationRate)
            editText.setText("Processing Respiration Rate...")
            var previousValue = 0f
            var currentValue = 0f
            previousValue = 10f
            var k = 0
            for (i in 0 until accelValuesX.size) {
                currentValue = sqrt(
                    accelValuesZ[i].toDouble().pow(2.0) + accelValuesX[i].toDouble()
                        .pow(2.0) + accelValuesY[i].toDouble()
                        .pow(2.0)
                ).toFloat()
                if (abs(x = previousValue - currentValue) > 0.05) {
                    k++
                }
                previousValue = currentValue
            }
            val ret = (k / accelValuesX.size.toFloat())
            // clear the list
            accelValuesX.clear()
            accelValuesY.clear()
            accelValuesZ.clear()


            println("respiration rate: "+(ret * 30).toInt())

            return (ret * 30).toInt()
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event != null) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    println("x: $x, y: $y, z: $z")
                    accelValuesX.add(x)
                    accelValuesY.add(y)
                    accelValuesZ.add(z)
                    val currentTimeMillis = System.currentTimeMillis()
                    val elapsedTimeMillis = currentTimeMillis - captureStartTimeMillis

                    if (elapsedTimeMillis >= captureDurationMillis) {
                        // Data collection is complete, set the flag
                        isDataCollectionComplete = true
                    }
//                    if (accelValuesX.size > captureDurationSeconds) {
//                        accelValuesX.removeAt(0)
//                        accelValuesY.removeAt(0)
//                        accelValuesZ.removeAt(0)
//                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Do nothing here
        }
//
    }
}