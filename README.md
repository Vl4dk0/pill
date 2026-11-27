# Pill

Pill draws a small black pill around the camera hole of your phone, like the Dynamic Island on an iPhone.

It is a personal project. I made it for the Nothing Phone (2) on Android 16. Other phones may look wrong.

## What it shows

- Messages: read them, reply, snooze, open the chat.
- Calls: answer, decline, see the ongoing call.
- Music: controls for any player app.
- Charger: a short note when you plug in.
- Navigation and alarms.
- Menus: an app menu, quick actions and recent apps, opened by tapping the pill.
- Pixel pets (cat, dog, bird, monkey, Clawd) that live in the idle pill and react to events.

## Privacy

Pill has no INTERNET permission (see `pill/app/src/main/AndroidManifest.xml`). It cannot send data off your phone.

## Install

1. On the phone, download the APK from the Releases page of this repo.
2. Allow "Install unknown apps" for your browser or file manager. Open the APK.
3. Open Pill. Turn on two permissions by hand:
   - Settings > Accessibility > Pill.
   - Settings > Notifications > Device & app notifications > Pill (notification access).
4. If Android greys these out, use "Allow restricted settings": Settings > Apps > Pill > three dots > Allow restricted settings. Then try again.

## Updates

Use Obtainium. It installs and updates apps from GitHub Releases.

1. Install Obtainium.
2. Add an app with this URL: `https://github.com/Vl4dk0/pill`

## Build from source

Use the JDK that comes with Android Studio.

```
cd pill
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew --no-daemon assembleRelease
```

The APK is in `pill/app/build/outputs/apk/release/`. Without a release key it is signed with the debug key. To sign with your own key, create `~/.pill-signing/keystore.properties` with the keys `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

Developer notes: `pill/README.md`.

## Licence

There is no licence file. All rights are reserved.
