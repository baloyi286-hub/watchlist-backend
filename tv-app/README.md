# WatchCue TV

Native Android/Google TV companion for WatchCue. It connects outbound to the production backend, so no PC/Mac bridge or inbound TV port is required.

## Build

Install Android Studio/JDK 17, then from tv-app:

    gradlew.bat assembleDebug -PWATCHCUE_TV_BRIDGE_KEY=YOUR_TV_BRIDGE_KEY

APK:

    app\build\outputs\apk\debug\app-debug.apk

## Install with ADB

Enable Developer Options and USB/network debugging on the TV, then:

    adb connect TV_IP:5555
    adb install -r app\build\outputs\apk\debug\app-debug.apk

Launch WatchCue TV once and allow notifications. The app polls the WatchCue backend every 15 seconds while running, displays queued reminders as native high-priority TV notifications, acknowledges delivered jobs, and calls /tv/online when opened.

The bridge key is compiled into this private APK. Do not commit the key or publish the APK.
