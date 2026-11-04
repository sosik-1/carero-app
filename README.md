# 케어로 (Carero)

**차량 경고가 뜨면 남은 경로·시간대·주행 상태·주변 정비소를 함께 판단해, 같은 경고라도 상황에 맞게 "지금 무엇을 해야 하는지" 결정해 주는 상황판단형 차량 케어 앱**

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작입니다.

| 같은 타이어 저압 경고, 집까지 3km | 같은 경고, 야간 고속도로 120km |
|---|---|
| ![](docs/screenshots/s1_near_home_tire.png) | ![](docs/screenshots/s2_night_highway_tire.png) |
| **브레이크액 위험 → 즉시 정차** | **비 오는데 전조등 꺼짐 → 한 줄 제안** |
| ![](docs/screenshots/s3_brake_critical.png) | ![](docs/screenshots/s4_rain_headlight.png) |
| **정차 중: 상세 안내와 차량 컨디션** | **시연용 데모 패널** |
| ![](docs/screenshots/s5_parked_detail.png) | ![](docs/screenshots/s6_demo_panel.png) |

## APK 위치

- `apk/케어로팀_케어로_release.apk` (release 빌드)

## 실행 방법

1. Android Studio에 **Pleos Connect Emulator**(API 34 시스템 이미지)를 설치하고 실행합니다. ([Pleos Connect 개발 환경 설정 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/))
2. APK를 설치합니다.
   ```bash
   adb install -r "apk/케어로팀_케어로_release.apk"
   ```
3. 앱 목록에서 **케어로**를 실행합니다. 처음 실행하면 차속 권한(`android.car.permission.CAR_SPEED`)을 허용해 주세요.
4. 에뮬레이터의 **Virtual Console**에서 차량 상태(브레이크액·엔진오일 수준, 워셔액, 와이퍼, 전조등 등)를 바꾸면 화면이 바뀝니다.
5. 내비게이션에서 목적지를 설정하면 남은 거리를 반영해 판단이 달라집니다.

### 데모 모드
오른쪽 위 **데모** 버튼을 누르면 가상 값으로 시나리오를 재현할 수 있습니다(화면 상단에 "데모 모드 · 가상 값"이 표시됩니다). 에뮬레이터에서 바꾸기 어려운 값(주행 중 여부, 남은 거리 등)을 시연할 때 사용합니다.

## 테스트 환경 및 설정

- Pleos Connect Emulator (Android 14, API 34)
- Pleos Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5
- 빌드: JDK 17 이상, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.21, minSdk 28 / targetSdk 35
- 소스에서 빌드: `./gradlew :app:assembleRelease`
- 단위 테스트: `./gradlew :app:testDebugUnitTest` (상황 판단 엔진 14개 테스트)

## 주요 기능

| 기능 | 설명 |
|---|---|
| 상황 판단 엔진 | 경고 심각도 × 남은 거리 × 시간대 × 주행 상태 × 정비소 접근성으로 행동 4종(즉시 정차 / 경로 위 정비소 경유 / 도착 후 점검 / 다음 기회에) 결정 |
| 경로 연동 정비소 안내 | 목적지 방향에 있고 우회 거리가 짧은 정비소를 골라 버튼 한 번으로 경유지 추가 |
| 안전 운전 제안 | 와이퍼 작동(비) + 전조등 꺼짐, 일몰 후 전조등 꺼짐 → 한 줄 제안 (같은 주행 중 반복하지 않음) |
| 정차 후 상세 안내 | 원인과 조치 방법, 이번 주 차량 컨디션 |

주행 중에는 한 줄 안내와 버튼 하나만 보여주고(`distractionOptimized`), 상세 안내는 정차했을 때만 보여줍니다.

## Pleos SDK 사용

| SDK | API | 용도 |
|---|---|---|
| Vehicle · Safety | `registerBrakeFluidLevelState`, `registerEngineOilLevelState`, `getTransmissionOilLevelState`, `registerLowWasherFluidWaringState` | 브레이크액·엔진오일·변속기오일·워셔액 |
| Vehicle · Tire | `getLowTirePressure` (바퀴별) | 타이어 저압 신호 |
| Vehicle · Wiper | `registerWiperState` (앞유리) | 비 감지 |
| Vehicle · Light | `registerLowBeamLightState` | 전조등 상태 |
| NaviHelper | `onDestinationInfo`(남은 거리·시간·목적지), `onCurrentLocationInfo`, `addWaypoint` | 경로 맥락, 정비소 경유지 추가 |
| AAOS CarPropertyManager | `PERF_VEHICLE_SPEED` | 주행/정차 판단 |

- 조회 권한만 선언하고 차량 제어 권한은 쓰지 않습니다.
- 지원하지 않는 차종·기능은 "미지원 차종"으로 표시하고 판단에서 뺍니다.
- 변화가 잦지 않은 값은 리스너로 구독하고, 리스너가 없는 값(타이어 저압, 변속기오일)만 30초 간격으로 조회합니다.

## 코드 구조

```
app/src/main/java/app/carero/
├── engine/          상황 판단 엔진 (안드로이드 의존성 없는 순수 Kotlin)
│   ├── DecisionEngine.kt   행동 결정, 경로 위 정비소 선택, 안전 운전 제안
│   ├── Messages.kt         안내 문구
│   └── Geo.kt              거리 계산, 일출·일몰 계산
├── data/            Pleos SDK·AAOS 연동, 정비소 데이터
├── ui/CareroApp.kt  Jetpack Compose 화면
└── MainViewModel.kt
```

## 데이터 출처

- 정비소 목록(`app/src/main/assets/repair_shops.json`): 공공데이터포털 「충청남도_전국자동차정비업체 정보」, 「서울특별시 영등포구_자동차정비업체 인허가 현황」의 주소를 OpenStreetMap Nominatim으로 지오코딩한 시연용 표본입니다(도로 단위 정밀도). 서비스 시 「전국자동차정비업체표준데이터」로 교체할 예정입니다.
- 일몰 시각은 NOAA 일출·일몰 공식으로 계산합니다.

## 알려진 제약

- `Tire.getLowTirePressure`가 돌려주는 값의 의미는 문서에 명시되어 있지 않아, 0보다 크면 저압으로 해석했습니다. 실제 에뮬레이터에서 검증이 필요합니다.
- 현재 공개 SDK에는 장소 검색 API가 없어 정비소 목록을 앱에 내장했습니다.
- 경고등 전체(Telltales), 타이어 공기압 수치는 비공개 API라 사용하지 않습니다.
