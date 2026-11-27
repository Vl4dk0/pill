#!/bin/zsh
# One-word verbs for an AI agent to drive Pill on the emulator. Never targets a real phone:
# the target is always the first "emulator-NNNN" adb device, so a phone's serial is never used.
set -e
SELF=${0:A}
cd "${0:A:h}"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
PKG=sk.vladko.pill
A11Y=$PKG/$PKG.PillAccessibilityService
NLS=$PKG/$PKG.PillNotificationListener
FAKE=sk.vladko.fakeplayer

serial() {
  local s=$(adb devices | awk '/^emulator-[0-9]+\tdevice/{print $1; exit}')
  [[ -n $s ]] || { echo "no emulator running" >&2; exit 1; }
  echo $s
}
a() { adb -s "$(serial)" "$@"; }

# Centre of the visible pill, read from `state`, so no verb needs coordinates.
pill_xy() {
  local b=$(state_json | jq -c '.bounds')
  [[ $b == "[0,0,0,0]" || -z $b ]] && { echo "pill is not visible" >&2; exit 1; }
  echo $b | jq -r '"\(((.[0]+.[2])/2)|floor) \(((.[1]+.[3])/2)|floor)"'
}
state_json() { a shell dumpsys activity service $A11Y | grep '^ *{' | sed 's/^ *//'; }
# Centre of a named button in the large view.
button_xy() {
  local b=$(state_json | jq -c --arg n "$1" '.buttons[$n]')
  [[ $b == null || -z $b ]] && { echo "no button '$1'; see: pill.sh state | jq .buttons" >&2; exit 1; }
  echo $b | jq -r '"\(((.[0]+.[2])/2)|floor) \(((.[1]+.[3])/2)|floor)"'
}
unsnooze_all() {
  local k
  for k in ${(f)"$(a shell dumpsys notification | grep -o "key: 0|$1|[^ ]*" | sed 's/^key: //' | tr -d '\r' | sort -u)"}; do
    a shell cmd notification unsnooze "'$k'" >/dev/null || true
  done
}
# Sends a command to Pill's receiver (only adb shell may) and prints its answer: ok, unavailable: ..., error: ...
command_() {
  local act=$1; shift
  local out=$(a shell am broadcast -n $PKG/$PKG.CommandReceiver -a $PKG.$act "$@" | sed -n 's/.*data="\(.*\)"/\1/p')
  echo ${out:-"error: no answer (is Pill installed?)"}
  [[ $out == ok ]]
}
# Every id `pill.sh pet` accepts; `reel` takes its list of actions from here.
PET_STATES='sit|look|walk|sleep|heart|stretch|groom|fidget|jump|hide|hidden|peek|lick|knead|bark|dig|wings|peck|scratch|banana|think|type|excited|tired|startle|dance|roam'
ms() { printf "%d.%03d" $(($1/1000)) $(($1%1000)); }
pkgof() { [[ $1 == (one|two) ]] || { echo "player must be one or two" >&2; exit 2 }; echo $FAKE.$1; }

