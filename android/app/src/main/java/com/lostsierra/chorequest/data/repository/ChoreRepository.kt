package com.lostsierra.chorequest.data.repository

import android.util.Log
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.local.dao.ChoreDao
import com.lostsierra.chorequest.data.local.dao.UserDao
import com.lostsierra.chorequest.data.local.entities.toEntity
import com.lostsierra.chorequest.data.local.entities.toDomain
import com.lostsierra.chorequest.data.remote.*
import com.lostsierra.chorequest.domain.models.Chore
import com.lostsierra.chorequest.domain.models.ChoreStatus
import com.lostsierra.chorequest.domain.models.ChoreTemplate
import com.lostsierra.chorequest.domain.models.ChoresData
import com.lostsierra.chorequest.domain.models.TemplatesData
import com.lostsierra.chorequest.utils.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChoreRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val choreDao: ChoreDao,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.lostsierra.chorequest.data.drive.TokenManager,
    private val userDao: com.lostsierra.chorequest.data.local.dao.UserDao
) {
    companion object {
        private const val TAG = "ChoreRepository"
    }
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Mutex to prevent concurrent executions of ensureChoresUpToDate
    private val ensureChoresUpToDateMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Get all chores - loads from Drive on-demand, then returns from local cache
     */
    fun getAllChores(): Flow<List<Chore>> {
        // Trigger background load from Drive (non-blocking)
        repositoryScope.launch {
            loadChoresFromDrive()
        }
        
        // Return from local cache immediately, then update when Drive data loads
        return choreDao.getAllChores().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Ensure chores are up to date using Google Drive API directly
     * This removes expired chores and creates current cycle instances
     * On 401 errors, refreshes token and retries, falls back to Apps Script if refresh fails
     * Uses a mutex to prevent concurrent executions
     */
    suspend fun ensureChoresUpToDate() {
        // Use mutex to prevent concurrent executions
        if (!ensureChoresUpToDateMutex.tryLock()) {
            Log.d(TAG, "ensureChoresUpToDate already running, skipping")
            return
        }
        
        try {
            val session = sessionManager.loadSession() ?: return
            var accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken == null) {
                Log.w(TAG, "No access token available, falling back to Apps Script for ensureChoresUpToDate")
                ensureChoresUpToDateViaAppsScript(session)
                return
            }
            
            Log.d(TAG, "Ensuring chores are up to date using Drive API (expired removal, current creation)")
            
            val folderId = session.driveWorkbookLink
            
            // Load templates, users, and chores from Drive
            // If we get a 401, refresh token and retry once
            val templatesData = try {
                readTemplatesFromDrive(accessToken, folderId)
            } catch (e: Exception) {
                if (isUnauthorizedError(e)) {
                    Log.w(TAG, "Drive API authentication failed (401) when reading templates, refreshing token and retrying")
                    val refreshedToken = tokenManager.forceRefreshToken()
                    if (refreshedToken != null) {
                        accessToken = refreshedToken
                        // Retry with new token
                        try {
                            readTemplatesFromDrive(accessToken, folderId)
                        } catch (retryE: Exception) {
                            if (isUnauthorizedError(retryE)) {
                                Log.w(TAG, "Token refresh failed or still unauthorized, falling back to Apps Script")
                                ensureChoresUpToDateViaAppsScript(session)
                                return
                            }
                            Log.e(TAG, "Error reading templates from Drive after token refresh", retryE)
                            return
                        }
                    } else {
                        Log.w(TAG, "Token refresh failed, falling back to Apps Script")
                        ensureChoresUpToDateViaAppsScript(session)
                        return
                    }
                } else {
                    Log.e(TAG, "Error reading templates from Drive", e)
                    return
                }
            } ?: run {
                Log.d(TAG, "No templates found, skipping ensureChoresUpToDate")
                return
            }
            
            if (templatesData.templates.isNullOrEmpty()) {
                Log.d(TAG, "No templates found, skipping ensureChoresUpToDate")
                return
            }
            
            val usersData = try {
                readUsersFromDrive(accessToken, folderId)
            } catch (e: Exception) {
                if (isUnauthorizedError(e)) {
                    Log.w(TAG, "Drive API authentication failed (401), refreshing token and retrying")
                    val refreshedToken = tokenManager.forceRefreshToken()
                    if (refreshedToken != null) {
                        accessToken = refreshedToken
                        // Retry with new token
                        try {
                            readUsersFromDrive(accessToken, folderId) ?: run {
                                Log.d(TAG, "No users found after token refresh, skipping ensureChoresUpToDate")
                                return
                            }
                        } catch (retryE: Exception) {
                            if (isUnauthorizedError(retryE)) {
                                Log.w(TAG, "Token refresh failed or still unauthorized, falling back to Apps Script")
                                ensureChoresUpToDateViaAppsScript(session)
                                return
                            }
                            Log.e(TAG, "Error reading users from Drive after token refresh", retryE)
                            return
                        }
                    } else {
                        Log.w(TAG, "Token refresh failed, falling back to Apps Script")
                        ensureChoresUpToDateViaAppsScript(session)
                        return
                    }
                } else {
                    Log.e(TAG, "Error reading users from Drive", e)
                    return
                }
            } ?: run {
                Log.d(TAG, "No users found, skipping ensureChoresUpToDate")
                return
            }
            
            if (usersData.users.isEmpty()) {
                Log.d(TAG, "No users found, skipping ensureChoresUpToDate")
                return
            }
            
            val choresData = try {
                readChoresFromDrive(accessToken, folderId) ?: ChoresData(chores = emptyList())
            } catch (e: Exception) {
                if (isUnauthorizedError(e)) {
                    Log.w(TAG, "Drive API authentication failed (401), refreshing token and retrying")
                    val refreshedToken = tokenManager.forceRefreshToken()
                    if (refreshedToken != null) {
                        accessToken = refreshedToken
                        // Retry with new token
                        try {
                            readChoresFromDrive(accessToken, folderId) ?: ChoresData(chores = emptyList())
                        } catch (retryE: Exception) {
                            if (isUnauthorizedError(retryE)) {
                                Log.w(TAG, "Token refresh failed or still unauthorized, falling back to Apps Script")
                                ensureChoresUpToDateViaAppsScript(session)
                                return
                            }
                            Log.e(TAG, "Error reading chores from Drive after token refresh", retryE)
                            ChoresData(chores = emptyList())
                        }
                    } else {
                        Log.w(TAG, "Token refresh failed, falling back to Apps Script")
                        ensureChoresUpToDateViaAppsScript(session)
                        return
                    }
                } else {
                    Log.e(TAG, "Error reading chores from Drive", e)
                    ChoresData(chores = emptyList())
                }
            } ?: ChoresData(chores = emptyList())
            
            var hasChanges = false
            var templatesNeedSaving = false
            val today = java.time.LocalDate.now()
            Log.d(TAG, "Starting ensureChoresUpToDate: today=$today, total chores=${choresData.chores.size}, total templates=${templatesData.templates.size}")
            
            // Process each template
            val updatedTemplates = templatesData.templates.toMutableList()
            val updatedChores = choresData.chores.toMutableList()
            
            // Log all chores to see what we're working with
            Log.d(TAG, "All chores in system:")
            for (chore in updatedChores) {
                Log.d(TAG, "  Chore: id=${chore.id}, title=${chore.title}, templateId=${chore.templateId}, cycleId=${chore.cycleId}, dueDate=${chore.dueDate}, status=${chore.status}")
            }
            
            // Also check for chores with templateIds that don't match any template
            val allTemplateIds = templatesData.templates.map { it.id }.toSet()
            val orphanedChores = updatedChores.filter { 
                it.templateId != null && it.templateId !in allTemplateIds 
            }
            if (orphanedChores.isNotEmpty()) {
                Log.w(TAG, "Found ${orphanedChores.size} chores with templateIds that don't match any template:")
                for (chore in orphanedChores) {
                    Log.w(TAG, "  Orphaned chore: id=${chore.id}, title=${chore.title}, templateId=${chore.templateId}, dueDate=${chore.dueDate}, status=${chore.status}")
                }
            }
            
            for (template in templatesData.templates) {
                if (template.recurring == null) continue
                
                val frequency = template.recurring.frequency
                val currentCycleId = getCurrentCycleIdentifier(frequency)
                
                Log.d(TAG, "Processing template ${template.id} (${template.title}), currentCycleId=$currentCycleId, today=$today")
                
                // Find all chores for this template first
                val templateChores = updatedChores.filter { it.templateId == template.id }
                Log.d(TAG, "Found ${templateChores.size} chores for template ${template.id}")
                
                // Find expired instances (dueDate < today, not completed/verified)
                val expiredInstances = templateChores.filter { chore ->
                    Log.d(TAG, "Checking chore ${chore.id} (title=${chore.title}, templateId=${chore.templateId}, cycleId=${chore.cycleId}, status=${chore.status})")
                    
                    if (chore.dueDate == null) {
                        Log.d(TAG, "  -> Chore ${chore.id} has no dueDate, skipping")
                        return@filter false
                    }
                    
                    Log.d(TAG, "  -> Chore ${chore.id} dueDate string: '${chore.dueDate}'")
                    
                    if (chore.status == ChoreStatus.COMPLETED || chore.status == ChoreStatus.VERIFIED) {
                        Log.d(TAG, "  -> Chore ${chore.id} is ${chore.status}, skipping")
                        return@filter false
                    }
                    
                    // Parse and normalize due date
                    val choreDueDate = parseDate(chore.dueDate)
                    if (choreDueDate == null) {
                        Log.w(TAG, "  -> Chore ${chore.id} has invalid dueDate: '${chore.dueDate}', skipping")
                        return@filter false
                    }
                    
                    Log.d(TAG, "  -> Chore ${chore.id} parsed dueDate: $choreDueDate, today: $today")
                    
                    val isExpired = choreDueDate.isBefore(today)
                    if (isExpired) {
                        Log.d(TAG, "  -> Chore ${chore.id} IS EXPIRED: dueDate=$choreDueDate < today=$today")
                    } else {
                        Log.d(TAG, "  -> Chore ${chore.id} is NOT expired: dueDate=$choreDueDate >= today=$today")
                    }
                    
                    isExpired
                }
                
                Log.d(TAG, "Found ${expiredInstances.size} expired instances for template ${template.id}")
                
                // Remove expired instances
                var removedCurrentCycleInstance = false
                for (expiredInstance in expiredInstances) {
                    val isCurrentCycle = expiredInstance.cycleId == currentCycleId
                    if (isCurrentCycle) {
                        removedCurrentCycleInstance = true
                    }
                    val removed = updatedChores.removeAll { it.id == expiredInstance.id }
                    if (removed) {
                        hasChanges = true
                        Log.d(TAG, "Removed expired chore: ${expiredInstance.id} (cycleId=${expiredInstance.cycleId}, dueDate=${expiredInstance.dueDate}, title=${expiredInstance.title})")
                        
                        // Log activity for expired chore removal
                        logActivityForChore(
                            accessToken = accessToken,
                            folderId = folderId,
                            actionType = "chore_deleted",
                            choreId = expiredInstance.id,
                            choreTitle = expiredInstance.title,
                            details = mapOf(
                                "dueDate" to (expiredInstance.dueDate ?: ""),
                                "cycleId" to (expiredInstance.cycleId ?: ""),
                                "reason" to "Expired (system cleanup)"
                            )
                        )
                    } else {
                        Log.w(TAG, "Failed to remove expired chore: ${expiredInstance.id} (not found in list)")
                    }
                }
                
                // Check if instance exists for current cycle
                val instanceExists = updatedChores.any { 
                    it.templateId == template.id && it.cycleId == currentCycleId 
                }
                
                // Check if there's a valid (non-expired) instance for current cycle
                val hasValidCurrentCycleInstance = updatedChores.any { chore ->
                    if (chore.templateId != template.id || chore.cycleId != currentCycleId) return@any false
                    val choreDueDate = parseDate(chore.dueDate) ?: return@any false
                    choreDueDate >= today
                }
                
                // Get template's lastCycleId
                val templateLastCycleId = template.lastCycleId
                
                // Determine if we should create a new instance
                // Only create if:
                // 1. We removed an expired instance for current cycle (need to recreate), OR
                // 2. No instance exists for current cycle AND:
                //    a. template's lastCycleId is null (first time), OR
                //    b. template's lastCycleId is for a PAST cycle (need to catch up)
                //    BUT: If lastCycleId is null, only create if we removed a current cycle instance (don't create on first run)
                //    AND: Don't create if lastCycleId is for a FUTURE cycle (we've already moved forward)
                // 3. AND there's no valid (non-expired) instance for current cycle
                // 4. AND it's not already completed for current cycle
                val shouldCreateInstance = (
                    removedCurrentCycleInstance || 
                    (!instanceExists && templateLastCycleId != null && 
                     templateLastCycleId != currentCycleId && 
                     isCycleIdBefore(templateLastCycleId, currentCycleId)) // Only if lastCycleId is for a PAST cycle
                ) &&
                !hasValidCurrentCycleInstance &&
                !isCompletedForCycle(template.id, currentCycleId, updatedChores)
                
                if (shouldCreateInstance) {
                    val newInstance = createChoreInstanceFromTemplate(template, frequency)
                    if (newInstance != null) {
                        updatedChores.add(newInstance)
                        hasChanges = true
                        
                        val newInstanceCycleId = newInstance.cycleId
                        val newInstanceIsForCurrentCycle = newInstanceCycleId == currentCycleId
                        
                        // Update template's lastCycleId and lastDueDate
                        val templateIndex = updatedTemplates.indexOfFirst { it.id == template.id }
                        if (templateIndex != -1) {
                            updatedTemplates[templateIndex] = updatedTemplates[templateIndex].copy(
                                lastCycleId = newInstanceCycleId,
                                lastDueDate = newInstance.dueDate
                            )
                            templatesNeedSaving = true
                        }
                        
                        Log.d(TAG, "Created new chore instance for template ${template.id}: ${newInstance.id} (cycleId=${newInstanceCycleId}, dueDate=${newInstance.dueDate})")
                        
                        // Log activity for new chore creation
                        logActivityForChore(
                            accessToken = accessToken,
                            folderId = folderId,
                            actionType = "chore_created",
                            choreId = newInstance.id,
                            choreTitle = newInstance.title,
                            details = mapOf(
                                "dueDate" to (newInstance.dueDate ?: ""),
                                "cycleId" to (newInstanceCycleId ?: ""),
                                "points" to newInstance.pointValue,
                                "reason" to "Recurring chore instance created (system)"
                            )
                        )
                        
                        // If we removed a current cycle instance but created one for a future cycle,
                        // we've already handled the transition - don't create another instance for current cycle
                        // This prevents duplicate instances when the due date calculation moves to the next cycle
                        if (removedCurrentCycleInstance && !newInstanceIsForCurrentCycle) {
                            Log.d(TAG, "Removed expired instance for current cycle ($currentCycleId) but created instance for future cycle ($newInstanceCycleId). Current cycle is now handled.")
                        }
                    }
                }
            }
            
            // Handle expired chores without templateId (orphaned recurring chores)
            // These are chores that were created before templateId was added, or somehow lost their templateId
            val orphanedExpiredChores = updatedChores.filter { chore ->
                // Must not have templateId
                if (chore.templateId != null) return@filter false
                // Must have a dueDate
                if (chore.dueDate == null) return@filter false
                // Must not be completed/verified
                if (chore.status == ChoreStatus.COMPLETED || chore.status == ChoreStatus.VERIFIED) return@filter false
                // Must be expired
                val choreDueDate = parseDate(chore.dueDate) ?: return@filter false
                choreDueDate.isBefore(today)
            }
            
            if (orphanedExpiredChores.isNotEmpty()) {
                Log.d(TAG, "Found ${orphanedExpiredChores.size} expired orphaned chores (no templateId)")
                
                for (orphanedChore in orphanedExpiredChores) {
                    // Try to match to a template by title
                    val matchingTemplate = templatesData.templates?.find { 
                        it.recurring != null && it.title == orphanedChore.title 
                    }
                    
                    if (matchingTemplate != null) {
                        Log.d(TAG, "Matched orphaned expired chore '${orphanedChore.title}' to template ${matchingTemplate.id}, removing expired instance")
                    } else {
                        Log.d(TAG, "Orphaned expired chore '${orphanedChore.title}' has no matching template, removing anyway")
                    }
                    
                    // Remove the expired orphaned chore
                    updatedChores.removeAll { it.id == orphanedChore.id }
                    hasChanges = true
                    Log.d(TAG, "Removed expired orphaned chore: ${orphanedChore.id} (title=${orphanedChore.title}, dueDate=${orphanedChore.dueDate})")
                    
                    // Log activity for expired chore removal
                    logActivityForChore(
                        accessToken = accessToken,
                        folderId = folderId,
                        actionType = "chore_deleted",
                        choreId = orphanedChore.id,
                        choreTitle = orphanedChore.title,
                        details = mapOf(
                            "dueDate" to (orphanedChore.dueDate ?: ""),
                            "cycleId" to (orphanedChore.cycleId ?: ""),
                            "reason" to "Expired orphaned chore (no templateId) - system cleanup"
                        )
                    )
                }
            }
            
            // Save templates if needed
            if (templatesNeedSaving) {
                val updatedTemplatesData = TemplatesData(templates = updatedTemplates, metadata = templatesData.metadata)
                try {
                    writeTemplatesToDrive(accessToken, folderId, updatedTemplatesData)
                } catch (e: Exception) {
                    if (isUnauthorizedError(e)) {
                        Log.w(TAG, "Drive API authentication failed when saving templates, refreshing token and retrying")
                        val refreshedToken = tokenManager.forceRefreshToken()
                        if (refreshedToken != null) {
                            accessToken = refreshedToken
                            try {
                                writeTemplatesToDrive(accessToken, folderId, updatedTemplatesData)
                            } catch (retryE: Exception) {
                                if (isUnauthorizedError(retryE)) {
                                    Log.w(TAG, "Token refresh failed or still unauthorized when saving templates, falling back to Apps Script")
                                    ensureChoresUpToDateViaAppsScript(session)
                                    return
                                }
                                Log.e(TAG, "Error saving templates to Drive after token refresh", retryE)
                            }
                        } else {
                            Log.w(TAG, "Token refresh failed when saving templates, falling back to Apps Script")
                            ensureChoresUpToDateViaAppsScript(session)
                            return
                        }
                    } else {
                        Log.e(TAG, "Error saving templates to Drive", e)
                    }
                }
            }
            
            // Save chores if changed
            if (hasChanges) {
                Log.d(TAG, "Saving ${updatedChores.size} chores to Drive (had ${choresData.chores.size} before)")
                val updatedChoresData = ChoresData(chores = updatedChores, metadata = choresData.metadata)
                try {
                    val saved = writeChoresToDrive(accessToken, folderId, updatedChoresData)
                    if (saved) {
                        Log.d(TAG, "Successfully saved updated chores to Drive")
                        
                        // Update local cache - delete all and reinsert to ensure deleted chores are removed
                        choreDao.deleteAllChores()
                        val entities = updatedChores.map { it.toEntity() }
                        if (entities.isNotEmpty()) {
                            choreDao.insertChores(entities)
                        }
                        Log.d(TAG, "Updated local cache with ${entities.size} chores (removed ${choresData.chores.size - updatedChores.size} expired/deleted)")
                    } else {
                        Log.e(TAG, "Failed to save updated chores to Drive")
                    }
                } catch (e: Exception) {
                    if (isUnauthorizedError(e)) {
                        Log.w(TAG, "Drive API authentication failed when saving chores, refreshing token and retrying")
                        val refreshedToken = tokenManager.forceRefreshToken()
                        if (refreshedToken != null) {
                            accessToken = refreshedToken
                            try {
                                val retrySaved = writeChoresToDrive(accessToken, folderId, updatedChoresData)
                                if (retrySaved) {
                                    Log.d(TAG, "Saved updated chores to Drive after token refresh")
                                    
                                    // Update local cache after retry
                                    choreDao.deleteAllChores()
                                    val entities = updatedChores.map { it.toEntity() }
                                    if (entities.isNotEmpty()) {
                                        choreDao.insertChores(entities)
                                    }
                                    Log.d(TAG, "Updated local cache after token refresh with ${entities.size} chores")
                                }
                            } catch (retryE: Exception) {
                                if (isUnauthorizedError(retryE)) {
                                    Log.w(TAG, "Token refresh failed or still unauthorized when saving chores, falling back to Apps Script")
                                    ensureChoresUpToDateViaAppsScript(session)
                                    return
                                }
                                Log.e(TAG, "Error saving chores to Drive after token refresh", retryE)
                            }
                        } else {
                            Log.w(TAG, "Token refresh failed when saving chores, falling back to Apps Script")
                            ensureChoresUpToDateViaAppsScript(session)
                            return
                        }
                    } else {
                        Log.e(TAG, "Error saving chores to Drive", e)
                    }
                }
            } else {
                Log.d(TAG, "No changes to save")
            }
            
        } catch (e: Exception) {
            if (isUnauthorizedError(e)) {
                Log.w(TAG, "Drive API authentication failed, refreshing token and retrying")
                val session = sessionManager.loadSession()
                if (session != null) {
                    val refreshedToken = tokenManager.forceRefreshToken()
                    if (refreshedToken != null) {
                        // Retry the entire operation with new token
                        ensureChoresUpToDate()
                    } else {
                        Log.w(TAG, "Token refresh failed, falling back to Apps Script")
                        ensureChoresUpToDateViaAppsScript(session)
                    }
                }
                return
            }
            Log.e(TAG, "Error in ensureChoresUpToDate", e)
        } finally {
            // Always release the mutex
            ensureChoresUpToDateMutex.unlock()
        }
    }
    
    /**
     * Fallback: Ensure chores are up to date via Apps Script
     * This is used when Drive API authentication fails
     */
    private suspend fun ensureChoresUpToDateViaAppsScript(session: com.lostsierra.chorequest.domain.models.DeviceSession) {
        try {
            Log.d(TAG, "Ensuring chores are up to date via Apps Script (fallback)")
            // Call Apps Script getData endpoint for chores
            // This triggers ensureRecurringChoreInstances on the backend
            val response = api.getData(
                path = "data",
                action = "get",
                type = "chores",
                familyId = session.familyId
            )
            
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully triggered ensureRecurringChoreInstances via Apps Script")
            } else {
                Log.w(TAG, "Failed to trigger ensureRecurringChoreInstances via Apps Script: ${response.code()}")
            }
        } catch (e: Exception) {
            // Don't fail if this call fails - we can still read from Drive
            Log.w(TAG, "Error calling Apps Script to ensure chores up to date, continuing anyway", e)
        }
    }
    
    /**
     * Helper: Check if exception is a 401 Unauthorized error
     */
    private fun isUnauthorizedError(e: Exception): Boolean {
        return when (e) {
            is com.google.api.client.googleapis.json.GoogleJsonResponseException -> {
                e.statusCode == 401
            }
            else -> {
                val message = e.message ?: ""
                message.contains("401") || message.contains("Unauthorized") || message.contains("Invalid Credentials")
            }
        }
    }
    
    /**
     * Helper: Get current cycle identifier based on frequency
     */
    private fun getCurrentCycleIdentifier(frequency: com.lostsierra.chorequest.domain.models.RecurringFrequency): String {
        val now = java.time.LocalDate.now()
        return when (frequency) {
            com.lostsierra.chorequest.domain.models.RecurringFrequency.DAILY -> {
                "${now.year}-${now.monthValue.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
            }
            com.lostsierra.chorequest.domain.models.RecurringFrequency.WEEKLY -> {
                val weekNumber = now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                "${now.year}-W${weekNumber.toString().padStart(2, '0')}"
            }
            com.lostsierra.chorequest.domain.models.RecurringFrequency.MONTHLY -> {
                "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
            }
        }
    }
    
    /**
     * Helper: Compare two cycle IDs to determine if cycleId1 is before cycleId2
     * Returns true if cycleId1 < cycleId2 (cycleId1 is earlier)
     * Cycle IDs are formatted consistently and can be compared lexicographically
     */
    private fun isCycleIdBefore(cycleId1: String, cycleId2: String): Boolean {
        return cycleId1 < cycleId2
    }
    
    /**
     * Helper: Parse date string (YYYY-MM-DD or ISO format)
     */
    private fun parseDate(dateString: String?): java.time.LocalDate? {
        if (dateString == null) return null
        return try {
            // Try YYYY-MM-DD format first
            if (dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                java.time.LocalDate.parse(dateString)
            } else {
                // Try ISO format
                java.time.Instant.parse(dateString).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Helper: Log activity for chore operations (deletion, creation)
     */
    private suspend fun logActivityForChore(
        accessToken: String,
        folderId: String,
        actionType: String,
        choreId: String,
        choreTitle: String,
        details: Map<String, Any>
    ) {
        try {
            // Read existing activity log
            val fileName = "activity_log.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            val activityLogData = if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, com.lostsierra.chorequest.domain.models.ActivityLogData::class.java)
                    ?: com.lostsierra.chorequest.domain.models.ActivityLogData(logs = emptyList())
            } else {
                com.lostsierra.chorequest.domain.models.ActivityLogData(logs = emptyList())
            }
            
            // Create new log entry
            val logEntry = com.lostsierra.chorequest.domain.models.ActivityLog(
                id = java.util.UUID.randomUUID().toString(),
                timestamp = java.time.Instant.now().toString(),
                actorId = "system",
                actorName = "System",
                actorRole = com.lostsierra.chorequest.domain.models.UserRole.PARENT,
                actionType = when (actionType) {
                    "chore_deleted" -> com.lostsierra.chorequest.domain.models.ActivityActionType.CHORE_DELETED
                    "chore_created" -> com.lostsierra.chorequest.domain.models.ActivityActionType.CHORE_CREATED
                    else -> com.lostsierra.chorequest.domain.models.ActivityActionType.CHORE_DELETED
                },
                targetUserId = null,
                targetUserName = null,
                details = com.lostsierra.chorequest.domain.models.ActivityDetails(
                    choreTitle = choreTitle,
                    choreDueDate = details["dueDate"] as? String,
                    pointsAmount = (details["points"] as? Number)?.toInt(),
                    reason = details["reason"] as? String
                ),
                referenceId = choreId,
                referenceType = "chore",
                metadata = com.lostsierra.chorequest.domain.models.ActivityMetadata(
                    deviceType = com.lostsierra.chorequest.domain.models.DeviceType.ANDROID,
                    appVersion = "1.0.0",
                    location = null
                )
            )
            
            // Add to beginning of logs (newest first)
            val updatedLogs = (listOf(logEntry) + (activityLogData.logs ?: emptyList()))
                .take(1000) // Keep only last 1000 entries
            
            // Update metadata
            val now = java.time.Instant.now().toString()
            val updatedMetadata = (activityLogData.metadata ?: com.lostsierra.chorequest.domain.models.SyncMetadata(
                version = 0,
                lastModified = "",
                lastModifiedBy = "",
                lastSyncedAt = ""
            )).copy(
                lastModified = now,
                lastModifiedBy = "system",
                lastSyncedAt = now,
                version = (activityLogData.metadata?.version ?: 0) + 1
            )
            
            val updatedActivityLogData = com.lostsierra.chorequest.domain.models.ActivityLogData(
                logs = updatedLogs,
                metadata = updatedMetadata
            )
            
            // Save back to Drive
            val jsonContent = gson.toJson(updatedActivityLogData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            
            Log.d(TAG, "Logged activity: $actionType for chore $choreId")
        } catch (e: Exception) {
            // Don't fail the entire operation if logging fails
            Log.w(TAG, "Failed to log activity for chore $choreId", e)
        }
    }
    
    /**
     * Helper: Check if chore is completed for a cycle
     */
    private fun isCompletedForCycle(templateId: String, cycleId: String, chores: List<Chore>): Boolean {
        return chores.any { chore ->
            chore.templateId == templateId &&
            chore.cycleId == cycleId &&
            (chore.status == ChoreStatus.COMPLETED || chore.status == ChoreStatus.VERIFIED)
        }
    }
    
    
    /**
     * Helper: Create chore instance from template
     */
    private fun createChoreInstanceFromTemplate(
        template: ChoreTemplate,
        frequency: com.lostsierra.chorequest.domain.models.RecurringFrequency
    ): Chore? {
        try {
            val now = java.time.LocalDate.now()
            var dueDate: java.time.LocalDate? = null
            
            // If template has dueDate and no lastCycleId, use template's dueDate (for initial instance)
            if (template.dueDate != null && template.lastCycleId == null) {
                dueDate = parseDate(template.dueDate)
            }
            
            // Calculate due date based on frequency if not set
            if (dueDate == null) {
                dueDate = when (frequency) {
                    com.lostsierra.chorequest.domain.models.RecurringFrequency.DAILY -> now
                    com.lostsierra.chorequest.domain.models.RecurringFrequency.WEEKLY -> {
                        // Due at end of week (Sunday)
                        val dayOfWeek = now.dayOfWeek.value % 7 // 0 = Sunday
                        val daysUntilSunday = if (dayOfWeek == 0) 0 else 7 - dayOfWeek
                        now.plusDays(daysUntilSunday.toLong())
                    }
                    com.lostsierra.chorequest.domain.models.RecurringFrequency.MONTHLY -> {
                        // Use specified day of month, or default to end of month
                        val targetDay = template.recurring.dayOfMonth ?: now.lengthOfMonth()
                        try {
                            // Try to set the day in the current month
                            val targetDate = now.withDayOfMonth(minOf(targetDay, now.lengthOfMonth()))
                            // If the day has already passed this month, move to next month
                            if (targetDate < now) {
                                // Move to next month
                                val nextMonth = now.plusMonths(1)
                                nextMonth.withDayOfMonth(minOf(targetDay, nextMonth.lengthOfMonth()))
                            } else {
                                targetDate
                            }
                        } catch (e: Exception) {
                            // If day doesn't exist in current month (e.g., 31 in Feb), use last day
                            now.withDayOfMonth(now.lengthOfMonth())
                        }
                    }
                }
            }
            
            // Ensure dueDate is not null (should never be null at this point, but check for safety)
            val finalDueDate = dueDate ?: return null
            
            // Check end date
            if (template.recurring.endDate != null) {
                val endDate = parseDate(template.recurring.endDate)
                if (endDate != null && endDate < finalDueDate) {
                    return null // Past end date
                }
            }
            
            // Calculate cycle ID
            val cycleId = getCycleIdentifier(finalDueDate, frequency)
            
            // Format due date as YYYY-MM-DD
            val dueDateString = "${finalDueDate.year}-${finalDueDate.monthValue.toString().padStart(2, '0')}-${finalDueDate.dayOfMonth.toString().padStart(2, '0')}"
            
            // Create new chore instance
            val newChore = Chore(
                id = java.util.UUID.randomUUID().toString(),
                templateId = template.id,
                cycleId = cycleId,
                title = template.title,
                description = template.description,
                assignedTo = template.assignedTo,
                createdBy = template.createdBy,
                pointValue = template.pointValue,
                dueDate = dueDateString,
                recurring = template.recurring,
                subtasks = template.subtasks.map { subtask ->
                    com.lostsierra.chorequest.domain.models.Subtask(
                        id = subtask.id,
                        title = subtask.title,
                        completed = false,
                        completedBy = null,
                        completedAt = null
                    )
                },
                status = ChoreStatus.PENDING,
                photoProof = null,
                requirePhotoProof = template.requirePhotoProof,
                completedBy = null,
                completedAt = null,
                verifiedBy = null,
                verifiedAt = null,
                createdAt = java.time.Instant.now().toString(),
                color = template.color,
                icon = template.icon
            )
            
            // Verify templateId is set
            if (newChore.templateId != template.id) {
                Log.e(TAG, "ERROR: templateId not set correctly! Expected: ${template.id}, Got: ${newChore.templateId}")
            } else {
                Log.d(TAG, "Created new chore instance: id=${newChore.id}, templateId=${newChore.templateId}, cycleId=${newChore.cycleId}, dueDate=${newChore.dueDate}")
            }
            
            return newChore
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chore instance from template", e)
            return null
        }
    }
    
    /**
     * Helper: Get cycle identifier for a date and frequency
     */
    private fun getCycleIdentifier(date: java.time.LocalDate, frequency: com.lostsierra.chorequest.domain.models.RecurringFrequency): String {
        return when (frequency) {
            com.lostsierra.chorequest.domain.models.RecurringFrequency.DAILY -> {
                "${date.year}-${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
            }
            com.lostsierra.chorequest.domain.models.RecurringFrequency.WEEKLY -> {
                val weekNumber = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                "${date.year}-W${weekNumber.toString().padStart(2, '0')}"
            }
            com.lostsierra.chorequest.domain.models.RecurringFrequency.MONTHLY -> {
                "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
            }
        }
    }
    
    /**
     * Helper: Read users from Drive using direct API
     */
    private suspend fun readUsersFromDrive(accessToken: String, folderId: String): com.lostsierra.chorequest.domain.models.UsersData? {
        return try {
            val fileName = "users.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, com.lostsierra.chorequest.domain.models.UsersData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            // Re-throw 401 errors so they can be handled by caller
            if (isUnauthorizedError(e)) {
                throw e
            }
            Log.e(TAG, "Error reading users from Drive", e)
            null
        }
    }

    /**
     * Helper: Write users to Drive using direct API
     */
    private suspend fun writeUsersToDrive(accessToken: String, folderId: String, usersData: com.lostsierra.chorequest.domain.models.UsersData): Boolean {
        return try {
            val fileName = "users.json"
            val jsonContent = gson.toJson(usersData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing users to Drive", e)
            false
        }
    }

    /**
     * Helper: Read family from Drive using direct API
     */
    private suspend fun readFamilyFromDrive(accessToken: String, folderId: String): com.lostsierra.chorequest.domain.models.Family? {
        return try {
            val fileName = "family.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, com.lostsierra.chorequest.domain.models.Family::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading family from Drive", e)
            null
        }
    }

    /**
     * Helper: Write family to Drive using direct API
     */
    private suspend fun writeFamilyToDrive(accessToken: String, folderId: String, familyData: com.lostsierra.chorequest.domain.models.Family): Boolean {
        return try {
            val fileName = "family.json"
            val jsonContent = gson.toJson(familyData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing family to Drive", e)
            false
        }
    }

    /**
     * Helper: Read transactions from Drive using direct API
     */
    private suspend fun readTransactionsFromDrive(accessToken: String, folderId: String): com.lostsierra.chorequest.domain.models.TransactionsData? {
        return try {
            val fileName = "transactions.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, com.lostsierra.chorequest.domain.models.TransactionsData::class.java)
            } else {
                // Return empty transactions data if file doesn't exist
                com.lostsierra.chorequest.domain.models.TransactionsData(transactions = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading transactions from Drive", e)
            null
        }
    }

    /**
     * Helper: Write transactions to Drive using direct API
     */
    private suspend fun writeTransactionsToDrive(accessToken: String, folderId: String, transactionsData: com.lostsierra.chorequest.domain.models.TransactionsData): Boolean {
        return try {
            val fileName = "transactions.json"
            val jsonContent = gson.toJson(transactionsData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing transactions to Drive", e)
            false
        }
    }

    /**
     * Award points to user when chore is verified
     */
    private suspend fun awardPointsForVerification(
        accessToken: String,
        folderId: String,
        chore: Chore,
        completedByUserId: String
    ) {
        try {
            // Read users data
            val usersData = readUsersFromDrive(accessToken, folderId) ?: run {
                Log.w(TAG, "Could not read users data, skipping point award")
                return
            }

            // Read family data to get point multiplier
            val familyData = readFamilyFromDrive(accessToken, folderId) ?: run {
                Log.w(TAG, "Could not read family data, skipping point award")
                return
            }

            // Find the user who completed the chore
            val completedByUser = usersData.users.find { it.id == completedByUserId }
            if (completedByUser == null) {
                Log.w(TAG, "User $completedByUserId not found, skipping point award")
                return
            }

            // Check if user can earn points
            if (!completedByUser.canEarnPoints) {
                Log.d(TAG, "User $completedByUserId cannot earn points, skipping point award")
                return
            }

            // Calculate points to award
            val pointMultiplier = familyData.settings.pointMultiplier ?: 1.0
            val pointsAwarded = Math.round(chore.pointValue * pointMultiplier).toInt()

            Log.d(TAG, "Awarding $pointsAwarded points to user $completedByUserId (chore: ${chore.title}, base: ${chore.pointValue}, multiplier: $pointMultiplier)")

            // Find user index and update points
            val userIndex = usersData.users.indexOfFirst { it.id == completedByUserId }
            if (userIndex != -1) {
                val currentPoints = usersData.users[userIndex].pointsBalance
                val newPoints = currentPoints + pointsAwarded
                
                // Update user points and stats
                val updatedUser = usersData.users[userIndex].copy(
                    pointsBalance = newPoints,
                    stats = usersData.users[userIndex].stats.copy(
                        totalChoresCompleted = usersData.users[userIndex].stats.totalChoresCompleted + 1
                    )
                )
                
                val updatedUsers = usersData.users.toMutableList()
                updatedUsers[userIndex] = updatedUser
                val updatedUsersData = usersData.copy(users = updatedUsers)

                // Write updated users back to Drive
                if (writeUsersToDrive(accessToken, folderId, updatedUsersData)) {
                    Log.d(TAG, "Successfully awarded $pointsAwarded points. User balance: $currentPoints -> $newPoints")
                    
                    // Update family member data
                    val familyMemberIndex = familyData.members.indexOfFirst { it.id == completedByUserId }
                    if (familyMemberIndex != -1) {
                        val updatedFamilyMembers = familyData.members.toMutableList()
                        updatedFamilyMembers[familyMemberIndex] = updatedUser
                        val updatedFamilyData = familyData.copy(members = updatedFamilyMembers)
                        writeFamilyToDrive(accessToken, folderId, updatedFamilyData)
                    }

                    // Create transaction record
                    val transactionsData = readTransactionsFromDrive(accessToken, folderId) 
                        ?: com.lostsierra.chorequest.domain.models.TransactionsData(transactions = emptyList())
                    
                    val transaction = com.lostsierra.chorequest.domain.models.Transaction(
                        id = UUID.randomUUID().toString(),
                        userId = completedByUserId,
                        type = com.lostsierra.chorequest.domain.models.TransactionType.EARN,
                        points = pointsAwarded,
                        reason = "Completed: ${chore.title}",
                        referenceId = chore.id,
                        timestamp = java.time.Instant.now().toString()
                    )
                    
                    val updatedTransactions = transactionsData.transactions + transaction
                    val updatedTransactionsData = transactionsData.copy(transactions = updatedTransactions)
                    writeTransactionsToDrive(accessToken, folderId, updatedTransactionsData)
                    
                    Log.d(TAG, "Points awarded successfully and transaction recorded")
                } else {
                    Log.e(TAG, "Failed to write updated users data, points not awarded")
                }
            } else {
                Log.e(TAG, "User index not found, points not awarded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error awarding points for verification", e)
            throw e
        }
    }

    /**
     * Load chores from Drive and update local cache
     * First ensures chores are up to date (expired removal, current creation)
     */
    private suspend fun loadChoresFromDrive() {
        try {
            val session = sessionManager.loadSession() ?: return
            val accessToken = tokenManager.getValidAccessToken()
            
            // First, ensure chores are up to date (triggers expired removal and current creation)
            ensureChoresUpToDate()
            
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Loading chores from Drive on-demand")
                    val folderId = session.driveWorkbookLink
                    var choresData = readChoresFromDrive(accessToken, folderId)
                    
                    if (choresData != null) {
                        val chores = choresData.chores
                        // Update local cache
                        choreDao.deleteAllChores()
                        if (chores.isNotEmpty()) {
                            choreDao.insertChores(chores.map { it.toEntity() })
                        }
                        Log.d(TAG, "Loaded ${chores.size} chores from Drive")
                    }
                } catch (e: Exception) {
                    if (isUnauthorizedError(e)) {
                        Log.w(TAG, "Drive API authentication failed (401) when loading chores, refreshing token and retrying")
                        val refreshedToken = tokenManager.forceRefreshToken()
                        if (refreshedToken != null) {
                            // Retry with new token
                            try {
                                val folderId = session.driveWorkbookLink
                                val choresData = readChoresFromDrive(refreshedToken, folderId)
                                
                                if (choresData != null) {
                                    val chores = choresData.chores
                                    // Update local cache
                                    choreDao.deleteAllChores()
                                    if (chores.isNotEmpty()) {
                                        choreDao.insertChores(chores.map { it.toEntity() })
                                    }
                                    Log.d(TAG, "Loaded ${chores.size} chores from Drive after token refresh")
                                }
                            } catch (retryE: Exception) {
                                Log.e(TAG, "Error loading chores from Drive after token refresh, using local cache", retryE)
                            }
                        } else {
                            Log.w(TAG, "Token refresh failed, using local cache")
                        }
                    } else {
                        Log.e(TAG, "Error loading chores from Drive, using local cache", e)
                    }
                }
            } else {
                Log.d(TAG, "No access token, using local cache only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadChoresFromDrive", e)
        }
    }

    /**
     * Get chores for specific user
     */
    fun getChoresForUser(userId: String): Flow<List<Chore>> {
        return choreDao.getChoresForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get chore by ID
     */
    suspend fun getChoreById(choreId: String): Chore? {
        return choreDao.getChoreById(choreId)?.toDomain()
    }

    /**
     * Helper: Read chores from Drive using direct API
     */
    private suspend fun readChoresFromDrive(accessToken: String, folderId: String): ChoresData? {
        return try {
            val fileName = "chores.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, ChoresData::class.java)
            } else {
                // File doesn't exist, return empty
                ChoresData(chores = emptyList())
            }
        } catch (e: Exception) {
            // Re-throw 401 errors so they can be handled by caller
            if (isUnauthorizedError(e)) {
                throw e
            }
            Log.e(TAG, "Error reading chores from Drive", e)
            null
        }
    }

    /**
     * Helper: Write chores to Drive using direct API
     */
    private suspend fun writeChoresToDrive(accessToken: String, folderId: String, choresData: ChoresData): Boolean {
        return try {
            val fileName = "chores.json"
            val jsonContent = gson.toJson(choresData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chores to Drive", e)
            false
        }
    }

    /**
     * Helper: Read templates from Drive using direct API
     */
    private suspend fun readTemplatesFromDrive(accessToken: String, folderId: String): TemplatesData? {
        return try {
            val fileName = "recurring_chore_templates.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, TemplatesData::class.java)
            } else {
                // File doesn't exist, return empty
                TemplatesData(templates = emptyList())
            }
        } catch (e: Exception) {
            // Re-throw 401 errors so they can be handled by caller
            if (isUnauthorizedError(e)) {
                throw e
            }
            Log.e(TAG, "Error reading templates from Drive", e)
            null
        }
    }

    /**
     * Helper: Write templates to Drive using direct API
     */
    private suspend fun writeTemplatesToDrive(accessToken: String, folderId: String, templatesData: TemplatesData): Boolean {
        return try {
            val fileName = "recurring_chore_templates.json"
            val jsonContent = gson.toJson(templatesData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing templates to Drive", e)
            false
        }
    }

    /**
     * Create new chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun createChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to create chore")
                    val folderId = session.driveWorkbookLink
                    
                    var templateSaved = true
                    var template: ChoreTemplate? = null
                    
                    // If recurring, create template first
                    if (chore.recurring != null) {
                        Log.d(TAG, "Creating recurring chore - saving template")
                        val templatesData = readTemplatesFromDrive(accessToken, folderId) ?: TemplatesData(templates = emptyList())
                        
                        // Create template from chore
                        template = ChoreTemplate(
                            id = java.util.UUID.randomUUID().toString(),
                            title = chore.title,
                            description = chore.description,
                            assignedTo = chore.assignedTo,
                            createdBy = chore.createdBy,
                            pointValue = chore.pointValue,
                            dueDate = chore.dueDate,
                            recurring = chore.recurring,
                            subtasks = chore.subtasks,
                            createdAt = java.time.Instant.now().toString(),
                            color = chore.color,
                            icon = chore.icon,
                            requirePhotoProof = chore.requirePhotoProof
                        )
                        
                        // Add template to list
                        val currentTemplates = templatesData.templates ?: emptyList()
                        val updatedTemplates = currentTemplates + template
                        val updatedTemplatesData = templatesData.copy(templates = updatedTemplates)
                        
                        // Save template to Drive
                        templateSaved = writeTemplatesToDrive(accessToken, folderId, updatedTemplatesData)
                        if (!templateSaved) {
                            Log.e(TAG, "Failed to write template to Drive")
                        } else {
                            Log.d(TAG, "Template saved successfully: ${template.id}")
                        }
                    }
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId) ?: ChoresData(chores = emptyList())
                    
                    // For recurring chores, create the initial instance with templateId and cycleId
                    val choreToAdd = if (chore.recurring != null && templateSaved && template != null) {
                        // Create initial instance from template
                        val initialInstance = createChoreInstanceFromTemplate(template, chore.recurring.frequency)
                        if (initialInstance != null) {
                            Log.d(TAG, "Created initial instance for recurring chore: id=${initialInstance.id}, templateId=${initialInstance.templateId}, cycleId=${initialInstance.cycleId}")
                            initialInstance
                        } else {
                            Log.w(TAG, "Failed to create initial instance from template, using original chore")
                            chore
                        }
                    } else {
                        chore
                    }
                    
                    // Add new chore
                    val updatedChores = choresData.chores + choreToAdd
                    val updatedData = choresData.copy(chores = updatedChores)
                    
                    // Write back to Drive
                    val choreSaved = writeChoresToDrive(accessToken, folderId, updatedData)
                    
                    // For recurring chores, both template and chore must be saved
                    if (chore.recurring != null && template != null) {
                        if (templateSaved && choreSaved) {
                            // Update template with lastCycleId and lastDueDate if we created an instance
                            if (choreToAdd.templateId != null && choreToAdd.cycleId != null) {
                                val templatesData = readTemplatesFromDrive(accessToken, folderId) ?: TemplatesData(templates = emptyList())
                                val templateIndex = templatesData.templates?.indexOfFirst { it.id == template.id } ?: -1
                                if (templateIndex != -1) {
                                    val updatedTemplate = templatesData.templates!![templateIndex].copy(
                                        lastCycleId = choreToAdd.cycleId,
                                        lastDueDate = choreToAdd.dueDate
                                    )
                                    val updatedTemplates = templatesData.templates!!.toMutableList()
                                    updatedTemplates[templateIndex] = updatedTemplate
                                    val updatedTemplatesData = templatesData.copy(templates = updatedTemplates)
                                    writeTemplatesToDrive(accessToken, folderId, updatedTemplatesData)
                                }
                            }
                            
                            // Update local cache
                            choreDao.insertChore(choreToAdd.toEntity())
                            Log.d(TAG, "Recurring chore and template created successfully via Drive API: ${choreToAdd.id}")
                            // Ensure chores are up to date (expired removal, current creation)
                            repositoryScope.launch {
                                ensureChoresUpToDate()
                                // Reload chores after ensuring they're up to date
                                loadChoresFromDrive()
                            }
                            emit(Result.Success(choreToAdd))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to save recurring chore (template=$templateSaved, chore=$choreSaved), falling back to Apps Script")
                        }
                    } else {
                        // Non-recurring chore - only chore needs to be saved
                        if (choreSaved) {
                            // Update local cache
                            choreDao.insertChore(chore.toEntity())
                            Log.d(TAG, "Chore created successfully via Drive API: ${chore.id}")
                            // Ensure chores are up to date (expired removal, current creation)
                            repositoryScope.launch {
                                ensureChoresUpToDate()
                                // Reload chores after ensuring they're up to date
                                loadChoresFromDrive()
                            }
                            emit(Result.Success(chore))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to write chore to Drive, falling back to Apps Script")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Re-throw cancellation exceptions - they should propagate up
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val request = CreateChoreRequest(
                creatorId = session.userId,
                title = chore.title,
                description = chore.description,
                assignedTo = chore.assignedTo,
                pointValue = chore.pointValue,
                dueDate = chore.dueDate,
                recurring = chore.recurring,
                subtasks = chore.subtasks,
                color = null, // TODO: Add color/icon support
                icon = null,
                requirePhotoProof = chore.requirePhotoProof
            )

            val response = api.createChore(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                val createdChore = response.body()?.chore
                if (createdChore != null) {
                    // Only save to local cache AFTER successful Drive save
                    choreDao.insertChore(createdChore.toEntity())
                    Log.d(TAG, "Chore created successfully on Drive: ${createdChore.id}")
                    // Ensure chores are up to date (expired removal, current creation)
                    repositoryScope.launch {
                        ensureChoresUpToDate()
                        // Reload chores after ensuring they're up to date
                        loadChoresFromDrive()
                    }
                    emit(Result.Success(createdChore))
                } else {
                    Log.w(TAG, "Drive created chore but returned null chore")
                    emit(Result.Error("Chore created but response was invalid"))
                }
            } else {
                val errorBody = response.body()
                val errorMsg = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message() 
                    ?: "Failed to create chore on Drive"
                
                Log.d(TAG, "Error response: code=${response.code()}, errorMsg=$errorMsg")
                
                // Check if error message indicates authorization is needed
                // Also check response code again in case it's 401 but we got here
                if (response.code() == 401 || com.lostsierra.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                    val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                        ?: com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.lostsierra.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                    Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    Log.e(TAG, "Failed to create chore on Drive: $errorMsg")
                    emit(Result.Error("Failed to save chore to Drive: $errorMsg"))
                }
            }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error creating chore", e)
        emit(Result.Error(e.message ?: "Failed to create chore"))
    }

    /**
     * Update existing chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun updateChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to update chore")
                val folderId = session.driveWorkbookLink
                
                // Read current chores
                val choresData = readChoresFromDrive(accessToken, folderId)
                if (choresData != null) {
                    // Find and update the chore
                    val updatedChores = choresData.chores.map { 
                        if (it.id == chore.id) chore else it
                    }
                    val updatedData = choresData.copy(chores = updatedChores)
                    
                    // Write back to Drive
                    if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                        // Update local cache
                        choreDao.updateChore(chore.toEntity())
                        Log.d(TAG, "Chore updated successfully via Drive API: ${chore.id}")
                        // Ensure chores are up to date (expired removal, current creation)
                        repositoryScope.launch {
                            ensureChoresUpToDate()
                            // Reload chores after ensuring they're up to date
                            loadChoresFromDrive()
                        }
                        emit(Result.Success(chore))
                        return@flow
                    } else {
                        Log.w(TAG, "Failed to write chore update to Drive, falling back to Apps Script")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions - they should propagate up
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        }

        // Fallback to Apps Script
        val updates = ChoreUpdates(
            title = chore.title,
            description = chore.description,
            assignedTo = chore.assignedTo,
            pointValue = chore.pointValue,
            dueDate = chore.dueDate,
            recurring = chore.recurring,
            subtasks = chore.subtasks,
            color = null,
            icon = null,
            requirePhotoProof = chore.requirePhotoProof
        )

        val request = UpdateChoreRequest(
            userId = session.userId,
            choreId = chore.id,
            updates = updates
        )

        val response = api.updateChore(request = request)
        
        // Check for authorization error (401 status code)
        if (!response.isSuccessful && response.code() == 401) {
            val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
            val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
            Log.e(TAG, "Authorization required: $errorMsg")
            emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
            return@flow
        }
        
        if (response.isSuccessful && response.body()?.success == true) {
            val updatedChore = response.body()?.chore
            if (updatedChore != null) {
                // Only update local cache AFTER successful Drive save
                choreDao.updateChore(updatedChore.toEntity())
                Log.d(TAG, "Chore updated successfully on Drive: ${updatedChore.id}")
                // Ensure chores are up to date (expired removal, current creation)
                repositoryScope.launch {
                    ensureChoresUpToDate()
                    // Reload chores after ensuring they're up to date
                    loadChoresFromDrive()
                }
                emit(Result.Success(updatedChore))
            } else {
                Log.w(TAG, "Drive updated chore but returned null chore")
                emit(Result.Error("Chore updated but response was invalid"))
            }
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error 
                ?: errorBody?.message 
                ?: response.message() 
                ?: "Failed to update chore on Drive"
            
            // Check if error message indicates authorization is needed
            if (com.lostsierra.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                    ?: com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val userFriendlyMsg = com.lostsierra.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
            } else {
                Log.e(TAG, "Failed to update chore on Drive: $errorMsg")
                emit(Result.Error("Failed to save chore update to Drive: $errorMsg"))
            }
        }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error updating chore", e)
        emit(Result.Error(e.message ?: "Failed to update chore"))
    }

    /**
     * Delete chore
     * Drive is the source of truth - delete from Drive FIRST, then remove from local cache
     */
    fun deleteChore(chore: Chore): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to delete chore")
                val folderId = session.driveWorkbookLink
                
                // Read current chores
                val choresData = readChoresFromDrive(accessToken, folderId)
                if (choresData != null) {
                    // Remove the chore
                    val updatedChores = choresData.chores.filter { it.id != chore.id }
                    val updatedData = choresData.copy(chores = updatedChores)
                    
                    // Write back to Drive
                    if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                        // Remove from local cache
                        choreDao.deleteChore(chore.toEntity())
                        Log.d(TAG, "Chore deleted successfully via Drive API: ${chore.id}")
                        // Ensure chores are up to date (expired removal, current creation)
                        repositoryScope.launch {
                            ensureChoresUpToDate()
                            // Reload chores after ensuring they're up to date
                            loadChoresFromDrive()
                        }
                        emit(Result.Success(Unit))
                        return@flow
                    } else {
                        Log.w(TAG, "Failed to delete chore from Drive, falling back to Apps Script")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions - they should propagate up
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        }

        // Fallback to Apps Script
        val request = DeleteChoreRequest(
            userId = session.userId,
            choreId = chore.id
        )

        val response = api.deleteChore(request = request)
        
        // Check for authorization error (401 status code)
        if (!response.isSuccessful && response.code() == 401) {
            val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
            val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
            Log.e(TAG, "Authorization required: $errorMsg")
            emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
            return@flow
        }
        
        if (response.isSuccessful && response.body()?.success == true) {
            // Only remove from local cache AFTER successful Drive deletion
            choreDao.deleteChore(chore.toEntity())
            Log.d(TAG, "Chore deleted successfully on Drive: ${chore.id}")
            // Ensure chores are up to date (expired removal, current creation)
            repositoryScope.launch {
                ensureChoresUpToDate()
                // Reload chores after ensuring they're up to date
                loadChoresFromDrive()
            }
            emit(Result.Success(Unit))
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error 
                ?: errorBody?.message 
                ?: response.message() 
                ?: "Failed to delete chore on Drive"
            
            // Check if error message indicates authorization is needed
            if (com.lostsierra.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                    ?: com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val userFriendlyMsg = com.lostsierra.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
            } else {
                Log.e(TAG, "Failed to delete chore on Drive: $errorMsg")
                emit(Result.Error("Failed to delete chore from Drive: $errorMsg"))
            }
        }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error deleting chore", e)
        emit(Result.Error(e.message ?: "Failed to delete chore"))
    }

    /**
     * Complete chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun completeChore(choreId: String, userId: String, photoProof: String? = null): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to complete chore")
                val folderId = session.driveWorkbookLink
                
                // Read current chores
                val choresData = readChoresFromDrive(accessToken, folderId)
                if (choresData != null) {
                    // Find and update the chore
                    val chore = choresData.chores.find { it.id == choreId }
                    if (chore != null) {
                        val now = java.time.Instant.now().toString()
                        val completedChore = chore.copy(
                            status = ChoreStatus.COMPLETED,
                            completedBy = userId,
                            completedAt = now,
                            photoProof = photoProof ?: chore.photoProof
                        )
                        
                        val updatedChores = choresData.chores.map { 
                            if (it.id == choreId) completedChore else it
                        }
                        val updatedData = choresData.copy(chores = updatedChores)
                        
                        // Write back to Drive
                        if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                            // Update local cache
                            choreDao.updateChore(completedChore.toEntity())
                            Log.d(TAG, "Chore completed successfully via Drive API: $choreId")
                            // Ensure chores are up to date (expired removal, current creation)
                            repositoryScope.launch {
                                ensureChoresUpToDate()
                                // Reload chores after ensuring they're up to date
                                loadChoresFromDrive()
                            }
                            emit(Result.Success(completedChore))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to complete chore on Drive, falling back to Apps Script")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions - they should propagate up
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        }

        // Fallback to Apps Script
        val request = CompleteChoreRequest(
            userId = userId,
            choreId = choreId,
            photoProof = photoProof
        )

        val response = api.completeChore(request = request)
        
        // Check for authorization error (401 status code)
        if (!response.isSuccessful && response.code() == 401) {
            val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
            val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
            Log.e(TAG, "Authorization required: $errorMsg")
            emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
            return@flow
        }
        
        if (response.isSuccessful && response.body()?.success == true) {
            val completedChore = response.body()?.chore
            if (completedChore != null) {
                // Only update local cache AFTER successful Drive save
                choreDao.updateChore(completedChore.toEntity())
                Log.d(TAG, "Chore completed successfully on Drive: $choreId")
                // Ensure chores are up to date (expired removal, current creation)
                repositoryScope.launch {
                    ensureChoresUpToDate()
                    // Reload chores after ensuring they're up to date
                    loadChoresFromDrive()
                }
                emit(Result.Success(completedChore))
            } else {
                Log.w(TAG, "Drive completed chore but returned null chore")
                emit(Result.Error("Chore completed but response was invalid"))
            }
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error 
                ?: errorBody?.message 
                ?: response.message() 
                ?: "Failed to complete chore on Drive"
            
            // Check if error message indicates authorization is needed
            if (com.lostsierra.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                    ?: com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val userFriendlyMsg = com.lostsierra.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
            } else {
                Log.e(TAG, "Failed to complete chore on Drive: $errorMsg")
                emit(Result.Error("Failed to save chore completion to Drive: $errorMsg"))
            }
        }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error completing chore", e)
        emit(Result.Error(e.message ?: "Failed to complete chore"))
    }

    /**
     * Verify a completed chore (parent action)
     */
    fun verifyChore(choreId: String, approved: Boolean): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to verify chore")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    if (choresData != null) {
                        // Find and update the chore
                        val chore = choresData.chores.find { it.id == choreId }
                        if (chore != null) {
                            if (chore.status != ChoreStatus.COMPLETED) {
                                emit(Result.Error("Chore is not in completed status"))
                                return@flow
                            }
                            
                            val now = java.time.Instant.now().toString()
                            val verifiedChore = if (approved) {
                                chore.copy(
                                    status = ChoreStatus.VERIFIED,
                                    verifiedBy = session.userId,
                                    verifiedAt = now
                                )
                            } else {
                                // Reject: set back to pending
                                chore.copy(
                                    status = ChoreStatus.PENDING,
                                    completedBy = null,
                                    completedAt = null,
                                    photoProof = null,
                                    verifiedBy = null,
                                    verifiedAt = null
                                )
                            }
                            
                            val updatedChores = choresData.chores.map { 
                                if (it.id == choreId) verifiedChore else it
                            }
                            val updatedData = choresData.copy(chores = updatedChores)
                            
                            // Write back to Drive
                            if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                                // Award points if approved
                                if (approved && verifiedChore.completedBy != null) {
                                    try {
                                        awardPointsForVerification(
                                            accessToken = accessToken,
                                            folderId = folderId,
                                            chore = verifiedChore,
                                            completedByUserId = verifiedChore.completedBy!!
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error awarding points, but chore verification succeeded", e)
                                        // Continue even if points awarding fails
                                    }
                                }
                                
                                // Update local cache
                                choreDao.updateChore(verifiedChore.toEntity())
                                Log.d(TAG, "Chore verified successfully via Drive API: $choreId")
                                // Ensure chores are up to date (expired removal, current creation)
                                repositoryScope.launch {
                                    ensureChoresUpToDate()
                                    // Reload chores after ensuring they're up to date
                                    loadChoresFromDrive()
                                }
                                emit(Result.Success(verifiedChore))
                                return@flow
                            } else {
                                Log.w(TAG, "Failed to verify chore on Drive, falling back to Apps Script")
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Re-throw cancellation exceptions - they should propagate up
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            Log.d(TAG, "Falling back to Apps Script API for verification")
            val response = api.verifyChore(
                request = VerifyChoreRequest(
                    parentId = session.userId,
                    choreId = choreId,
                    approved = approved
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val updated = response.body()?.chore
                if (updated != null) {
                    // Update local cache
                    choreDao.updateChore(updated.toEntity())
                    // Ensure chores are up to date (expired removal, current creation)
                    repositoryScope.launch {
                        ensureChoresUpToDate()
                        // Reload chores after ensuring they're up to date
                        loadChoresFromDrive()
                    }
                    emit(Result.Success(updated))
                } else {
                    emit(Result.Error("Verify succeeded but chore was missing in response"))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to verify chore"
                emit(Result.Error(errorMsg))
            }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error verifying chore", e)
        emit(Result.Error(e.message ?: "Failed to verify chore"))
    }

    /**
     * Sync chores from backend
     */
    suspend fun syncChores() {
        try {
            // TODO: Fetch from backend and update local database
        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Get recurring chore templates
     * Uses direct Drive API for faster performance (no cold start)
     */
    fun getRecurringChoreTemplates(): Flow<List<ChoreTemplate>> = flow {
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                Log.w(TAG, "No session found for fetching templates")
                emit(emptyList())
                return@flow
            }

            // Try to get access token for direct Drive API
            val accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken != null) {
                // Use direct Drive API
                try {
                    Log.d(TAG, "Using direct Drive API to fetch templates")
                    val folderId = session.driveWorkbookLink
                    val fileName = "recurring_chore_templates.json"
                    
                    // Find file in folder
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    
                    if (fileId != null) {
                        // Read file content
                        val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                        val templatesData = gson.fromJson(jsonContent, TemplatesData::class.java)
                        val templates = templatesData.templates ?: emptyList()
                        Log.d(TAG, "Fetched ${templates.size} templates via Drive API")
                        emit(templates)
                    } else {
                        Log.d(TAG, "Template file not found, returning empty list")
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    // Fall back to Apps Script
                    val templates = fetchTemplatesViaAppsScript(session.familyId)
                    emit(templates)
                }
            } else {
                // Fall back to Apps Script if no token available
                Log.d(TAG, "No access token available, using Apps Script")
                val templates = fetchTemplatesViaAppsScript(session.familyId)
                emit(templates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching templates", e)
            emit(emptyList())
        }
    }
    
    /**
     * Fallback: Fetch templates via Apps Script
     */
    private suspend fun fetchTemplatesViaAppsScript(familyId: String): List<ChoreTemplate> {
        val response = api.getData(
            path = "data",
            action = "get",
            type = "recurring_chore_templates",
            familyId = familyId
        )

        Log.d(TAG, "Template fetch response - Success: ${response.isSuccessful}, Code: ${response.code()}")
        
        if (response.isSuccessful && response.body()?.success == true) {
            val data = response.body()?.data
            Log.d(TAG, "Template data received: ${data != null}")
            
            if (data != null) {
                try {
                    val jsonString = gson.toJson(data)
                    Log.d(TAG, "Template JSON string length: ${jsonString.length}")
                    val templatesData = gson.fromJson(jsonString, TemplatesData::class.java)
                    val templates = templatesData.templates ?: emptyList()
                    Log.d(TAG, "Parsed ${templates.size} templates")
                    return templates
                } catch (parseError: Exception) {
                    Log.e(TAG, "Error parsing templates data", parseError)
                    Log.e(TAG, "Data structure: ${gson.toJson(data)}")
                    return emptyList()
                }
            } else {
                Log.w(TAG, "No data in response body")
                return emptyList()
            }
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error ?: errorBody?.message ?: "Unknown error"
            Log.e(TAG, "Failed to fetch templates: $errorMsg")
            Log.e(TAG, "Response code: ${response.code()}, Body: ${errorBody}")
            return emptyList()
        }
    }

    /**
     * Delete recurring chore template
     * Uses direct Drive API for faster performance (no cold start)
     */
    fun deleteRecurringChoreTemplate(templateId: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try to get access token for direct Drive API
            val accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken != null) {
                // Use direct Drive API
                try {
                    Log.d(TAG, "Using direct Drive API to delete template")
                    val folderId = session.driveWorkbookLink
                    val fileName = "recurring_chore_templates.json"
                    
                    // Read current templates
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    if (fileId == null) {
                        emit(Result.Error("Template file not found"))
                        return@flow
                    }
                    
                    val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                    val templatesData = gson.fromJson(jsonContent, TemplatesData::class.java)
                    val templates = (templatesData.templates ?: emptyList()).toMutableList()
                    
                    // Remove template
                    templates.removeAll { it.id == templateId }
                    
                    // Update file
                    val updatedData = TemplatesData(templates = templates, metadata = templatesData.metadata)
                    val updatedJson = gson.toJson(updatedData)
                    driveApiService.writeFileContent(accessToken, folderId, fileName, updatedJson)
                    
                    Log.d(TAG, "Template deleted successfully via Drive API")
                    emit(Result.Success(Unit))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Re-throw cancellation exceptions - they should propagate up
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    // Fall back to Apps Script
                    val result = deleteTemplateViaAppsScript(session.userId, templateId)
                    emit(result)
                }
            } else {
                // Fall back to Apps Script if no token available
                Log.d(TAG, "No access token available, using Apps Script")
                val result = deleteTemplateViaAppsScript(session.userId, templateId)
                emit(result)
            }
    }.catch { e ->
        // Handle exceptions using Flow.catch operator to maintain exception transparency
        if (e is kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        }
        Log.e(TAG, "Error deleting template", e)
        emit(Result.Error(e.message ?: "Failed to delete template"))
    }
    
    /**
     * Fallback: Delete template via Apps Script
     */
    private suspend fun deleteTemplateViaAppsScript(userId: String, templateId: String): Result<Unit> {
        val request = DeleteTemplateRequest(
            userId = userId,
            templateId = templateId
        )

        val response = api.deleteRecurringChoreTemplate(request = request)
        
        // Check for authorization error (401 status code)
        if (!response.isSuccessful && response.code() == 401) {
            val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
            val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
            Log.e(TAG, "Authorization required: $errorMsg")
            return Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg")
        }
        
        if (response.isSuccessful && response.body()?.success == true) {
            Log.d(TAG, "Template deleted successfully on Drive: $templateId")
            return Result.Success(Unit)
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error ?: errorBody?.message ?: "Unknown error"
            Log.e(TAG, "Failed to delete template: $errorMsg")
            return Result.Error(errorMsg)
        }
    }
}

