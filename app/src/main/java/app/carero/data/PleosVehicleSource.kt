package app.carero.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import app.carero.engine.ActiveTelltale
import app.carero.engine.FluidLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pleos Vehicle SDK 연동.
 * - 변화가 잦지 않은 값은 리스너로 구독하고(폴링 최소화), 리스너가 없는 값(변속기오일 30초, 와이퍼 스위치 3초)만 주기적으로 조회합니다.
 * - 조회 실패(미지원 차종, 권한 없음)는 unsupported로 기록하고 판단에서 제외합니다.
 */
class PleosVehicleSource(context: Context, private val scope: CoroutineScope) {
    companion object {
        /**
         * 계기판 경고등(Safety.getTelltalesInfo) 사용 여부.
         * 이 API는 공개 문서에 없는 비공개 권한(pleos.car.permission.PRIVATE_CAR_SAFETY)이 필요하고,
         * Pleos 문서상 승인되지 않은 권한·API를 쓰면 앱 심사를 통과할 수 없어 꺼 둡니다.
         * 승인을 받으면 true로 바꾸고 매니페스트에 위 권한을 추가하면 됩니다(에뮬레이터에서 동작 확인함).
         */
        const val USE_TELLTALE_API = false
    }

    private val tag = "CareroVehicle"
    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(VehicleSnapshot())
    val state: StateFlow<VehicleSnapshot> = _state
    private var pollJob: Job? = null
    private var started = false

