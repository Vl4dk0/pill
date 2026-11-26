#!/bin/zsh
# Installs Pill on a real phone. The OWNER runs this by hand: phone-install.sh <adb-serial>
# It installs only Pill (not the fake test apps) and gives it the permissions adb may give.
# It never writes the accessibility or notification-listener lists: that would overwrite your other services.
set -e
cd "${0:A:h}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
PKG=sk.vladko.pill

serial=$1
[[ -n $serial ]] || { echo "usage: phone-install.sh <adb-serial>   (see: adb devices)" >&2; exit 2; }
[[ $serial != emulator-* ]] || { echo "refusing: $serial is an emulator; this script is for a real phone" >&2; exit 2; }
adb devices | grep -q "^${serial}[[:space:]]device" || { echo "phone $serial is not connected and authorised (adb devices)" >&2; exit 1; }

./gradlew -q assembleRelease

is_awake() {
  local wake=$(adb -s $serial shell dumpsys power | tr -d '\r' | awk -F= '/^[[:space:]]*mWakefulness=[A-Za-z]/{print $2; exit}')
  [[ -z $wake || $wake == Awake ]]
}
# Killing the accessibility service during a touch can leave Android's input filter stuck until reboot.
if is_awake; then
  echo "Turn the phone screen off to install (an install during a touch can block touch until a reboot)"
  while is_awake; do sleep 1; done
fi

adb -s $serial install -r app/build/outputs/apk/release/app-release.apk
adb -s $serial shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
adb -s $serial shell appops set $PKG GET_USAGE_STATS allow
# Lets Pill switch auto-rotate, brightness and screen timeout.
adb -s $serial shell appops set $PKG WRITE_SETTINGS allow
# Lets Pill switch Do Not Disturb.
adb -s $serial shell cmd notification allow_dnd $PKG

cat <<MSG

Pill is installed. Turn on two things by hand on the phone:
  1. Settings > Accessibility > Installed apps > Pill > turn on.
     (Android may grey it out: Settings > Apps > Pill > three dots > Allow restricted settings, then retry.)
  2. Settings > Notifications > Device & app notifications > Pill > allow.
     (Same "Allow restricted settings" rule applies.)
Then open the Pill app: all three lines should say On.
MSG
