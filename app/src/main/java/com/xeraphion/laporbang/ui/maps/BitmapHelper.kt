package com.xeraphion.laporbang.ui.maps


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap

object BitmapHelper {
    /**
     * Converts a vector drawable resource to a Bitmap.
     *
     * @param context The context to access resources.
     * @param vectorResId The resource ID of the vector drawable.
     * @param tintColor The color to tint the drawable (optional).
     * @return A Bitmap representation of the vector drawable.
     */
    fun vectorToBitmap(context: Context, vectorResId: Int, tintColor: Int? = null): Bitmap {
        val drawable: Drawable = ContextCompat.getDrawable(context, vectorResId)
            ?: throw IllegalArgumentException("Resource not found: $vectorResId")

        tintColor?.let {
            drawable.setTint(it)
        }

        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}