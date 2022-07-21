package com.example.messanger.client

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        private const val TAG = "TAG_ACTIVITY"
        private const val PACKAGE_NAME = "com.example.messanger.host"
        private const val ACTION_START = "$PACKAGE_NAME.START"
        private const val WHAT_INIT_REQUEST_FROM_CLIENT = 1001
        private const val WHAT_INIT_RESPONSE_FROM_HOST = 1002
        private const val WHAT_TIME_REQUEST_FROM_CLIENT = 1003
        private const val WHAT_TIME_RESPONSE_FROM_HOST = 1004
        private const val WHAT_REGULAR_MESSAGE_FROM_HOST = 2001
        private const val WHAT_FORCE_STOP_SERVICE_REQUEST_FROM_CLIENT = 9000
    }

    private var sendMessenger: Messenger? = null
    private lateinit var receiveMessenger: Messenger

    private val receiveHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Log.d(TAG, "what: ${msg.what}, obj: ${msg.obj}")
            when (msg.what) {
                WHAT_INIT_RESPONSE_FROM_HOST -> Unit
                WHAT_TIME_RESPONSE_FROM_HOST -> {
                    val bundle = msg.obj as Bundle
                    displayFormattedTime(bundle.getString("msg") ?: "")
                }
                WHAT_REGULAR_MESSAGE_FROM_HOST -> {
                    val bundle = msg.obj as Bundle
                    displayRegularMessage(bundle.getString("msg") ?: "")
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            this@MainActivity.service = service
            this@MainActivity.service?.linkToDeath(deathRecipient, 0)

            sendMessenger = Messenger(service)

            val msgData = Message.obtain(null, WHAT_INIT_REQUEST_FROM_CLIENT)
            msgData.replyTo = receiveMessenger
            sendMessenger?.send(msgData)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sendMessenger = null
        }
    }

    private var service: IBinder? = null
    private var deathRecipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            Log.d(TAG, "binderDied")
            if (service != null) {
                service?.unlinkToDeath(this, 0)
                // Restart service
                stopService()
                startService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.title_name)

        receiveMessenger = Messenger(receiveHandler)

        findViewById<Button>(R.id.request_button).setOnClickListener(this)
        findViewById<Button>(R.id.stop_service_button).setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        startService()
    }

    override fun onPause() {
        stopService()
        super.onPause()
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.request_button -> {
                val bundle = Bundle().apply {
                    putLong("msg", System.currentTimeMillis())
                }
                val msgData = Message.obtain(null, WHAT_TIME_REQUEST_FROM_CLIENT, bundle)
                sendMessenger?.send(msgData)
            }
            R.id.stop_service_button -> {
                val msgData = Message.obtain(null, WHAT_FORCE_STOP_SERVICE_REQUEST_FROM_CLIENT)
                sendMessenger?.send(msgData)
            }
        }
    }

    private fun createServiceIntent(): Intent {
        return Intent().apply {
            action = ACTION_START
            addCategory(Intent.CATEGORY_DEFAULT)
            `package` = PACKAGE_NAME
        }
    }

    private fun startService() {
        val intent = createServiceIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE)
    }

    private fun stopService() {
        unbindService(serviceConnection)
        stopService(createServiceIntent())
    }

    private fun displayFormattedTime(text: String) {
        findViewById<TextView>(R.id.formatted_time_text_view).text = text
    }

    private fun displayRegularMessage(text: String) {
        findViewById<TextView>(R.id.regular_message_text_view).text = text
    }
}
