package jg.intrduction.scannysecondchance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import jg.intrduction.scannysecondchance.databinding.ActivityImageBinding
import jg.intrduction.scannysecondchance.models.Detection
import jg.intrduction.scannysecondchance.models.ModelConfiguration

import java.io.IOException
import java.lang.Float
import java.util.concurrent.Executors
import kotlin.math.min


class ImageActivity: AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private lateinit var tfDetector: TFDetector



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val context = this
        openGallery()

        binding.cameraImageButton.setOnClickListener(object: View.OnClickListener {
            override fun onClick(view: View?) {
                startActivity(Intent(context, MainActivity::class.java))
                finish()
            }
        })

        binding.galleryImageButton.setOnClickListener(object: View.OnClickListener {
            override fun onClick(view: View?) {
                openGallery()
            }
        })

        tfDetector = TFDetector(assets, ModelConfiguration(640, 480))

    }



    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == GALLERY_REQUEST_CODE) {
            val imageUri = data?.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                binding.imageFromGallery.setImageBitmap(bitmap)
                detectObjects(bitmap)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    private fun detectObjects(bitmap: Bitmap) {
        val detections = tfDetector.detect(bitmap, 0)
        processDetections(detections, bitmap)
    }

    private fun processDetections(detections: List<Detection>, bitmap: Bitmap) {
        binding.overlayImageView.post {
            val canvas = binding.overlayImageView.lockCanvas()
            if (canvas != null) {
                // Clear the canvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // Draw the image on the canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                // Draw bounding boxes for each detection
                for (detection in detections) {
                    val bbox = detection.bbox

                    // Define the bounding box coordinates
                    val left = bbox.left * bitmap.width
                    val top = bbox.top * canvas.height + canvas.height / 3
                    val right = bbox.right * bitmap.width
                    val bottom = bbox.bottom * canvas.height + canvas.height / 3

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
                binding.overlayImageView.unlockCanvasAndPost(canvas)
            }
        }
    }






    companion object {
        private const val GALLERY_REQUEST_CODE = 1001
    }
}