# Records the emulator while the pet runs state $1 and cuts the frames of the pet's reach into $work/f%04d.png at $2 fps
# (default: the recording's real rate, rounded; never above 30). Sets: work b x0 y0 cw ch scale fps fr n first dur realfps.
# Optional: REELCROP="x0 y0 x1 y1" fixes the crop; KEEP="a|b" lists the states that all count as the action (default: $st);
# MAXS is the longest action in seconds (default 9).
# The caller sets the EXIT trap that removes $work (a trap set inside a function would fire when the function returns).
# first = the frame where the pet starts to move; dur = seconds the state lasted. Clears the recording on exit.
pet_capture() {
  zmodload zsh/mathfunc zsh/datetime
  local st=$1 sz W SH dens h box cell i
  $SELF pet sit >/dev/null || { echo "$verb: the pet is not shown (open the idle pill first)" >&2; exit 1 }
  sleep 1.5
  b=($(state_json | jq -r '.bounds | "\(.[0]) \(.[1]) \(.[2]) \(.[3])"')); local animal=$(state_json | jq -r .petAnimal)
  sz=$(a shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; SH=${sz#*x}
  dens=$(a shell wm density | tr -d '\r' | grep -o '[0-9]*$' | tail -1)
  h=$(( b[4]-b[2] ))
  # The area the pet reaches: roam goes up to 3 pill heights below the pill (the bird flies the whole screen width).
  if [[ $st == roam && $animal == bird ]]; then x0=0; x1=$W; y0=$(( b[2]-h*0.3 )); y1=$(( b[4]+h*0.1 ))
  elif [[ $st == roam ]]; then x0=$(( b[1]-h*1.2 )); x1=$(( b[3]+h*1.2 )); y0=$(( b[2]-h*0.5 )); y1=$(( b[4]+h*3 ))
  else x0=$(( b[1]-h )); x1=$(( b[3]+h )); y0=$(( b[2]-h )); y1=$(( b[4]+h*0.5 )); fi
  x0=$(( x0 < 0 ? 0 : int(x0) )); y0=$(( y0 < 0 ? 0 : int(y0) )); x1=$(( x1 > W ? W : int(x1) )); y1=$(( y1 > SH ? SH : int(y1) ))
  if [[ -n $REELCROP ]]; then local rc=(${=REELCROP}); x0=$rc[1]; y0=$rc[2]; x1=$rc[3]; y1=$rc[4]; fi
  cw=$(( (x1-x0)/2*2 )); ch=$(( (y1-y0)/2*2 ))
  # One pet cell is floor(0.95 * pill height / 20) px (the pill height is capped at 40.5 dp); enlarge to 6 px or more.
  box=$(( h < 40.5*dens/160 ? h : 40.5*dens/160 )); cell=$(( int(box*0.95/20) )); (( cell >= 1 )) || cell=1; scale=$(( (6+cell-1)/cell ))
  work=$(mktemp -d)
  a emu screenrecord start --time-limit 30 $work/rec.webm >/dev/null
  sleep 1
  $SELF pet $st || { echo "$verb: could not start '$st'" >&2; exit 1 }
  # The action lasts until the pet's state changes (at most 9 s: sleep and hidden last longer).
  local t0=$EPOCHREALTIME
  until [[ $(state_json | jq -r .petState) == $st ]] || (( EPOCHREALTIME - t0 > 3 )); do sleep 0.1; done
  until [[ $(state_json | jq -r .petState) != (${~${KEEP:-$st}}) ]] || (( EPOCHREALTIME - t0 > ${MAXS:-9} )); do sleep 0.2; done
  dur=$(( EPOCHREALTIME - t0 )); sleep 0.5
  a emu screenrecord stop >/dev/null; sleep 1
  [[ -s $work/rec.webm ]] || { echo "$verb: no recording" >&2; exit 1 }
  realfps=$(ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames:format=duration -of default=nw=1:nk=1 $work/rec.webm \
    | paste -sd' ' - | awk '{printf "%.1f", $1/$2}')
  fps=${2:-$(( int(rint(realfps)) > 30 ? 30 : int(rint(realfps)) ))}
  ffmpeg -v error -i $work/rec.webm -vf "fps=$fps,crop=$cw:$ch:$x0:$y0" $work/f%04d.png
  fr=($work/f*.png); n=$#fr
  # The action starts at the first frame unlike the still pet at the start.
  first=0
  for i in {5..$n}; do
    [[ $(magick compare -metric AE -fuzz 25% $fr[4] $fr[i] null: 2>&1 | sed 's/ .*//' || true) -gt 20 ]] && { first=$i; break }
  done
  # A still pet's blink is too small to detect: the reel starts a sit 1 s into the recording, where the state was set.
  (( first > 0 )) || { [[ $st == sit && -n $REELCROP ]] && first=$(( int(fps)+1 )) } || true
  [[ $(state_json | jq -r .petAnimal) == $animal ]] || { echo "$verb: the pet animal changed during the recording (another client uses the emulator)" >&2; exit 1 }
  (( first > 0 )) || { echo "$verb: the pet did not move (state '$st' may not apply to this animal)" >&2; exit 1 }
}

case $1 in
  boot)
    mkdir -p build
    nohup "$HOME/Library/Android/sdk/emulator/emulator" -avd Medium_Phone_API_37.0 -no-window -no-audio -no-boot-anim -no-snapshot \
      -gpu swiftshader_indirect >build/emulator.log 2>&1 &
    epid=$!
    until adb devices | grep -qE '^emulator-[0-9]+\sdevice'; do
      kill -0 $epid 2>/dev/null || { echo "emulator exited, see build/emulator.log" >&2; exit 1; }
      sleep 1
    done
    until [[ $(a shell getprop sys.boot_completed | tr -d '\r') == 1 ]]; do sleep 1; done
    a shell input keyevent KEYCODE_WAKEUP; a shell wm dismiss-keyguard; echo booted $(serial) ;;
  install)
    ./gradlew -q assembleDebug
    a install -r app/build/outputs/apk/debug/app-debug.apk
    for p in one two; do
      a install -r fakeplayer/build/outputs/apk/$p/debug/fakeplayer-$p-debug.apk
      a shell pm grant $FAKE.$p android.permission.POST_NOTIFICATIONS
    done ;;
  enable)
    a shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
    a shell appops set $PKG GET_USAGE_STATS allow
    a shell appops set $PKG WRITE_SETTINGS allow
    a shell cmd notification allow_dnd $PKG
    # Right after an install the system can drop or delay these writes, so retry until both stick.
    for i in {1..10}; do
      a shell settings put secure enabled_accessibility_services $A11Y
      a shell settings put secure accessibility_enabled 1
      a shell cmd notification allow_listener $NLS
      sleep 1
      [[ $(a shell settings get secure enabled_accessibility_services) == *$A11Y* &&
         $(a shell settings get secure enabled_notification_listeners) == *$NLS* ]] && { echo "enabled (try $i)"; exit 0; }
    done
    echo "enable failed" >&2; exit 1 ;;
  disable)
    # Turns the accessibility service off; Android then calls its onDestroy and Pill restores the pop-up setting.
    a shell settings delete secure enabled_accessibility_services
    sleep 1; echo disabled ;;
  shot) a exec-out screencap -p > "$2"; echo "$2" ;;
  tap)
    if [[ -n $2 ]]; then a shell input tap $2 $3; else a shell input tap ${(s: :)$(pill_xy)}; fi ;;
  doubletap)
    # Two taps inside one shell, so the gap between them stays short.
    a shell "input tap $2 $3; input tap $2 $3" ;;
  longpress)
    if [[ -n $2 ]]; then xy=($2 $3); else xy=(${(s: :)$(pill_xy)}); fi
    a shell "input motionevent DOWN $xy; sleep 1; input motionevent UP $xy" ;;
  tapleft|tapright|holdleft|holdright)
    # Left or right half of the visible pill, found from `state`: a quarter of its width from the centre.
    b=($(state_json | jq -r '.bounds | "\(.[0]) \(.[1]) \(.[2]) \(.[3])"'))
    [[ $b[3] -gt $b[1] ]] || { echo "pill is not visible" >&2; exit 1; }
    y=$(( (b[2]+b[4])/2 )); w=$(( b[3]-b[1] ))
    [[ $1 == *left ]] && x=$(( b[1]+w/4 )) || x=$(( b[1]+3*w/4 ))
    if [[ $1 == tap* ]]; then a shell input tap $x $y; else a shell "input motionevent DOWN $x $y; sleep 1; input motionevent UP $x $y"; fi ;;
  swipedown) xy=(${(s: :)$(pill_xy)}); a shell input swipe $xy[1] $xy[2] $xy[1] $(( xy[2]+120 )) 150 ;;
  volkeys)
    # Volume up + down pressed together. The emulator's `input keycombination` sends both at once.
    a shell input keycombination 24 25 ;;
  alarm)
    # A fake ringing alarm (default), a silent "upcoming alarm" (silent) or removes it (stop).
    p=$(pkgof one)
    lbl=${3:-Wake up}
    if [[ $2 == stop ]]; then a shell am broadcast -n $p/$FAKE.ChatReceiver -a $FAKE.ALARM_STOP --es button script >/dev/null
    else a shell am broadcast -n $p/$FAKE.ChatReceiver -a $FAKE.ALARM --es chat "${(qq)lbl}" --ez ring $([[ $2 == silent ]] && echo false || echo true) >/dev/null; fi ;;
  nav)
    # A fake Google Maps style navigation notification from fake app one, or removes it (stop). Buttons: navaction.
    p=$(pkgof one)
    if [[ $2 == stop ]]; then a shell am broadcast -n $p/$FAKE.ChatReceiver -a $FAKE.NAV_STOP >/dev/null
    else a shell am broadcast -n $p/$FAKE.ChatReceiver -a $FAKE.NAV --es title "${(qq)${2:-Head south-west on Main Street}}" --es sub "${(qq)${3:-Arrive 23:10}}" ${4:+--es text "${(qq)4}"} >/dev/null; fi ;;
  swipeup) xy=(${(s: :)$(pill_xy)}); a shell input swipe $xy[1] $xy[2] $xy[1] $(( xy[2] > 70 ? xy[2]-60 : 2 )) 120 ;;
  flick)
    # A real finger's flick up from the pill centre: <px> up (default 30, it may end above the screen) in <ms> (default 80).
    # A slow slide is the same with a longer time, e.g. flick 60 600.
    xy=(${(s: :)$(pill_xy)}); a shell input swipe $xy[1] $xy[2] $xy[1] $(( xy[2] - ${2:-30} )) ${3:-80} ;;
  swipeleft) xy=(${(s: :)$(pill_xy)}); a shell input swipe $xy[1] $xy[2] $(( xy[1]-160 )) $xy[2] 150 ;;
  swiperight) xy=(${(s: :)$(pill_xy)}); a shell input swipe $xy[1] $xy[2] $(( xy[1]+160 )) $xy[2] 150 ;;
  drag)
    # Press at x1,y1, hold, move to x2,y2, release.
    [[ -n $6 ]] || { echo "usage: pill.sh drag <x1> <y1> <x2> <y2> <hold-ms>" >&2; exit 2 }
    a shell "input motionevent DOWN $2 $3; sleep $(ms $6); input motionevent MOVE $(( ($2+$4)/2 )) $(( ($3+$5)/2 )); input motionevent MOVE $4 $5; sleep 0.3; input motionevent UP $4 $5" ;;
  button)
    xy=(${(s: :)$(button_xy $2)}); a shell input tap $xy ;;
  notify) a shell cmd notification post -S bigtext -t "${(qq)2}" pilltag "${(qq)3}" >/dev/null ;;
  chat)
    # A message from a fake chat app. Same chat name = same Android notification key.
    [[ -n $4 ]] || { echo "usage: pill.sh chat one|two <chat> <text> [read,mute,delete,archive,custom]" >&2; exit 2 }
    p=$(pkgof $2)
    # Android hides a snoozed key for 1 h and swallows new posts under it (and the snooze survives reboots),
    # so release this app's snoozed keys first.
    unsnooze_all $p
    a shell am broadcast -n $p/$FAKE.ChatReceiver -a $FAKE.CHAT --es chat "${(qq)3}" --es text "${(qq)4}" --es actions "${5:-read}" >/dev/null
    for i in {1..10}; do
      a shell dumpsys notification --noredact | grep -A60 "pkg=$p " | grep -qF -- "$4" && exit 0
      sleep 0.5
    done
    echo "chat: no notification from $p appeared (broadcast not delivered or key snoozed); see pill.sh logs" >&2; exit 1 ;;
  unsnooze) [[ $2 == all ]] && unsnooze_all "[^|]*" || unsnooze_all $(pkgof $2) ;;
  clearnotifs)
    # Releases every snoozed notification, then removes every notification that can be removed (old SMS, missed calls).
    unsnooze_all "[^|]*"; command_ CLEAR_NOTIFICATIONS ;;
  sms) a emu sms send "$2" "$3" ;;
  call) a emu gsm call "$2" ;;
  hangup) a emu gsm cancel "$2" ;;
  voip)
    # A fake VoIP call from fake chat app one: style = real CallStyle, dialing / connected = outgoing CallStyle without / with a running chronometer, plain = Slovak button titles and no CallStyle. Buttons: answer, decline, hangup.
    a shell am broadcast -n $(pkgof one)/$FAKE.ChatReceiver -a $FAKE.VOIP --es chat "${(qq)${2:-Caller}}" --es mode "${3:-style}" >/dev/null ;;
  lock) a shell input keyevent KEYCODE_SLEEP ;;
  unlock) a shell input keyevent KEYCODE_WAKEUP; sleep 1; a shell wm dismiss-keyguard ;;
  rotate)
    # Toggles portrait/landscape. The launcher stays portrait by itself; open an app first (fullscreen one off).
    cur=$(a shell dumpsys window displays | grep -o -m1 'mCurrentRotation=ROTATION_[0-9]*' | grep -o '[0-9]*$')
    if [[ $cur != 0 ]]; then a shell wm user-rotation lock 0; echo portrait
    else a shell wm user-rotation lock 1; echo landscape; fi ;;
  fullscreen)
    # Opens a fake app with the status bar hidden (on) or shown (off).
    p=$(pkgof $2)
    [[ $3 == on ]] && fs=true || fs=false
    a shell am start -S -n $p/$FAKE.ScreenActivity --ez fullscreen $fs >/dev/null ;;
  home) a shell input keyevent KEYCODE_HOME ;;
  settings) a shell am start -n $PKG/$PKG.MainActivity >/dev/null ;;
  charge) [[ $2 == on ]] && a shell dumpsys battery set ac 1 || a shell dumpsys battery unplug ;;
  battery)
    if [[ $2 == reset ]]; then a shell dumpsys battery reset
    elif [[ $2 =~ ^[0-9]+$ ]]; then a shell dumpsys battery set level $2
    else echo "usage: pill.sh battery <0-100|reset>" >&2; exit 2; fi ;;
  player)
    [[ $3 == (play|pause|next|prev|stop) ]] || { echo "usage: pill.sh player one|two play|pause|next|prev|stop" >&2; exit 2 }
    p=$(pkgof $2)
    a shell am start-foreground-service -n $p/$FAKE.FakePlayerService -a ${(U)3} >/dev/null ;;
  media)
    # One line per active session: package, playback state and position, title.
    a shell dumpsys media_session | awk '
      /Sessions Stack/ { on=1; next }
      /^Audio playback/ { on=0 }
      on && /package=/ { sub(/^ *package=/, ""); pkg=$0 }
      on && /state=PlaybackState/ { match($0, /state=[A-Z_]+\([0-9]+\), position=[0-9]+/); st=substr($0, RSTART+6, RLENGTH-6) }
      on && /metadata:/ { sub(/.*description=/, ""); print pkg " | " st " | " $0 }' ;;
  gesture)
    # Sets what a gesture does: pill.sh gesture tapLeft|tapRight|holdLeft|holdRight|volume appmenu|actionsmenu|recents|hideidle|none|<action id>|app:<package>
    [[ -n $3 ]] || { echo "usage: pill.sh gesture tapLeft|tapRight|holdLeft|holdRight|volume appmenu|actionsmenu|recents|hideidle|none|<action id>" >&2; exit 2 }
    command_ SET_GESTURE --es slot $2 --es choice $3 ;;
  actionsmenu)
    # Sets the Actions menu: pill.sh actionsmenu torch,lock,app:com.x (up to 4 ids; empty clears it)
    command_ SET_ACTIONS_MENU --es ids "'$2'" ;;
  appmenu)
    # Sets the App menu: pill.sh appmenu com.a,com.b (up to 4 packages; empty clears it)
    command_ SET_APP_MENU --es pkgs "'$2'" ;;
  action)
    # Runs one catalog action by id, like a gesture bound to it would. Prints ok, unavailable: <reason> or error.
    [[ -n $2 ]] || { echo "usage: pill.sh action <id>   (ids: pill.sh state | jq -r '.actions[].id')" >&2; exit 2 }
    command_ RUN_ACTION --es id $2 ;;
  pet)
    # Forces the idle pet into a state (lick, knead: cat; bark, dig: dog; wings, peck: bird; scratch, banana: monkey; think, type: clawd). Prints ok, unavailable: <reason> or error.
    [[ $2 == (${~PET_STATES}) ]] || { echo "usage: pill.sh pet $PET_STATES" >&2; exit 2 }
    command_ PET --es state $2 ;;
  petevent)
    # Makes the pet react as if it happened: notify (runs into the hole), plug (excited), low (tired), open|close (a menu), dance (6 s).
    # Prints ok, or "ok: waits until the pet is seen" when the pet is not shown now.
    [[ $2 == (notify|plug|low|open|close|dance) ]] || { echo "usage: pill.sh petevent notify|plug|low|open|close|dance" >&2; exit 2 }
    command_ PET_EVENT --es event $2 ;;
  petstyle)
    # Sets which animal the pet is. Prints ok or error.
    [[ $2 == (cat|dog|bird|monkey|clawd) ]] || { echo "usage: pill.sh petstyle cat|dog|bird|monkey|clawd" >&2; exit 2 }
    command_ SET_PET_ANIMAL --es animal $2 ;;
  strip)
    # One picture of 12 evenly spaced frames of a pet state, cut from the emulator's own recording (the phone-side
    # screenrecord gives stale video). Only the action is shown, not the still pet before and after it. The crop is the
    # area the pet can reach with the pill's outline drawn in grey; each pet cell is enlarged to at least 6 px; each frame says its number and seconds.
    # Usage: pill.sh strip <state> [out.png]   (default out: build/strip-<state>.png)
    [[ -n $2 ]] || { echo "usage: pill.sh strip <pet state> [out.png]" >&2; exit 2 }
    verb=strip; st=$2; out=${3:-build/strip-$2.png}; mkdir -p ${out:h}; out=${out:A}
    trap 'a emu screenrecord stop >/dev/null 2>&1 || true; rm -rf $work' EXIT
    pet_capture $st 20
    # The action lasts as long as the state did (plus 0.8 s).
    last=$(( first + int((dur+0.8)*20) )); (( last <= n )) || last=$n
    (( last > first )) || { echo "strip: no action frames found (state '$st' may not apply to this animal)" >&2; exit 1 }
    for k in {0..11}; do
      i=$(( first + int(rint(k*(last-first)/11.0)) ))
      magick $fr[i] -filter point -resize $((scale*100))% -fill none -stroke '#707070' -strokewidth 1 \
        -draw "rectangle $(( (b[1]-x0)*scale )),$(( (b[2]-y0)*scale )) $(( (b[3]-x0)*scale )),$(( (b[4]-y0)*scale ))" \
        -stroke none -background '#202020' -gravity north -splice 0x34 \
        -font /System/Library/Fonts/Helvetica.ttc -fill white -pointsize 26 \
        -annotate +0+4 "$((k+1))/12   $(printf %.2f $(( (i-first)/20.0 ))) s" $work/l$(printf %02d $k).png
    done
    # The widest layout under 4000 px on both sides.
    fw=$(( cw*scale+4 )); fh=$(( ch*scale+38 ))
    for cols in 4 6 3 2 1; do (( cols*fw <= 4000 && ((12+cols-1)/cols)*fh <= 4000 )) && break; done
    magick montage -font /System/Library/Fonts/Helvetica.ttc $work/l*.png -tile ${cols}x -geometry +2+2 -background black $out
    echo $out ;;
  clip)
    # One mp4 of a pet state for watching on a phone: 0.5 s of the still pet, the action, 0.5 s of the still pet, then the same
    # again at half speed. Same crop as strip, enlarged with nearest-neighbour scaling to a short side of about 720 px. Frames are
    # the recording's own (no interpolation); a recording under 20 fps (busy machine) is retried twice.
    # Usage: pill.sh clip <state> [out.mp4]   (default out: build/clip-<state>.mp4)
    [[ -n $2 ]] || { echo "usage: pill.sh clip <pet state> [out.mp4]" >&2; exit 2 }
    verb=clip; st=$2; out=${3:-build/clip-$2.mp4}; mkdir -p ${out:h}; out=${out:A}
    trap 'a emu screenrecord stop >/dev/null 2>&1 || true; rm -rf $work' EXIT
    pet_capture $st
    if (( realfps < 20 )); then
      echo "clip: the recording has only $realfps fps; trying once more" >&2
      rm -rf $work; pet_capture $st
    fi
    pad=$(( fps/2 )); s=$(( first-pad < 1 ? 1 : first-pad )); e=$(( first + int((dur+0.5)*fps) )); (( e <= n )) || e=$n
    short=$(( cw < ch ? cw : ch )); k=$(( int(rint(720.0/short)) )); (( k >= 1 && (short*k-720 < 108 && 720-short*k < 108) )) || k=0
    if (( k > 0 )); then sc="scale=iw*${k}:ih*${k}:flags=neighbor"; else sc="scale=-2:720:flags=neighbor"; fi
    ffmpeg -v error -y -framerate $fps -start_number $s -i $work/f%04d.png -filter_complex \
      "[0:v]trim=end_frame=$(( e-s+1 )),setpts=PTS-STARTPTS,$sc,format=yuv420p,split[a][b];[b]setpts=2*PTS[c];[a][c]concat=n=2:v=1[o];[o]fps=${fps}[v]" -map '[v]' \
      -c:v libx264 -preset slow -crf 16 -an -movflags +faststart $out
    echo "$out ($(ffprobe -v error -show_entries stream=width,height:format=duration -of csv=p=0 $out | paste -sd' ' -), recorded at $realfps fps)" >&2
    echo $out ;;
  reel)
    # One mp4 with every action of one pet, one after the other at real speed, each named in a strip under the picture;
    # a 1.5 s title comes first. One fixed crop (the full screen width, from above the pill to 2 pill heights below it: the pet stays large, a roam that leaves the band simply leaves the frame)
    # for every part and every animal, enlarged with nearest-neighbour scaling to 1280 px wide. A part that fails is retried
    # twice, then skipped and named at the end; a capture under 18 fps (busy machine) is retried twice.
    # Usage: pill.sh reel <animal> [label] [out.mp4]   (default out: build/reel-<animal>.mp4)
    [[ $2 == (cat|dog|bird|monkey|clawd) ]] || { echo "usage: pill.sh reel cat|dog|bird|monkey|clawd [label] [out.mp4]" >&2; exit 2 }
    zmodload zsh/mathfunc
    verb=reel; animal=$2; label=$3; out=${4:-build/reel-$animal.mp4}; mkdir -p ${out:h}; out=${out:A}
    typeset -A own=(cat "lick knead" dog "bark dig" bird "wings peck" monkey "scratch banana" clawd "think type")
    known=(sit look walk stretch groom fidget jump heart sleep hide hidden peek excited tired startle dance roam ${=own[cat]} ${=own[dog]} ${=own[bird]} ${=own[monkey]} ${=own[clawd]})
    extra=(${${(s:|:)PET_STATES}:|known})
    states=(sit look walk stretch groom fidget jump ${=own[$animal]} $extra heart sleep hide excited tired startle dance roam)
    $SELF petstyle $animal >/dev/null; sleep 1
    $SELF pet sit >/dev/null || { echo "reel: the pet is not shown (open the idle pill first)" >&2; exit 1 }
    sleep 1.5
    bb=($(state_json | jq -r '.bounds | "\(.[0]) \(.[1]) \(.[2]) \(.[3])"'))
    sz=$(a shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -1); W=${sz%x*}; SH=${sz#*x}
    rh=$(( bb[4]-bb[2] )); ry0=$(( int(bb[2]-rh*0.5) )); ry1=$(( int(bb[4]+rh*2) )); (( ry0 >= 0 )) || ry0=0; (( ry1 <= SH )) || ry1=$SH
    export REELCROP="0 $ry0 $W $ry1"
    rd=$(mktemp -d); trap 'a emu screenrecord stop >/dev/null 2>&1 || true; rm -rf $rd' EXIT
    : >$rd/fps.log; : >$rd/list.txt; missing=()
    # Records one action into $rd/p<i>.mp4 and encodes it with the caption strip (exit 3: under 18 fps, try again).
    reel_part() {
      local i=$1 st=$2 name=$3 try=$4
      export KEEP=$5 MAXS=$6
      trap 'a emu screenrecord stop >/dev/null 2>&1 || true; rm -rf $work' EXIT
      pet_capture $st
      echo "$name $realfps" >>$rd/fps.log
      (( try == 3 || realfps >= 18 )) || exit 3
      local pad=$(( fps/2 )) s e
      s=$(( first-pad < 1 ? 1 : first-pad )); e=$(( first + int((dur+0.5)*fps) )); (( e <= n )) || e=$n
      ffmpeg -v error -y -framerate $fps -start_number $s -i $work/f%04d.png -loop 1 -framerate $fps -i $rd/cap-$i.png -filter_complex \
        "[0:v]trim=end_frame=$(( e-s+1 )),setpts=PTS-STARTPTS,scale=1280:-2:flags=neighbor,fps=30,pad=iw:ih+90[p];[p][1:v]overlay=0:H-h:shortest=1,format=yuv420p[v]" -map '[v]' \
        -c:v libx264 -preset slow -crf 18 -r 30 -an $rd/p$i.mp4
    }
    i=0
    for st in $states; do
      i=$((i+1)); name=$st; keep=$st; maxs=9
      [[ $st == hide ]] && { name="hide + peek"; keep='hide|hidden|peek'; maxs=16 }
      magick -size 1280x90 xc:'#202020' -font /System/Library/Fonts/Helvetica.ttc -fill white -pointsize 60 -gravity center -annotate +0+0 "$name" $rd/cap-$i.png
      for try in 1 2 3; do
        ( reel_part $i $st $name $try $keep $maxs ) >/dev/null && break
        a emu screenrecord stop >/dev/null 2>&1 || true
      done
      if [[ -s $rd/p$i.mp4 ]]; then echo "file '$rd/p$i.mp4'" >>$rd/list.txt; else missing+=($name); fi
    done
    first=$(sed -n 1p $rd/list.txt | sed "s/^file '//; s/'$//")
    [[ -n $first ]] || { echo "reel: no part could be recorded" >&2; exit 1 }
    dims=$(ffprobe -v error -show_entries stream=width,height -of csv=s=x:p=0 $first); tw=${dims%x*}; th=${dims#*x}
    magick -size ${tw}x${th} xc:'#202020' -font /System/Library/Fonts/Helvetica.ttc -fill white -gravity center \
      -pointsize 84 -annotate +0-50 "${(C)animal} · ${(C)look}" -pointsize 52 -fill '#b0b0b0' -annotate +0+50 "$label" $rd/title.png
    ffmpeg -v error -y -loop 1 -framerate 30 -t 1.5 -i $rd/title.png -vf format=yuv420p -c:v libx264 -preset slow -crf 18 -r 30 -an $rd/p0.mp4
    { echo "file '$rd/p0.mp4'"; cat $rd/list.txt; } >$rd/all.txt
    ffmpeg -v error -y -f concat -safe 0 -i $rd/all.txt -c copy -movflags +faststart $out
    echo "$out ($(ffprobe -v error -show_entries stream=width,height:format=duration,size -of csv=p=0 $out | paste -sd' ' -))" >&2
    echo "lowest capture rate: $(awk '{ if (m == "" || $NF < m) { m = $NF; l = $0 } } END { print m " fps (" l ")" }' $rd/fps.log)" >&2
    (( $#missing == 0 )) || echo "missing parts: $missing" >&2
    echo $out ;;
  state) state_json ;;
  logs) a logcat -d -s PILL FAKECHAT ;;
  cutout)
    # `on` makes the emulator look like Vladko's Nothing Phone (2): a 1080 x 2412 screen and one punch hole at the top centre.
    # The image has no centred-hole overlay of its own, so it borrows the Pixel 7 one (68 px circle, centre x 540, y 68; the real
    # phone has 58.5 px at x 540, y 62, status bar 126 px), and replaces the stock top-left `hole`. `off` returns the image's own screen and switches every overlay off.
    # Overlays and `wm size` survive a reboot, but `on` is safe to repeat.
    for o in $(a shell cmd overlay list | tr -d '\r' | grep -o 'com.android.internal.display.cutout.emulation.[a-z0-9]*'); do
      a shell cmd overlay disable --user current $o >/dev/null
    done
    if [[ $2 == on ]]; then
      for o in com.android.internal.emulation.pixel_7 com.android.systemui.emulation.pixel_7; do a shell cmd overlay enable --user current $o >/dev/null; done
      a shell wm size 1080x2412 >/dev/null
    else
      for o in com.android.internal.emulation.pixel_7 com.android.systemui.emulation.pixel_7; do a shell cmd overlay disable --user current $o >/dev/null; done
      a shell wm size reset >/dev/null
    fi ;;
  shutdown) a emu kill ;;
  test)
    # Unit tests (may fail the run), then the comparison pictures (never fail the run).
    ./gradlew -q testDebugUnitTest
    adb devices | grep -qE '^emulator-[0-9]+\sdevice' || $0 boot
    $0 install; $0 enable
    compare/compare.sh ;;
  *) echo "usage: pill.sh boot|install|enable|disable|shot <file>|tap [x y]|doubletap <x> <y>|longpress [x y]|tapleft|tapright|holdleft|holdright|swipedown|volkeys|alarm [ring|silent|stop] [label]|nav [stop|<title> [sub [text]]]|swipeup|flick [px] [ms]|swipeleft|swiperight|drag <x1> <y1> <x2> <y2> <hold-ms>|button <name>|chat one|two <chat> <text> [actions]|unsnooze one|two|all|clearnotifs|notify <title> <body>|sms <number> <text>|call <number>|hangup <number>|voip <name> [style|plain|dialing|connected]|lock|unlock|rotate|fullscreen one|two on|off|home|settings|charge on|off|battery <0-100|reset>|player one|two play|pause|next|prev|stop|media|gesture <slot> <choice>|actionsmenu <ids>|appmenu <packages>|action <id>|pet <state>|petevent notify|plug|low|open|close|dance|petstyle <cat|dog|bird|monkey|clawd>|strip <pet state> [out.png]|clip <pet state> [out.mp4]|reel <animal> [label] [out.mp4]|state|logs|cutout on|off|test|shutdown" >&2; exit 2 ;;
esac
