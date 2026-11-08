package app.carero.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    // 서울역 → 대전역 방향 경로를 가정한 좌표
    private val seoul = GeoPoint(37.5547, 126.9707)
    private val daejeon = GeoPoint(36.3326, 127.4342)
    private val nearHome = GeoPoint(37.5600, 126.9750)

    private val shopOnRoute = RepairShop("경로위정비", "경기 오산", null, GeoPoint(37.1498, 127.0772))   // 서울에서 남쪽 약 46km
    private val shopBehind = RepairShop("반대편정비", "경기 파주", null, GeoPoint(37.7599, 126.7802))    // 서울 북서쪽(목적지 반대)
    private val engine = DecisionEngine(listOf(shopOnRoute, shopBehind))

    private val tire = VehicleWarning(WarningType.TIRE_LOW_PRESSURE, Severity.CAUTION, "왼쪽 뒤")

    @Test fun `같은 타이어 경고 - 집 근처 3km면 도착 후 점검`() {
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 3_000, current = nearHome, destination = seoul)
        val d = engine.decideOne(tire, ctx)
        assertEquals(Action.CHECK_ON_ARRIVAL, d.action)
        assertTrue(d.headline.contains("도착 후"))
    }

    @Test fun `같은 타이어 경고 - 야간 고속도로 120km면 경로 위 정비소 경유`() {
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 120_000, isNight = true, current = seoul, destination = daejeon)
        val d = engine.decideOne(tire, ctx)
        assertEquals(Action.VIA_REPAIR_SHOP, d.action)
        assertEquals("경로위정비", d.shop?.shop?.name)
        assertTrue(d.headline.contains("120km"))
    }

    @Test fun `목적지 반대 방향 정비소는 고르지 않음`() {
        val onlyBehind = DecisionEngine(listOf(shopBehind))
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 120_000, current = seoul, destination = daejeon)
        assertNull(onlyBehind.bestShop(ctx))
    }

    @Test fun `정비소가 없으면 도착 후 점검으로 대체`() {
        val none = DecisionEngine(emptyList())
        val ctx = DrivingContext(isDriving = true, remainDistanceM = 120_000, current = seoul, destination = daejeon)
        assertEquals(Action.CHECK_ON_ARRIVAL, none.decideOne(tire, ctx).action)
    }

    @Test fun `브레이크액 위험 수준은 남은 거리와 상관없이 즉시 정차`() {
        val brake = VehicleWarning(WarningType.BRAKE_FLUID, DecisionEngine.severityOf(FluidLevel.CRITICALLY_LOW)!!)
        for (remain in listOf(1_000, 200_000)) {
            val d = engine.decideOne(brake, DrivingContext(isDriving = true, remainDistanceM = remain, current = seoul))
            assertEquals(Action.STOP_NOW, d.action)
            assertTrue(d.headline.contains("정차"))
        }
    }

    @Test fun `워셔액 부족은 다음 기회에`() {
        val washer = VehicleWarning(WarningType.WASHER_FLUID, Severity.INFO)
        assertEquals(Action.NEXT_TIME, engine.decideOne(washer, DrivingContext(isDriving = true)).action)
    }

    @Test fun `정차 중 주의 경고는 지금 확인 안내`() {
        val d = engine.decideOne(tire, DrivingContext(isDriving = false))
        assertEquals(Action.CHECK_ON_ARRIVAL, d.action)
        assertTrue(d.headline.contains("지금 확인"))
    }

    @Test fun `여러 경고는 심각한 순서로 정렬`() {
        val list = listOf(
            VehicleWarning(WarningType.WASHER_FLUID, Severity.INFO),
            tire,
            VehicleWarning(WarningType.ENGINE_OIL, Severity.CRITICAL),
        )
        val ds = engine.decide(list, DrivingContext(isDriving = true, current = seoul))
        assertEquals(listOf(Severity.CRITICAL, Severity.CAUTION, Severity.INFO), ds.map { it.warning.severity })
    }

    @Test fun `정상 수준은 경고가 아님`() {
        assertNull(DecisionEngine.severityOf(FluidLevel.NORMAL))
        assertNull(DecisionEngine.severityOf(FluidLevel.UNKNOWN))
        assertEquals(Severity.CAUTION, DecisionEngine.severityOf(FluidLevel.LOW))
    }
    // ---------- 계기판 경고등 ----------
    private fun lamp(id: String, blink: Boolean = false) = TelltaleGuide.warningOf(ActiveTelltale(id, blink))

    @Test fun `빨간 엔진 과열 경고등은 즉시 정차`() {
        val w = lamp("ENGINE_TEMPERATURE")!!
        val d = engine.decideOne(w, DrivingContext(isDriving = true, current = seoul))
        assertEquals(Action.STOP_NOW, d.action)
        assertTrue(d.headline.contains("엔진 과열"))
        assertTrue(d.detail.contains("라디에이터"))
    }

    @Test fun `같은 엔진 점검등 - 3km면 도착 후, 120km면 정비소 경유`() {
        val w = lamp("ENGINE")!!
        assertEquals(Action.CHECK_ON_ARRIVAL, engine.decideOne(w, DrivingContext(isDriving = true, remainDistanceM = 3_000, current = nearHome, destination = seoul)).action)
        assertEquals(Action.VIA_REPAIR_SHOP, engine.decideOne(w, DrivingContext(isDriving = true, remainDistanceM = 120_000, current = seoul, destination = daejeon)).action)
    }

    @Test fun `엔진 점검등이 깜빡이면 즉시 정차`() {
        assertEquals(Action.STOP_NOW, engine.decideOne(lamp("ENGINE", blink = true)!!, DrivingContext(isDriving = true, current = seoul)).action)
    }

    @Test fun `안전벨트 경고등은 바로 조치 한 줄`() {
        val d = engine.decideOne(lamp("SEAT_BELT")!!, DrivingContext(isDriving = true, current = seoul))
        assertEquals(Action.FIX_NOW, d.action)
        assertEquals("안전벨트를 매지 않은 자리가 있어요", d.headline)
        assertNull(d.shop)
    }

    @Test fun `ESC가 깜빡이는 건 정상 작동이라 경고 아님`() {
        assertNull(lamp("ESC", blink = true))
        assertNotNull(lamp("ESC"))
    }

    @Test fun `안내 표시와 모르는 경고등은 경고 아님`() {
        assertNull(lamp("AUTO_HOLD"))
        assertNull(lamp("TURN_SIGNAL_LEFT"))
    }

    @Test fun `경고등 사전 id는 겹치지 않고 SDK 이름 형식`() {
        val ids = TelltaleGuide.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[A-Z0-9_]+")) })
        assertTrue(TelltaleGuide.all.all { it.level != TelltaleLevel.FIX_NOW || it.quick != null })
    }

    @Test fun `경고등 이름이 겹치지 않게 읽힘`() {
        assertTrue(Messages.what(lamp("ABS")!!).startsWith("ABS 경고등이"))
        assertTrue(Messages.what(lamp("ENGINE")!!).startsWith("엔진 점검 경고등이"))
        assertTrue(Messages.what(lamp("ENGINE", blink = true)!!).endsWith("깜빡여요"))
    }

    @Test fun `정차 중 경고등 안내는 사전의 할 일을 한 번만`() {
        val d = engine.decideOne(lamp("ABS")!!, DrivingContext(isDriving = false, current = seoul))
        assertEquals(1, Regex("급제동을 피하고").findAll(d.detail).count())
        assertTrue(!d.detail.contains("상태를 확인"))
    }

    @Test fun `같은 수준이면 바로 조치할 일이 먼저`() {
        val ds = engine.decide(listOf(lamp("ABS")!!, lamp("SEAT_BELT")!!, lamp("ENGINE_TEMPERATURE")!!), DrivingContext(isDriving = true, current = seoul))
        assertEquals(listOf("ENGINE_TEMPERATURE", "SEAT_BELT", "ABS"), ds.map { it.warning.telltale })
    }

    @Test fun `경고등은 등마다 따로 숨김`() {
        assertTrue(lamp("ABS")!!.key != lamp("ENGINE")!!.key)
        assertEquals("ENGINE_OIL", VehicleWarning(WarningType.ENGINE_OIL, Severity.CAUTION).key)
    }
}

