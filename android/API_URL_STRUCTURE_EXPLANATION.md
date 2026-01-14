# API URL Structure - Script ID vs Full URL

## 🔍 Current Setup

### What We Have Now:

**Base URL (in AppModule.kt):**
```kotlin
val baseUrl = "https://script.google.com/"
```

**API Endpoint (in ChoreQuestApi.kt):**
```kotlin
@POST("macros/s/{scriptId}/exec")
suspend fun authenticateWithGoogle(
    @Path("scriptId") scriptId: String,
    @Query("path") path: String = "auth",
    ...
)
```

**Script ID (in Constants.kt):**
```kotlin
const val APPS_SCRIPT_WEB_APP_ID = "AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk"
```

### This Creates the Full URL:
```
https://script.google.com/macros/s/AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk/exec?path=auth&action=google
```

---

## 🤔 Why Use Script ID Instead of Full URL?

### Current Approach: Script ID as Path Parameter

**Pros:**
- ✅ Follows RESTful patterns
- ✅ Easy to see which deployment ID is being used
- ✅ Standard Retrofit pattern for path parameters
- ✅ Can theoretically support multiple script IDs (dev/staging/prod)

**Cons:**
- ❌ More verbose - have to pass `scriptId` to every API call
- ❌ Can't easily change the URL without rebuilding the app
- ❌ Repetitive code

### Alternative Approach: Full URL as Base URL

**Pros:**
- ✅ Simpler API interface (no scriptId parameter needed)
- ✅ Less repetitive code
- ✅ Standard for most REST APIs

**Cons:**
- ❌ Harder to switch between different deployments
- ❌ The "base URL" becomes specific to one deployment

---

## 🔄 Recommended Refactor: Use Full URL

Since we only have ONE Apps Script deployment, we should simplify:

### Option 1: Full URL in Constants (Recommended)

**Change Constants.kt:**
```kotlin
// Full Apps Script Web App URL
const val APPS_SCRIPT_BASE_URL = "https://script.google.com/macros/s/AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk/exec"
```

**Change AppModule.kt:**
```kotlin
val baseUrl = com.chorequest.utils.Constants.APPS_SCRIPT_BASE_URL
```

**Change ChoreQuestApi.kt (every endpoint):**
```kotlin
// From:
@POST("macros/s/{scriptId}/exec")
suspend fun authenticateWithGoogle(
    @Path("scriptId") scriptId: String,
    @Query("path") path: String = "auth",
    ...
)

// To:
@POST(".")  // or just leave empty ""
suspend fun authenticateWithGoogle(
    @Query("path") path: String = "auth",
    @Query("action") action: String = "google",
    @Body request: GoogleAuthRequest
): Response<AuthResponse>
```

**Remove scriptId from all repository calls:**
```kotlin
// From:
val response = api.authenticateWithGoogle(
    scriptId = Constants.APPS_SCRIPT_WEB_APP_ID,
    request = GoogleAuthRequest(...)
)

// To:
val response = api.authenticateWithGoogle(
    request = GoogleAuthRequest(...)
)
```

---

### Option 2: Keep Script ID for Multi-Environment Support

If you plan to have dev/staging/prod deployments:

**Constants.kt:**
```kotlin
object Constants {
    // Different deployment URLs for different environments
    private const val SCRIPT_ID_DEV = "AKfycbyXXXXXX_DEV"
    private const val SCRIPT_ID_STAGING = "AKfycbyXXXXXX_STAGING"
    private const val SCRIPT_ID_PROD = "AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk"
    
    // Set this based on build variant
    const val APPS_SCRIPT_WEB_APP_ID = SCRIPT_ID_PROD
}
```

Then keep the current approach.

---

## ⚡ Quick Win: Simplify Now

Since you only have ONE deployment, let's refactor to use the full URL.

This will:
- ✅ Remove `scriptId` parameter from every API call
- ✅ Simplify all repository code
- ✅ Make the API interface cleaner
- ✅ Follow standard REST API patterns

---

## 🎯 What Should We Do?

**Recommendation:** Refactor to use full URL (Option 1)

**Why?**
- You have one Apps Script deployment
- Simpler code, less repetition
- Standard approach for single-backend apps
- Can always add environment support later if needed

Would you like me to refactor the code to use the full URL instead of script ID parameters?
