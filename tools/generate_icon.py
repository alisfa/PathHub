import math
import os
from PIL import Image, ImageDraw, ImageFilter

def create_orange_bathtub_icon(size=1024):
    # Full-bleed square canvas (iOS & Android apply their own squircle masks)
    img = Image.new("RGBA", (size, size), (13, 17, 24, 255)) # Luxury obsidian dark slate

    # 1. Subtle background gradient (deep obsidian top to dark slate bottom)
    bg_draw = ImageDraw.Draw(img)
    for y in range(size):
        ratio = y / size
        # from (11, 15, 23) to (18, 24, 38)
        r = int(11 + ratio * 8)
        g = int(15 + ratio * 10)
        b = int(23 + ratio * 16)
        bg_draw.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # 2. Warm ambient orange radial halo behind the tub
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow)
    cx, cy = size // 2, int(size * 0.52)
    max_r = int(size * 0.44)
    for r in range(max_r, 0, -8):
        alpha = int(70 * ((1.0 - r / max_r) ** 1.7))
        g_draw.ellipse(
            [cx - int(r * 1.25), cy - int(r * 0.85), cx + int(r * 1.25), cy + int(r * 0.85)],
            fill=(255, 120, 0, alpha)
        )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=30))
    img.paste(Image.alpha_composite(img, glow))

    # 3. Ambient ground shadow under tub feet
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.ellipse(
        [int(size * 0.22), int(size * 0.74), int(size * 0.78), int(size * 0.83)],
        fill=(0, 0, 0, 200)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=18))
    img.paste(Image.alpha_composite(img, shadow))

    # 4. Bathtub Feet (Sculpted claw/modern block feet with specular highlights)
    draw = ImageDraw.Draw(img)
    foot_color = (210, 75, 0, 255) # Deep orange
    foot_rim = (255, 140, 20, 255) # Bright orange highlight
    # Left foot
    lf = [int(size * 0.32), int(size * 0.70), int(size * 0.38), int(size * 0.78)]
    draw.rounded_rectangle(lf, radius=12, fill=foot_color)
    draw.line([lf[0] + 3, lf[1] + 4, lf[0] + 3, lf[3] - 4], fill=foot_rim, width=3)
    # Right foot
    rf = [int(size * 0.62), int(size * 0.70), int(size * 0.68), int(size * 0.78)]
    draw.rounded_rectangle(rf, radius=12, fill=foot_color)
    draw.line([rf[0] + 3, rf[1] + 4, rf[0] + 3, rf[3] - 4], fill=foot_rim, width=3)

    # 5. Bathtub Body (Smooth curved roll-top basin)
    # Using high-density polygon to produce a smooth curved bathtub
    body_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    b_draw = ImageDraw.Draw(body_img)

    top_y = int(size * 0.48)
    bot_y = int(size * 0.72)
    top_w = int(size * 0.66)
    bot_w = int(size * 0.46)
    center_x = size // 2

    # Generate smooth curved outline points
    points = []
    # Top rim under-edge
    steps = 40
    for i in range(steps + 1):
        t = i / steps
        px = int(center_x - top_w / 2 + t * top_w)
        # slight natural sag in middle
        py = int(top_y + 10 * math.sin(t * math.pi))
        points.append((px, py))

    # Right side curve down to bottom
    r_top = (int(center_x + top_w / 2), top_y)
    r_bot = (int(center_x + bot_w / 2), bot_y)
    for i in range(1, steps):
        t = i / steps
        # bezier curve inward
        px = int((1 - t) * r_top[0] + t * r_bot[0] + math.sin(t * math.pi) * 8)
        py = int((1 - t) * r_top[1] + t * r_bot[1])
        points.append((px, py))

    # Bottom curved base
    for i in range(steps, -1, -1):
        t = i / steps
        px = int(center_x - bot_w / 2 + t * bot_w)
        py = int(bot_y + 4 * math.sin(t * math.pi))
        points.append((px, py))

    # Left side curve up to top
    l_bot = (int(center_x - bot_w / 2), bot_y)
    l_top = (int(center_x - top_w / 2), top_y)
    for i in range(1, steps):
        t = i / steps
        px = int((1 - t) * l_bot[0] + t * l_top[0] - math.sin(t * math.pi) * 8)
        py = int((1 - t) * l_bot[1] + t * l_top[1])
        points.append((px, py))

    b_draw.polygon(points, fill=(255, 120, 0, 255))

    # Apply rich multi-stop vertical & horizontal lighting gradient to bathtub body
    for y in range(top_y - 5, bot_y + 15):
        y_ratio = max(0.0, min(1.0, (y - top_y) / (bot_y - top_y)))
        for x in range(int(center_x - top_w / 2) - 10, int(center_x + top_w / 2) + 10):
            if body_img.getpixel((x, y))[3] > 0:
                x_ratio = (x - (center_x - top_w / 2)) / top_w
                # Highlight on the left (x_ratio ~ 0.25), deep rich shadow on the right/bottom
                shine = math.exp(-((x_ratio - 0.22) ** 2) / 0.035) * 45
                shadow = max(0.0, (x_ratio - 0.55)) * 40

                # Warm orange tones:
                # Top: (255, 155, 15) -> Bottom: (215, 60, 0)
                r = int(min(255, max(180, (255 - y_ratio * 40 + shine - shadow * 0.4))))
                g = int(min(220, max(50, (155 - y_ratio * 95 + shine * 0.9 - shadow))))
                b = int(min(120, max(0, (15 - y_ratio * 15 + shine * 0.5))))
                body_img.putpixel((x, y), (r, g, b, 255))

    img.paste(Image.alpha_composite(img, body_img))

    # 6. Sleek Glassy Specular Highlight Stripe on the left of tub body
    spec = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(spec)
    s_x1 = int(center_x - top_w * 0.36)
    s_x2 = int(center_x - top_w * 0.28)
    s_draw.polygon([
        (s_x1, top_y + 18),
        (s_x2, top_y + 18),
        (s_x2 - 12, bot_y - 12),
        (s_x1 - 12, bot_y - 12)
    ], fill=(255, 255, 255, 75))
    spec = spec.filter(ImageFilter.GaussianBlur(radius=8))
    img.paste(Image.alpha_composite(img, spec))

    # 7. Bathtub Rim (Dimensional rolled lip with liquid gloss)
    draw = ImageDraw.Draw(img)
    rim_w = int(top_w * 1.08)
    rim_h = int(size * 0.09)
    rim_box = [center_x - rim_w // 2, top_y - rim_h // 2, center_x + rim_w // 2, top_y + rim_h // 2]

    # Rim drop shadow
    draw.ellipse([rim_box[0] - 2, rim_box[1] + 4, rim_box[2] + 2, rim_box[3] + 8], fill=(160, 45, 0, 180))
    # Rim outer body
    draw.ellipse(rim_box, fill=(255, 145, 10, 255))
    # Rim top highlight gradient
    draw.ellipse([rim_box[0] + 4, rim_box[1] + 2, rim_box[2] - 4, rim_box[3] - 2], fill=(255, 175, 30, 255))

    # Inner basin depth (dark warm orange)
    inner_w = int(rim_w * 0.88)
    inner_h = int(rim_h * 0.72)
    inner_box = [center_x - inner_w // 2, top_y - inner_h // 2 + 1, center_x + inner_w // 2, top_y + inner_h // 2 + 1]
    draw.ellipse(inner_box, fill=(195, 60, 0, 255))

    # Bath Water Surface (Warm amber glowing water with surface shine)
    water_w = int(inner_w * 0.94)
    water_h = int(inner_h * 0.75)
    water_box = [center_x - water_w // 2, top_y - water_h // 2 + 3, center_x + water_w // 2, top_y + water_h // 2 + 3]
    draw.ellipse(water_box, fill=(255, 185, 50, 255))
    # Water reflection highlight
    draw.ellipse([water_box[0] + 20, water_box[1] + 3, water_box[2] - 20, water_box[3] - 3], fill=(255, 235, 170, 190))
    # Water ripple line
    draw.arc([water_box[0] + 35, water_box[1] + 4, water_box[2] - 35, water_box[3] - 2], start=10, end=170, fill=(255, 255, 240, 210), width=3)

    # Rim front specular edge
    draw.arc(rim_box, start=170, end=370, fill=(255, 235, 160, 255), width=4)

    # 8. High-End Chrome Gooseneck Faucet
    # Elegant silver/platinum faucet with bright highlights
    faucet_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    f_draw = ImageDraw.Draw(faucet_img)
    f_base_x = int(center_x - top_w * 0.38)
    f_base_y = top_y + 4
    f_top_y = int(top_y - size * 0.18)
    f_spout_x = int(center_x - top_w * 0.24)
    pipe_w = 14

    # Vertical pipe
    f_draw.line([f_base_x, f_base_y, f_base_x, f_top_y + 35], fill=(225, 235, 245, 255), width=pipe_w)
    # Arc curve to right
    f_draw.arc([f_base_x, f_top_y, f_base_x + 70, f_top_y + 70], start=180, end=290, fill=(225, 235, 245, 255), width=pipe_w)
    # Horizontal top pipe
    f_draw.line([f_base_x + 35, f_top_y, f_spout_x - 10, f_top_y], fill=(225, 235, 245, 255), width=pipe_w)
    # Downward spout curve
    f_draw.arc([f_spout_x - 40, f_top_y, f_spout_x + 10, f_top_y + 50], start=270, end=360, fill=(225, 235, 245, 255), width=pipe_w)
    f_draw.line([f_spout_x + 5, f_top_y + 25, f_spout_x + 5, f_top_y + 40], fill=(225, 235, 245, 255), width=pipe_w)

    # Faucet specular highlight core (bright white center line)
    f_draw.line([f_base_x - 2, f_base_y, f_base_x - 2, f_top_y + 35], fill=(255, 255, 255, 230), width=4)
    f_draw.line([f_base_x + 35, f_top_y - 2, f_spout_x - 10, f_top_y - 2], fill=(255, 255, 255, 230), width=4)

    # Water drop falling from faucet
    f_draw.ellipse([f_spout_x + 2, f_top_y + 55, f_spout_x + 8, f_top_y + 64], fill=(255, 240, 180, 240))

    img.paste(Image.alpha_composite(img, faucet_img))
    draw = ImageDraw.Draw(img)

    # 9. Iridescent Bath Bubbles (Luminous pearl-amber foam floating upwards)
    bubbles = [
        # (cx, cy, radius, opacity)
        (int(center_x - size * 0.08), int(top_y - size * 0.06), 28, 240),
        (int(center_x + size * 0.02), int(top_y - size * 0.09), 36, 250),
        (int(center_x + size * 0.12), int(top_y - size * 0.05), 30, 240),
        (int(center_x + size * 0.22), int(top_y - size * 0.10), 38, 255),
        (int(center_x + size * 0.31), int(top_y - size * 0.04), 26, 230),
        # Higher floating bubbles
        (int(center_x + size * 0.06), int(top_y - size * 0.19), 22, 220),
        (int(center_x + size * 0.25), int(top_y - size * 0.22), 24, 210),
        (int(center_x + size * 0.16), int(top_y - size * 0.27), 18, 200),
        (int(center_x - size * 0.01), int(top_y - size * 0.24), 16, 190),
    ]

    bubble_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    b_draw = ImageDraw.Draw(bubble_layer)

    for cx, cy, r, a in bubbles:
        # Soft ambient glow behind each bubble
        b_draw.ellipse([cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4], fill=(255, 140, 20, int(a * 0.25)))
        # Bubble body: luminous warm pearl
        b_draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 180, 70, int(a * 0.65)))
        # Bubble outline with specular rim
        b_draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 235, 180, a), width=3)
        # Specular glint
        glint_r = max(3, r // 4)
        b_draw.ellipse([cx - r // 2 - glint_r, cy - r // 2 - glint_r, cx - r // 2 + glint_r, cy - r // 2 + glint_r], fill=(255, 255, 255, 245))

    img.paste(Image.alpha_composite(img, bubble_layer))

    return img

def export_all_icons():
    master = create_orange_bathtub_icon(1024)
    os.makedirs("assets", exist_ok=True)
    master.save("assets/app_icon_1024.png", "PNG")
    print("Exported master: assets/app_icon_1024.png")

    # 1. iOS App Icons
    ios_sizes = [
        ("ios/xtool/PathHub.app/AppIcon60x60@2x.png", 120),
        ("ios/xtool/PathHub.app/AppIcon60x60@3x.png", 180),
        ("ios/xtool/PathHub.app/AppIcon76x76@2x~ipad.png", 152),
        ("ios/xtool/PathHub.app/AppIcon.png", 180),
    ]

    for path, sz in ios_sizes:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        resized = master.resize((sz, sz), Image.Resampling.LANCZOS)
        resized.save(path, "PNG")
        print(f"Exported iOS icon: {path} ({sz}x{sz})")

    # 2. Android Mipmap Icons
    android_sizes = [
        ("android/app/src/main/res/mipmap-mdpi", 48),
        ("android/app/src/main/res/mipmap-hdpi", 72),
        ("android/app/src/main/res/mipmap-xhdpi", 96),
        ("android/app/src/main/res/mipmap-xxhdpi", 144),
        ("android/app/src/main/res/mipmap-xxxhdpi", 192),
    ]

    for dir_path, sz in android_sizes:
        os.makedirs(dir_path, exist_ok=True)
        resized = master.resize((sz, sz), Image.Resampling.LANCZOS)
        # Standard launcher icon
        resized.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")

        # Round launcher icon for circular launchers
        mask = Image.new("L", (sz, sz), 0)
        m_draw = ImageDraw.Draw(mask)
        m_draw.ellipse([0, 0, sz, sz], fill=255)
        round_icon = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
        round_icon.paste(resized, (0, 0), mask)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")

        print(f"Exported Android icons in {dir_path} ({sz}x{sz})")

if __name__ == "__main__":
    export_all_icons()