class SuggestionEngineTest {
    @Test fun `비 오는데 전조등 꺼짐 - 한 주행에 한 번만 제안`() {
        val e = SuggestionEngine()
        val first = e.evaluate(isDriving = true, wiperOn = true, lowBeamOn = false, isNight = false)
        assertNotNull(first)
        assertEquals(SuggestionType.RAIN_HEADLIGHT, first!!.type)
        assertNull(e.evaluate(isDriving = true, wiperOn = true, lowBeamOn = false, isNight = false))
        e.newTrip()
        assertNotNull(e.evaluate(isDriving = true, wiperOn = true, lowBeamOn = false, isNight = false))
    }

    @Test fun `전조등이 켜져 있거나 정차 중이면 제안 없음`() {
        val e = SuggestionEngine()
        assertNull(e.evaluate(isDriving = true, wiperOn = true, lowBeamOn = true, isNight = true))
        assertNull(e.evaluate(isDriving = false, wiperOn = true, lowBeamOn = false, isNight = true))
        assertNull(e.evaluate(isDriving = true, wiperOn = true, lowBeamOn = null, isNight = true))
    }

    @Test fun `야간에 전조등 꺼짐`() {
        val s = SuggestionEngine().evaluate(isDriving = true, wiperOn = false, lowBeamOn = false, isNight = true)
        assertEquals(SuggestionType.NIGHT_HEADLIGHT, s?.type)
    }
}

