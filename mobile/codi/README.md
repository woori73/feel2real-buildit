# F2R 코디 모바일

Android와 iOS는 같은 Capacitor 런처를 사용합니다. 앱은 설정된 F2R 서버의 `/codi`를 WebView에서 엽니다. 로그인과 학원 접근 권한은 해당 서버가 확인합니다. 서버 연결 실패 화면에서는 다시 연결할 수 있습니다.

## 개발 빌드

Node.js 22 이상으로 이 폴더에서 실행합니다.

```sh
npm ci
npm test
npm run sync
```

기본 빌드는 `https://feel2real.co.kr`을 엽니다. 미배포 변경 사항은 해당 브랜치를 설치한 별도 HTTPS 서버로 검증합니다. 아래 예시 주소를 실제 시험 서버 주소로 바꿔 실행하세요.

```sh
F2R_CODI_ORIGIN=https://pilot.example.org npm run build:android
F2R_CODI_ORIGIN=https://pilot.example.org npm run sync
npm run open:ios
```

빌드가 대상 주소를 앱에 저장하며 동일한 호스트만 WebView 내부 탐색에 추가합니다. 테스트 계정으로 해당 호스트에 로그인하면 같은 WebView 세션으로 코디에 돌아옵니다. 브라우저에서 받은 로그인 상태는 기기의 앱 WebView와 별개입니다. 소셜 로그인 공급자별 복귀는 기기에서 별도 검증해야 합니다.

- Android는 JDK 21, Android SDK 36 및 Build Tools를 사용합니다. 기본 호스트의 디버그 APK는 `npm run build:android`로 만들며 결과는 `android/app/build/outputs/apk/debug/app-debug.apk`입니다. 앱 ID는 `kr.co.feel2real.codi`, 최소 Android는 7.0(API 24)입니다.
- iOS는 macOS에서 `npm run open:ios`로 Xcode 프로젝트를 엽니다. iOS 15 이상이 대상이며 기기 설치 시 팀 서명을 지정합니다.
- 촬영은 시스템 카메라, 기존 사진은 시스템 선택기를 사용합니다. 파일 요청과 채점은 F2R 화면에서 처리합니다. 파일 선택과 취소, 권한 거부, 키보드, 외부 로그인 제공자 복귀는 양쪽 실기기의 인수 시험 항목입니다.

Android는 기기의 글자 크기를 WebView의 절대 글자 배율과 맞추며 같은 배율을 두 번 적용하지 않습니다. iOS는 본문에 사용하는 시스템 글꼴 스타일(`-apple-system-body`)로 Dynamic Type을 따릅니다.
