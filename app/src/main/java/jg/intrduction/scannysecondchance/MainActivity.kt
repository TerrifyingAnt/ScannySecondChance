package jg.intrduction.scannysecondchance

import android.annotation.SuppressLint
import android.content.AsyncQueryHandler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.GestureDetector
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageAnalysisConfig
import androidx.camera.core.impl.PreviewConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jg.intrduction.scannysecondchance.databinding.ActivityMainBinding
import jg.intrduction.scannysecondchance.models.Detection
import jg.intrduction.scannysecondchance.models.ModelConfiguration
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var textureView: PreviewView
    private lateinit var tfDetector: TFDetector

    private val executor = Executors.newSingleThreadExecutor()
    private val requiredPermissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private var isProcessingFrame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textureView = binding.textureView

        val context = this

        binding.galleryButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                startActivity(Intent(context, ImageActivity::class.java))
                finish()
            }
        })

        tfDetector = TFDetector(assets, ModelConfiguration(640, 480))

        if (allPermissionsGranted()) {
            textureView.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_PERMISSIONS)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().apply {
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                setTargetResolution(Size(640, 480))
                setTargetRotation(textureView.display.rotation)
                setCameraSelector(cameraSelector)
            }.build()

            preview.setSurfaceProvider(textureView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder().apply {
                setTargetResolution(Size(640, 480))
                setTargetRotation(textureView.display.rotation)
                setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            }.build()

            imageAnalysis.setAnalyzer(executor, { imageProxy ->
                if (!isProcessingFrame) {
                    runOnUiThread {
                        val bitmap = textureView.bitmap
                        //val rotatedBitmap = rotateBitmap(bitmap!!, 90)
                        if(bitmap != null) {
                            val detections = tfDetector.detect(bitmap, 0)
                            println(detections.size)
                            processDetections(detections)
                        }
                    }

                    isProcessingFrame = true
                }
                imageProxy.close()
                isProcessingFrame = false
            })

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }



    private fun processDetections(detections: List<Detection>) {
        binding.overlayView.post {
            val canvas = binding.overlayView.lockCanvas()
            if (canvas != null) {
                // Clear the canvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // Draw bounding boxes for each detection
                for (detection in detections) {
                    val bbox = detection.bbox


                    // Define the bounding box coordinates
                    val left = bbox.left * canvas.width * 0.75f + canvas.width / 8
                    val top = bbox.top * canvas.height
                    val right = bbox.right * canvas.width * 0.75f + canvas.width / 8
                    val bottom = bbox.bottom * canvas.height

                    // Define the paint for drawing the bounding box
                    val paint = Paint().apply {
                        color = Color.RED
                        style = Paint.Style.STROKE
                        strokeWidth = 10f
                    }

                    // Draw the bounding box rectangle
                    canvas.drawRect(left, top, right, bottom, paint)

                }

                // Unlock the canvas and refresh the overlay view
                binding.overlayView.unlockCanvasAndPost(canvas)
            }
        }
    }


    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                textureView.post { startCamera() }
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tfDetector.close()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}