    private val frontWiper = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val brakeListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(p0: OilLevelState?) = _state.update { it.copy(brakeFluid = p0.toLevel()) }
        override fun onFailed(p0: Exception) = unsupported("brake", p0)
    }
    private val engineListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(p0: OilLevelState?) = _state.update { it.copy(engineOil = p0.toLevel()) }
        override fun onFailed(p0: Exception) = unsupported("engineOil", p0)
    }
    private val washerListener = object : LowWasherFluidWaringListener {
        override fun onLowWasherFluidWaringUpdated(p0: Boolean?) = _state.update { it.copy(washerLow = p0) }
        override fun onFailed(p0: Exception) = unsupported("washer", p0)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(p0: WiperState?) = _state.update { it.copy(wiperOn = p0.toOn()) }
        override fun onFailed(p0: Exception) = unsupported("wiper", p0)
    }
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(p0: LightState?) = _state.update { it.copy(lowBeamOn = p0.toOn()) }
        override fun onFailed(p0: Exception) = unsupported("lowBeam", p0)
    }

    // 계기판 경고등 전체 목록. 켜짐(ON)·깜빡임(BLINK)만 남깁니다.
    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(p0: Telltales?) = onTelltales(p0)
        override fun onFailed(p0: Exception) = unsupported("telltale", p0)
    }

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
            started = true
            _state.update { it.copy(sdkConnected = true) }
        } catch (e: Exception) {
            Log.w(tag, "Vehicle SDK unavailable", e)
            return
        }
        val safety = vehicle.getSafety()
        safe { safety.registerBrakeFluidLevelState(brakeListener) }
        safe { safety.registerEngineOilLevelState(engineListener) }
        safe { safety.registerLowWasherFluidWaringState(washerListener) }
        safe { vehicle.getWiper().registerWiperState(frontWiper, wiperListener) }
        safe { vehicle.getLight().registerLowBeamLightState(lowBeamListener) }
        if (USE_TELLTALE_API) safe { safety.registerTelltales(telltaleListener) }
        // 초기값
        safe { safety.getBrakeFluidLevelState({ v -> _state.update { it.copy(brakeFluid = v.toLevel()) } }, { unsupported("brake", it) }) }
        safe { safety.getEngineOilLevelState({ v -> _state.update { it.copy(engineOil = v.toLevel()) } }, { unsupported("engineOil", it) }) }
        safe { safety.hasLowWasherFluidWarning({ v -> _state.update { it.copy(washerLow = v) } }, { unsupported("washer", it) }) }
        safe { vehicle.getWiper().getCurrentWiperState(frontWiper, { v -> _state.update { it.copy(wiperOn = v.toOn()) } }, { unsupported("wiper", it) }) }
        safe { vehicle.getLight().getCurrentLowBeamLightState({ v -> _state.update { it.copy(lowBeamOn = v.toOn()) } }, { unsupported("lowBeam", it) }) }
        if (USE_TELLTALE_API) safe { safety.getTelltalesInfo({ v -> onTelltales(v) }, { unsupported("telltale", it) }) }
        else _state.update { it.copy(unsupported = it.unsupported + "telltale") }

        pollJob = scope.launch {
            var tick = 0
            while (isActive) {
                // 와이퍼 스위치는 리스너가 없어 3초마다, 타이어·변속기오일은 30초마다 조회
                pollWiperSwitch()
                if (tick % 10 == 0) pollSlowValues()
                tick++
                delay(3_000)
            }
        }
    }

    private fun pollWiperSwitch() {
        safe {
            vehicle.getWiper().getCurrentWiperSwitch(
                frontWiper,
                { v -> _state.update { it.copy(wiperSwitchOn = v.toOn()) } },
                { unsupported("wiperSwitch", it) },
            )
        }
    }

    private fun pollSlowValues() {
        safe {
            vehicle.getSafety().getTransmissionOilLevelState(
                { v -> _state.update { it.copy(transmissionOil = v.toLevel()) } },
                { unsupported("transmissionOil", it) },
            )
        }
        // 타이어 저압: Tire.getLowTirePressure는 저압 '기준값'(임계 압력)을 돌려주며 실제 공기압은 비공개 API입니다.
        // 따라서 실제 데이터로는 조회하지 않습니다.
    }

    fun stop() {
        if (!started) return
        pollJob?.cancel()
        val safety = vehicle.getSafety()
        safe { safety.unregisterBrakeFluidLevelState(brakeListener) }
        safe { safety.unregisterEngineOilLevelState(engineListener) }
        safe { safety.unregisterLowWasherFluidWaringState(washerListener) }
        safe { vehicle.getWiper().unregisterWiperState(frontWiper, wiperListener) }
        safe { vehicle.getLight().unregisterLowBeamLightState(lowBeamListener) }
        if (USE_TELLTALE_API) safe { safety.unregisterTelltales(telltaleListener) }
        safe { vehicle.release() }
        started = false
    }

    private fun unsupported(key: String, e: Exception) {
        Log.i(tag, "$key unavailable: ${e.message}")
        _state.update { it.copy(unsupported = it.unsupported + key) }
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(tag, "SDK call failed", e) }
    }

    private fun onTelltales(t: Telltales?) {
        val active = t.toActive()
        Log.i(tag, "telltales: ${t?.telltales?.size ?: 0} received, on=${active.joinToString { it.id + if (it.blinking) "(blink)" else "" }}")
        _state.update { it.copy(telltales = active) }
    }

    private fun Telltales?.toActive(): List<ActiveTelltale> =
        this?.telltales.orEmpty()
            .filter { it.state?.name == "ON" || it.state?.name == "BLINK" }
            .mapNotNull { t -> t.type?.name?.let { ActiveTelltale(it, blinking = t.state?.name == "BLINK") } }
            .distinctBy { it.id }

    private fun OilLevelState?.toLevel(): FluidLevel = when (this?.name) {
        "LOW" -> FluidLevel.LOW
        "CRITICALLY_LOW" -> FluidLevel.CRITICALLY_LOW
        "NORMAL" -> FluidLevel.NORMAL
        "HIGH" -> FluidLevel.HIGH
        else -> FluidLevel.UNKNOWN
    }

    private fun WiperState?.toOn(): Boolean? = when (this?.name) {
        "ON" -> true
        "OFF" -> false
        else -> null
    }

    /** 저속·고속·간헐·미스트는 작동 중. AUTO는 비 여부를 알 수 없어 판단하지 않습니다. */
    private fun WiperSwitch?.toOn(): Boolean? = when (val n = this?.name) {
        null, "OTHER", "SERVICE", "AUTO" -> null
        "OFF" -> false
        else -> n.startsWith("CONTINUOUS") || n.startsWith("INTERMITTENT") || n == "MIST"
    }

    /** 주간주행등(DAYTIME_RUNNING)은 전조등이 아니므로 꺼진 것으로 봅니다. */
    private fun LightState?.toOn(): Boolean? = when (this?.name) {
        "ON" -> true
        "OFF", "DAYTIME_RUNNING" -> false
        else -> null
    }
}
