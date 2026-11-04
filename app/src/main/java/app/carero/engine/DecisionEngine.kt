package app.carero.engine

/**
 * 상황 판단 엔진.
 * 같은 경고라도 주행 맥락(주행/정차, 남은 거리, 시간대, 주변 정비소)에 따라 다른 행동을 고릅니다.
 * 안전과 직결되므로 규칙 기반으로 결정하고, 결과를 항상 설명할 수 있게 합니다.
 */
class DecisionEngine(
    private val shops: List<RepairShop>,
    private val config: Config = Config(),
) {
    data class Config(
        /** 이보다 가까우면 '도착 후 점검' (m) */
        val nearArrivalM: Int = 5_000,
        /** 야간에는 더 일찍 정비소 경유를 권함 (m) */
        val nearArrivalNightM: Int = 3_000,
        /** 정비소 후보 최대 직선거리 (m) */
        val maxShopDistanceM: Int = 60_000,
        /** 경로 대비 허용 우회 거리 (m) */
        val maxDetourM: Int = 10_000,
    )

    fun decide(warnings: List<VehicleWarning>, ctx: DrivingContext): List<Decision> =
        warnings
            .sortedBy { it.severity.ordinal }
            .map { decideOne(it, ctx) }

    fun decideOne(w: VehicleWarning, ctx: DrivingContext): Decision {
        val action = chooseAction(w, ctx)
        val shop = if (action == Action.VIA_REPAIR_SHOP || action == Action.STOP_NOW) bestShop(ctx) else null
        val finalAction = if (action == Action.VIA_REPAIR_SHOP && shop == null) Action.CHECK_ON_ARRIVAL else action
        return Decision(
            warning = w,
            action = finalAction,
            headline = Messages.headline(w, finalAction, ctx, shop),
            detail = Messages.detail(w, finalAction, ctx.isDriving),
            shop = shop,
        )
    }

    internal fun chooseAction(w: VehicleWarning, ctx: DrivingContext): Action = when (w.severity) {
        Severity.CRITICAL -> Action.STOP_NOW
        Severity.INFO -> Action.NEXT_TIME
        Severity.CAUTION -> {
            val remain = ctx.remainDistanceM
            val near = if (ctx.isNight) config.nearArrivalNightM else config.nearArrivalM
            when {
                !ctx.isDriving -> Action.CHECK_ON_ARRIVAL
                remain != null && remain <= near -> Action.CHECK_ON_ARRIVAL
                else -> Action.VIA_REPAIR_SHOP
            }
        }
    }

    /**
     * 경로 위 정비소 찾기.
     * 목적지가 있으면 '현재→정비소→목적지' 우회 거리가 작고 목적지 방향에 있는 곳을,
     * 없으면 가장 가까운 곳을 고릅니다.
     */
    fun bestShop(ctx: DrivingContext): ShopSuggestion? {
        val cur = ctx.current ?: return null
        val dest = ctx.destination
        val direct = dest?.let { Geo.distanceM(cur, it) }
        return shops.asSequence()
            .map { s ->
                val toShop = Geo.distanceM(cur, s.point)
                val detour = if (dest != null && direct != null) toShop + Geo.distanceM(s.point, dest) - direct else null
                val ahead = dest == null || Geo.distanceM(s.point, dest) < direct!!
                Triple(s, toShop, detour) to ahead
            }
            .filter { (t, ahead) -> t.second <= config.maxShopDistanceM && ahead && (t.third == null || t.third!! <= config.maxDetourM) }
            .map { it.first }
            .minByOrNull { (_, toShop, detour) -> detour?.let { it * 2 + toShop } ?: toShop }
            ?.let { (s, toShop, detour) -> ShopSuggestion(s, toShop.toInt(), detour?.toInt()) }
    }

    companion object {
        /** 오일·브레이크액 수준 → 심각도. 정상·과다·알 수 없음은 경고로 보지 않습니다. */
        fun severityOf(level: FluidLevel): Severity? = when (level) {
            FluidLevel.CRITICALLY_LOW -> Severity.CRITICAL
            FluidLevel.LOW -> Severity.CAUTION
            else -> null
        }
    }
}

/**
 * 비·야간 전조등 제안. 같은 주행(trip) 동안 같은 제안은 한 번만 띄웁니다.
 */
class SuggestionEngine {
    private val shown = mutableSetOf<SuggestionType>()

    fun newTrip() = shown.clear()

    fun evaluate(isDriving: Boolean, wiperOn: Boolean?, lowBeamOn: Boolean?, isNight: Boolean): Suggestion? {
        if (!isDriving || lowBeamOn != false) return null
        val type = when {
            wiperOn == true -> SuggestionType.RAIN_HEADLIGHT
            isNight -> SuggestionType.NIGHT_HEADLIGHT
            else -> return null
        }
        if (!shown.add(type)) return null
        return Suggestion(type, Messages.suggestion(type))
    }
}
