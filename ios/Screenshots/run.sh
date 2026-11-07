#!/bin/bash
# 시뮬레이터에서 예시 데이터로 App Store 스크린샷 5장씩을 찍는다.
# - iPhone 16 Pro Max (6.9형, 1320x2868)  → out/raw/iphone/
# - iPad Pro 13형 (2064x2752)            → out/raw/ipad/
# 사용: ios/Screenshots/run.sh  (ios 폴더에서 xcodegen generate 를 먼저 실행)
# 결과: out/raw/<기기>/*.png (원본), frame.py 로 out/framed/<기기>/*.png (문구를 얹은 버전)
set -euo pipefail
cd "$(dirname "$0")"
HERE=$PWD
BUNDLE=com.dup04205.photolog
OUT=$HERE/out
rm -rf "$OUT" && mkdir -p "$OUT/raw" "$OUT/photos" "$OUT/candidates"

# 1) 예시 사진 내려받기 (Lorem Picsum = Unsplash 사진)
grep -v '^#' photos.txt | while read -r name id; do
  [ -z "$name" ] && continue
  curl -fsSL --retry 3 -o "$OUT/photos/$name.jpg" "https://picsum.photos/id/$id/1200/1500"
done
# 후보 미리보기용(사진을 바꾸고 싶을 때 참고)
for id in ${CANDIDATES:-}; do
  curl -fsSL --retry 2 -o "$OUT/candidates/$id.jpg" "https://picsum.photos/id/$id/400/500" || true
done

devtype() { # 기기 이름 후보 중 설치된 첫 번째의 식별자
  xcrun simctl list devicetypes -j | python3 -c "
import json,sys
names=sys.argv[1:]
ts={d['name']:d['identifier'] for d in json.load(sys.stdin)['devicetypes']}
print(next(ts[n] for n in names if n in ts))" "$@"
}

# 2) 빌드 (시뮬레이터용 앱 하나를 iPhone·iPad 모두에 설치)
cd "$HERE/.."
xcodebuild -project PhotoLog.xcodeproj -scheme PhotoLog -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build-sim \
  CODE_SIGNING_ALLOWED=NO build > "$OUT/build.log" 2>&1 || { grep -E 'error:' "$OUT/build.log" | head -30; exit 1; }
APP=$PWD/build-sim/Build/Products/Debug-iphonesimulator/PhotoLog.app

LANG_ARGS=(-AppleLanguages "(ko)" -AppleLocale ko_KR -PLDemo YES)

run_device() { # 폴더 이름, 기기 종류 식별자
  local dir=$1 type=$2
  mkdir -p "$OUT/raw/$dir"
  local udid
  udid=$(xcrun simctl create "PLShot-$dir" "$type")
  xcrun simctl boot "$udid"
  xcrun simctl bootstatus "$udid" -b > /dev/null
  xcrun simctl status_bar "$udid" override --time "9:41" --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode active --cellularBars 4 --operatorName "" --batteryState charged --batteryLevel 100 || true
  xcrun simctl install "$udid" "$APP"
  xcrun simctl privacy "$udid" grant location "$BUNDLE" || true
  xcrun simctl privacy "$udid" grant photos "$BUNDLE" || true
  xcrun simctl location "$udid" set 37.5446,127.0557 || true

  # 예시 데이터 넣기
  local data support
  data=$(xcrun simctl get_app_container "$udid" "$BUNDLE" data)
  support="$data/Library/Application Support"
  mkdir -p "$support"
  python3 "$HERE/demo.py" "$OUT/photos" "$support"

  shot() { # 이름, 앱 실행 인자...
    local name=$1; shift
    xcrun simctl terminate "$udid" "$BUNDLE" 2>/dev/null || true
    xcrun simctl launch "$udid" "$BUNDLE" "${LANG_ARGS[@]}" "$@" > /dev/null
    sleep 6
    xcrun simctl io "$udid" screenshot --type=png "$OUT/raw/$dir/$name.png" > /dev/null
    echo "찍음: $dir/$name"
  }

  xcrun simctl ui "$udid" appearance light
  shot 1-grid
  shot 2-detail -PLOpen demo00
  shot 3-card -PLShare demo00 -PLShareStyle card -PLShareRatio 2
  shot 4-story -PLShare demo09 -PLShareStyle overlay -PLShareRatio 3
  xcrun simctl ui "$udid" appearance dark
  shot 5-dark

  xcrun simctl shutdown "$udid" || true
  xcrun simctl delete "$udid" || true
}

run_device iphone "$(devtype 'iPhone 16 Pro Max' 'iPhone 17 Pro Max')"
run_device ipad "$(devtype 'iPad Pro 13-inch (M4)' 'iPad Pro 13-inch (M5)' 'iPad Air 13-inch (M3)' 'iPad Air 13-inch (M2)')"
