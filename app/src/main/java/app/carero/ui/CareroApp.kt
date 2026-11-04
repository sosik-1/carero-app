package app.carero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.carero.CheckItem
import app.carero.CheckStatus
import app.carero.MainViewModel
import app.carero.UiState
import app.carero.data.DemoState
import app.carero.engine.Action
import app.carero.engine.Decision
import app.carero.engine.Messages
import app.carero.engine.Severity

private object C {
    val bg = Color(0xFF101418)
    val surface = Color(0xFF1A2027)
    val surface2 = Color(0xFF232B34)
    val text = Color(0xFFF0EFEC)
    val text2 = Color(0xFFB9BDC4)
    val critical = Color(0xFFD03B3B)
    val warning = Color(0xFFFAB219)
    val good = Color(0xFF0CA30C)
    val info = Color(0xFF3987E5)
    val demo = Color(0xFF9085E9)
}

interface CareroActions {
    fun dismiss(type: app.carero.engine.WarningType)
    fun resetDismissed()
    fun addWaypoint(decision: Decision)
    fun setDemo(update: (DemoState) -> DemoState)
}

@Composable
fun CareroApp(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    CareroScreen(s, vm)
}

@Composable
fun CareroScreen(s: UiState, vm: CareroActions, initialShowDemo: Boolean = false, uiScale: Float = 1.35f) {
    var showDemo by remember { mutableStateOf(initialShowDemo) }
    // 차량 화면에서 멀리서도 읽히도록 전체를 키웁니다(주의 분산 최소화).
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * uiScale, base.fontScale)) {
    MaterialTheme(colorScheme = darkColorScheme(background = C.bg, surface = C.surface, primary = C.info)) {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
                TopBar(s, onDemoToggle = { showDemo = !showDemo })
                Spacer(Modifier.height(20.dp))
                val critical = s.decisions.firstOrNull { it.action == Action.STOP_NOW }
                when {
                    critical != null -> CriticalScreen(critical, s, vm)
                    s.driving -> DrivingScreen(s, vm)
                    else -> ParkedScreen(s, vm)
                }
            }
            // 안전 운전 제안 (상단 한 줄)
            AnimatedVisibility(s.suggestion != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)) {
                s.suggestion?.let { Pill(it.message, C.info) }
            }
            s.toast?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) { Pill(it, C.good) } }
            if (showDemo) DemoPanel(s.demo, vm, Modifier.align(Alignment.CenterEnd))
        }
    }
    }
}

@Composable
private fun TopBar(s: UiState, onDemoToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("케어로", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(20.dp))
        Chip(if (s.driving) "주행 중" else "정차 중", if (s.driving) C.info else C.text2)
        s.ctx.remainDistanceM?.let { Chip("목적지까지 ${Messages.km(it)}", C.text2) }
        Chip(if (s.ctx.isNight) "야간" else "주간", C.text2)
        if (s.demo.enabled) Chip("데모 모드 · 가상 값", C.demo)
        Spacer(Modifier.weight(1f))
        Text(
            if (s.vehicleSdk) "차량 연결됨" else "차량 미연결",
            color = if (s.vehicleSdk) C.good else C.text2, fontSize = 16.sp,
        )
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = onDemoToggle) { Text("데모", color = C.text2) }
        Spacer(Modifier.width(10.dp))
        Text("v${app.carero.BuildConfig.VERSION_NAME}", color = C.text2.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 18.sp,
        modifier = Modifier.padding(end = 10.dp).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text, color = C.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(40.dp)).background(C.surface2).border(2.dp, color, RoundedCornerShape(40.dp)).padding(horizontal = 36.dp, vertical = 18.dp),
    )
}

private fun severityColor(s: Severity) = when (s) {
    Severity.CRITICAL -> C.critical
    Severity.CAUTION -> C.warning
    Severity.INFO -> C.info
}

