package jg.intrduction.scannysecondchance



import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import jg.intrduction.scannysecondchance.models.Detection
import jg.intrduction.scannysecondchance.models.ModelConfiguration
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFDetector (
    assets: AssetManager,
    private val configuration: ModelConfiguration,
) {

    private val tflite: Interpreter
    private val tensorImage = TensorImage(DataType.FLOAT32)
    private val boxesTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000, 4), DataType.FLOAT32)
    private val detectionsCountTensor = TensorBuffer.createFixedSize(intArrayOf(4), DataType.UINT8)
    private val labelsTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000), DataType.FLOAT32)
    private val scoresTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000), DataType.FLOAT32)
    private val outputs = mutableMapOf<Int, Any>(
        0 to boxesTensor.buffer, // 1000 values (4 float)
        1 to detectionsCountTensor.buffer, // 1 value (objects count)
        2 to labelsTensor.buffer, // 1000 values
        3 to scoresTensor.buffer, // 1000 values
    )

    init {
        val path = "sku-base-640-480-fp16.tflite"
        val tfliteModel = loadTFLiteModelFromAsset(assets, path) // here you should get tf file
        val tfliteOptions = Interpreter.Options()
        //tfliteOptions.addDelegate(FlexDelegate())
        //tfliteOptions.addDelegate(GpuDelegate())
        tflite = Interpreter(tfliteModel, tfliteOptions)
        tflite.allocateTensors()
    }


    // function for detect bboxes
    fun detect(image: Bitmap, imageRotation: Int): List<Detection> {
        for (buffer in outputs.values) {
            (buffer as ByteBuffer).rewind()
        }
        val paddedImage = getResizedBitmap(image, configuration.imgWidth, configuration.imgHeight)
        tensorImage.load(paddedImage)
        val imageProcessor = ImageProcessor.Builder()
            .add(NormalizeOp(0f, 255f))
            .build()
        val tensorImage = imageProcessor.process(tensorImage)
        tflite.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputs)

        return convert(
                image.width,
                image.height,
                configuration.imgWidth,
                configuration.imgHeight)
    }

    fun close() {
        tflite.close()
    }

    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        return resizedBitmap
    }

    @Throws(IOException::class)
    private fun loadTFLiteModelFromAsset(
        assetManager: AssetManager,
        modelPath: String
    ): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convert(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): List<Detection> {
        var detectionsCount = 0
        detectionsCountTensor.intArray.forEach { count ->
            detectionsCount += count
            if (count < 255)
                return@forEach
        }
        val boxesTensor = boxesTensor.floatArray
        val scoresTensor = scoresTensor.floatArray
        val detections = ArrayList<Detection>(detectionsCount)
        val srcRatio = 1f * srcWidth / srcHeight
        val dstRatio = 1f * dstWidth / dstHeight
        var ax = 1f
        var bx = 0f
        var ay = 1f
        var by = 0f
        if (dstRatio >= srcRatio) {
            val notScaledDstWidth = (srcWidth * dstRatio / srcRatio).toInt()
            ax = 1f * notScaledDstWidth / srcWidth
            bx = -ax * ((notScaledDstWidth - srcWidth) / 2) / notScaledDstWidth
        } else {
            val notScaledDstHeight = (srcHeight * srcRatio / dstRatio).toInt()
            ay = 1f * notScaledDstHeight / srcHeight
            by = -ay * ((notScaledDstHeight - srcHeight) / 2) / notScaledDstHeight
        }
        for (k in 0 until detectionsCount) {
            val det = Detection(
                RectF(
                    ax * boxesTensor[k * 4 + 0] + bx,
                    ay * boxesTensor[k * 4 + 1] + by,
                    ax * boxesTensor[k * 4 + 2] + bx,
                    ay * boxesTensor[k * 4 + 3] + by,
                ),
                scoresTensor[k],
            )
            detections.add(det)
        }
        println(detectionsCount)
        return detections
    }

}