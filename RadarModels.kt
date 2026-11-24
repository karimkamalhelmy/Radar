package com.example.radarbeep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RadarPoint(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val speedLimitKmh: Int = 80
)

data class TrackingStatus(
    val isMonitoring: Boolean = false,
    val nearestRadarName: String? = null,
    val nearestDistanceMeters: Float? = null,
    val currentSpeedKmh: Int = 0,
    val lastKnownLat: Double? = null,
    val lastKnownLng: Double? = null,
    val isBeeping: Boolean = false
)

object RadarRepository {
    private val _radars = MutableStateFlow(
        listOf(
            RadarPoint(1L, "رادار تجريبي 1", 25.1972, 55.2744, 100),
            RadarPoint(2L, "رادار تجريبي 2", 25.2048, 55.2708, 80)
        )
    )
    val radars: StateFlow<List<RadarPoint>> = _radars.asStateFlow()

    private val _status = MutableStateFlow(TrackingStatus())
    val status: StateFlow<TrackingStatus> = _status.asStateFlow()

    fun addRadar(name: String, lat: Double, lng: Double, speedLimit: Int = 80) {
        val nextId = (_radars.value.maxOfOrNull { it.id } ?: 0L) + 1L
        _radars.update { it + RadarPoint(nextId, name, lat, lng, speedLimit) }
    }

    fun updateStatus(transform: (TrackingStatus) -> TrackingStatus) {
        _status.update(transform)
    }
}
