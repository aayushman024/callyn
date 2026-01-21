package com.mnivesh.callyn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    var currentFile: File? = null
        private set

    fun startRecording(phoneNumber: String): Boolean {
        if (isRecording) return false

        // 1. Permission Check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CallRecorder", "RECORD_AUDIO permission DENIED. Aborting.")
            return false
        }

        // 2. File Setup
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // Changed extension to .3gp (Standard for AMR)
        val fileName = "WorkCall_${phoneNumber}_$timeStamp.3gp"
        val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecordings")
        if (!storageDir.exists()) storageDir.mkdirs()

        val file = File(storageDir, fileName)

        // 3. NEW SOURCE PRIORITY (To fix White Noise)
        // VOICE_RECOGNITION (6): Often bypasses 'Call Privacy' noise injection
        // MIC (1): Fallback
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // #1 Best for avoiding white noise
            MediaRecorder.AudioSource.MIC,                 // #2 Raw fallback
            MediaRecorder.AudioSource.VOICE_COMMUNICATION  // #3 Tuned for VoIP
        )

        for (source in sourcesToTry) {
            if (initRecorder(source, file)) {
                isRecording = true
                currentFile = file
                Log.d("CallRecorder", "Started recording with source: $source")
                return true
            }
        }

        Log.e("CallRecorder", "Failed to start recording with ANY source.")
        return false
    }

    private fun initRecorder(source: Int, file: File): Boolean {
        return try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(source)

                // --- CRITICAL CHANGES FOR "WHITE NOISE" FIX ---
                // Use THREE_GPP container (Standard for calls)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

                // Use AMR_NB (Narrowband) encoder.
                // It is designed for 8kHz voice and matches hardware modem lock.
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                // FORCE 8kHz sampling. 44.1kHz often causes static during calls.
                setAudioSamplingRate(8000)
                setAudioEncodingBitRate(12200) // Standard AMR bitrate
                // ----------------------------------------------

                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("CallRecorder", "Source $source failed to init: ${e.message}")
            try {
                mediaRecorder?.reset()
                mediaRecorder?.release()
            } catch (cleanupEx: Exception) { /* Ignored */ }
            mediaRecorder = null
            false
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            Log.d("CallRecorder", "Recording saved: ${currentFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error stopping recording", e)
            currentFile?.delete()
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }
}