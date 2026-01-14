package com.chorequest.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Image utilities for compression and encoding
 */
object ImageUtils {

    private const val MAX_WIDTH = 1024
    private const val MAX_HEIGHT = 1024
    private const val JPEG_QUALITY = 85 // 0-100, 85 is good balance of quality/size

    /**
     * Compress and convert image to Base64
     * @param context Application context
     * @param imageUri URI of the image
     * @param maxWidth Maximum width (default 1024)
     * @param maxHeight Maximum height (default 1024)
     * @return Base64 encoded string or null if error
     */
    fun compressAndEncodeImage(
        context: Context,
        imageUri: Uri,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            
            // Decode with inJustDecodeBounds to get dimensions without loading full image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            
            // Decode the actual image
            val inputStream2 = context.contentResolver.openInputStream(imageUri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2.close()
            
            if (bitmap == null) return null
            
            // Fix orientation
            bitmap = fixOrientation(context, imageUri, bitmap)
            
            // Further resize if needed
            bitmap = resizeBitmap(bitmap, maxWidth, maxHeight)
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val byteArray = outputStream.toByteArray()
            
            // Clean up
            bitmap.recycle()
            outputStream.close()
            
            // Convert to Base64
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error compressing image", e)
            null
        }
    }

    /**
     * Calculate sample size for image decoding
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (ratio > 1) {
            // Landscape
            targetWidth = maxWidth
            targetHeight = (maxWidth / ratio).toInt()
        } else {
            // Portrait or square
            targetHeight = maxHeight
            targetWidth = (maxHeight * ratio).toInt()
        }

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        
        // Recycle original if it's different
        if (resizedBitmap != bitmap) {
            bitmap.recycle()
        }
        
        return resizedBitmap
    }

    /**
     * Fix image orientation based on EXIF data
     */
    private fun fixOrientation(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            
            rotatedBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error fixing orientation", e)
            bitmap
        }
    }

    /**
     * Save bitmap to file
     */
    fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error saving bitmap to file", e)
            false
        }
    }

    /**
     * Get estimated size after compression
     * @return Size in KB
     */
    fun estimateCompressedSize(
        context: Context,
        imageUri: Uri,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT
    ): Long? {
        return try {
            val base64 = compressAndEncodeImage(context, imageUri, maxWidth, maxHeight)
            if (base64 != null) {
                (base64.length * 3L / 4L) / 1024 // Convert base64 length to KB
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
