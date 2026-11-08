package app.carero.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.carero.CheckItem
import app.carero.CheckStatus
import app.carero.UiState
import app.carero.data.DemoState
import app.carero.engine.Decision
import app.carero.engine.ActiveTelltale
import app.carero.engine.DecisionEngine
import app.carero.engine.TelltaleGuide
import app.carero.engine.DrivingContext
import app.carero.engine.GeoPoint
import app.carero.engine.RepairShop
import app.carero.engine.Severity
import app.carero.engine.Suggestion
import app.carero.engine.SuggestionType
import app.carero.engine.VehicleWarning
import app.carero.engine.WarningType
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 시연 시나리오 화면을 JVM에서 렌더링해 확인합니다(기획서 화면 시안으로도 사용). */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_10.copy(screenWidth = 2560, screenHeight = 1440, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        showSystemUi = false,
    )

    private val shops = listOf(
        RepairShop("두정모터스", "충청남도 천안시 서북구 천안대로 1024-6", null, GeoPoint(36.952508, 127.131118)),
        RepairShop("하나모터스", "충청남도 천안시 동남구 천안대로 683", null, GeoPoint(36.824677, 127.152976)),
        RepairShop("(주)선유모터스", "서울특별시 영등포구 선유로45길 14", "02-2672-1468", GeoPoint(37.5370, 126.8960)),
    )
    private val engine = DecisionEngine(shops)
    private val noop = object : CareroActions {
        override fun dismiss(warning: VehicleWarning) {}
        override fun resetDismissed() {}
        override fun addWaypoint(decision: Decision) {}
        override fun setDemo(update: (DemoState) -> DemoState) {}
    }
    private val oil = VehicleWarning(WarningType.ENGINE_OIL, Severity.CAUTION)
    private fun checks(oilWarn: Boolean = false, brakeCrit: Boolean = false, washer: Boolean = false) = listOf(
        CheckItem("브레이크액", if (brakeCrit) CheckStatus.CRITICAL else CheckStatus.OK),
        CheckItem("엔진오일", if (oilWarn) CheckStatus.WARN else CheckStatus.OK),
        CheckItem("변속기오일", CheckStatus.OK),
        CheckItem("타이어 공기압", CheckStatus.UNSUPPORTED),
        CheckItem("워셔액", if (washer) CheckStatus.WARN else CheckStatus.OK),
        CheckItem("계기판 경고등", CheckStatus.OK),
    )
    private fun state(ws: List<VehicleWarning>, ctx: DrivingContext, checks: List<CheckItem>, suggestion: Suggestion? = null) =
        UiState(driving = ctx.isDriving, ctx = ctx, decisions = engine.decide(ws, ctx), suggestion = suggestion, checks = checks,
            demo = DemoState(enabled = true), vehicleSdk = true, naviSdk = true)

    @Test fun s1_near_home_oil() {
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 3_000, current = GeoPoint(37.5156, 126.9077), destination = GeoPoint(37.5260, 126.8966))
        paparazzi.snapshot { CareroScreen(state(listOf(oil), ctx, checks(oilWarn = true)), noop) }
    }

    @Test fun s2_night_highway_oil() {
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 120_000, isNight = true, current = GeoPoint(37.2636, 127.0286), destination = GeoPoint(36.3326, 127.4342))
        paparazzi.snapshot { CareroScreen(state(listOf(oil), ctx, checks(oilWarn = true)), noop) }
    }

    @Test fun s3_brake_critical() {
        val ctx = DrivingContext(isDriving = true, current = GeoPoint(37.5156, 126.9077))
        paparazzi.snapshot { CareroScreen(state(listOf(VehicleWarning(WarningType.BRAKE_FLUID, Severity.CRITICAL)), ctx, checks(brakeCrit = true)), noop) }
    }

    @Test fun s4_rain_headlight() {
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 18_000, current = GeoPoint(37.5156, 126.9077))
        paparazzi.snapshot { CareroScreen(state(emptyList(), ctx, checks(), Suggestion(SuggestionType.RAIN_HEADLIGHT, "비 올 땐 전조등을 켜주세요")), noop) }
    }

    @Test fun s5_parked_detail() {
        val ctx = DrivingContext(isDriving = false, current = GeoPoint(37.5156, 126.9077))
        val ws = listOf(oil, VehicleWarning(WarningType.WASHER_FLUID, Severity.INFO))
        paparazzi.snapshot { CareroScreen(state(ws, ctx, checks(oilWarn = true, washer = true)), noop) }
    }

    @Test fun s6_demo_panel() {
        val ctx = DrivingContext(isDriving = false, current = GeoPoint(37.5156, 126.9077))
        paparazzi.snapshot { CareroScreen(state(emptyList(), ctx, checks()).copy(demo = DemoState(enabled = true, driving = true, remainKm = 120, engineOilLow = true)), noop, initialShowDemo = true) }
    }

    @Test fun s7_telltale_driving() {
        // 주행 중 엔진 점검등(노랑) + 안전벨트(바로 조치)
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 120_000, current = GeoPoint(37.2636, 127.0286), destination = GeoPoint(36.3326, 127.4342))
        val ws = listOf("SEAT_BELT", "ENGINE").mapNotNull { TelltaleGuide.warningOf(ActiveTelltale(it)) }
        paparazzi.snapshot { CareroScreen(state(ws, ctx, checks()), noop) }
    }

    @Test fun s8_telltale_guide() {
        val ctx = DrivingContext(isDriving = false, current = GeoPoint(37.5156, 126.9077))
        val on = listOf(ActiveTelltale("ENGINE"), ActiveTelltale("TPMS"))
        val ws = on.mapNotNull { TelltaleGuide.warningOf(it) }
        paparazzi.snapshot { CareroScreen(state(ws, ctx, checks()).copy(telltales = on, telltaleSupported = true), noop, initialShowGuide = true) }
    }
}
