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
import kotlinx.coroutines.withContext
import java.io.File

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val TAG = "CallRecorder"

    /**
     * Starts recording safely on a background thread.
     */
    suspend fun startRecording(number: String): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. Check Permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Recording failed: RECORD_AUDIO permission not granted")
                return@withContext false
            }

            // 2. Play Beep (safe to call, catches its own errors)
            playStartTone()

            // 3. Create File
            val dir = context.getExternalFilesDir("WorkCallRecordings")
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val fileName = "REC_${number}_${System.currentTimeMillis()}.amr"
            val file = File(dir, fileName)
            currentFile = file

            // 4. Initialize Recorder Safely
            try {
                val recorder = MediaRecorder()
                mediaRecorder = recorder

                // Strategy: Try VOICE_CALL, Fallback to MIC
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                } catch (e: Exception) {
                    Log.w(TAG, "VOICE_CALL source failed, falling back to MIC")
                    recorder.reset()
                    // Double check permission just in case
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                }

                recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setOutputFile(file.absolutePath)

                recorder.prepare()
                recorder.start()

                Log.d(TAG, "Recording started: ${file.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                cleanup()
                false
            }
        }
    }

    /**
     * Stops the recording.
     */
    suspend fun stopRecording(): String? {
        return withContext(Dispatchers.IO) {
            if (mediaRecorder == null) return@withContext null

            try {
                mediaRecorder?.stop()
                Log.d(TAG, "Recording stopped")
                currentFile?.absolutePath
            } catch (e: RuntimeException) {
                Log.e(TAG, "Stop failed (recording likely too short)", e)
                // Delete the corrupt file
                try { currentFile?.delete() } catch (deleteEx: Exception) { }
                null
            } finally {
                cleanup()
            }
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) { }
        mediaRecorder = null
        // Don't null currentFile here, we might need the path
    }

    private fun playStartTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGen.startTone(ToneGenerator.TONE_SUP_PIP, 200)
            // Release after a delay to let it play, or let GC handle it.
            // Releasing immediately often cuts the sound.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play beep", e)
        }
    }
}