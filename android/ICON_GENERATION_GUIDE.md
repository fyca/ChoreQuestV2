# ChoreQuest Icon Generation Guide

This guide explains how to generate PNG icons for Play Store submission.

## Play Store Requirements

- **Size:** 512x512 pixels
- **Format:** PNG (not JPEG)
- **Background:** No transparency (solid background)
- **File:** Single PNG file

## Method 1: HTML Generator (Easiest - No Installation)

1. Open `generate_play_store_icon.html` in any web browser
2. Click "Download PNG Icon" button
3. The icon will be downloaded as `chorequest_icon_512x512.png`

**Advantages:**
- Works on any computer with a web browser
- No software installation needed
- Instant preview
- Can generate multiple sizes

## Method 2: Python Script (Automated)

### Prerequisites
```bash
pip install cairosvg pillow
```

### Run
```bash
python generate_icons.py
```

This will create a `play_store_icons/` folder with:
- `chorequest_icon_512x512.png` (Play Store required)
- `chorequest_icon_1024x1024.png` (High-res option)

## Method 3: Android Studio Image Asset Studio

1. Open Android Studio
2. Right-click on `app/src/main/res` folder
3. Select **New > Image Asset**
4. Choose **Launcher Icons (Adaptive and Legacy)**
5. **Foreground Layer:**
   - Select **Image**
   - Browse to an exported icon file (from Method 1 or 2)
6. **Background Layer:**
   - Select **Color**
   - Use: `#4A90E2` (Sky Blue)
7. Click **Next** then **Finish**

**Note:** This generates all density icons for the app, but you'll need to extract the 512x512 version for Play Store.

## Method 4: Online Tools

You can also use online SVG to PNG converters:

1. Export the vector drawables as SVG (if needed)
2. Use tools like:
   - [CloudConvert](https://cloudconvert.com/svg-to-png)
   - [Convertio](https://convertio.co/svg-png/)
   - [SVG2PNG](https://svgtopng.com/)

Set output size to 512x512 pixels.

## Icon Design Details

The ChoreQuest icon features:

- **Background:** Gradient from Sky Blue (#4A90E2) to Purple (#9B59B6) with Sunshine Yellow accent
- **Foreground:** White shield/badge with:
  - Green checkmark (completed chores)
  - Yellow star (quest/adventure theme)
  - Light blue inner highlight

## File Locations

- Vector drawables: `app/src/main/res/drawable/`
  - `ic_launcher_background.xml`
  - `ic_launcher_foreground.xml`
- HTML generator: `generate_play_store_icon.html`
- Python script: `generate_icons.py`

## Quick Start (Recommended)

**Fastest method:** Use the HTML generator (`generate_play_store_icon.html`)

1. Double-click the HTML file
2. Click "Download PNG Icon"
3. Upload to Play Store Console

That's it! 🎉