class GeoTest {
    @Test fun `서울 11월 4일 일몰은 17시 20분대`() {
        // 11월 4일 = 연중 308일째
        val (rise, set) = Geo.sunriseSunset(GeoPoint(37.5665, 126.9780), 308, 9.0)!!
        assertTrue("sunrise=$rise", rise in 6.9..7.3)
        assertTrue("sunset=$set", set in 17.2..17.6)
    }

    @Test fun `야간 판단 - 에뮬레이터처럼 위치와 시간대가 안 맞으면 시각 기준`() {
        // 위치 모름, 미국 서부 시간대, 오전 7시 51분 → 주간
        assertTrue(!Geo.isNight(null, 308, 7.85, -8.0))
        // 서울 좌표인데 시간대가 미국 → 시각 기준 → 주간
        assertTrue(!Geo.isNight(GeoPoint(37.5665, 126.9780), 308, 7.85, -8.0))
        // 시각 기준 저녁 7시 → 야간
        assertTrue(Geo.isNight(null, 308, 19.0, 0.0))
    }

    @Test fun `야간 판단 - 서울 시간대와 위치가 맞으면 일몰 기준`() {
        val seoul = GeoPoint(37.5665, 126.9780)
        assertTrue(!Geo.isNight(seoul, 308, 7.85, 9.0))   // 오전 7시 51분, 일출 후
        assertTrue(Geo.isNight(seoul, 308, 17.75, 9.0))   // 오후 5시 45분, 일몰 후
        assertTrue(Geo.isNight(seoul, 308, 6.5, 9.0))     // 오전 6시 30분, 일출 전
    }

    @Test fun `서울-대전 직선거리 약 140km`() {
        val d = Geo.distanceM(GeoPoint(37.5547, 126.9707), GeoPoint(36.3326, 127.4342))
        assertTrue("d=$d", d in 135_000.0..145_000.0)
    }

}
