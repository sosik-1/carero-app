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

    @Test fun `서울-대전 직선거리 약 140km`() {
        val d = Geo.distanceM(GeoPoint(37.5547, 126.9707), GeoPoint(36.3326, 127.4342))
        assertTrue("d=$d", d in 135_000.0..145_000.0)
    }
}
