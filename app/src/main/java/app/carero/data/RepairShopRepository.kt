package app.carero.data

import android.content.Context
import app.carero.engine.GeoPoint
import app.carero.engine.RepairShop
import org.json.JSONObject

/**
 * 정비소 목록. 공공데이터(자동차정비업체 정보)를 앱에 내장합니다.
 * 데이터 출처와 가공 방법은 assets/repair_shops.json의 source_note에 기록되어 있습니다.
 */
object RepairShopRepository {
    fun load(context: Context): List<RepairShop> = runCatching {
        val json = context.assets.open("repair_shops.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(json).getJSONArray("shops")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RepairShop(
                name = o.getString("name"),
                address = o.getString("address"),
                phone = o.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                point = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
            )
        }
    }.getOrDefault(emptyList())
}
