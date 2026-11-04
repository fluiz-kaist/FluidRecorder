# FluidRecorder

Android `AccessibilityService` 기반의 **터치 이벤트 레코더**입니다. 다른 앱을 사용하면서 발생하는 탭/입력 동작을 가로채 화면 계층 구조와 함께 원격 서버로 전송합니다. 모바일 UI 자동화 시나리오를 만들기 위한 데이터(액션 + 화면 스냅샷)를 수집하는 데 사용합니다.

## 주요 기능

- **터치 좌표 캡처 후 재현**: 전체 화면을 덮는 투명 오버레이(`TouchDetectorOverlay`)로 사용자의 탭을 캡처한 뒤, `AccessibilityService.dispatchGesture()`로 동일 좌표에 제스처를 다시 보내 아래 앱에서도 정상 클릭이 동작하도록 처리
- **터치 위치의 노드 식별**: 활성 윈도우의 `AccessibilityNodeInfo` 트리에서 좌표를 포함하는 노드 중 가장 작은 영역의 노드를 선택. 동일 영역인 경우 `clickable` 노드 우선
- **XPath / 인덱스 산출**: `AccessibilityNodeInfoDumper`가 화면 전체 노드를 XML로 직렬화하면서 인덱스를 부여하고, 선택된 노드의 XPath 계산
- **액션 자동 분류**: 입력 필드 탭은 `input`, 일반 탭은 `click`으로 액션 이름 구분
- **플로팅 컨트롤 버튼**: 화면 위에 떠 있는 ☰ 버튼은 드래그 및 펼침 가능. 펼치면 *녹화 시작/중지* 버튼과 *현재 화면 덤프 업로드* 버튼이 노출됨
- **빨간 테두리 하이라이트**: 클릭이 인식된 노드의 bounds에 1초간 빨간 테두리를 그려 시각적 확인 제공
- **서버 전송**: 액션은 `POST /api/collect/actions`, 단발 화면 덤프는 `POST /dump` 엔드포인트로 OkHttp를 통해 비동기 전송

## 동작 흐름

```
[사용자 탭]
  └─> TouchDetectorOverlay 가 좌표 (x, y) 캡처
        └─> RecorderAccessibilityService.handleTouchAtPosition()
              ├─ rootInActiveWindow → XML 덤프 (AccessibilityNodeInfoDumper)
              ├─ findSmallestNodeAtPosition() 으로 타깃 노드 선정
              ├─ 노드 XPath / index 계산
              ├─ 노드 bounds에 빨간 테두리 표시 (OverlayManager)
              ├─ NetworkManager.sendAction(xpath, name, index, screen, sessionId)
              └─ dispatchGesture() 로 원래 탭 좌표에 클릭 재전달
```

## 모듈 구성

| 파일 | 역할 |
| --- | --- |
| `MainActivity.kt` | 진입 화면. 접근성/오버레이 권한 안내, 세션 ID 입력 UI (Jetpack Compose) |
| `RecorderAccessibilityService.kt` | 핵심 접근성 서비스. 화면 덤프, 노드 선택, 액션 전송 오케스트레이션 |
| `TouchDetectorOverlay.kt` | 전체 화면 투명 오버레이. 터치 좌표 캡처 후 `dispatchGesture`로 재전달 |
| `OverlayService.kt` | 플로팅 버튼(드래그/확장/접기) UI. 녹화 토글과 즉시 덤프 트리거 |
| `OverlayManager.kt` | 노드 위에 빨간 하이라이트 테두리를 그리는 윈도우 매니저 래퍼 |
| `AccessibilityNodeInfoDumper.kt` | 노드 트리를 XML로 직렬화하고 XPath/노드 인덱스를 계산 |
| `NetworkManager.kt` | OkHttp 기반 서버 통신 (액션/스크린 덤프) |
| `AppGlobals.kt` | 세션 ID, BASE_URL 등 전역 상태 보관 |

## 권한

`AndroidManifest.xml`에 선언된 권한은 모두 사용자 동의가 필요합니다.

- `android.permission.SYSTEM_ALERT_WINDOW` — 플로팅 버튼과 터치 캡처 오버레이 표시용
- `android.permission.INTERNET` — 서버 전송용

