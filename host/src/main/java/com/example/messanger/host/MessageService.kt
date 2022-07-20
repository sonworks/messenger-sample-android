package com.example.messanger.host

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class MessageService : Service() {

    companion object {
        private const val TAG = "TAG_SERVICE"
        private const val ACTION_START = "com.example.messanger.host.START"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "my_channel_1"
        private const val WHAT_INIT_REQUEST_FROM_CLIENT = 1001
        private const val WHAT_INIT_RESPONSE_FROM_HOST = 1002
        private const val WHAT_TIME_REQUEST_FROM_CLIENT = 1003
        private const val WHAT_TIME_RESPONSE_FROM_HOST = 1004
        private const val WHAT_REGULAR_MESSAGE_FROM_HOST = 2001
    }

    private var sendMessenger: Messenger? = null
    private lateinit var receiveMessenger: Messenger

    private val regularMessageHandler = Handler(Looper.getMainLooper())
    private val regularMessageRunnable = object : Runnable {
        private var cnt = 0
        override fun run() {
            val bundle = Bundle().apply {
                putString("msg", "${cnt++}")
            }
            val msgData = Message.obtain(null, WHAT_REGULAR_MESSAGE_FROM_HOST, bundle)
            sendMessenger?.send(msgData)

            regularMessageHandler.postDelayed(this, 3_000)
        }
    }

    internal class ServiceHandler(
        private val service: MessageService
    ) : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "what: ${msg.what}, obj: ${msg.obj}")
            when (msg.what) {
                WHAT_INIT_REQUEST_FROM_CLIENT -> {
                    val msgData = Message.obtain(null, WHAT_INIT_RESPONSE_FROM_HOST)
                    if (service.sendMessenger == null) {
                        service.sendMessenger = msg.replyTo
                    }
                    service.sendMessenger!!.send(msgData)
                }
                WHAT_TIME_REQUEST_FROM_CLIENT -> {
                    val timeText =
                        service.getFormattedTime((msg.obj as Bundle).getLong("msg"))
                    val bundle = Bundle().apply {
                        putString("msg", timeText)
                    }
                    val msgData = Message.obtain(null, WHAT_TIME_RESPONSE_FROM_HOST, bundle)
                    service.sendMessenger!!.send(msgData)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        receiveMessenger = Messenger(ServiceHandler(this))
        return receiveMessenger.binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                    startForeground()
                }
                regularMessageHandler.post(regularMessageRunnable)
            }
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = "My Channel 1"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, channelName, importance)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("My notification")
            .setContentText("Hello World!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getFormattedTime(timeMillis: Long?): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
        return timeMillis?.let(sdf::format) ?: " - "
    }

    override fun onDestroy() {
        super.onDestroy()
        regularMessageHandler.removeCallbacks(regularMessageRunnable)
        stopForeground(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.deleteNotificationChannel(CHANNEL_ID)
        }
    }
}