private fun severityLabel(s: Severity) = when (s) {
    Severity.CRITICAL -> "위험 · 지금 정차"
    Severity.CAUTION -> "주의 · 오늘 중"
    Severity.INFO -> "안내 · 나중에"
}

private fun actionLabel(a: Action) = when (a) {
    Action.STOP_NOW -> "즉시 정차"
    Action.VIA_REPAIR_SHOP -> "경로 위 정비소 경유"
    Action.CHECK_ON_ARRIVAL -> "도착 후 점검"
    Action.NEXT_TIME -> "다음 기회에"
}

// ---------------- 위험: 큰 경고 ----------------
@Composable
private fun CriticalScreen(d: Decision, s: UiState, vm: CareroActions) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(C.critical.copy(alpha = 0.16f)).border(3.dp, C.critical, RoundedCornerShape(28.dp)).padding(48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠  ${severityLabel(d.warning.severity)}", color = C.critical, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(d.headline, color = C.text, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
        if (!s.driving) {
            Spacer(Modifier.height(20.dp))
            Text(d.detail, color = C.text2, fontSize = 22.sp, lineHeight = 32.sp)
        }
        Spacer(Modifier.height(28.dp))
        d.shop?.let { Text("가장 가까운 정비소: ${it.shop.name} (${Messages.km(it.distanceM)})${it.shop.phone?.let { p -> " · $p" } ?: ""}", color = C.text, fontSize = 22.sp) }
        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = { vm.dismiss(d.warning.type) }, colors = ButtonDefaults.buttonColors(containerColor = C.critical, contentColor = Color.White)) {
                Text("확인했어요", fontSize = 22.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// ---------------- 주행 중: 한 줄 + 버튼 하나 ----------------
@Composable
private fun DrivingScreen(s: UiState, vm: CareroActions) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusDots(s.checks)
                Spacer(Modifier.height(16.dp))
                Text(if (s.decisions.isEmpty()) "차량 상태를 지켜보고 있어요" else "확인할 일이 ${s.decisions.size}개 있어요", color = C.text2, fontSize = 24.sp)
            }
        }
        s.decisions.firstOrNull()?.let { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.surface2).border(2.dp, severityColor(d.warning.severity), RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(severityColor(d.warning.severity)))
                Spacer(Modifier.width(18.dp))
                Text(d.headline, color = C.text, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                if (d.action == Action.VIA_REPAIR_SHOP && d.shop != null) {
                    Button(onClick = { vm.addWaypoint(d) }, colors = ButtonDefaults.buttonColors(containerColor = C.info, contentColor = Color.White)) { Text("경유지 추가", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp)) }
                } else {
                    OutlinedButton(onClick = { vm.dismiss(d.warning.type) }) { Text("확인", fontSize = 24.sp, color = C.text, modifier = Modifier.padding(10.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatusDots(checks: List<CheckItem>) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        checks.forEach { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(statusColor(c.status)))
                Spacer(Modifier.height(8.dp))
                Text(c.label, color = C.text2, fontSize = 16.sp)
            }
        }
    }
}

private fun statusColor(st: CheckStatus) = when (st) {
    CheckStatus.OK -> C.good
    CheckStatus.WARN -> C.warning
    CheckStatus.CRITICAL -> C.critical
    CheckStatus.UNKNOWN -> C.text2.copy(alpha = 0.4f)
    CheckStatus.UNSUPPORTED -> C.text2.copy(alpha = 0.2f)
}

private fun statusText(st: CheckStatus) = when (st) {
    CheckStatus.OK -> "정상"
    CheckStatus.WARN -> "주의"
    CheckStatus.CRITICAL -> "위험"
    CheckStatus.UNKNOWN -> "확인 중"
    CheckStatus.UNSUPPORTED -> "미지원"
}

