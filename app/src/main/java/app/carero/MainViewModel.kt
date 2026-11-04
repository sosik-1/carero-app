package app.carero

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.carero.data.DemoState
import app.carero.data.NaviSnapshot
import app.carero.data.PleosNaviSource
import app.carero.data.PleosVehicleSource
import app.carero.data.RepairShopRepository
import app.carero.data.SpeedSource
import app.carero.data.VehicleSnapshot
import app.carero.engine.Decision
import app.carero.engine.DecisionEngine
import app.carero.engine.DrivingContext
import app.carero.engine.FluidLevel
import app.carero.engine.Geo
import app.carero.engine.GeoPoint
import app.carero.engine.Severity
import app.carero.engine.Suggestion
import app.carero.engine.SuggestionEngine
import app.carero.engine.VehicleWarning
import app.carero.engine.WarningType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class CheckStatus { OK, WARN, CRITICAL, UNKNOWN, UNSUPPORTED }
data class CheckItem(val label: String, val status: CheckStatus)

data class UiState(
    val driving: Boolean = false,
    val ctx: DrivingContext = DrivingContext(isDriving = false),
    val decisions: List<Decision> = emptyList(),
    val suggestion: Suggestion? = null,
    val checks: List<CheckItem> = emptyList(),
    val demo: DemoState = DemoState(),
    val vehicleSdk: Boolean = false,
    val naviSdk: Boolean = false,
    val toast: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app), app.carero.ui.CareroActions {
    private val shops = RepairShopRepository.load(app)
    private val engine = DecisionEngine(shops)
    private val suggestions = SuggestionEngine()
    private val vehicleSource = PleosVehicleSource(app, viewModelScope)
    private val naviSource = PleosNaviSource(app, viewModelScope)
    private val speedSource = SpeedSource(app)

    private val demo = MutableStateFlow(DemoState())
    private val dismissed = MutableStateFlow<Set<WarningType>>(emptySet())
    private val activeSuggestion = MutableStateFlow<Suggestion?>(null)
    private val toast = MutableStateFlow<String?>(null)
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private var wasDriving = false

    val state: StateFlow<UiState>

    init {
        vehicleSource.start()
        naviSource.start()
        speedSource.start()
        viewModelScope.launch { while (true) { delay(60_000); clock.value = System.currentTimeMillis() } }

        val inputs = combine(vehicleSource.state, naviSource.state, combine(speedSource.speedKmh, speedSource.gearDrive) { sp, g -> sp to g }, demo, clock) { v, n, s, d, t -> Inputs(v, n, s.first, d, t, s.second) }
        state = combine(inputs, dismissed, activeSuggestion, toast) { i, dis, sug, msg -> build(i, dis, sug, msg) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    private data class Inputs(val v: VehicleSnapshot, val n: NaviSnapshot, val speed: Float?, val d: DemoState, val now: Long, val gearDrive: Boolean? = null)

    private fun build(i: Inputs, dismissedTypes: Set<WarningType>, sug: Suggestion?, msg: String?): UiState {
        val d = i.d
        val driving = if (d.enabled && d.driving != null) d.driving else (i.speed ?: 0f) >= 2f || i.gearDrive == true
        if (driving && !wasDriving) suggestions.newTrip()
        wasDriving = driving

        val (current, destination, remainM) = if (d.enabled) demoGeo(d) else Triple(i.n.current, i.n.destination, i.n.remainDistanceM)
        val night = if (d.enabled && d.night != null) d.night else isNight(current?.takeIf { it.lat in 33.0..39.0 && it.lon in 124.0..132.0 } ?: SEOUL, i.now)
        val ctx = DrivingContext(isDriving = driving, remainDistanceM = remainM, remainTimeSec = if (d.enabled) null else i.n.remainTimeSec, isNight = night, current = current, destination = destination)

        val warnings = if (d.enabled) demoWarnings(d) else warningsOf(i.v)
        val decisions = engine.decide(warnings.filter { it.type !in dismissedTypes }, ctx)

        val wiper = if (d.enabled) d.rain else when {
            i.v.wiperOn == true || i.v.wiperSwitchOn == true -> true
            i.v.wiperOn == false || i.v.wiperSwitchOn == false -> false
            else -> null
        }
        val lowBeam = if (d.enabled) !d.headlightOff else i.v.lowBeamOn
        suggestions.evaluate(driving, wiper, lowBeam, night)?.let { s ->
            activeSuggestion.value = s
            viewModelScope.launch { delay(10_000); if (activeSuggestion.value == s) activeSuggestion.value = null }
        }

        return UiState(
            driving = driving, ctx = ctx, decisions = decisions, suggestion = activeSuggestion.value ?: sug,
            checks = checksOf(if (d.enabled) demoSnapshot(d) else i.v), demo = d,
            vehicleSdk = i.v.sdkConnected, naviSdk = i.n.sdkConnected, toast = msg,
        )
    }

    private fun warningsOf(v: VehicleSnapshot): List<VehicleWarning> = buildList {
        DecisionEngine.severityOf(v.brakeFluid ?: FluidLevel.UNKNOWN)?.let { add(VehicleWarning(WarningType.BRAKE_FLUID, it)) }
        DecisionEngine.severityOf(v.engineOil ?: FluidLevel.UNKNOWN)?.let { add(VehicleWarning(WarningType.ENGINE_OIL, it)) }
        DecisionEngine.severityOf(v.transmissionOil ?: FluidLevel.UNKNOWN)?.let { add(VehicleWarning(WarningType.TRANSMISSION_OIL, it)) }
        // 타이어: 공개 API(getLowTirePressure)는 저압 '기준값'만 제공하고 실제 공기압은 비공개 API라 실제 데이터로는 판단하지 않습니다(데모 모드에서만 재현).
        if (v.washerLow == true) add(VehicleWarning(WarningType.WASHER_FLUID, Severity.INFO))
    }

    private fun checksOf(v: VehicleSnapshot): List<CheckItem> {
        fun fluid(key: String, label: String, l: FluidLevel?) = CheckItem(label, when {
            key in v.unsupported -> CheckStatus.UNSUPPORTED
            l == null || l == FluidLevel.UNKNOWN -> CheckStatus.UNKNOWN
            l == FluidLevel.CRITICALLY_LOW -> CheckStatus.CRITICAL
            l == FluidLevel.LOW -> CheckStatus.WARN
            else -> CheckStatus.OK
        })
        val tire = CheckItem("타이어 공기압", when {
            v.tireLow.isEmpty() -> CheckStatus.UNSUPPORTED // 실제 공기압은 비공개 API
            v.tireLow.values.any { it } -> CheckStatus.WARN
            else -> CheckStatus.OK
        })
        val washer = CheckItem("워셔액", when {
            "washer" in v.unsupported -> CheckStatus.UNSUPPORTED
            v.washerLow == null -> CheckStatus.UNKNOWN
            v.washerLow -> CheckStatus.WARN
            else -> CheckStatus.OK
        })
        return listOf(
            fluid("brake", "브레이크액", v.brakeFluid), fluid("engineOil", "엔진오일", v.engineOil),
            fluid("transmissionOil", "변속기오일", v.transmissionOil), tire, washer,
        )
    }

    // ---------- 데모 모드 (시연 영상용 가상 값) ----------
    private fun demoWarnings(d: DemoState) = buildList {
        if (d.brakeCritical) add(VehicleWarning(WarningType.BRAKE_FLUID, Severity.CRITICAL))
        if (d.engineOilLow) add(VehicleWarning(WarningType.ENGINE_OIL, Severity.CAUTION))
        if (d.tireLow) add(VehicleWarning(WarningType.TIRE_LOW_PRESSURE, Severity.CAUTION, "왼쪽 뒤"))
        if (d.washerLow) add(VehicleWarning(WarningType.WASHER_FLUID, Severity.INFO))
    }

    private fun demoSnapshot(d: DemoState) = VehicleSnapshot(
        brakeFluid = if (d.brakeCritical) FluidLevel.CRITICALLY_LOW else FluidLevel.NORMAL,
        engineOil = if (d.engineOilLow) FluidLevel.LOW else FluidLevel.NORMAL,
        transmissionOil = FluidLevel.NORMAL,
        washerLow = d.washerLow,
        tireLow = mapOf("왼쪽 앞" to false, "오른쪽 앞" to false, "왼쪽 뒤" to d.tireLow, "오른쪽 뒤" to false),
    )

    /** 데모 위치: 120km → 수원에서 대전으로 가는 길, 3km → 영등포 도심 */
    private fun demoGeo(d: DemoState): Triple<GeoPoint?, GeoPoint?, Int?> = when (d.remainKm) {
        null -> Triple(YEONGDEUNGPO, null, null)
        in 0..10 -> Triple(YEONGDEUNGPO, GeoPoint(37.5260, 126.8966), d.remainKm * 1000)
        else -> Triple(SUWON, DAEJEON, d.remainKm * 1000)
    }

    override fun setDemo(update: (DemoState) -> DemoState) = demo.update(update)

    override fun dismiss(type: WarningType) = dismissed.update { it + type }

    override fun resetDismissed() { dismissed.value = emptySet() }

    override fun addWaypoint(decision: Decision) {
        val shop = decision.shop?.shop ?: return
        val ok = if (demo.value.enabled) true else naviSource.addWaypoint(shop)
        toast.value = if (ok) "${shop.name}을(를) 경유지로 추가했어요" else "내비게이션에 연결되지 않아 경유지를 추가하지 못했어요"
        dismiss(decision.warning.type)
        viewModelScope.launch { delay(4_000); toast.value = null }
    }

    private fun isNight(p: GeoPoint, now: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val (rise, set) = Geo.sunriseSunset(p, cal.get(Calendar.DAY_OF_YEAR), cal.timeZone.getOffset(now) / 3_600_000.0) ?: return false
        val h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
        return h < rise || h > set
    }

    override fun onCleared() {
        vehicleSource.stop(); naviSource.stop(); speedSource.stop()
    }

    companion object {
        val SEOUL = GeoPoint(37.5665, 126.9780)
        val YEONGDEUNGPO = GeoPoint(37.5156, 126.9077)
        val SUWON = GeoPoint(37.2636, 127.0286)
        val DAEJEON = GeoPoint(36.3326, 127.4342)
    }
}
