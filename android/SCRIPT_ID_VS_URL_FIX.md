# 🐛 Bug Found: Using Script ID Instead of Full URL

## ❌ The Problem

**Current Code (WRONG):**

In `Constants.kt`:
```kotlin
const val APPS_SCRIPT_WEB_APP_ID = "AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk"
```

In `AppModule.kt`:
```kotlin
val baseUrl = "https://script.google.com/"
```

In `ChoreQuestApi.kt`:
```kotlin
@POST("macros/s/{scriptId}/exec")
suspend fun authenticateWithGoogle(
    @Path("scriptId") scriptId: String = "",
    @Query("path") path: String = "auth",
    ...
)
```

**This creates:**
```
https://script.google.com/macros/s/AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk/exec?path=auth
```

---

## ✅ The Fix

**According to `apps-script/DEPLOYMENT_GUIDE.md` (line 134):**

```kotlin
const val APPS_SCRIPT_WEB_APP_URL = "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec"
```

We should store the **FULL URL**, not just the script ID!

---

## 🔧 Two Options to Fix

### Option A: Keep Path Parameters (Current Pattern)

**Pros:** Follows RESTful conventions, path parameter pattern  
**Cons:** More verbose, repetitive scriptId parameter  

**No changes needed** - the current approach works, just confusing naming.

### Option B: Use Full URL as Base (Recommended)

**Pros:** Simpler, less repetitive, standard pattern  
**Cons:** Less flexible for multi-environment setups  

**Changes required:** Update Constants, AppModule, API interface, and all repositories.

---

## 🎯 Recommendation: Option B (Full URL)

Since you have ONE deployment and the guide expects a full URL, let's refactor.

### Benefits:
- ✅ Matches the deployment guide
- ✅ Removes ~200 lines of repetitive `scriptId` parameters
- ✅ Simpler API calls
- ✅ Standard REST pattern

---

## 🚀 Implementation Plan

### Step 1: Update Constants.kt

```kotlin
object Constants {
    // Full Apps Script Web App URL (from deployment)
    const val APPS_SCRIPT_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk/exec"
    
    // Google OAuth Web Client ID
    const val GOOGLE_WEB_CLIENT_ID = "156195149694-a3c7v365m6a2rhq46icqh1c13oi6r8h2.apps.googleusercontent.com"
    
    // ... rest of constants
}
```

### Step 2: Update AppModule.kt

```kotlin
@Provides
@Singleton
fun provideRetrofit(
    okHttpClient: OkHttpClient,
    gson: Gson
): Retrofit {
    return Retrofit.Builder()
        .baseUrl(com.lostsierra.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
```

### Step 3: Update ChoreQuestApi.kt

Remove `@Path("scriptId") scriptId: String` from ALL endpoints and change paths:

```kotlin
// Authentication
@POST(".")  // Changed from "macros/s/{scriptId}/exec"
suspend fun authenticateWithGoogle(
    // Removed: @Path("scriptId") scriptId: String
    @Query("path") path: String = "auth",
    @Query("action") action: String = "google",
    @Body request: GoogleAuthRequest
): Response<AuthResponse>

@POST(".")
suspend fun authenticateWithQR(
    // Removed: @Path("scriptId") scriptId: String
    @Query("path") path: String = "auth",
    @Query("action") action: String = "qr",
    @Body request: QRAuthRequest
): Response<AuthResponse>

// ... and so on for all ~30 endpoints
```

### Step 4: Update All Repositories

Remove `scriptId = Constants.APPS_SCRIPT_WEB_APP_ID` from all API calls:

**AuthRepository.kt:**
```kotlin
// Before:
val response = api.authenticateWithGoogle(
    scriptId = Constants.APPS_SCRIPT_WEB_APP_ID,
    request = GoogleAuthRequest(...)
)

// After:
val response = api.authenticateWithGoogle(
    request = GoogleAuthRequest(...)
)
```

**Apply to:**
- `AuthRepository.kt`
- `ChoreRepository.kt`
- `RewardRepository.kt`
- `UserRepository.kt`
- `SyncRepository.kt`
- `ActivityLogRepository.kt`

---

## 📊 Impact Analysis

### Files to Change: 8
1. `Constants.kt` - Add full URL constant
2. `AppModule.kt` - Use new constant as baseUrl
3. `ChoreQuestApi.kt` - Remove scriptId from ~30 endpoints
4. `AuthRepository.kt` - Remove scriptId from ~3 calls
5. `ChoreRepository.kt` - Remove scriptId from ~6 calls
6. `RewardRepository.kt` - Remove scriptId from ~5 calls
7. `UserRepository.kt` - Remove scriptId from ~4 calls
8. `SyncRepository.kt` - Remove scriptId from ~3 calls
9. `ActivityLogRepository.kt` - Remove scriptId from ~1 call

### Lines to Change: ~250-300

### Time Estimate: 15-20 minutes

### Risk: Low
- Simple find/replace refactor
- Doesn't change functionality
- Easy to test

---

## ⚠️ Current Issue: Why OAuth Fails

The Google OAuth failure might be because:

1. **Wrong Web Client ID**: Using Apps Script deployment ID instead of OAuth client ID
2. **Missing SHA-1**: Not added to Google Cloud Console
3. **Wrong API URL pattern**: Using script ID incorrectly

**This refactor will clarify #1 and #3!**

---

## 🎯 Decision Point

**Do you want me to refactor to use the full URL?**

**YES** → Clean, simple, matches deployment guide  
**NO** → Keep current pattern, just fix the OAuth client ID issue

Let me know and I'll proceed!