// ---------------- 정차 중: 상세 안내 + 컨디션 카드 ----------------
@Composable
private fun ParkedScreen(s: UiState, vm: CareroActions) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.6f)) {
            Text("지금 확인할 일", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (s.decisions.isEmpty()) {
                val anyKnown = s.checks.any { it.status == CheckStatus.OK || it.status == CheckStatus.WARN || it.status == CheckStatus.CRITICAL }
                Card { Text(if (anyKnown) "확인할 경고가 없어요. 차량 상태가 양호합니다." else "확인할 경고가 없어요. 이 차량(에뮬레이터)에서 받을 수 있는 점검 정보가 아직 없어요.", color = C.text2, fontSize = 22.sp) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.decisions) { d -> DecisionCard(d, vm) }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text("이번 주 내 차 컨디션", color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card {
                s.checks.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(statusColor(c.status)))
                        Spacer(Modifier.width(14.dp))
                        Text(c.label, color = C.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        Text(statusText(c.status), color = C.text2, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(24.dp)) { content() }
}

@Composable
private fun DecisionCard(d: Decision, vm: CareroActions) {
    val color = severityColor(d.warning.severity)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).border(2.dp, color.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(severityLabel(d.warning.severity), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("권장 행동: ${actionLabel(d.action)}", color = C.text2, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(d.headline, color = C.text, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(d.detail, color = C.text2, fontSize = 19.sp, lineHeight = 28.sp)
        d.shop?.let {
            Spacer(Modifier.height(10.dp))
            Text("추천 정비소: ${it.shop.name} · ${Messages.km(it.distanceM)} · ${it.shop.address}", color = C.text2, fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.action == Action.VIA_REPAIR_SHOP && d.shop != null) Button(onClick = { vm.addWaypoint(d) }, colors = ButtonDefaults.buttonColors(containerColor = C.info, contentColor = Color.White)) { Text("경유지 추가", fontSize = 20.sp) }
            OutlinedButton(onClick = { vm.dismiss(d.warning.type) }) { Text("확인", fontSize = 20.sp, color = C.text) }
        }
    }
}

// ---------------- 데모 패널 (시연 영상 촬영용) ----------------
@Composable
private fun DemoPanel(d: DemoState, vm: CareroActions, modifier: Modifier) {
    Column(
        modifier.padding(16.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(20.dp)).background(C.surface2).border(2.dp, C.demo, RoundedCornerShape(20.dp)).padding(20.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("데모 모드 (가상 값)", color = C.demo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("실제 차량·내비 값 대신 아래 값을 사용합니다", color = C.text2, fontSize = 14.sp)
        Toggle("데모 모드 켜기", d.enabled) { v -> vm.setDemo { it.copy(enabled = v) }; vm.resetDismissed() }
        if (d.enabled) {
            Toggle("주행 중", d.driving == true) { v -> vm.setDemo { it.copy(driving = v) } }
            Toggle("야간", d.night == true) { v -> vm.setDemo { it.copy(night = v) } }
            Text("남은 거리", color = C.text, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null, 3, 120).forEach { km ->
                    val sel = d.remainKm == km
                    Text(km?.let { "${it}km" } ?: "경로 없음", color = if (sel) C.bg else C.text, fontSize = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) C.demo else C.surface).clickable { vm.setDemo { it.copy(remainKm = km) }; vm.resetDismissed() }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            Toggle("엔진오일 부족", d.engineOilLow) { v -> vm.setDemo { it.copy(engineOilLow = v) }; vm.resetDismissed() }
            Toggle("브레이크액 위험", d.brakeCritical) { v -> vm.setDemo { it.copy(brakeCritical = v) }; vm.resetDismissed() }
            Toggle("타이어 저압 (확장 기능 미리보기)", d.tireLow) { v -> vm.setDemo { it.copy(tireLow = v) }; vm.resetDismissed() }
            Toggle("워셔액 부족", d.washerLow) { v -> vm.setDemo { it.copy(washerLow = v) }; vm.resetDismissed() }
            Toggle("비 (와이퍼 작동)", d.rain) { v -> vm.setDemo { it.copy(rain = v) } }
            Toggle("전조등 꺼짐", d.headlightOff) { v -> vm.setDemo { it.copy(headlightOff = v) } }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
