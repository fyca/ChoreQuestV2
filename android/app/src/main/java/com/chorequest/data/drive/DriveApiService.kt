package com.chorequest.data.drive

import android.util.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for direct Google Drive API operations
 * Bypasses Apps Script to eliminate cold start delays
 */
@Singleton
class DriveApiService @Inject constructor() {
    companion object {
        private const val TAG = "DriveApiService"
        private const val FOLDER_NAME = "ChoreQuest_Data"
        private const val CHOREQUEST_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    /**
     * Create a Drive API client with the given access token
     */
    private fun createDriveClient(accessToken: String): Drive {
        val credential = GoogleCredential()
            .setAccessToken(accessToken)
        
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ChoreQuest")
            .build()
    }

    /**
     * Get or create the ChoreQuest folder in the user's Drive
     * @param accessToken OAuth access token
     * @return Folder ID
     */
    suspend fun getOrCreateChoreQuestFolder(accessToken: String): String = withContext(Dispatchers.IO) {
        try {
            val drive = createDriveClient(accessToken)
            
            // Search for existing folder
            val query = "name='$FOLDER_NAME' and mimeType='$CHOREQUEST_FOLDER_MIME_TYPE' and trashed=false and 'root' in parents"
            val request = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
            
            val response: FileList = request.execute()
            
            if (response.files != null && response.files.isNotEmpty()) {
                val folderId = response.files[0].id
                Log.d(TAG, "Found existing ChoreQuest folder: $folderId")
                folderId
            } else {
                // Create new folder
                Log.d(TAG, "Creating new ChoreQuest folder")
                val folderMetadata = File()
                    .setName(FOLDER_NAME)
                    .setMimeType(CHOREQUEST_FOLDER_MIME_TYPE)
                    .setDescription("ChoreQuest app data storage")
                    .setParents(listOf("root"))
                
                val folder = drive.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                
                val folderId = folder.id
                Log.d(TAG, "Created ChoreQuest folder: $folderId")
                folderId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating ChoreQuest folder", e)
            throw e
        }
    }

    /**
     * Find a file by name in the specified folder
     * @param accessToken OAuth access token
     * @param folderId Folder ID to search in
     * @param fileName Name of the file to find
     * @return File ID if found, null otherwise
     */
    suspend fun findFileByName(accessToken: String, folderId: String, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val drive = createDriveClient(accessToken)
            
            val query = "name='$fileName' and '$folderId' in parents and trashed=false"
            val request = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
            
            val response: FileList = request.execute()
            
            if (response.files != null && response.files.isNotEmpty()) {
                val fileId = response.files[0].id
                Log.d(TAG, "Found file '$fileName' with ID: $fileId")
                fileId
            } else {
                Log.d(TAG, "File '$fileName' not found in folder $folderId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding file '$fileName'", e)
            throw e
        }
    }

    /**
     * Read JSON file content from Drive
     * @param accessToken OAuth access token
     * @param fileId File ID to read
     * @return File content as string
     */
    suspend fun readFileContent(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        try {
            val drive = createDriveClient(accessToken)
            
            val fileContent = drive.files().get(fileId)
                .setAlt("media")
                .executeMediaAsInputStream()
            
            fileContent.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file $fileId", e)
            throw e
        }
    }

    /**
     * Write/update JSON file content in Drive
     * @param accessToken OAuth access token
     * @param folderId Folder ID where file should be stored
     * @param fileName Name of the file
     * @param content JSON content as string
     * @return File ID
     */
    suspend fun writeFileContent(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val drive = createDriveClient(accessToken)
            
            // Check if file exists
            val existingFileId = findFileByName(accessToken, folderId, fileName)
            
            if (existingFileId != null) {
                // Update existing file
                Log.d(TAG, "Updating existing file: $fileName")
                val contentStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                val mediaContent = InputStreamContent("application/json", contentStream)
                mediaContent.length = content.length.toLong()
                
                val fileMetadata = File()
                    .setName(fileName)
                
                // Update with media content as second parameter
                drive.files().update(existingFileId, fileMetadata, mediaContent)
                    .execute()
                
                Log.d(TAG, "File updated: $existingFileId")
                existingFileId
            } else {
                // Create new file
                Log.d(TAG, "Creating new file: $fileName")
                val fileMetadata = File()
                    .setName(fileName)
                    .setParents(listOf(folderId))
                
                val contentStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                val mediaContent = InputStreamContent("application/json", contentStream)
                mediaContent.length = content.length.toLong()
                
                val file = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                
                val fileId = file.id
                Log.d(TAG, "File created: $fileId")
                fileId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file '$fileName'", e)
            throw e
        }
    }

    /**
     * Delete a file from Drive
     * @param accessToken OAuth access token
     * @param fileId File ID to delete
     */
    suspend fun deleteFile(accessToken: String, fileId: String) = withContext(Dispatchers.IO) {
        try {
            val drive = createDriveClient(accessToken)
            drive.files().delete(fileId).execute()
            Log.d(TAG, "File deleted: $fileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file $fileId", e)
            throw e
        }
    }
}
