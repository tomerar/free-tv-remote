package io.github.tomerar.freetvremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import io.github.tomerar.freetvremote.FreeTvRemoteApp
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import kotlinx.coroutines.launch

/** Home-screen widget: power, volume down/up and mute for the last used TV. */
class RemoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views =
            RemoteViews(context.packageName, R.layout.widget_remote).apply {
                bind(context, R.id.widget_power, KeyCodes.POWER)
                bind(context, R.id.widget_volume_down, KeyCodes.VOLUME_DOWN)
                bind(context, R.id.widget_volume_up, KeyCodes.VOLUME_UP)
                bind(context, R.id.widget_mute, KeyCodes.VOLUME_MUTE)
            }
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    private fun RemoteViews.bind(context: Context, viewId: Int, keyCode: Int) {
        val intent =
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(WidgetActionReceiver.ACTION_KEY)
                .putExtra(WidgetActionReceiver.EXTRA_KEY_CODE, keyCode)
        val pending =
            PendingIntent.getBroadcast(
                context,
                keyCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        setOnClickPendingIntent(viewId, pending)
    }
}

/** Sends the tapped key. Uses a short-lived connection when the app is not running. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY) return
        val code = intent.getIntExtra(EXTRA_KEY_CODE, -1)
        if (code < 0) return
        val appContext = context.applicationContext
        val container = (appContext as FreeTvRemoteApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (!container.remoteController.sendQuickKey(code)) {
                    Toast.makeText(appContext, R.string.widget_failed, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_KEY = "io.github.tomerar.freetvremote.widget.KEY"
        const val EXTRA_KEY_CODE = "key_code"
    }
}