추가로 사용자가 직접 활성화해야 하는 항목은 다음과 같습니다.

- **접근성 서비스**: 설정 → 접근성 → FluidRecorder 활성화 (`canPerformGestures`, `canTakeScreenshot`, `canRetrieveWindowContent`)
- **다른 앱 위에 표시(오버레이) 권한**

## 빌드 / 실행

요구사항은 Android Studio, JDK 17+, `minSdk 30`, `targetSdk 35`입니다.

서버 주소는 `gradle.properties` 또는 `local.properties`의 `BASE_URL`로 주입되며, 빌드 시 `BuildConfig.BASE_URL`로 노출됩니다. 저장소에 커밋된 `gradle.properties`의 값은 원래 개발 환경에서 쓰던 사설 IP placeholder이므로, 사용 전에 **반드시 본인의 백엔드 주소로 교체**해야 합니다. 개인 설정은 git에 추적되지 않는 `local.properties`에 두면 `gradle.properties`의 값을 덮어씁니다.

```properties
# gradle.properties — 저장소 기본값 (placeholder)
BASE_URL=http://10.1.2.76:3000

# local.properties — 본인 환경 (커밋되지 않음)
BASE_URL=http://your-backend.example.com:3000
```

빌드 및 설치는 다음과 같이 수행합니다.

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## 사용 방법

1. 앱 실행 후 **세션 ID** 입력 (서버에서 액션을 그룹화하는 키)
2. **접근성 서비스 설정** 버튼 → 시스템 설정에서 FluidRecorder 활성화
3. **오버레이 권한 설정** 버튼 → 권한 허용
4. 화면 우측 상단의 ☰ 플로팅 버튼을 탭해 펼친 뒤 **녹화** 시작
5. 다른 앱을 평소처럼 사용하면 탭마다 액션 + 화면 XML이 서버로 전송됨
6. **덤프** 버튼은 현재 화면의 XML만 즉시 업로드하는 일회성 액션

## 서버 API

이 앱이 호출하는 두 엔드포인트는 별도 백엔드에 구현되어 있어야 합니다.

### `POST /api/collect/actions`

```json
{
  "xpath": "//hierarchy/node[2]/node[1]",
  "name": "click",
  "index": 17,
  "screen": "<hierarchy>...</hierarchy>",
  "session_id": "12345"
}
```

- `name`: `click` 또는 `input`
- `index`: 화면 덤프 내 노드 인덱스
- `screen`: 탭 시점의 화면 XML 전체

### `POST /dump`

```json
{
  "xml": "<hierarchy>...</hierarchy>",
  "filename": "screen_dump_20260430_153022.xml"
}
```

## 기술 스택

- Kotlin, Jetpack Compose (Material 3)
- Android `AccessibilityService` + `dispatchGesture`
- WindowManager 오버레이 (`TYPE_APPLICATION_OVERLAY`)
- OkHttp 4.12 / `org.json`

## 알려진 제한 사항

- **자기 자신의 오버레이 위 탭은 무시됨** (`OverlayService.isPointInsideOverlay`로 필터링). 플로팅 버튼이 녹화에 들어가는 사고를 방지하기 위함
- 터치 캡처 → `dispatchGesture` 재현 사이의 짧은 딜레이 존재. 매우 빠른 연속 탭이나 멀티터치 제스처는 정확히 재현되지 않을 수 있음
- 화면 보안 플래그(`FLAG_SECURE`)가 걸린 앱(은행/DRM 콘텐츠 등)은 덤프 불가
- `usesCleartextTraffic="true"` 설정이라 사내/개발 서버용. 운영 환경에서는 HTTPS + `network_security_config` 조정 필요

## 라이선스

Apache License 2.0 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

```
Copyright 2026 주식회사 플루이즈 (Fluiz)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

## Contributors

The following individuals contributed to the research, development, and testing of this project:

- [jongseok0116](https://github.com/jongseok0116) — Research & Development
- [minu0816](https://github.com/minu0816) — Research & Development
- [leesaeha](https://github.com/leesaeha) — Research & Development
- [sangmin-del](https://github.com/sangmin-del) — Testing & Planning
