package app.carero.engine

/** 앱이 다루는 차량 점검 경고 종류 (Pleos Vehicle SDK 공개 조회 API 기준) */
enum class WarningType(val label: String) {
    BRAKE_FLUID("브레이크액"),
    ENGINE_OIL("엔진오일"),
    TRANSMISSION_OIL("변속기오일"),
    TIRE_LOW_PRESSURE("타이어 공기압"),
    WASHER_FLUID("워셔액"),
}

/** 경고 심각도 */
enum class Severity { CRITICAL, CAUTION, INFO }

/** 차량 SDK 값의 수준 (SDK OilLevelState를 앱 내부 값으로 옮긴 것) */
enum class FluidLevel { NORMAL, LOW, CRITICALLY_LOW, HIGH, UNKNOWN }

data class VehicleWarning(
    val type: WarningType,
    val severity: Severity,
    /** 예: 타이어 위치 "왼쪽 뒤" */
    val position: String? = null,
)

/** 상황 판단 엔진이 내리는 행동 4종 */
enum class Action {
    /** 즉시 안전한 곳에 정차 */
    STOP_NOW,
    /** 경로 위 정비소를 경유지로 추가 */
    VIA_REPAIR_SHOP,
    /** 목적지 도착 후 점검 */
    CHECK_ON_ARRIVAL,
    /** 다음 주유·정차 때 처리 */
    NEXT_TIME,
}

data class GeoPoint(val lat: Double, val lon: Double)

data class RepairShop(
    val name: String,
    val address: String,
    val phone: String?,
    val point: GeoPoint,
)

/** 판단에 쓰는 주행 맥락 */
data class DrivingContext(
    val isDriving: Boolean,
    /** 목적지까지 남은 거리(m). 경로 안내 중이 아니면 null */
    val remainDistanceM: Int? = null,
    /** 목적지까지 남은 시간(초) */
    val remainTimeSec: Int? = null,
    val isNight: Boolean = false,
    val current: GeoPoint? = null,
    val destination: GeoPoint? = null,
)

data class ShopSuggestion(
    val shop: RepairShop,
    /** 현재 위치에서 정비소까지 직선거리(m) */
    val distanceM: Int,
    /** 목적지 경로 대비 추가로 돌아가는 거리(m). 목적지가 없으면 null */
    val detourM: Int?,
)

data class Decision(
    val warning: VehicleWarning,
    val action: Action,
    /** 주행 중에 보여줄 한 줄 */
    val headline: String,
    /** 정차 시 보여줄 상세 설명 */
    val detail: String,
    val shop: ShopSuggestion? = null,
)

/** 비·야간 전조등 같은 안전 운전 제안 */
enum class SuggestionType { RAIN_HEADLIGHT, NIGHT_HEADLIGHT }

data class Suggestion(val type: SuggestionType, val message: String)
