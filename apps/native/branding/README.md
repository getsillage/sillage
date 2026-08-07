# Native application branding

The SVG files in this directory compose the existing Sillage product mark for
platform app-icon requirements. They do not define a separate logo system:

- `sillage-app-icon-ios.svg` is full bleed because iOS applies its own mask and
  App Store icons cannot contain an alpha channel.
- `sillage-app-icon-desktop.svg` includes the rounded tile and transparent outer
  canvas expected by desktop launchers.

On macOS, regenerate every committed native icon artifact with:

```bash
apps/native/branding/generate-icons.sh
```

The script uses system `sips`, `iconutil`, and the small `write-ico.swift`
container writer to produce the iOS AppIcon PNG set, multi-resolution
`Sillage.icns`, and multi-resolution `Sillage.ico`. Commit the generated files
so Linux and Windows verification hosts do not need Apple conversion tools.

Run `cd apps/native && ./gradlew checkNativeIdentity` after changing the mark,
colors, dimensions, product version, or platform icon wiring.
