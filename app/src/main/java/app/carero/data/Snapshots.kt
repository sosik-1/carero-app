package app.carero.data

import app.carero.engine.FluidLevel
import app.carero.engine.GeoPoint

/** 차량 상태 한 시점. null은 '아직 못 받음' 또는 '이 차종에서 지원하지 않음'. */
data class VehicleSnapshot(
    val brakeFluid: FluidLevel? = null,
    val engineOil: FluidLevel? = null,
    val transmissionOil: FluidLevel? = null,
    val washerLow: Boolean? = null,
    /** 바퀴 위치 → 저압 여부 */
    val tireLow: Map<String, Boolean> = emptyMap(),
    val wiperOn: Boolean? = null,
    val lowBeamOn: Boolean? = null,
    val unsupported: Set<String> = emptySet(),
    val sdkConnected: Boolean = false,
)

/** 내비게이션 경로 상태 */
data class NaviSnapshot(
    val routing: Boolean = false,
    val remainDistanceM: Int? = null,
    val remainTimeSec: Int? = null,
    val destination: GeoPoint? = null,
    val destinationName: String? = null,
    val current: GeoPoint? = null,
    val sdkConnected: Boolean = false,
)

/** 시연용 가상 값. 실제 SDK 값보다 우선합니다(화면에 '데모 모드'로 표시). */
data class DemoState(
    val enabled: Boolean = false,
    val driving: Boolean? = null,
    val remainKm: Int? = null,
    val night: Boolean? = null,
    val tireLow: Boolean = false,
    val brakeCritical: Boolean = false,
    val engineOilLow: Boolean = false,
    val washerLow: Boolean = false,
    val rain: Boolean = false,
    val headlightOff: Boolean = false,
)
