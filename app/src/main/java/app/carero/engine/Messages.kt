package app.carero.engine

/**
 * 안내 문구.
 * 현대자동차 취급설명서의 경고별 권장 조치를 바탕으로 생성형 AI로 초안을 만들고 사람이 검수한 문구를
 * 앱에 내장합니다(네트워크 없이 동작, 응답 지연 없음). 구체적인 수리 방법이나 주행 가능 거리처럼
 * 차종마다 다른 내용은 단정하지 않고 '점검'을 권합니다.
 */
object Messages {

    fun what(w: VehicleWarning): String = when (w.type) {
        WarningType.BRAKE_FLUID -> "브레이크액이 ${level(w)} 상태예요"
        WarningType.ENGINE_OIL -> "엔진오일이 ${level(w)} 상태예요"
        WarningType.TRANSMISSION_OIL -> "변속기오일이 ${level(w)} 상태예요"
        WarningType.TIRE_LOW_PRESSURE -> "${w.position?.let { "$it " } ?: ""}타이어 공기압이 낮아요"
        WarningType.WASHER_FLUID -> "워셔액이 부족해요"
    }

    private fun level(w: VehicleWarning) = if (w.severity == Severity.CRITICAL) "위험 수준으로 부족한" else "부족한"

    fun why(type: WarningType): String = when (type) {
        WarningType.BRAKE_FLUID -> "브레이크액이 부족하면 제동력이 떨어질 수 있습니다. 누유일 가능성도 있어 정비소 점검이 필요합니다."
        WarningType.ENGINE_OIL -> "엔진오일이 부족하면 엔진 마모와 과열로 이어질 수 있습니다. 보충과 누유 점검이 필요합니다."
        WarningType.TRANSMISSION_OIL -> "변속기오일이 부족하면 변속 충격이나 변속기 손상이 생길 수 있습니다. 정비소 점검이 필요합니다."
        WarningType.TIRE_LOW_PRESSURE -> "공기압이 낮으면 제동거리가 늘고 타이어가 손상될 수 있습니다. 공기압을 확인하고 필요하면 보충하세요."
        WarningType.WASHER_FLUID -> "워셔액이 없으면 비나 먼지로 시야가 가려질 때 앞유리를 닦을 수 없습니다. 주유소나 마트에서 구입해 보충할 수 있습니다."
    }

    fun headline(w: VehicleWarning, action: Action, ctx: DrivingContext, shop: ShopSuggestion?): String = when (action) {
        Action.STOP_NOW -> "${what(w)}. 안전한 곳에 정차하세요"
        Action.VIA_REPAIR_SHOP -> {
            val remain = ctx.remainDistanceM?.let { "목적지까지 ${km(it)} 남았어요. " } ?: ""
            val where = shop?.let { "${km(it.distanceM)} 거리 ${it.shop.name}" } ?: "가까운 정비소"
            "${what(w)}. ${remain}${where}에 들를까요?"
        }
        Action.CHECK_ON_ARRIVAL -> {
            val remain = ctx.remainDistanceM?.let { "목적지까지 ${km(it)}예요. " } ?: ""
            if (ctx.isDriving) "${what(w)}. ${remain}도착 후 확인하세요"
            else "${what(w)}. 지금 확인하고, 이상하면 점검을 받으세요"
        }
        Action.NEXT_TIME -> "${what(w)}. 다음에 주유하거나 정차할 때 처리하세요"
    }

    fun detail(w: VehicleWarning, action: Action, isDriving: Boolean = true): String {
        val todo = when (action) {
            Action.STOP_NOW -> "지금 할 일: 비상등을 켜고 안전한 곳에 정차한 뒤, 가입한 보험사나 제조사의 긴급출동 서비스에 연락하세요. 무리하게 주행하지 마세요."
            Action.VIA_REPAIR_SHOP -> "지금 할 일: 경로 위 정비소를 경유지로 추가해 점검을 받으세요. 정비소까지는 급가속·급제동을 피하고 천천히 주행하세요."
            Action.CHECK_ON_ARRIVAL -> if (isDriving) "지금 할 일: 목적지에 도착하면 상태를 확인하고, 가까운 시일 안에 정비소 점검을 예약하세요."
                else "지금 할 일: 정차한 지금 상태를 확인하고, 이상이 있으면 가까운 정비소에서 점검을 받으세요."
            Action.NEXT_TIME -> "지금 할 일: 급하지 않습니다. 다음 주유나 정차 때 보충하세요."
        }
        return "${why(w.type)}\n\n$todo"
    }

    fun suggestion(type: SuggestionType): String = when (type) {
        SuggestionType.RAIN_HEADLIGHT -> "비 올 땐 전조등을 켜주세요"
        SuggestionType.NIGHT_HEADLIGHT -> "해가 졌어요. 전조등을 켜주세요"
    }

    fun km(m: Int): String = if (m < 1000) "${m}m" else String.format("%.0fkm", m / 1000.0)
}
