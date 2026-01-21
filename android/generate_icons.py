#!/usr/bin/env python3
"""
ChoreQuest Icon Generator
Generates PNG icons for Play Store submission from vector drawables.

Requirements:
    pip install cairosvg pillow

Usage:
    python generate_icons.py
"""

import os
import sys

try:
    from PIL import Image, ImageDraw
    import cairosvg
except ImportError:
    print("Error: Missing required packages.")
    print("Install with: pip install cairosvg pillow")
    sys.exit(1)

def create_play_store_icon(size=512):
    """Create a Play Store icon (512x512 PNG with no transparency)"""
    # Create image with white background (no transparency)
    img = Image.new('RGB', (size, size), color='#FFFFFF')
    draw = ImageDraw.Draw(img)
    
    # Draw background gradient
    # Sky Blue to Purple gradient
    for y in range(size):
        ratio = y / size
        r1, g1, b1 = 0x4A, 0x90, 0xE2  # Sky Blue
        r2, g2, b2 = 0x9B, 0x59, 0xB6  # Purple
        
        r = int(r1 + (r2 - r1) * ratio)
        g = int(g1 + (g2 - g1) * ratio)
        b = int(b1 + (b2 - b1) * ratio)
        
        color = (r, g, b)
        draw.rectangle([(0, y), (size, y + 1)], fill=color)
    
    # Draw yellow accent triangle
    yellow_overlay = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    yellow_draw = ImageDraw.Draw(yellow_overlay)
    yellow_draw.polygon([(0, 0), (size, size // 2), (0, size)], 
                       fill=(255, 217, 61, 76))  # 30% opacity
    img = Image.alpha_composite(img.convert('RGBA'), yellow_overlay).convert('RGB')
    
    # Draw purple overlay
    purple_overlay = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    purple_draw = ImageDraw.Draw(purple_overlay)
    purple_draw.rectangle([(0, 0), (size, size)], 
                         fill=(155, 89, 182, 153))  # 60% opacity
    img = Image.alpha_composite(img.convert('RGBA'), purple_overlay).convert('RGB')
    
    # Scale factor for foreground elements
    scale = size / 108
    foreground_scale = 0.7 * scale
    translate_x = 16.2 * scale
    translate_y = 16.2 * scale
    
    # Create foreground layer
    foreground = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(foreground)
    
    # Shield shape (simplified path)
    shield_points = []
    base_x = int(54 * foreground_scale + translate_x)
    base_y = int(54 * foreground_scale + translate_y)
    shield_size = int(26 * foreground_scale)
    
    # Draw shield
    shield_path = [
        (base_x - shield_size, base_y - shield_size * 0.6),
        (base_x - shield_size * 0.7, base_y - shield_size * 0.9),
        (base_x, base_y - shield_size * 1.1),
        (base_x + shield_size * 0.7, base_y - shield_size * 0.9),
        (base_x + shield_size, base_y - shield_size * 0.6),
        (base_x + shield_size, base_y + shield_size * 0.3),
        (base_x, base_y + shield_size * 0.8),
        (base_x - shield_size, base_y + shield_size * 0.3),
    ]
    fg_draw.polygon(shield_path, fill='#FFFFFF')
    
    # Inner highlight
    inner_shield = [(int(p[0] * 0.85 + base_x * 0.15), int(p[1] * 0.85 + base_y * 0.15)) 
                    for p in shield_path]
    fg_draw.polygon(inner_shield, fill='#E8F4FD')
    
    # Checkmark
    check_size = int(12 * foreground_scale)
    check_points = [
        (base_x - check_size, base_y),
        (base_x - check_size * 0.3, base_y + check_size * 0.5),
        (base_x + check_size * 0.8, base_y - check_size * 0.8),
        (base_x + check_size * 0.6, base_y - check_size),
        (base_x - check_size * 0.3, base_y + check_size * 0.2),
        (base_x - check_size * 0.6, base_y - check_size * 0.2),
    ]
    fg_draw.polygon(check_points, fill='#27AE60')
    
    # Star
    star_x = base_x
    star_y = int((54 - 34) * foreground_scale + translate_y)
    star_radius = int(6 * foreground_scale)
    star_points = []
    for i in range(10):
        angle = (i * 3.14159 / 5) - 1.5708
        radius = star_radius if i % 2 == 0 else star_radius * 0.4
        x = star_x + int(radius * (1 if i % 2 == 0 else -1) * 0.7)
        y = star_y + int(radius * (1 if i % 2 == 0 else -1) * 0.7)
        star_points.append((x, y))
    fg_draw.polygon(star_points, fill='#FFD93D')
    
    # Composite foreground onto background
    img = Image.alpha_composite(img.convert('RGBA'), foreground).convert('RGB')
    
    return img

def main():
    """Generate icons for Play Store"""
    output_dir = 'play_store_icons'
    os.makedirs(output_dir, exist_ok=True)
    
    sizes = {
        512: 'chorequest_icon_512x512.png',  # Play Store required
        1024: 'chorequest_icon_1024x1024.png',  # High-res option
    }
    
    print("🎮 Generating ChoreQuest Play Store Icons...")
    print()
    
    for size, filename in sizes.items():
        print(f"Generating {size}x{size} icon...")
        icon = create_play_store_icon(size)
        output_path = os.path.join(output_dir, filename)
        icon.save(output_path, 'PNG', quality=100)
        print(f"✓ Saved: {output_path}")
    
    print()
    print("✅ Icon generation complete!")
    print(f"📁 Icons saved in: {output_dir}/")
    print()
    print("For Play Store submission, use: chorequest_icon_512x512.png")

if __name__ == '__main__':
    main()
