# 🔧 Quick Fix for Build Error

## Error You're Seeing
```
AAPT: error: resource mipmap/ic_launcher not found
```

## Solution (2 minutes)

### Step 1: Open Android Studio
Open the `android` folder in Android Studio.

### Step 2: Generate Launcher Icons
1. In the **Project** panel, right-click on **`app/src/main/res`**
2. Select **New → Image Asset**
3. In the wizard:
   - Icon Type: **Launcher Icons (Adaptive and Legacy)** ✓
   - Name: `ic_launcher` (default)
   - Foreground: **Clip Art** → Click icon button → Choose "check_circle" or "task"
   - Background: **Color** → `#4ECDC4` (ChoreQuest teal)
4. Click **Next**, then **Finish**

### Step 3: Rebuild
- **Build → Clean Project**
- **Build → Rebuild Project**
- **Run** ▶️

## That's It!

The app will now build successfully. The adaptive icons I created will work for the launcher, and the PNG files Android Studio generates will support older devices.

---

**For detailed instructions, see [LAUNCHER_ICON_FIX.md](LAUNCHER_ICON_FIX.md)**
