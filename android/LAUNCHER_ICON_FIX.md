# Launcher Icon Fix

## Issue
The build is failing because actual PNG launcher icons are missing for devices running Android < 8.0 (API < 26).

## What I Created
✅ Adaptive icons for Android 8.0+ (API 26+):
- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`  
- `res/drawable/ic_launcher_background.xml` (colorful gradient)
- `res/drawable/ic_launcher_foreground.xml` (checkmark icon)

❌ Missing PNG files for Android < 8.0:
- `res/mipmap-mdpi/ic_launcher.png` (48x48)
- `res/mipmap-hdpi/ic_launcher.png` (72x72)
- `res/mipmap-xhdpi/ic_launcher.png` (96x96)
- `res/mipmap-xxhdpi/ic_launcher.png` (144x144)
- `res/mipmap-xxxhdpi/ic_launcher.png` (192x192)
- (same for `ic_launcher_round.png`)

## Quick Fix in Android Studio (2 minutes)

### Option 1: Use Image Asset Studio (Recommended)

1. **Right-click** on `app/src/main/res` folder
2. Select **New > Image Asset**
3. In the wizard:
   - **Icon Type:** Launcher Icons (Adaptive and Legacy)
   - **Name:** ic_launcher
   - **Foreground Layer:**
     - Select **Clip Art**
     - Click the icon button
     - Search for "check_circle" or "task" or "list"
     - Choose a suitable icon
     - Set color to **#FFFFFF** (white)
   - **Background Layer:**
     - Select **Color**
     - Set color to **#4ECDC4** (ChoreQuest teal/cyan)
4. Click **Next**
5. Review the icons it will generate
6. Click **Finish**

This will automatically generate ALL necessary PNG files for all densities!

### Option 2: Quick Temporary Icon

If you just want to build quickly without a perfect icon:

1. Right-click on `app/src/main/res` folder
2. Select **New > Image Asset**
3. Select **Launcher Icons (Adaptive and Legacy)**
4. Choose **Foreground Layer > Clip Art**
5. Pick any icon (like a star or checkmark)
6. Use default colors
7. Click **Finish**

### Option 3: Use Existing Assets

If you have a logo/icon file:

1. Right-click on `app/src/main/res` folder
2. Select **New > Image Asset**
3. Select **Image** instead of Clip Art
4. Browse to your icon file (PNG, JPG, or SVG)
5. Adjust padding/scaling as needed
6. Click **Finish**

## After Generating Icons

1. **Sync Gradle** (File > Sync Project with Gradle Files)
2. **Clean Build** (Build > Clean Project)
3. **Rebuild** (Build > Rebuild Project)
4. **Run** the app

## Alternative: Increase Minimum SDK (Not Recommended)

If you only want to support Android 8.0+ (API 26+), you could change in `app/build.gradle.kts`:

```kotlin
minSdk = 26  // Instead of 24
```

This would make the adaptive icons sufficient, but you'd lose support for Android 7.0 and 7.1 devices.

## What the Icons Look Like

The adaptive icons I created have:
- **Background:** Colorful teal-to-blue gradient (ChoreQuest brand colors)
- **Foreground:** White checkmark in circle (representing completed tasks)
- **Style:** Modern, playful, suitable for a family app

You can customize these by:
1. Editing `res/drawable/ic_launcher_background.xml` (change colors)
2. Editing `res/drawable/ic_launcher_foreground.xml` (change icon)
3. Or regenerating completely using Image Asset Studio

## Verification

After generating icons, check that these files exist:
```
res/mipmap-mdpi/ic_launcher.png
res/mipmap-mdpi/ic_launcher_round.png
res/mipmap-hdpi/ic_launcher.png
res/mipmap-hdpi/ic_launcher_round.png
res/mipmap-xhdpi/ic_launcher.png
res/mipmap-xhdpi/ic_launcher_round.png
res/mipmap-xxhdpi/ic_launcher.png
res/mipmap-xxhdpi/ic_launcher_round.png
res/mipmap-xxxhdpi/ic_launcher.png
res/mipmap-xxxhdpi/ic_launcher_round.png
```

## Need a Custom ChoreQuest Icon?

For a professional ChoreQuest icon, consider:
- Chore/task themed icons: checklist, broom, star, trophy
- Family themed icons: house, people, family
- Gamification themed: quest marker, level up, achievement

Use **Image Asset Studio** with these search terms in the clip art selector:
- "check_circle"
- "task_alt"
- "assignment_turned_in"
- "emoji_events" (trophy)
- "home"
- "family_restroom"

## Summary

**To fix the build error RIGHT NOW:**
1. Open Android Studio
2. Right-click `res` folder > New > Image Asset
3. Follow wizard with any icon
4. Click Finish
5. Rebuild project

**Time to fix:** Under 2 minutes! 🎉
