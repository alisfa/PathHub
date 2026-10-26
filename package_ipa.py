import os
import zipfile
import time
from PIL import Image, ImageDraw
import sys

# Ensure tools directory is on python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tools.generate_icon import create_orange_bathtub_icon

def create_app_icons(app_dir):
    """Generates official Path Hub orange bathtub app icons for iOS."""
    master = create_orange_bathtub_icon(1024)
    sizes = [
        ("AppIcon60x60@2x.png", 120),
        ("AppIcon60x60@3x.png", 180),
        ("AppIcon76x76@2x~ipad.png", 152),
        ("AppIcon.png", 180),
    ]

    for filename, size in sizes:
        resized = master.resize((size, size), Image.Resampling.LANCZOS)
        out_path = os.path.join(app_dir, filename)
        resized.save(out_path, "PNG")

def create_enhanced_info_plist(app_dir):
    plist_path = os.path.join(app_dir, "Info.plist")
    content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleDisplayName</key>
	<string>Path Hub</string>
	<key>CFBundleExecutable</key>
	<string>PathHub</string>
	<key>CFBundleIdentifier</key>
	<string>com.pathhub.app</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>PathHub</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleSignature</key>
	<string>????</string>
	<key>CFBundleShortVersionString</key>
	<string>1.0.4</string>
	<key>CFBundleVersion</key>
	<string>5</string>
	<key>CFBundleSupportedPlatforms</key>
	<array>
		<string>iPhoneOS</string>
	</array>
	<key>MinimumOSVersion</key>
	<string>15.0</string>
	<key>LSRequiresIPhoneOS</key>
	<true/>
	<key>UIDeviceFamily</key>
	<array>
		<integer>1</integer>
		<integer>2</integer>
	</array>
	<key>CFBundleIcons</key>
	<dict>
		<key>CFBundlePrimaryIcon</key>
		<dict>
			<key>CFBundleIconFiles</key>
			<array>
				<string>AppIcon60x60</string>
			</array>
			<key>CFBundleIconName</key>
			<string>AppIcon</string>
		</dict>
	</dict>
	<key>CFBundleIconFile</key>
	<string>AppIcon</string>
	<key>CFBundleIconFiles</key>
	<array>
		<string>AppIcon60x60</string>
		<string>AppIcon76x76</string>
	</array>
	<key>UILaunchScreen</key>
	<dict/>
	<key>UIRequiredDeviceCapabilities</key>
	<array>
		<string>arm64</string>
	</array>
	<key>UISupportedInterfaceOrientations</key>
	<array>
		<string>UIInterfaceOrientationPortrait</string>
		<string>UIInterfaceOrientationLandscapeLeft</string>
		<string>UIInterfaceOrientationLandscapeRight</string>
	</array>
	<key>UISupportedInterfaceOrientations~ipad</key>
	<array>
		<string>UIInterfaceOrientationPortrait</string>
		<string>UIInterfaceOrientationPortraitUpsideDown</string>
		<string>UIInterfaceOrientationLandscapeLeft</string>
		<string>UIInterfaceOrientationLandscapeRight</string>
	</array>
	<key>LSApplicationQueriesSchemes</key>
	<array>
		<string>livecontainer</string>
		<string>warp</string>
	</array>
</dict>
</plist>
"""
    with open(plist_path, "w", encoding="utf-8") as f:
        f.write(content)

def build_compliant_ipa(app_dir, output_ipa_path):
    print(f"Packaging {app_dir} -> {output_ipa_path}...")
    create_app_icons(app_dir)
    create_enhanced_info_plist(app_dir)

    dt = time.localtime(time.time())[:6]

    with zipfile.ZipFile(output_ipa_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zipf:
        # Add Payload/ directory entry
        payload_entry = zipfile.ZipInfo("Payload/", dt)
        payload_entry.create_system = 3 # UNIX
        payload_entry.external_attr = 0o040755 << 16 | 0x10 # Directory
        payload_entry.extra = b''
        zipf.writestr(payload_entry, '')

        # Add Payload/PathHub.app/ directory entry
        app_entry = zipfile.ZipInfo("Payload/PathHub.app/", dt)
        app_entry.create_system = 3 # UNIX
        app_entry.external_attr = 0o040755 << 16 | 0x10 # Directory
        app_entry.extra = b''
        zipf.writestr(app_entry, '')

        # Walk through app directory and add files with strict Unix permissions
        for root, dirs, files in os.walk(app_dir):
            rel_root = os.path.relpath(root, app_dir)
            if rel_root != ".":
                dir_arcname = f"Payload/PathHub.app/{rel_root}/".replace("\\", "/")
                d_entry = zipfile.ZipInfo(dir_arcname, dt)
                d_entry.create_system = 3 # UNIX
                d_entry.external_attr = 0o040755 << 16 | 0x10
                d_entry.extra = b''
                zipf.writestr(d_entry, '')

            for file in files:
                file_path = os.path.join(root, file)
                if rel_root == ".":
                    arcname = f"Payload/PathHub.app/{file}".replace("\\", "/")
                else:
                    arcname = f"Payload/PathHub.app/{rel_root}/{file}".replace("\\", "/")

                with open(file_path, "rb") as f:
                    file_data = f.read()

                f_entry = zipfile.ZipInfo(arcname, dt)
                f_entry.create_system = 3 # UNIX
                f_entry.compress_type = zipfile.ZIP_DEFLATED
                # If executable Mach-O binary
                if file == "PathHub":
                    f_entry.external_attr = 0o100755 << 16
                else:
                    f_entry.external_attr = 0o100644 << 16
                f_entry.extra = b''
                zipf.writestr(f_entry, file_data)

    print(f"[OK] Compliant IPA created successfully at {output_ipa_path} (Size: {os.path.getsize(output_ipa_path)} bytes)")

if __name__ == "__main__":
    app_directory = os.path.abspath("ios/xtool/PathHub.app")
    out_ipa = os.path.abspath("PathHub-unsigned.ipa")
    build_compliant_ipa(app_directory, out_ipa)
    # Also copy to ios/xtool/PathHub.ipa
    import shutil
    shutil.copyfile(out_ipa, os.path.abspath("ios/xtool/PathHub.ipa"))
