#!/bin/zsh
# Comparison run: for each row of features.tsv, put Pill into that state on the emulator, screenshot it and build
# one picture with the matching iPhone reference picture(s) next to it. It never fails: whatever happens, it exits 0.
# Output: <repo>/comparison/<nn>-<id>.png and index.html (wiped on every run).
setopt null_glob
here=${0:A:h}
cd ${here:h}                                   # pill/
ROOT=${PWD:h}
OUT=$ROOT/comparison
REF=$ROOT/reference/ios
TMP=$(mktemp -d)
trap 'rm -rf $TMP' EXIT
rm -rf $OUT; mkdir -p $OUT

BG='#1a1a1a'; PANEL='#2a2a2a'; FG='#d8d8d8'
P=./pill.sh
FONT=/System/Library/Fonts/Supplemental/Arial.ttf       # ImageMagick has no font list here, so name a file
[[ -f $FONT ]] || FONT=/System/Library/Fonts/Helvetica.ttc

# ---- reference index: one line per crop box: file, feature, box label, x, y, w, h ----
typeset -a REFS
if [[ -f $REF/index.md ]]; then
  REFS=("${(@f)$(awk -F'|' '
    /^\| *(sim-)?[0-9][0-9]-/ {
      split($2, f, " "); feat=$3; gsub(/^ +| +$/, "", feat)
      n=split($5, parts, ";")
      for (i=1; i<=n; i++) {
        p=parts[i]; gsub(/^ +| +$/, "", p)
        if (match(p, /[0-9]+,[0-9]+,[0-9]+,[0-9]+$/)) {
          lab=substr(p, 1, RSTART-1); gsub(/ +$/, "", lab)
          split(substr(p, RSTART), b, ",")
          printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n", f[1], feat, lab, b[1], b[2], b[3], b[4]
        }
      }
    }' $REF/index.md)}")
  REFS=(${REFS:#})
fi

# Prints the reference crop lines (file, label, x, y, w, h) that a row key matches. Files that are missing are skipped.
matching_refs() {
  local key=$1 alt sub lab line file feat blab seen=" " f web exact
  for alt in ${(s:;:)key}; do
    alt=${alt## #}; alt=${alt%% #}
    sub=${alt%%#*}; lab=""; [[ $alt == *'#'* ]] && lab=${alt#*#}
    [[ -n $sub ]] || continue
    web=0; [[ $sub == web:* ]] && { web=1; sub=${sub#web:}; }
    exact=0; [[ $sub == '='* ]] && { exact=1; sub=${sub#=}; }
    for line in $REFS; do
      f=("${(@ps:\t:)line}")
      file=$f[1]; feat=$f[2]; blab=$f[3]
      [[ -f $REF/$file ]] || continue
      # Simulator pictures only, unless the key says "web:" (then only the other pictures).
      if (( web )); then [[ $file == sim-* ]] && continue; else [[ $file == sim-* ]] || continue; fi
      if (( exact )); then [[ ${(L)feat} == ${(L)sub} ]] || continue
      else [[ ${(L)feat} == *${(L)sub}* ]] || continue; fi
      if [[ -n $blab ]]; then
        # A picture with several labelled boxes: take the named box, or the first one when no label is asked for.
        if [[ -n $lab ]]; then [[ ${(L)blab} == *${(L)lab}* ]] || continue
        else [[ $seen == *" $file "* ]] && continue; fi
      fi
      [[ $seen == *" $file:$f[4],$f[5] "* ]] && continue
      seen+="$file:$f[4],$f[5] "; [[ -z $lab && -n $blab ]] && seen+="$file "
      printf "%s\t%s\t%s\t%s\t%s\t%s\n" $file "$blab" $f[4] $f[5] $f[6] $f[7]
    done
  done
}

# Runs ";"-separated pill.sh steps. Returns 0, or 1 and sets FAIL to the step that failed.
# The step "@shot" takes a screenshot of the emulator at that moment (see take_shot).
FAIL=""
run_steps() {
  local steps=$1 step out
  for step in "${(@s: ; :)steps}"; do
    step=${step## #}; [[ -n $step ]] || continue
    if [[ $step == sleep\ * ]]; then eval "$step"; continue; fi
    if [[ $step == @shot ]]; then take_shot; continue; fi
    if [[ $step == '& '* ]]; then eval "$P ${step#& } >/dev/null 2>&1 &"; continue; fi
    out=$(eval "$P $step" 2>&1) || { FAIL="$step (${out//$'\n'/ })"; FAIL=${FAIL[1,160]}; return 1 }
  done
  return 0
}

# One Android picture of the current state: $d/a<n>.png, plus Pill's state in SJ[n].
typeset -a SJ
SHOTN=0
take_shot() {
  SHOTN=$((SHOTN+1))
  SJ[$SHOTN]=$($P state 2>/dev/null)
  $P shot $d/a$SHOTN.png >/dev/null 2>&1
}

# A text panel: text_panel <file> <width> <height> <text>
text_panel() { magick -size ${2}x${3} -background $PANEL -fill $FG -gravity center -font $FONT -pointsize 26 caption:"$4" "$1" }
# A caption strip: caption <file> <width> <size> <text>
caption() { magick -size ${2}x -background $BG -fill $FG -font $FONT -pointsize $3 -gravity west caption:"$4" -gravity north -splice 0x10 +repage "$1" }

# The App menu that was set before the run; rows that change it put it back, and so does the end of the run.
APPMENU_ORIG=$($P state 2>/dev/null | jq -r '.appMenu // [] | join(",")' 2>/dev/null)

# Puts the emulator and Pill in the plain starting state: nothing playing, ringing, charging or pending; settings as normal.
reset_emulator() {
  local s
  for s in "player one stop" "player two stop" "alarm stop" "charge off" "hangup 5557777" "appmenu \"$APPMENU_ORIG\"" "home" "cutout off" "clearnotifs"; do
    eval "$P $s" >/dev/null 2>&1
  done
}

# Waits until Pill shows the idle pill again (an event or call still on its way out would spoil the next picture).
# If it does not, resets once more. Returns 1 when the pill is still not idle.
settle() {
  local i j
  for i in {1..3}; do
    for j in {1..12}; do
      [[ $($P state 2>/dev/null | jq -r '.shown.type' 2>/dev/null) == idle ]] && return 0
      sleep 0.5
    done
    reset_emulator
  done
  return 1
}

typeset -a TABLE HTML
n=0
reset_emulator
settle
while IFS=$'\t' read -u3 -r id title steps delay cleanup key expect; do
  [[ -z $id || $id == \#* ]] && continue
  n=$((n+1)); nn=$(printf %02d $n)
  png=$OUT/$nn-$id.png
  d=$TMP/$nn; mkdir -p $d
  FAIL=""; res=made

  # ---- Android side ----
  SHOTN=0; SJ=()
  settle || FAIL="pill was not idle before the row"
  # Centre of the idle pill, for steps that use $PX $PY.
  PX=0; PY=0
  pb=($($P state 2>/dev/null | jq -r '.bounds | "\(.[0]) \(.[1]) \(.[2]) \(.[3])"' 2>/dev/null))
  (( ${#pb} == 4 && pb[3] > pb[1] )) && { PX=$(( (pb[1]+pb[3])/2 )); PY=$(( (pb[2]+pb[4])/2 )); }
  run_steps "$steps"; ok=$?
  if (( SHOTN == 0 )); then sleep ${delay:-0.5}; take_shot; fi
  [[ -s $d/a1.png ]] || [[ -n $FAIL ]] || { FAIL="screenshot failed"; ok=1; }
  [[ $cleanup == - ]] || { keep=$FAIL; run_steps "$cleanup" >/dev/null 2>&1; FAIL=$keep; }
  [[ $ok != 0 ]] && res="android step failed"

  # Every shot must show exactly the expected state (type/kind/size, "*" matches anything).
  typeset -a sts bnds; sts=(); bnds=()
  for i in {1..$SHOTN}; do
    sts+=("$(jq -r '"shown: \(.shown.type)/\(.shown.kind // "-")   size: \(.size)   activities: \(.activities|length)"' <<<$SJ[$i] 2>/dev/null)")
    bnds+=("$(jq -r '.bounds | "\(.[0]) \(.[1]) \(.[2]) \(.[3])"' <<<$SJ[$i] 2>/dev/null)")
    got=$(jq -r '"\(.shown.type)/\(.shown.kind // "-")/\(.size)"' <<<$SJ[$i] 2>/dev/null)
    if [[ -n $expect && $expect != - && $got != ${~expect} ]]; then
      [[ $res == made ]] && res="wrong state: $got, wanted $expect"
    fi
  done

  # ---- references for this row ----
  refs=("${(@f)$(matching_refs "$key")}"); refs=(${refs:#})
  simmode=1; ihmax=0
  for line in $refs; do
    f=("${(@ps:\t:)line}")
    [[ $f[1] == sim-* ]] || simmode=0
    (( f[4]+f[6] > ihmax )) && ihmax=$(( f[4]+f[6] ))
  done
  # With no picture, or only simulator pictures, both sides show the top of the whole screen at the same size:
  # the same visible height, scaled so the SCREEN WIDTHS match. Pictures that are not full simulator screenshots
  # (marketing renders) cannot be compared that way; there both sides are cropped around the pill and scaled
  # so the pill is as wide as the island.
  # The visible height is the iPhone feature's height, or more when Pill's own picture is taller (then both sides
  # get that height, so nothing of Pill is cut off). In Android pixels; the iPhone side converts it.
  W=720; CHA=0
  for i in {1..$SHOTN}; do
    [[ -s $d/a$i.png ]] || continue
    aw=$(magick identify -format %w $d/a$i.png); ah=$(magick identify -format %h $d/a$i.png)
    b=(${=bnds[$i]}); bot=$(( ${b[4]:-0}+40 ))
    if (( ${#refs} )); then c=$(( ihmax*aw/1206 )); (( bot > c )) && c=$bot
    else c=$bot; (( c < 350 )) && c=350; fi
    [[ $key == full ]] && c=$ah
    (( c > ah )) && c=$ah
    (( c > CHA )) && CHA=$c
  done
  typeset -a rights; rights=()
  for i in {1..$SHOTN}; do
    [[ -s $d/a$i.png ]] || continue
    aw=$(magick identify -format %w $d/a$i.png); ah=$(magick identify -format %h $d/a$i.png)
    b=(${=bnds[$i]})
    if (( simmode )); then
      magick $d/a$i.png -crop ${aw}x${CHA}+0+0 +repage -resize ${W}x $d/android$i.png
    else
      l=540; r=540; t=0; bt=200
      if (( ${#b} == 4 && b[3] > b[1] )); then l=$b[1]; t=$b[2]; r=$b[3]; bt=$b[4]; fi
      pw=$(( r-l )); (( pw < 100 )) && pw=500 && l=$(( 540-250 ))
      m=$(( (560-pw)/2 )); (( m < 48 )) && m=48
      PW=$(( pw+2*m )); PH=$(( (bt-t < 150) ? bt+3*m : bt+m ))
      magick $d/a$i.png -bordercolor $PANEL -border ${m}x${m} -crop ${PW}x${PH}+${l}+${m} +repage -resize ${W}x $d/android$i.png
      PWREF=$pw; MREF=$m
    fi
    [[ -s $d/android$i.png ]] || text_panel $d/android$i.png $W 160 "Android failed: ${FAIL:-no screenshot}"
    caption $d/capa$i.png $W 20 "Pill   (${sts[$i]})"
    magick $d/capa$i.png $d/android$i.png +repage -append +repage $d/r$i.png
    rights+=($d/r$i.png)
  done
  if (( ${#rights} == 0 )); then
    text_panel $d/right.png $W 160 "Android failed: ${FAIL:-no screenshot}"
    [[ $res == made ]] && res="android step failed"
  else
    magick $rights -background $BG -gravity north -splice 0x14 +repage -append +repage $d/right.png
  fi
  [[ $res == "android step failed" ]] && magick $d/right.png -fill '#ff6a6a' -font $FONT -pointsize 20 -gravity southwest -annotate +8+8 "step failed: ${FAIL}" $d/right.png

  # ---- iPhone side ----
  typeset -a lefts; lefts=()
  i=0
  for line in $refs; do
    f=("${(@ps:\t:)line}"); i=$((i+1))
    file=$f[1]; blab=$f[2]; bx=$f[3]; by=$f[4]; bw=$f[5]; bh=$f[6]
    if (( simmode )); then
      magick $REF/$file -crop 1206x$(( CHA*1206/aw ))+0+0 +repage -resize ${W}x $d/ref$i.png 2>/dev/null
    else
      mi=$(( MREF*bw/PWREF + 1 ))
      magick $REF/$file -bordercolor $PANEL -border ${mi}x${mi} -crop $(( bw+2*mi ))x$(( bh+2*mi ))+${bx}+${by} +repage \
        -resize ${W}x $d/ref$i.png 2>/dev/null
    fi
    [[ -s $d/ref$i.png ]] || continue
    caption $d/cap$i.png $W 20 "iPhone: $file${blab:+ ($blab)}"
    magick $d/cap$i.png $d/ref$i.png +repage -append +repage $d/l$i.png
    lefts+=($d/l$i.png)
  done
  if (( ${#lefts} == 0 )); then
    [[ $res == made ]] && res="no reference"
    text_panel $d/left.png $W 160 "no iPhone picture for this"
    caption $d/capn.png $W 20 "iPhone"
    magick $d/capn.png $d/left.png +repage -append +repage $d/left.png
  else
    magick $lefts -background $BG -gravity north -splice 0x14 +repage -append +repage $d/left.png
  fi
  maps=""; for line in $refs; do maps+="${${(@ps:\t:)line}[1]} "; done
  print "$id -> ${maps:-none}" >> $TMP/map.txt

  # ---- one picture ----
  TW=$(( 2*W+72 ))
  caption $d/title.png $TW 34 "$nn  $title"
  magick $d/left.png $d/right.png -background $BG -gravity north +repage +append +repage -bordercolor $BG -border 24x12 $d/body.png
  magick $d/title.png -bordercolor $BG -border 24x12 $d/body.png -background $BG -gravity west +repage -append +repage $png 2>/dev/null \
    || { res="android step failed"; FAIL="picture could not be built"; }

  TABLE+=("$(printf '%-24s %s' $id "$res")")
  HTML+=("<section><h2>$nn $title <small>$res</small></h2><img src=\"$nn-$id.png\" alt=\"$title\"></section>")
  unset lefts rights
done 3< $here/features.tsv

reset_emulator

cat > $OUT/index.html <<HTMLEND
<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Pill vs iPhone</title>
<style>body{background:#111;color:#ddd;font:16px system-ui,sans-serif;margin:0;padding:16px}
h2{font-weight:600;margin:32px 0 8px}small{color:#999;font-weight:400;margin-left:8px}img{width:100%;height:auto;display:block}</style>
<h1>Pill vs iPhone</h1>
${(F)HTML}
HTMLEND

print "Comparison pictures: $OUT (open index.html)"
print -l $TABLE
print -- "--- row -> iPhone pictures"
cat $TMP/map.txt
exit 0
