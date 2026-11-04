package app.carero.data

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import app.carero.engine.GeoPoint
import app.carero.engine.RepairShop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pleos NaviHelper SDK 연동: 남은 거리·시간, 목적지, 현재 위치 수신과 정비소 경유지 추가.
 * 정보 조회 API는 결과를 리스너 콜백으로 돌려주므로, 경로 안내 중에만 15초 간격으로 요청합니다.
 */
class PleosNaviSource(context: Context, private val scope: CoroutineScope) {
    private val tag = "CareroNavi"
    private val navi = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(NaviSnapshot())
    val state: StateFlow<NaviSnapshot> = _state
    private var pollJob: Job? = null
    private var started = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _state.update { it.copy(routing = true, remainDistanceM = routeStartInfo.distance, remainTimeSec = routeStartInfo.duration, destinationName = routeStartInfo.name) }
            navi.getDestinationInfo()
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            _state.update {
                it.copy(
                    routing = true,
                    remainDistanceM = destinationInfo.remainDistance,
                    remainTimeSec = destinationInfo.remainTime,
                    destination = GeoPoint(destinationInfo.latitude, destinationInfo.longitude),
                    destinationName = destinationInfo.name,
                )
            }
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _state.update { it.copy(current = GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude)) }
        }
        override fun onRouteEnded() = clearRoute()
        override fun onRouteCancelled() = clearRoute()
        override fun onRoutingCompleted() { navi.getDestinationInfo() }
    }

    fun start() {
        if (started) return
        try {
            navi.initialize({
                _state.update { it.copy(sdkConnected = true) }
                navi.addListener(listener)
                navi.getCurrentLocationInfo()
                navi.getDestinationInfo()
            }, { e -> Log.w(tag, "NaviHelper init failed", e) })
            started = true
        } catch (e: Exception) {
            Log.w(tag, "NaviHelper unavailable", e)
            return
        }
        pollJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (_state.value.sdkConnected) {
                    safe { navi.getCurrentLocationInfo() }
                    if (_state.value.routing) safe { navi.getDestinationInfo() }
                }
            }
        }
    }

    /** 정비소를 첫 번째 경유지로 추가. 성공 여부는 onRoutingCompleted 이벤트로 확인됩니다. */
    fun addWaypoint(shop: RepairShop): Boolean {
        if (!_state.value.sdkConnected) return false
        return try {
            navi.addWaypoint(
                RequestWaypointInfo(shop.point.lat, shop.point.lon, shop.name, WaypointIndex.FIRST, "", shop.address, "", ""),
            )
            true
        } catch (e: Exception) {
            Log.w(tag, "addWaypoint failed", e); false
        }
    }

    private fun clearRoute() = _state.update { it.copy(routing = false, remainDistanceM = null, remainTimeSec = null, destination = null, destinationName = null) }

    fun stop() {
        if (!started) return
        pollJob?.cancel()
        safe { navi.removeListener(listener) }
        safe { navi.release() }
        started = false
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(tag, "Navi call failed", e) }
    }
}
