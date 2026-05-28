# PoGo Map

PoGo Map is a personal Android map viewer for nearby Pokemon GO activity. It uses Google Maps for the map surface and Niantic Campfire map data to show game objects in the visible area.

The app is built for quick field use: open the map, pan or zoom to the area you care about, and filter the visible Pokemon GO objects without jumping between apps.

## Features

- Google Maps-based map view
- Campfire token sign-in field stored locally on the device
- Visible-map loading based on S2 cells
- Map markers for gyms, raids, Max Battles, routes, Pokestops, and live events
- Raid filters for egg raids, boss raids, ratings, names, and special raid types
- Marker detail sheets with raid and object metadata
- Saved camera position between app launches
- Debug view for loaded object and marker counts

## Requirements

- Android 8.0 or newer
- A Google Maps API key
- A valid Campfire bearer token

## Local Setup

Create a local properties file from the example:

```bash
cp local.properties.example local.properties
```

Add your Google Maps API key:

```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

`local.properties` is ignored by git and should not be committed.

## Build

Build a debug APK:

```bash
gradle assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases

This repo includes a GitHub Actions workflow that builds a signed release APK when a version tag is pushed.

Required GitHub Actions secrets:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
MAPS_API_KEY
```

To publish a release APK:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow creates a GitHub Release and uploads `PoGoMap-v0.1.0.apk`, which can be picked up by GitHub-release-based Android app stores.

Before each new release, increment `versionCode` and update `versionName` in `app/build.gradle.kts`.

## Notes

Keep your Android signing keystore safe. Future app updates must be signed with the same key, or Android will reject them as updates to the existing install.

This is an unofficial personal tool and is not affiliated with Niantic, Campfire, Pokemon GO, Nintendo, The Pokemon Company, or Google.
