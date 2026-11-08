package app.carero.engine

/**
 * 계기판 경고등(텔테일) 사전.
 * Pleos Vehicle SDK의 TelltaleType 이름(id)마다 생김새·뜻·할 일을 정리했습니다.
 * 차종마다 모양과 색이 조금씩 다를 수 있어, 단정하기 어려운 내용은 '점검'과 '취급설명서 확인'을 권합니다.
 */

/** 계기판에 켜지는 색 */
enum class LampColor { RED, YELLOW, GREEN, BLUE, WHITE }

/** 경고등이 켜졌을 때 케어로가 다루는 방식 */
enum class TelltaleLevel {
    /** 안전한 곳에 바로 정차 (빨간 위험) */
    STOP,
    /** 바로 할 수 있는 조치 (안전벨트 매기, 문 닫기, 주유 등) */
    FIX_NOW,
    /** 주행 맥락에 따라 도착 후 점검 / 경로 위 정비소 경유 */
    CHECK,
    /** 급하지 않음. 다음 주유·정차 때 */
    LATER,
    /** 경고가 아닌 안내 표시. 사전에서만 보여 줌 */
    SHOW,
}

data class TelltaleInfo(
    /** SDK TelltaleType 이름 */
    val id: String,
    val name: String,
    /** 사전 배지에 쓰는 짧은 표시 */
    val symbol: String,
    /** 계기판에서 보이는 모양 설명 */
    val shape: String,
    val color: LampColor,
    val level: TelltaleLevel,
    val meaning: String,
    val todo: String,
    /** FIX_NOW일 때 주행 중 한 줄 안내 */
    val quick: String? = null,
    /** 깜빡이면 정상 작동이라는 뜻(예: ESC가 미끄러짐을 잡는 중) */
    val blinkIsNormal: Boolean = false,
    /** 깜빡이면 더 위험(예: 엔진 점검등) */
    val blinkStop: Boolean = false,
)

/** 지금 켜져 있는 경고등 하나 */
data class ActiveTelltale(val id: String, val blinking: Boolean = false, val color: LampColor? = null)

object TelltaleGuide {
    private val R = LampColor.RED
    private val Y = LampColor.YELLOW
    private val G = LampColor.GREEN
    private val B = LampColor.BLUE
    private val W = LampColor.WHITE

