package com.example.radarbeep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class BeepController(private val scope: CoroutineScope) {
    private var beepJob: Job? = null
    private var currentDistanceMeters: Float? = null
    private var active: Boolean = false

    fun updateDistance(distanceMeters: Float?) {
        currentDistanceMeters = distanceMeters
        val shouldBeep = active && distanceMeters != null && distanceMeters in 0f..100f

        RadarRepository.updateStatus { it.copy(isBeeping = shouldBeep) }

        if (!shouldBeep) {
            stopBeeping()
            return
        }

        if (beepJob == null || beepJob?.isActive == false) {
            startLoop()
        }
    }

    fun setMonitoringActive(enabled: Boolean) {
        active = enabled
        if (!enabled) {
            currentDistanceMeters = null
            stopBeeping()
            RadarRepository.updateStatus { it.copy(isBeeping = false) }
        }
    }

    fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
    }

    private fun startLoop() {
        beepJob = scope.launch {
            while (isActive && active) {
                val dist = currentDistanceMeters
                if (dist == null || dist > 100f) {
                    RadarRepository.updateStatus { it.copy(isBeeping = false) }
                    delay(200)
                    continue
                }

                val proximity = ((100f - dist) / 100f).coerceIn(0f, 1f)
                val freqHz = 750.0 + (proximity * 900.0)
                val amplitude = 0.18 + (proximity * 0.72)
                val pauseMs = (900f - (proximity * 760f)).toLong().coerceAtLeast(120L)

                playTone(freqHz = freqHz, durationMs = 75, amplitude = amplitude)
                delay(pauseMs)
            }
        }
    }

    private suspend fun playTone(freqHz: Double, durationMs: Int, amplitude: Double) {
        withContext(Dispatchers.IO) {
            val sampleRate = 22050
            val numSamples = (sampleRate * durationMs) / 1000
            val samples = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val angle = 2.0 * PI * i / (sampleRate / freqHz)
                val envelope = when {
                    i < numSamples / 8 -> i.toDouble() / (numSamples / 8.0)
                    i > numSamples * 7 / 8 -> (numSamples - i).toDouble() / (numSamples / 8.0)
                    else -> 1.0
                }
                val value = sin(angle) * amplitude * envelope
                samples[i] = (value * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(samples.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                track.write(samples, 0, samples.size)
                track.play()
                delay(durationMs.toLong() + 10L)
                track.stop()
            } finally {
                track.release()
            }
        }
    }
}
