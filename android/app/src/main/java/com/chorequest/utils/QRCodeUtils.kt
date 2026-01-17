package com.chorequest.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chorequest.domain.models.QRCodePayload
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.*

/**
 * Utility functions for QR code generation and parsing
 */
object QRCodeUtils {

    private const val QR_CODE_SIZE = 512
    private val gson = Gson()

    /**
     * Generate QR code data string (JSON) for a user
     */
    fun generateQRCodeData(
        userId: String,
        userName: String,
        familyId: String,
        authToken: String,
        tokenVersion: Int,
        ownerEmail: String,
        folderId: String
    ): String {
        val payload = QRCodePayload(
            familyId = familyId,
            userId = userId,
            token = authToken,
            version = tokenVersion,
            appVersion = "1.0.0",
            timestamp = java.time.Instant.now().toString(),
            ownerEmail = ownerEmail,
            folderId = folderId
        )
        return gson.toJson(payload)
    }

    /**
     * Generate QR code bitmap from JSON data string
     */
    fun generateQRCodeBitmap(jsonData: String, size: Int = QR_CODE_SIZE): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(jsonData, BarcodeFormat.QR_CODE, size, size, hints)
            
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate QR code bitmap from payload
     */
    fun generateQRCode(payload: QRCodePayload, size: Int = QR_CODE_SIZE): Bitmap? {
        return try {
            // Convert payload to JSON string
            val jsonString = gson.toJson(payload)
            
            // Encode as QR code
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(jsonString, BarcodeFormat.QR_CODE, size, size, hints)
            
            // Create bitmap from bit matrix
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse QR code data string to payload
     */
    fun parseQRCodeData(data: String): QRCodePayload? {
        return try {
            gson.fromJson(data, QRCodePayload::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Validate QR code payload
     */
    fun validateQRCodePayload(payload: QRCodePayload): Boolean {
        // Check required fields
        if (payload.familyId.isBlank() || 
            payload.userId.isBlank() || 
            payload.token.isBlank() ||
            payload.ownerEmail.isBlank() ||
            payload.folderId.isBlank()) {
            android.util.Log.e("QRCodeUtils", "QR code validation failed: missing required fields")
            android.util.Log.e("QRCodeUtils", "familyId: ${payload.familyId.isNotBlank()}, userId: ${payload.userId.isNotBlank()}, token: ${payload.token.isNotBlank()}, ownerEmail: ${payload.ownerEmail.isNotBlank()}, folderId: ${payload.folderId.isNotBlank()}")
            return false
        }
        
        // Check version
        if (payload.version < 1) {
            android.util.Log.e("QRCodeUtils", "QR code validation failed: invalid version ${payload.version}")
            return false
        }
        
        // Check timestamp (not too old - max 30 days)
        // Note: We'll be lenient with timestamp parsing - if it fails, we'll still accept the QR code
        // as long as other validations pass. The backend will do the final validation.
        try {
            if (payload.timestamp.isNotBlank()) {
                val timestamp = java.time.Instant.parse(payload.timestamp)
                val now = java.time.Instant.now()
                val daysDiff = java.time.Duration.between(timestamp, now).toDays()
                
                if (daysDiff > 30) {
                    android.util.Log.e("QRCodeUtils", "QR code validation failed: QR code is ${daysDiff} days old (max 30 days)")
                    return false
                }
            }
        } catch (e: Exception) {
            // Log but don't fail - timestamp parsing errors shouldn't block QR code usage
            // The backend will do the final validation
            android.util.Log.w("QRCodeUtils", "QR code timestamp parsing failed: ${e.message}, but continuing validation")
        }
        
        return true
    }

    /**
     * Create QR code URI format (for deep linking if needed)
     */
    fun createQRCodeUri(payload: QRCodePayload): String {
        val jsonString = gson.toJson(payload)
        val encodedData = Base64.getEncoder().encodeToString(jsonString.toByteArray())
        return "${Constants.QrCode.URI_SCHEME}?data=$encodedData&v=${Constants.QrCode.VERSION}"
    }

    /**
     * Parse QR code URI format
     */
    fun parseQRCodeUri(uri: String): QRCodePayload? {
        return try {
            if (!uri.startsWith(Constants.QrCode.URI_SCHEME)) {
                return null
            }
            
            val dataParam = uri.substringAfter("data=").substringBefore("&")
            val decodedData = String(Base64.getDecoder().decode(dataParam))
            
            parseQRCodeData(decodedData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate colored QR code with custom colors
     */
    fun generateColoredQRCode(
        payload: QRCodePayload,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        size: Int = QR_CODE_SIZE
    ): Bitmap? {
        return try {
            val jsonString = gson.toJson(payload)
            
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(jsonString, BarcodeFormat.QR_CODE, size, size, hints)
            
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save QR code bitmap to Downloads folder (or Pictures on Android 10+)
     * @param context Application context
     * @param bitmap QR code bitmap to save
     * @param fileName Optional custom filename (without extension). If null, generates timestamp-based name
     * @return Uri of saved file, or null if failed
     */
    fun saveQRCodeToDownloads(
        context: Context,
        bitmap: Bitmap,
        fileName: String? = null
    ): android.net.Uri? {
        return try {
            val displayName = fileName ?: "ChoreQuest_QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, MediaStore.Images doesn't allow Downloads directory
                // Use Pictures folder instead, which is more appropriate for images
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ChoreQuest")
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return null
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                uri
            } else {
                // Use File API for older Android versions - can save directly to Downloads
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                
                val file = File(downloadsDir, "$displayName.png")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                android.net.Uri.fromFile(file)
            }
        } catch (e: Exception) {
            android.util.Log.e("QRCodeUtils", "Error saving QR code", e)
            null
        }
    }
}
