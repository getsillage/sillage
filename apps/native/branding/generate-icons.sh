#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
native_dir=$(dirname "$script_dir")
ios_source="$script_dir/sillage-app-icon-ios.svg"
desktop_source="$script_dir/sillage-app-icon-desktop.svg"
ios_output="$native_dir/iosApp/Sillage/Assets.xcassets/AppIcon.appiconset"
desktop_output="$native_dir/desktopApp/src/main/resources"
temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/sillage-native-icons.XXXXXX")

cleanup() {
  rm -rf "$temporary_dir"
}
trap cleanup EXIT INT TERM

command -v sips >/dev/null
command -v iconutil >/dev/null
command -v xcrun >/dev/null

mkdir -p "$ios_output" "$desktop_output"

# iOS App Store icons must not contain an alpha channel. Rendering through a
# high-quality JPEG intermediate flattens the full-bleed SVG background before
# the final PNG sizes are emitted.
sips -s format png -z 1024 1024 "$ios_source" --out "$temporary_dir/ios-source.png" >/dev/null
sips -s format jpeg -s formatOptions 100 "$temporary_dir/ios-source.png" --out "$temporary_dir/ios-opaque.jpg" >/dev/null
sips -s format png "$temporary_dir/ios-opaque.jpg" --out "$temporary_dir/ios-opaque.png" >/dev/null

while IFS=: read -r size filename; do
  sips -z "$size" "$size" "$temporary_dir/ios-opaque.png" --out "$ios_output/$filename" >/dev/null
done <<'EOF'
20:AppIcon-20.png
29:AppIcon-29.png
40:AppIcon-20@2x.png
40:AppIcon-40.png
58:AppIcon-29@2x.png
60:AppIcon-20@3x.png
76:AppIcon-76.png
80:AppIcon-40@2x.png
87:AppIcon-29@3x.png
120:AppIcon-40@3x.png
120:AppIcon-60@2x.png
152:AppIcon-76@2x.png
167:AppIcon-83.5@2x.png
180:AppIcon-60@3x.png
1024:AppIcon-1024.png
EOF

sips -s format png -z 1024 1024 "$desktop_source" --out "$temporary_dir/desktop-1024.png" >/dev/null
iconset="$temporary_dir/Sillage.iconset"
mkdir -p "$iconset"

while IFS=: read -r size filename; do
  sips -z "$size" "$size" "$temporary_dir/desktop-1024.png" --out "$iconset/$filename" >/dev/null
done <<'EOF'
16:icon_16x16.png
32:icon_16x16@2x.png
32:icon_32x32.png
64:icon_32x32@2x.png
128:icon_128x128.png
256:icon_128x128@2x.png
256:icon_256x256.png
512:icon_256x256@2x.png
512:icon_512x512.png
1024:icon_512x512@2x.png
EOF

iconutil -c icns "$iconset" -o "$desktop_output/Sillage.icns"

windows_iconset="$temporary_dir/windows"
mkdir -p "$windows_iconset"
windows_arguments=""
for size in 16 24 32 48 64 128 256; do
  image="$windows_iconset/icon-$size.png"
  sips -z "$size" "$size" "$temporary_dir/desktop-1024.png" --out "$image" >/dev/null
  windows_arguments="$windows_arguments $size=$image"
done
# The paths are created by mktemp and contain no shell metacharacters. Splitting
# the generated SIZE=PATH list supplies one argument per ICO image.
# shellcheck disable=SC2086
xcrun swift "$script_dir/write-ico.swift" "$desktop_output/Sillage.ico" $windows_arguments

printf 'Generated iOS, macOS, and Windows application icons.\n'
