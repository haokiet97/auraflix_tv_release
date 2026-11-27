package com.auraflix.mobile.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.cast.CastProtocol
import com.auraflix.tv.core.cast.DiscoveredTv
import com.auraflix.tv.core.cast.PairedTv
import com.auraflix.tv.core.cast.defaultPhoneName

/**
 * Màn hình "Kết nối TV": tự tìm TV cùng Wi-Fi, hoặc nhập IP thủ công,
 * sau đó nhập mã PIN hiển thị trên TV để ghép cặp.
 */
@Composable
fun ConnectTvScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val pairing = remember { CastPairingController(MobileGraph.castClient, MobileGraph.tvRemote) }
    val ui by pairing.state.collectAsStateWithLifecycle()

    DisposableEffect(pairing) { onDispose { pairing.dispose() } }

    LaunchedEffect(Unit) {
        if (!ui.tvFound && !ui.manualMode) pairing.scan()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = AuraColors.TextPrimary,
                )
            }
            Text(
                text = "Kết nối TV",
                color = AuraColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pairing.scan() }, enabled = !ui.scanning) {
                Text(if (ui.scanning) "Đang tìm…" else "Tìm lại", color = AuraColors.Accent, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Điện thoại và TV phải kết nối cùng một mạng Wi-Fi.",
            color = AuraColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))

        when {
            ui.pairing -> PairingPinSection(
                tv = ui.target,
                pin = ui.pin,
                error = ui.error,
                busy = ui.busy,
                onPinChange = pairing::setPin,
                onConfirm = { pairing.pair(onConnected) },
                onCancel = pairing::reset,
            )

            ui.manualMode -> ManualIpSection(
                ip = ui.manualIp,
                busy = ui.busy,
                message = ui.error,
                onIpChange = pairing::setManualIp,
                onConnect = pairing::resolveManual,
                onBack = pairing::exitManual,
            )

            else -> DiscoverySection(
                scanning = ui.scanning,
                found = ui.found,
                error = ui.error,
                paired = ui.paired,
                onSelect = { pairing.choose(it) },
                onManual = pairing::openManual,
            )
        }
    }
}

@Composable
private fun DiscoverySection(
    scanning: Boolean,
    found: List<DiscoveredTv>,
    error: String?,
    paired: PairedTv?,
    onSelect: (DiscoveredTv) -> Unit,
    onManual: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "TV tìm thấy trên mạng",
            color = AuraColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (scanning) {
            CircularProgressIndicator(
                color = AuraColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))

    if (found.isEmpty() && !scanning) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AuraColors.Surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = error ?: "Không tìm thấy TV. Kiểm tra: TV đã mở AuraFlix, Wi-Fi cùng mạng, " +
                    "router không chặn broadcast. Hoặc nhập IP của TV bên dưới.",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    found.forEach { tv ->
        DiscoveredCard(tv = tv, onClick = { onSelect(tv) })
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
        Text("Nhập IP TV thủ công", color = AuraColors.TextPrimary, fontSize = 13.sp)
    }

    paired?.let {
        Spacer(Modifier.height(22.dp))
        Text(
            text = "TV đang kết nối: ${it.name} · ${it.host}",
            color = AuraColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DiscoveredCard(tv: DiscoveredTv, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null, tint = AuraColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tv.info.name,
                color = AuraColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${tv.host}:${tv.info.port}",
                color = AuraColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        Text(if (tv.info.paired) "Ghép cặp" else "Chọn", color = AuraColors.Accent, fontSize = 12.sp)
    }
}

@Composable
private fun ManualIpSection(
    ip: String,
    busy: Boolean,
    message: String?,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Nhập IP của TV",
        color = AuraColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Xem IP tại: TV → Cài đặt → Điều khiển bằng điện thoại.",
        color = AuraColors.TextMuted,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(10.dp))
    AuraTextField(
        value = ip,
        onValueChange = onIpChange,
        placeholder = "192.168.1.12",
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        onDone = onConnect,
    )
    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = AuraColors.Accent, fontSize = 12.sp)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onConnect,
            enabled = ip.isNotBlank() && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
        ) {
            Text(if (busy) "Đang kiểm tra…" else "Kết nối", fontSize = 13.sp)
        }
        TextButton(onClick = onBack) {
            Text("Huỷ", color = AuraColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PairingPinSection(
    tv: DiscoveredTv?,
    pin: String,
    error: String?,
    busy: Boolean,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "Nhập mã PIN hiển thị trên TV",
        color = AuraColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    tv?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${it.info.name} · ${it.host}",
            color = AuraColors.TextMuted,
            fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    AuraTextField(
        value = pin,
        onValueChange = { onPinChange(it.filter(Char::isDigit).take(6)) },
        placeholder = "000000",
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        onDone = { if (pin.length == 6) onConfirm() },
        letterSpacing = 8.sp,
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = AuraColors.Accent, fontSize = 12.sp, lineHeight = 18.sp)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onConfirm,
            enabled = pin.length == 6 && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
        ) {
            Text(if (busy) "Đang ghép cặp…" else "Ghép cặp", fontSize = 13.sp)
        }
        TextButton(onClick = onCancel) {
            Text("Huỷ", color = AuraColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun AuraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = AuraColors.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = AuraColors.TextPrimary,
            letterSpacing = letterSpacing,
            fontWeight = FontWeight.SemiBold,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AuraColors.Accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            cursorColor = AuraColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tên điện thoại gửi tới TV khi ghép cặp. */
internal fun phoneName(): String = defaultPhoneName()
