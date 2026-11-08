# PHOTO-LOG for iOS · iPadOS · macOS

안드로이드판 v0.9.3과 같은 기능의 iPhone 앱. SwiftUI, iOS 16 이상. 서버 없이 모든 데이터가 폰 안에 저장된다.

## 안드로이드판과 같은 점
- 시작 화면: 연 → 월 → 일 아코디언 사이드바 + 날짜별 3열 사진 그리드(사이드바를 숨기면 4열, 상태 유지)
- 사진 추가: 카메라로 찍기 / 앨범에서 가져오기(여러 장). 원본을 앱 안으로 복사한다.
- 사진은 앱 전용 공간에만 저장되어 사진 앱·파일 앱에 보이지 않는다.
- 촬영 위치 자동 기록: 사진의 EXIF GPS를 읽는다. 카메라로 찍은 사진은 촬영 직후 폰 위치(15분 이내)를 사진에 기록한다.
- 사진 꾹 누르기: 공유·삭제 메뉴
- 상세 화면: 사진 + 필름카메라 날짜 각인, 위치(지도에서 보기), 기록 입력(공유 이미지 기준 최대 5줄)·저장, ⋯ 메뉴의 삭제
- 공유: 크기 조절해서 공유(원본·1:1·4:5·9:16, 확대·이동, 사진만 / 사진 위에 기록 / 사진 + 기록 카드, 기록 글 함께 보내기, 날짜 넣기) / 원본 그대로 / 기록 복사
- 백업: ⋮ → 백업 내보내기 / 불러오기. zip 형식이 안드로이드판과 같아서 **안드로이드 백업을 iPhone에서 불러올 수 있고, 그 반대도 된다.**
- 디자인: 기본 모드 흰색 · 다크 모드 검정, 강조색은 날짜 주황 하나. 글꼴(Playfair Display, 고운바탕, DSEG7)도 같다.
- 앱 아이콘: 흰 바탕 검정 PHOTO-LOG, 다크 모드 아이콘은 흑백 반전(iOS 18 이상), 색조 아이콘도 지원.

## 다른 점(iOS 제약)
- Google 포토에서 바로 가져오기는 없다. iOS는 다른 앱이 Google 포토 사진을 고르는 기능을 열어 두지 않는다. Google 포토 앱에서 사진을 "기기에 저장"한 뒤 앨범에서 가져오면 된다.
- 지도에서 보기: Apple 지도 · 카카오맵 · Google 지도 중에서 고른다.
- 백업 내보내기는 파일 앱 저장 화면으로 열린다(iCloud Drive, Google 드라이브 등 선택).

## 맥북 앱 (Mac Catalyst)
같은 코드로 맥북용 앱도 만든다. iPad 화면 배치를 쓰고, 창 크기를 자유롭게 바꿀 수 있다(최소 720×600).
- 카메라로 찍기: 맥북 내장 카메라(또는 연결된 카메라)로 찍는다. 스페이스바로도 찍을 수 있다. 촬영 위치는 맥의 위치 서비스로 기록한다.
- 사진 추가 메뉴에 "파일에서 가져오기"가 더 있다(Finder의 사진 여러 장).
- 데이터 위치: `~/Library/Application Support/com.dup04205.photolog/`(사진 앱에는 보이지 않는다). 백업 zip은 iPhone·Android와 같은 형식이다.
- 빌드: `ios/` 가 바뀌면 GitHub Actions(`.github/workflows/mac.yml`)가 로컬 서명한 `PHOTO-LOG-mac.zip`을 `mac` 브랜치에 올린다. 압축을 풀어 `PHOTO-LOG.app`을 응용 프로그램 폴더로 옮기면 된다.
  인터넷에서 받은 경우 처음 한 번은 앱을 Control-클릭 → 열기로 실행한다.
- Xcode에서 직접 실행하려면 실행 대상에서 "My Mac (Mac Catalyst)"을 고르고 ▶를 누른다.

## 빌드
`ios/` 폴더가 바뀐 커밋이 main에 올라가면 GitHub Actions(`.github/workflows/ios.yml`)가 macOS에서 서명 없는 `PHOTO-LOG.ipa`를 만든다.
- Actions 탭 → Build iOS → Artifacts의 `photo-log-ipa`
- 또는 `ipa` 브랜치의 `PHOTO-LOG.ipa`(최신 빌드 하나만 보관)

Mac에서 직접 빌드하려면: `brew install xcodegen && cd ios && xcodegen generate && open PhotoLog.xcodeproj`

## iPhone에 설치
iOS는 APK처럼 파일만으로 설치할 수 없고, 반드시 Apple 서명이 필요하다. 방법은 둘 중 하나다.

1. **무료 Apple ID + Sideloadly(Windows/Mac)**
   - PC에 Sideloadly를 설치하고 iPhone을 케이블로 연결한다.
   - `PHOTO-LOG.ipa`를 끌어다 놓고 본인 Apple ID로 로그인하면 설치된다.
   - iPhone 설정 → 개인정보 보호 및 보안 → 개발자 모드 켜기, 설정 → 일반 → VPN 및 기기 관리에서 본인 Apple ID 신뢰.
   - 무료 계정 서명은 **7일마다 다시 설치**해야 한다(데이터는 그대로 남는다). AltStore를 쓰면 자동 갱신할 수 있다.
2. **Apple Developer Program(유료 연회원) + TestFlight**
   - 1년짜리 서명, TestFlight 앱으로 설치·업데이트. 이 경우 Actions에 서명 인증서를 Secrets로 등록하는 단계를 추가하면 된다.

## 처음 실행 시 권한
- 위치: 카메라로 찍은 사진에 촬영 위치를 넣기 위해
- 사진 보관함: 가져온 사진에 위치 정보가 빠져 있을 때 보관함의 촬영 위치·날짜로 보완하기 위해
- 카메라: 사진 찍기

거부해도 앱은 동작하며, 그 경우 위치가 기록되지 않을 수 있다.

## 구성
- `PhotoLog/Model` — `Entry.swift` 데이터·저장, `PhotoStore.swift` 사진 파일·EXIF, `Backup.swift` zip, `AppModel.swift` 화면 상태
- `PhotoLog/UI` — 목록·사이드바·상세 화면, 테마
- `PhotoLog/Share` — 공유 선택, 크기 조절 화면, 공유 이미지 그리기
- zip 처리는 [ZIPFoundation](https://github.com/weichsel/ZIPFoundation)(MIT)을 쓴다.