    val all: List<TelltaleInfo> = listOf(
        // ---------- 빨간색: 바로 확인 ----------
        TelltaleInfo("ENGINE_TEMPERATURE", "엔진 과열(냉각수 온도)", "온도", "물결 위에 온도계가 서 있는 모양", R, TelltaleLevel.STOP,
            "냉각수 온도가 너무 높아 엔진이 과열됐다는 뜻이에요. 계속 달리면 엔진이 크게 손상될 수 있어요.",
            "안전한 곳에 정차하고 시동을 끈 뒤 엔진이 식을 때까지 기다리세요. 뜨거울 때 냉각수 뚜껑(라디에이터 캡)을 열면 데일 수 있어요. 긴급출동이나 정비소에 연락하세요."),
        TelltaleInfo("ENGINE_OIL_PRESSURE", "엔진오일 압력", "오일", "기름 주전자에서 방울이 떨어지는 모양", R, TelltaleLevel.STOP,
            "엔진 안에 오일이 제대로 돌지 않는다는 뜻이에요. 이대로 달리면 엔진이 멈추거나 망가질 수 있어요.",
            "안전한 곳에 정차하고 시동을 끄세요. 다시 달리지 말고 긴급출동이나 정비소에 연락하세요."),
        TelltaleInfo("PARKING_BRAKE", "브레이크 경고", "(P)", "동그라미 안에 P 또는 ! 가 있는 모양", R, TelltaleLevel.FIX_NOW,
            "주차 브레이크가 채워져 있거나, 브레이크액이 부족한 등 브레이크 계통에 이상이 있다는 뜻이에요.",
            "주차 브레이크를 푸세요. 풀었는데도 계속 켜져 있으면 브레이크 이상일 수 있으니 안전한 곳에 정차하고 점검을 받으세요.",
            quick = "브레이크 경고등이 켜졌어요. 주차 브레이크를 확인하세요"),
        TelltaleInfo("LOW_VOLT_BATTERY", "배터리 충전 경고", "배터리", "네모난 배터리에 +, − 가 있는 모양", R, TelltaleLevel.CHECK,
            "배터리가 충전되지 않고 있다는 뜻이에요. 그대로 두면 전기가 떨어져 차가 멈출 수 있어요.",
            "에어컨·열선 같은 전기 장치를 끄고 가까운 정비소에서 점검을 받으세요. 시동을 껐다 켜면 다시 안 걸릴 수 있어요."),
        TelltaleInfo("AIRBAG", "에어백 경고", "에어백", "앉은 사람 앞에 큰 공이 있는 모양", R, TelltaleLevel.CHECK,
            "에어백 장치에 이상이 있다는 뜻이에요. 사고가 나도 에어백이 터지지 않을 수 있어요.",
            "운전은 할 수 있지만 되도록 빨리 정비소에서 점검을 받으세요."),
        TelltaleInfo("SEAT_BELT", "안전벨트 미착용", "벨트", "앉은 사람에게 벨트가 대각선으로 걸린 모양", R, TelltaleLevel.FIX_NOW,
            "운전석이나 동승석에서 안전벨트를 매지 않았다는 뜻이에요.",
            "모든 사람이 안전벨트를 맸는지 확인하세요.",
            quick = "안전벨트를 매지 않은 자리가 있어요"),
        TelltaleInfo("DOOR_OPEN", "문 열림", "문", "위에서 본 차에 문이 열린 모양", R, TelltaleLevel.FIX_NOW,
            "문이 완전히 닫히지 않았다는 뜻이에요.",
            "안전한 곳에 세운 뒤 문을 다시 닫으세요.",
            quick = "문이 덜 닫혔어요. 안전한 곳에 세우고 닫으세요"),
        TelltaleInfo("TRUNK_OPEN", "트렁크 열림", "트렁크", "옆에서 본 차에 뒤쪽이 열린 모양", R, TelltaleLevel.FIX_NOW,
            "트렁크(뒷문)가 완전히 닫히지 않았다는 뜻이에요.",
            "안전한 곳에 세운 뒤 트렁크를 다시 닫으세요.",
            quick = "트렁크가 덜 닫혔어요. 안전한 곳에 세우고 닫으세요"),
        TelltaleInfo("EV_SYSTEM", "전기차 시스템 경고", "EV!", "차 모양 안에 ! 가 있는 모양", R, TelltaleLevel.STOP,
            "전기차 구동 장치에 이상이 있다는 뜻이에요.",
            "안전한 곳에 정차하고 긴급출동이나 서비스센터에 연락하세요."),
        TelltaleInfo("H2_LEAK", "수소 누출 경고", "H₂", "H2 글자와 경고 표시", R, TelltaleLevel.STOP,
            "수소 연료가 새고 있을 수 있다는 뜻이에요.",
            "안전한 곳에 정차하고 시동을 끈 뒤, 차에서 떨어져 긴급출동이나 서비스센터에 연락하세요. 불을 가까이 하지 마세요."),

        // ---------- 노란색: 점검 필요 ----------
        TelltaleInfo("MASTER_WARNING", "종합 주의 경고", "!", "삼각형 안에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "여러 경고 중 하나가 생겼다는 뜻이에요. 계기판 가운데에 자세한 문구가 함께 나와요.",
            "계기판 메시지를 확인하고, 이해가 어렵다면 정비소에 물어보세요."),
        TelltaleInfo("ENGINE", "엔진 점검", "엔진", "엔진 모양(옆에서 본 네모난 덩어리)", Y, TelltaleLevel.CHECK,
            "엔진이나 배기가스 관련 장치에 이상이 있다는 뜻이에요. 깜빡이면 엔진 손상 위험이 더 커요.",
            "되도록 빨리 점검을 받으세요. 깜빡이면 속도를 줄이고 안전한 곳에 정차하세요.",
            blinkStop = true),
        TelltaleInfo("ABS", "ABS 경고", "ABS", "동그라미 안에 ABS 글자", Y, TelltaleLevel.CHECK,
            "급제동할 때 바퀴가 잠기지 않게 해 주는 장치(ABS)에 이상이 있다는 뜻이에요. 일반 브레이크는 작동해요.",
            "급제동을 피하고 앞차와 거리를 넉넉히 두세요. 되도록 빨리 점검을 받으세요."),
        TelltaleInfo("ESC", "차체 자세 제어(ESC)", "ESC", "차 뒤에 구불구불한 바퀴 자국 두 줄", Y, TelltaleLevel.CHECK,
            "깜빡이면 미끄러운 길에서 장치가 차를 잡아 주는 중이라 정상이에요. 계속 켜져 있으면 장치에 이상이 있다는 뜻이에요.",
            "깜빡일 땐 속도를 줄이세요. 계속 켜져 있으면 점검을 받으세요.",
            blinkIsNormal = true),
        TelltaleInfo("MDPS", "핸들 보조(전동 조향) 경고", "핸들", "핸들 옆에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "핸들을 가볍게 돌려 주는 장치에 이상이 있다는 뜻이에요. 핸들이 무거워질 수 있어요.",
            "핸들이 무거우면 속도를 줄여 조심히 운전하고, 되도록 빨리 점검을 받으세요."),
        TelltaleInfo("TPMS", "타이어 공기압", "타이어", "말굽 모양 타이어 단면 안에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "타이어 공기압이 낮다는 뜻이에요. 깜빡이다가 켜진 채로 있으면 공기압 감지 장치 이상이에요.",
            "급제동·급회전을 피하고, 주유소나 정비소에서 공기압을 확인하세요. 타이어가 눈에 띄게 주저앉았다면 정차하세요."),
        TelltaleInfo("ELECTRONIC_PARKING_BRAKE", "전자식 주차 브레이크 경고", "EPB", "동그라미 안에 P 와 공구 모양", Y, TelltaleLevel.CHECK,
            "전자식 주차 브레이크(EPB)에 이상이 있다는 뜻이에요.",
            "주차할 땐 평평한 곳에 세우고 기어를 P에 두세요. 되도록 빨리 점검을 받으세요."),
        TelltaleInfo("POWER_DOWN", "출력 제한", "거북이", "거북이 모양", Y, TelltaleLevel.CHECK,
            "배터리나 구동 장치를 보호하려고 차의 힘을 줄였다는 뜻이에요. 가속이 느려져요.",
            "추월이나 급가속을 피하세요. 배터리가 거의 없다면 충전하고, 계속 켜져 있으면 점검을 받으세요."),
        TelltaleInfo("HEV_SYSTEM", "하이브리드 시스템 경고", "HEV!", "차 모양 안에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "하이브리드 구동 장치에 이상이 있다는 뜻이에요.", "되도록 빨리 서비스센터에서 점검을 받으세요."),
        TelltaleInfo("FCEV_SYSTEM", "수소전기차 시스템 경고", "FCEV!", "차 모양 안에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "수소전기차 구동 장치에 이상이 있다는 뜻이에요.", "되도록 빨리 서비스센터에서 점검을 받으세요."),
        TelltaleInfo("EREV_SYSTEM", "주행거리 연장형 시스템 경고", "EREV!", "차 모양 안에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "주행거리 연장형 전기차의 구동 장치에 이상이 있다는 뜻이에요.", "되도록 빨리 서비스센터에서 점검을 받으세요."),
        TelltaleInfo("H2_SENSOR", "수소 감지 장치 경고", "H₂센서", "H2 글자와 공구 모양", Y, TelltaleLevel.CHECK,
            "수소 누출을 감지하는 장치에 이상이 있다는 뜻이에요.", "되도록 빨리 서비스센터에서 점검을 받으세요."),
        TelltaleInfo("RBS", "회생 제동 경고", "RBS", "RBS 글자", Y, TelltaleLevel.CHECK,
            "브레이크를 밟을 때 전기를 다시 모으는 장치에 이상이 있다는 뜻이에요. 브레이크 느낌이 달라질 수 있어요.",
            "앞차와 거리를 넉넉히 두고, 되도록 빨리 점검을 받으세요."),
        TelltaleInfo("PARTICLE_FILTER", "매연 저감 장치(DPF)", "DPF", "네모 상자 안에 점들이 찬 모양", Y, TelltaleLevel.CHECK,
            "디젤차의 매연 필터에 그을음이 많이 쌓였다는 뜻이에요.",
            "막히지 않은 도로를 일정 시간 꾸준히 달리면 꺼지기도 해요. 계속 켜져 있으면 점검을 받으세요."),
        TelltaleInfo("WATER_SEPARATOR", "연료 필터 수분", "물", "물방울이 떨어지는 필터 모양", Y, TelltaleLevel.CHECK,
            "디젤 연료 필터에 물이 찼다는 뜻이에요. 그대로 두면 엔진 부품이 손상될 수 있어요.",
            "되도록 빨리 정비소에서 물을 빼고 점검을 받으세요."),
        TelltaleInfo("AFS", "적응형 전조등 경고", "AFS", "전조등 모양 옆에 화살표", Y, TelltaleLevel.CHECK,
            "핸들 방향에 맞춰 비추는 전조등 장치에 이상이 있다는 뜻이에요.", "밤길에 조심하고 점검을 받으세요."),
        TelltaleInfo("LED_HEADLAMP", "LED 전조등 경고", "LED", "전조등 모양에 ! 가 있는 모양", Y, TelltaleLevel.CHECK,
            "LED 전조등에 이상이 있다는 뜻이에요. 불빛이 약하거나 꺼질 수 있어요.", "밤길에 조심하고 점검을 받으세요."),
        TelltaleInfo("SCR", "요소수 부족", "요소수", "물방울이 담긴 통 모양", Y, TelltaleLevel.LATER,
            "디젤차의 요소수가 부족하다는 뜻이에요. 다 떨어지면 시동이 걸리지 않을 수 있어요.",
            "주유소나 마트에서 요소수를 사서 보충하세요."),
        TelltaleInfo("LOW_FUEL", "연료 부족", "주유", "주유기 모양", Y, TelltaleLevel.FIX_NOW,
            "연료가 얼마 남지 않았다는 뜻이에요.",
            "가까운 주유소에 들러 연료를 채우세요.",
            quick = "연료가 얼마 남지 않았어요. 가까운 주유소에 들르세요"),
        TelltaleInfo("LOW_FUEL_FCEV", "수소 부족", "수소", "주유기 모양에 H2 글자", Y, TelltaleLevel.FIX_NOW,
            "수소 연료가 얼마 남지 않았다는 뜻이에요.",
            "가까운 수소 충전소를 찾아 충전하세요. 충전소가 적으니 미리 확인하세요.",
            quick = "수소가 얼마 남지 않았어요. 가까운 충전소를 확인하세요"),
        TelltaleInfo("LOW_EV_BATTERY", "구동 배터리 부족", "충전", "플러그가 꽂힌 배터리 모양", Y, TelltaleLevel.FIX_NOW,
            "전기차 배터리가 얼마 남지 않았다는 뜻이에요.",
            "가까운 충전소에서 충전하세요. 에어컨·히터를 줄이면 조금 더 갈 수 있어요.",
            quick = "배터리가 얼마 남지 않았어요. 가까운 충전소에 들르세요"),
        TelltaleInfo("ICY_ROAD", "노면 결빙 주의", "결빙", "눈 결정 모양", Y, TelltaleLevel.FIX_NOW,
            "바깥 기온이 낮아 길이 얼었을 수 있다는 뜻이에요.",
            "속도를 줄이고 다리 위·그늘진 길에서 특히 조심하세요.",
            quick = "길이 얼었을 수 있어요. 속도를 줄이세요"),
        TelltaleInfo("DRIVER_ATTENTION", "운전자 주의 경고", "휴식", "커피잔 모양", Y, TelltaleLevel.FIX_NOW,
            "운전 패턴을 보고 피곤해 보인다고 판단했다는 뜻이에요.",
            "가까운 휴게소나 졸음쉼터에서 잠시 쉬어 가세요.",
            quick = "피곤해 보여요. 가까운 쉼터에서 쉬어 가세요"),

        // ---------- 안내 표시(경고 아님) ----------
        TelltaleInfo("ESC_OFF", "ESC 꺼짐", "ESC OFF", "ESC 아이콘 아래 OFF 글자", Y, TelltaleLevel.SHOW,
            "차체 자세 제어(ESC)를 버튼으로 끈 상태예요.", "특별한 경우가 아니면 다시 켜 두세요. 미끄러운 길에서 위험할 수 있어요."),
        TelltaleInfo("IMMOBILIZER", "스마트키 인식", "키", "열쇠 모양", Y, TelltaleLevel.SHOW,
            "차가 스마트키를 찾고 있거나 인식하지 못했다는 뜻이에요.", "키를 차 안에 두었는지 확인하세요. 배터리가 약하면 키를 시동 버튼에 대고 눌러 보세요."),
        TelltaleInfo("PREHEAT", "예열(디젤)", "예열", "용수철 모양", Y, TelltaleLevel.SHOW,
            "디젤 엔진을 시동 전에 데우는 중이에요.", "불이 꺼진 뒤 시동을 거세요."),
        TelltaleInfo("PASSENGER_AIRBAG_OFF", "동승석 에어백 꺼짐", "에어백 OFF", "에어백 모양과 OFF 글자", Y, TelltaleLevel.SHOW,
            "동승석 에어백이 꺼진 상태예요.", "어른이 앉았는데 꺼져 있다면 점검을 받으세요."),
        TelltaleInfo("CHILD_PRESENCE_DETECTION_OFF", "뒷좌석 동승자 알림 꺼짐", "뒷좌석", "뒷좌석 아이 모양과 OFF", Y, TelltaleLevel.SHOW,
            "내릴 때 뒷좌석에 사람이 남아 있으면 알려 주는 기능이 꺼진 상태예요.", "아이와 함께 탄다면 켜 두세요."),
        TelltaleInfo("EMERGENCY_CALL", "긴급 통화 장치", "SOS", "SOS 글자 또는 전화기 모양", Y, TelltaleLevel.SHOW,
            "사고가 나면 자동으로 연락하는 장치의 상태 표시예요. 노란색으로 계속 켜져 있으면 장치 이상일 수 있어요.", "계속 켜져 있으면 서비스센터에 문의하세요."),
        TelltaleInfo("AUTO_HOLD", "오토홀드", "AUTO HOLD", "AUTO HOLD 글자", G, TelltaleLevel.SHOW,
            "정차하면 브레이크를 밟지 않아도 차를 세워 두는 기능이 작동 중이에요.", "가속 페달을 밟으면 풀려요."),
        TelltaleInfo("ISG", "공회전 제한(ISG)", "ISG", "A 글자를 화살표가 둘러싼 모양", G, TelltaleLevel.SHOW,
            "신호 대기 중 엔진을 잠시 꺼서 연료를 아끼는 기능이에요.", "출발하면 엔진이 다시 켜져요."),
        TelltaleInfo("XEV_READY", "출발 준비(READY)", "READY", "READY 글자", G, TelltaleLevel.SHOW,
            "전기차·하이브리드가 출발할 준비가 됐다는 뜻이에요. 소리가 안 나도 시동이 켜진 상태예요.", "내릴 땐 꼭 전원을 끄세요."),
        TelltaleInfo("EV_CHARGING_CONNECTOR", "충전 커넥터 연결", "커넥터", "플러그 모양", G, TelltaleLevel.SHOW,
            "충전 커넥터가 차에 꽂혀 있다는 뜻이에요.", "출발 전에 커넥터를 뽑으세요."),
        TelltaleInfo("FCA", "전방 충돌방지 보조", "FCA", "앞차와 부딪히는 두 차 모양", W, TelltaleLevel.SHOW,
            "앞차와 부딪힐 위험이 있으면 경고하고 브레이크를 돕는 기능이에요. 노란색이면 기능에 이상이 있거나 꺼진 상태예요.", "노란색으로 계속 켜져 있으면 앞 범퍼 센서를 닦아 보고, 그래도 켜져 있으면 점검을 받으세요."),
        TelltaleInfo("LKA", "차로 이탈방지 보조", "LKA", "차로 두 줄 사이의 차 모양", G, TelltaleLevel.SHOW,
            "차로를 벗어나려 하면 핸들을 살짝 돌려 주는 기능이 켜져 있어요.", "노란색이면 기능에 이상이 있는 거예요. 앞유리 카메라 쪽을 닦아 보세요."),
        TelltaleInfo("LFA", "차로 유지 보조", "LFA", "핸들 양옆에 차로 선", G, TelltaleLevel.SHOW,
            "차로 가운데를 따라가도록 핸들을 돕는 기능이 작동 중이에요.", "핸들은 계속 잡고 있어야 해요."),
        TelltaleInfo("HEADLAMP_LOW", "전조등(하향등)", "하향등", "불빛이 아래로 비스듬한 전조등 모양", G, TelltaleLevel.SHOW,
            "전조등이 켜져 있어요.", "밤이나 비 올 땐 켜 두세요."),
        TelltaleInfo("HEADLAMP_HIGH", "상향등", "상향등", "불빛이 곧게 뻗은 전조등 모양", B, TelltaleLevel.SHOW,
            "상향등이 켜져 있어요. 마주 오는 차의 눈이 부실 수 있어요.", "앞에 차가 있으면 하향등으로 바꾸세요."),
        TelltaleInfo("AUTO_HIGH_BEAM", "자동 상향등", "AUTO", "상향등 모양에 A 글자", G, TelltaleLevel.SHOW,
            "앞에 차가 있는지 보고 상향등을 자동으로 켜고 끄는 기능이 켜져 있어요.", "따로 할 일은 없어요."),
        TelltaleInfo("FRONT_FOG_LAMP", "앞 안개등", "안개등", "전조등 모양을 물결선이 가로지르는 모양", G, TelltaleLevel.SHOW,
            "앞 안개등이 켜져 있어요.", "안개가 걷히면 끄세요."),
        TelltaleInfo("REAR_FOG_LAMP", "뒤 안개등", "뒤 안개등", "불빛이 오른쪽으로 향하고 물결선이 있는 모양", Y, TelltaleLevel.SHOW,
            "뒤 안개등이 켜져 있어요. 뒤차의 눈이 부실 수 있어요.", "안개가 걷히면 끄세요."),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String?): TelltaleInfo? = id?.let { byId[it] }

    /**
     * 켜진 경고등 → 판단에 넣을 경고. 안내 표시(SHOW)나 '깜빡이면 정상'인 경우는 경고로 보지 않습니다.
     */
    fun warningOf(t: ActiveTelltale): VehicleWarning? {
        val info = of(t.id) ?: return null
        if (t.blinking && info.blinkIsNormal) return null
        val severity = when {
            t.blinking && info.blinkStop -> Severity.CRITICAL
            else -> when (info.level) {
                TelltaleLevel.STOP -> Severity.CRITICAL
                TelltaleLevel.FIX_NOW, TelltaleLevel.CHECK -> Severity.CAUTION
                TelltaleLevel.LATER -> Severity.INFO
                TelltaleLevel.SHOW -> return null
            }
        }
        return VehicleWarning(WarningType.TELLTALE, severity, telltale = info.id, blinking = t.blinking)
    }
}
