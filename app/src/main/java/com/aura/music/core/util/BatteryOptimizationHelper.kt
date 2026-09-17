package com.aura.music.core.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    private const val NOTIFICATION_ID = 8842

    /**
     * Verifica si la app ya tiene concedido ignorar las optimizaciones de batería ("Sin restricciones").
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Verifica si la app tiene deshabilitada la suspensión de actividad / revocación automática de permisos (Android 11+).
     */
    fun isAutoRevokeDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.isAutoRevokeWhitelisted
        } else {
            true
        }
    }

    /**
     * Retorna true si ambas opciones clave ya están configuradas para no cerrar la app en segundo plano.
     */
    fun isFullyOptimizedForBackground(context: Context): Boolean {
        return isIgnoringBatteryOptimizations(context) && isAutoRevokeDisabled(context)
    }

    /**
     * Genera el Intent para abrir directamente "Info. de la aplicación" (Ajustes de Aura Music).
     */
    fun getAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Cancela y elimina cualquier notificación remanente de guía para no saturar la barra de estado.
     */
    fun dismissGuideNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
