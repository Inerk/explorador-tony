package com.tony.explorador

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
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Mantiene viva la app mientras hace algo largo (bajar la actualización, sincronizar con el NAS),
 * aunque cambies de app o la mandes al fondo. Muestra una notificación con el avance.
 */
object Work {
    private lateinit var app: Context
    private val tasks = ConcurrentHashMap<String, Pair<String, Int>>()   // tarea → (texto, %; -1 = sin %)

    fun init(c: Context) { app = c.applicationContext }

    fun begin(key: String, text: String) { tasks[key] = text to -1; refresh() }
    fun update(key: String, text: String, percent: Int = -1) {
        if (!tasks.containsKey(key)) return
        val old = tasks[key]
        tasks[key] = text to percent
        if (old?.second != percent || old.first != text) WorkService.notifyNow(app, current())
    }
    fun end(key: String) { tasks.remove(key); refresh() }

    internal fun current(): Pair<String, Int>? = tasks.values.firstOrNull()

    private fun refresh() {
        if (!::app.isInitialized) return
        if (tasks.isEmpty()) {
            // nunca se detiene «desde fuera»: si el servicio aún no arranca, él mismo se apaga al arrancar
            WorkService.instance?.finishIfIdle()
        } else if (WorkService.instance == null) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, WorkService::class.java)) }
        } else WorkService.notifyNow(app, current())
    }
}

class WorkService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); instance = this }

    fun finishIfIdle() {
        if (Work.current() != null) return
        runCatching { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true) }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(this, Work.current())
        // siempre se llama startForeground primero (si no, Android cierra la app)
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, n)
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "drivenebula:work").apply { acquire(3 * 60 * 60 * 1000L) }
        if (Work.current() == null) finishIfIdle()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        runCatching { wake?.release() }; wake = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: WorkService? = null
        private const val ID = 4711
        private const val CH = "trabajos"

        fun notifyNow(c: Context, t: Pair<String, Int>?) {
            if (t == null) return
            runCatching { (c.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build(c, t)) }
        }

        private fun build(c: Context, t: Pair<String, Int>?): Notification {
            val nm = c.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
                nm.createNotificationChannel(NotificationChannel(CH, "Descargas y sincronización", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(c, 0,
                c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent(), PendingIntent.FLAG_IMMUTABLE)
            val (text, pct) = t ?: ("Trabajando…" to -1)
            return NotificationCompat.Builder(c, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("DriveNebula")
                .setContentText(text)
                .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open)
                .setProgress(100, pct.coerceAtLeast(0), pct < 0)
                .build()
        }
    }
}
