package com.example.radarbeep

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class RadarMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var beepController: BeepController
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        beepController = BeepController(serviceScope)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("مراقبة الرادارات نشطة في الخلفية"))
        beepController.setMonitoringActive(true)
        RadarRepository.updateStatus { it.copy(isMonitoring = true) }
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            stopMonitoring()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                processLocation(loc)
            }
        }

        fusedClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun processLocation(location: Location) {
        val radars = RadarRepository.radars.value
        val speedKmh = (location.speed * 3.6f).toInt().coerceAtLeast(0)

        var nearestRadar: RadarPoint? = null
        var minDistance = Float.MAX_VALUE

        for (radar in radars) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                radar.lat,
                radar.lng,
                results
            )
            val dist = results[0]
            if (dist < minDistance) {
                minDistance = dist
                nearestRadar = radar
            }
        }

        val finalDistance = if (nearestRadar != null) minDistance else null
        beepController.updateDistance(finalDistance)

        RadarRepository.updateStatus {
            it.copy(
                isMonitoring = true,
                nearestRadarName = nearestRadar?.name,
                nearestDistanceMeters = finalDistance,
                currentSpeedKmh = speedKmh,
                lastKnownLat = location.latitude,
                lastKnownLng = location.longitude
            )
        }

        val notifText = if (finalDistance != null && finalDistance <= 100f) {
            "تنبيه: رادار (${nearestRadar?.name}) على بعد ${finalDistance.toInt()} متر"
        } else {
            "المراقبة نشطة — السرعة: $speedKmh كم/س"
        }
        updateNotification(notifText)
    }

    private fun stopMonitoring() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        beepController.setMonitoringActive(false)
        RadarRepository.updateStatus {
            TrackingStatus(isMonitoring = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مراقبة رادارات السرعة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار مستمر أثناء مراقبة القرب من الرادارات في الخلفية"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RadarMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Radar Beep")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        beepController.stopBeeping()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "radar_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.radarbeep.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, RadarMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadarMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
