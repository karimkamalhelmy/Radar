package com.example.radarbeep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    RadarScreen()
                }
            }
        }
    }
}

@Composable
fun RadarScreen() {
    val context = LocalContext.current
    val status by RadarRepository.status.collectAsState()
    val radars by RadarRepository.radars.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            RadarMonitorService.start(context)
        }
    }

    fun requestAndStart() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            RadarMonitorService.start(context)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "منبّه القرب من الرادار (100م)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.isBeeping) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (status.isMonitoring) "الحالة: يعمل في الخلفية" else "الحالة: متوقف",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "أقرب رادار: ${status.nearestRadarName ?: "غير محدد"}"
                    )
                    Text(
                        text = "المسافة المتبقية: ${status.nearestDistanceMeters?.toInt()?.let { "$it م" } ?: "--"}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "السرعة الحالية: ${status.currentSpeedKmh} كم/س"
                    )
                    Text(
                        text = if (status.isBeeping) {
                            "تنبيه صوتي نشط (ضمن 100 متر)"
                        } else {
                            "خارج نطاق 100 متر — الصوت متوقف"
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (status.isMonitoring) {
                            RadarMonitorService.stop(context)
                        } else {
                            requestAndStart()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (status.isMonitoring) "إيقاف المراقبة" else "بدء المراقبة")
                }

                OutlinedButton(
                    onClick = {
                        val lat = status.lastKnownLat
                        val lng = status.lastKnownLng
                        if (lat != null && lng != null) {
                            RadarRepository.addRadar(
                                name = "رادار مضاف #${radars.size + 1}",
                                lat = lat,
                                lng = lng
                            )
                        }
                    },
                    enabled = status.lastKnownLat != null && status.lastKnownLng != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("حفظ موقعي كرادار")
                }
            }

            Text(
                text = "الرادارات المسجلة (${radars.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(radars, key = { it.id }) { radar ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(radar.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${radar.lat}, ${radar.lng}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("حد ${radar.speedLimitKmh} كم/س")
                        }
                    }
                }
            }
        }
    }
}
