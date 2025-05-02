package com.xeraphion.laporbang.helper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.xeraphion.laporbang.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAXIMAL_SIZE = 1000000
private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
private val timeStamp: String = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())

fun getImageUri(context: Context): Uri {
    var uri: Uri? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$timeStamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MyCamera/")
        }
        uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }
    return uri ?: getImageUriForPreQ(context)
}

private fun getImageUriForPreQ(context: Context): Uri {
    val filesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val imageFile = File(filesDir, "/MyCamera/$timeStamp.jpg")
    if (imageFile.parentFile?.exists() == false) imageFile.parentFile?.mkdir()
    return FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        imageFile
    )
}

fun createCustomTempFile(context: Context): File {
    val filesDir = context.externalCacheDir
    return File.createTempFile(timeStamp, ".jpg", filesDir)
}

fun uriToFile(imageUri: Uri, context: Context): File {
    val myFile = createCustomTempFile(context)
    val inputStream = context.contentResolver.openInputStream(imageUri) as InputStream
    val outputStream = FileOutputStream(myFile)
    val buffer = ByteArray(1024)
    var length: Int
    while (inputStream.read(buffer).also { length = it } > 0) outputStream.write(buffer, 0, length)
    outputStream.close()
    inputStream.close()
    return myFile
}

fun File.reduceFileImage(): File {
    val file = this
    val bitmap = BitmapFactory.decodeFile(file.path).getRotatedBitmap(file)
    var compressQuality = 100
    var streamLength: Int
    do {
        val bmpStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, bmpStream)
        val bmpPicByteArray = bmpStream.toByteArray()
        streamLength = bmpPicByteArray.size
        compressQuality -= 5
    } while (streamLength > MAXIMAL_SIZE)
    bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, FileOutputStream(file))
    return file
}

fun Bitmap.getRotatedBitmap(file: File): Bitmap {
    val orientation = ExifInterface(file).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
    )
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(this, 90F)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(this, 180F)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(this, 270F)
        ExifInterface.ORIENTATION_NORMAL -> this
        else -> this
    }
}

fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, matrix, true
    )
}


// Draw bounding boxes and labels on a bitmap (like StaticOverlayView)
fun drawDetectionsOnBitmap(
    baseBitmap: Bitmap,
    detections: List<org.tensorflow.lite.task.vision.detector.Detection>,
    frameWidth: Int,
    frameHeight: Int,
): Bitmap {
    val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(resultBitmap)

    val boxPaint = Paint().apply {
        color = android.graphics.Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
    }
    val textPaint = Paint().apply {
        color = android.graphics.Color.CYAN
        textSize = 16.0f
        style = Paint.Style.FILL
    }

    val imageAspectRatio = frameWidth.toFloat() / frameHeight
    val viewAspectRatio = baseBitmap.width.toFloat() / baseBitmap.height

    val scaleX: Float
    val scaleY: Float
    val dx: Float
    val dy: Float

    if (imageAspectRatio > viewAspectRatio) {
        scaleX = baseBitmap.width.toFloat() / frameWidth
        scaleY = scaleX
        dx = 0f
        dy = (baseBitmap.height - frameHeight * scaleY)
    } else {
        scaleY = baseBitmap.height.toFloat() / frameHeight
        scaleX = scaleY
        dx = (baseBitmap.width - frameWidth * scaleX)
        dy = 0f
    }

    for (result in detections) {
        val boundingBox = result.boundingBox
        val left = boundingBox.left * scaleX * 1.45f + dx
        val top = boundingBox.top * scaleY * 1.45f + dy
        val right = boundingBox.right * scaleX * 1.45f + dx
        val bottom = boundingBox.bottom * scaleY * 1.6f + dy

        canvas.drawRect(left, top, right, bottom, boxPaint)
        val label = "${result.categories[0].label} ${"%.2f".format(result.categories[0].score)}"
        canvas.drawText(label, left + 4, top - 4, textPaint)
    }

    return resultBitmap
}

// Save bitmap to file (JPG)
fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
    val file = File(context.cacheDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return file
}

fun formatDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date = parser.parse(dateString)
        if (date != null) formatter.format(date) else "-"
    } catch (e: Exception) {
        "-"
    }
}