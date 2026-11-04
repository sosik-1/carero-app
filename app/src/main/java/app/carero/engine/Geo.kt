package app.carero.engine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Geo {
    private const val R = 6_371_000.0

    /** 두 지점 사이 직선거리(m), haversine */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * 일출·일몰 시각(현지 시각, 시 단위 소수). 계산 불가(백야 등)면 null.
     * NOAA 일출 공식(천정각 90.833°)을 사용합니다.
     * @param tzOffsetHours 표준시 오프셋 (한국 9.0)
     */
    fun sunriseSunset(point: GeoPoint, dayOfYear: Int, tzOffsetHours: Double): Pair<Double, Double>? {
        val rise = solarEvent(point, dayOfYear, tzOffsetHours, rising = true) ?: return null
        val set = solarEvent(point, dayOfYear, tzOffsetHours, rising = false) ?: return null
        return rise to set
    }

    private fun solarEvent(p: GeoPoint, n: Int, tz: Double, rising: Boolean): Double? {
        val zenith = 90.833
        val lngHour = p.lon / 15.0
        val t = if (rising) n + ((6 - lngHour) / 24) else n + ((18 - lngHour) / 24)
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634
        l = norm360(l)
        var ra = deg(kotlin.math.atan(0.91764 * tan(rad(l))))
        ra = norm360(ra)
        val lQuadrant = floor(l / 90) * 90
        val raQuadrant = floor(ra / 90) * 90
        ra = (ra + (lQuadrant - raQuadrant)) / 15
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(zenith)) - sinDec * sin(rad(p.lat))) / (cosDec * cos(rad(p.lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = if (rising) (360 - deg(acos(cosH))) / 15 else deg(acos(cosH)) / 15
        val localMeanTime = h + ra - 0.06571 * t - 6.622
        val ut = ((localMeanTime - lngHour) % 24 + 24) % 24
        return ((ut + tz) % 24 + 24) % 24
    }

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI
    private fun norm360(x: Double) = ((x % 360) + 360) % 360
}
