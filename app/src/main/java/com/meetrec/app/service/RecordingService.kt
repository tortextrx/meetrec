package com.meetrec.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.meetrec.app.MainActivity
import com.meetrec.app.R
import com.meetrec.app.data.audio.AudioRecordEngine
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.repository.MeetingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class RecordingState(
    val isRecording: Boolean = false,
    val durationSec: Long = 0L,
    val formattedDuration: String = "00:00:00",
    val rmsDb: Float = -96f,
    val normalizedRms: Float = 0f,
    val isClipping: Boolean = false,
    val currentSegmentIndex: Int = 1,
    val meetingTitle: String = "Reunión",
    val errorMessage: String? = null
)

class RecordingService : Service(), AudioRecordEngine.AudioCaptureListener {

    private var audioEngine: AudioRecordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var currentSessionId: String? = null
    private var currentTitle: String = "Reunión de trabajo"
    private var currentParticipants: String = ""
    private var currentNotes: String = ""
    private var startTimeMs: Long = 0L
    private val recordedSegments = mutableListOf<String>()

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "meetrec_recording_channel"

        const val ACTION_START = "com.meetrec.action.START"
        const val ACTION_STOP = "com.meetrec.action.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PARTICIPANTS = "extra_participants"
        const val EXTRA_NOTES = "extra_notes"

        private val _recordingState = MutableStateFlow(RecordingState())
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        fun startRecording(
            context: Context,
            title: String = "Reunión de trabajo",
            participants: String = "",
            notes: String = ""
        ) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PARTICIPANTS, participants)
                putExtra(EXTRA_NOTES, notes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reunión de trabajo"
                val part = intent.getStringExtra(EXTRA_PARTICIPANTS) ?: ""
                val notes = intent.getStringExtra(EXTRA_NOTES) ?: ""
                startRecordingSession(title, part, notes)
            }
            ACTION_STOP -> {
                stopRecordingSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecordingSession(title: String, participants: String, notes: String) {
        if (_recordingState.value.isRecording) return

        currentTitle = title
        currentParticipants = participants
        currentNotes = notes
        currentSessionId = "meet_${System.currentTimeMillis()}"
        startTimeMs = System.currentTimeMillis()
        recordedSegments.clear()

        // Acquire WakeLock
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeetRec::RecordingLock").apply {
            acquire(3 * 60 * 60 * 1000L) // 3 horas max
        }

        val sessionDir = File(filesDir, "session_${currentSessionId}")
        sessionDir.mkdirs()

        audioEngine = AudioRecordEngine(
            context = this,
            sessionDir = sessionDir,
            listener = this,
            segmentDurationSeconds = 300 // 5 min
        )

        // Iniciar servicio en primer plano
        val notification = buildNotification(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        audioEngine?.start()

        _recordingState.value = RecordingState(
            isRecording = true,
            meetingTitle = currentTitle,
            currentSegmentIndex = 1
        )

        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000L
                val hours = elapsedSec / 3600
                val mins = (elapsedSec % 3600) / 60
                val secs = elapsedSec % 60
                val formatted = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)

                _recordingState.value = _recordingState.value.copy(
                    durationSec = elapsedSec,
                    formattedDuration = formatted
                )

                // Actualizar notificación cada 5s
                if (elapsedSec % 5 == 0L) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(elapsedSec))
                }
                delay(1000)
            }
        }
    }

    private fun stopRecordingSession() {
        timerJob?.cancel()
        timerJob = null

        audioEngine?.stop()
        audioEngine = null

        val finalDurationSec = if (startTimeMs > 0) (System.currentTimeMillis() - startTimeMs) / 1000L else 0L

        // Guardar sesión en el repositorio
        currentSessionId?.let { sId ->
            val session = MeetingSession(
                id = sId,
                title = currentTitle,
                participants = currentParticipants,
                notes = currentNotes,
                createdAt = startTimeMs,
                durationSec = finalDurationSec,
                audioSegments = recordedSegments.toList()
            )
            serviceScope.launch(Dispatchers.IO) {
                MeetingRepository.getInstance(applicationContext).saveSession(session)
            }
        }

        _recordingState.value = RecordingState(
            isRecording = false,
            durationSec = finalDurationSec,
            meetingTitle = currentTitle
        )

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onAudioLevel(rmsDb: Float, normalizedLevel: Float, isClipping: Boolean) {
        _recordingState.value = _recordingState.value.copy(
            rmsDb = rmsDb,
            normalizedRms = normalizedLevel,
            isClipping = isClipping
        )
    }

    override fun onSegmentCompleted(segmentFile: File, durationSec: Long, index: Int) {
        if (!recordedSegments.contains(segmentFile.absolutePath)) {
            recordedSegments.add(segmentFile.absolutePath)
        }
        _recordingState.value = _recordingState.value.copy(
            currentSegmentIndex = index + 1
        )
    }

    override fun onError(message: String, isFatal: Boolean) {
        _recordingState.value = _recordingState.value.copy(
            errorMessage = message
        )
        if (isFatal) {
            stopRecordingSession()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Grabación de Reunión",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente de captura de audio"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedSec: Long): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val m = elapsedSec / 60
        val s = elapsedSec % 60
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", m, s)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Grabando: $currentTitle")
            .setContentText("Tiempo de reunión: $timeStr • Grabación en bloques segura")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopPending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecordingSession()
        super.onDestroy()
    }
}

