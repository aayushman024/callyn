package com.example.callyn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val TAG = "CallRecorder"

    suspend fun startRecording(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, ">>> Request to start recording: $fileName")

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "FAILURE: RECORD_AUDIO permission missing.")
                return@withContext false
            }

            // 2-second delay to let audio path settle
            delay(2000)
            playStartTone()

            val dir = context.getExternalFilesDir("WorkCallRecordings")
            if (dir != null && !dir.exists()) dir.mkdirs()

            // Changed extension to .m4a (MPEG-4) for better compatibility
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val finalName = "${safeFileName}.m4a"
            val file = File(dir, finalName)
            currentFile = file

            var recorder: MediaRecorder? = null
            try {
                recorder = MediaRecorder()
                this@CallRecorder.mediaRecorder = recorder

                // --- STRATEGY A: VOICE_CALL (Best, but often blocked) ---
                try {
                    Log.d(TAG, "Strategy A: VOICE_CALL + AAC")
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Better format
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)    // Better encoder
                    recorder.setAudioEncodingBitRate(128000)
                    recorder.setAudioSamplingRate(44100)
                    recorder.setOutputFile(file.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    Log.d(TAG, "SUCCESS: Recording with VOICE_CALL")
                    return@withContext true
                } catch (e: Exception) {
                    Log.w(TAG, "Strategy A failed. Resetting... Error: ${e.message}")
                    recorder.reset()
                    delay(500)
                }

                // --- STRATEGY B: VOICE_COMMUNICATION (Best Fallback) ---
                // Tuned for VoIP, often bypasses the mute restriction better than MIC
                try {
                    Log.d(TAG, "Strategy B: VOICE_COMMUNICATION + AAC")
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(128000)
                    recorder.setAudioSamplingRate(44100)
                    recorder.setOutputFile(file.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    Log.d(TAG, "SUCCESS: Recording with VOICE_COMMUNICATION")
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Strategy B failed: ${e.message}")
                    recorder.reset()
                    delay(500)
                }

                // --- STRATEGY C: MIC (Last Resort) ---
                try {
                    Log.d(TAG, "Strategy C: MIC + AAC")
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(64000) // Lower bitrate for safety
                    recorder.setAudioSamplingRate(16000)    // Lower sample rate for safety
                    recorder.setOutputFile(file.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    Log.d(TAG, "SUCCESS: Recording with MIC")
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Strategy C failed: ${e.message}")
                }

                cleanup()
                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Critical recorder error", e)
                cleanup()
                return@withContext false
            }
        }
    }

    suspend fun stopRecording(): String? {
        return withContext(Dispatchers.IO) {
            if (mediaRecorder == null) return@withContext null

            try {
                delay(500)
                mediaRecorder?.stop()
                Log.d(TAG, "Recording stopped.")
                currentFile?.absolutePath
            } catch (e: RuntimeException) {
                Log.e(TAG, "Stop failed", e)
                try { currentFile?.delete() } catch (x: Exception) {}
                null
            } finally {
                cleanup()
            }
        }
    }

    private fun cleanup() {
        try { mediaRecorder?.release() } catch (e: Exception) {}
        mediaRecorder = null
    }

    private fun playStartTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGen.startTone(ToneGenerator.TONE_SUP_PIP, 150)
        } catch (e: Exception) { }
    }
}