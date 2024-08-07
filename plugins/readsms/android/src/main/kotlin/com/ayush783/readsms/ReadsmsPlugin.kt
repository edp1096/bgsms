package com.ayush783.readsms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel

class ReadsmsPlugin :
        FlutterPlugin, EventChannel.StreamHandler, BroadcastReceiver(), ActivityAware {

  private var channel: EventChannel? = null
  private var eventSink: EventChannel.EventSink? = null
  private lateinit var context: Context
  private lateinit var activity: Activity
  private lateinit var mmsObserver: MmsObserver

  private var lastTimeStamp: Long = 0L

  override fun onAttachedToEngine(
          @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
  ) {
    context = flutterPluginBinding.applicationContext
    context.registerReceiver(this, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))

    channel = EventChannel(flutterPluginBinding.binaryMessenger, "readsms")
    channel!!.setStreamHandler(this)

    registerContentObservers()
  }

  private fun registerContentObservers() {
    val handler = Handler(Looper.getMainLooper())
    mmsObserver = MmsObserver(handler)
    context.contentResolver.registerContentObserver(
            Uri.parse("content://mms/inbox"),
            true,
            mmsObserver
    )
  }

  private inner class MmsObserver(handler: Handler) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
      super.onChange(selfChange)

      val uri = Uri.parse("content://mms/inbox")
      val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")

      cursor?.use {
        if (it.moveToFirst()) {
          val messageId = it.getString(it.getColumnIndex("_id"))

          val address = getMmsAddress(messageId)
          val body = getMmsBody(messageId)
          val timestamp = getMmsTimestamp(messageId)

          if (address == "Unknown") {
            return
          }

          if (body == "No Body") {
            return
          }

          if (lastTimeStamp == timestamp) {
            return
          }

          lastTimeStamp = timestamp

          val data = listOf(body, address, timestamp.toString())
          eventSink?.success(data)
        }
      }
    }
  }

  private fun getMmsAddress(messageId: String): String {
    val uri = Uri.parse("content://mms/$messageId/addr")
    val cursor = context.contentResolver.query(uri, arrayOf("address"), null, null, null)

    cursor?.use {
      if (it.moveToFirst()) {
        return it.getString(it.getColumnIndex("address"))
      }
    }

    return "Unknown"
  }

  private fun getMmsBody(messageId: String): String {
    val uri = Uri.parse("content://mms/part")
    val cursor =
            context.contentResolver.query(
                    uri,
                    arrayOf("ct", "text"),
                    "mid=?",
                    arrayOf(messageId),
                    null
            )

    cursor?.use {
      while (it.moveToNext()) {
        if (it.getString(it.getColumnIndex("ct")) == "text/plain") {
          return it.getString(it.getColumnIndex("text"))
        }
      }
    }

    return "No Body"
  }

  private fun getMmsTimestamp(messageId: String): Long {
    val uri = Uri.parse("content://mms/$messageId")
    val cursor = context.contentResolver.query(uri, arrayOf("date"), null, null, null)

    cursor?.use {
      if (it.moveToFirst()) {
        return it.getLong(it.getColumnIndex("date")) * 1000
      }
    }

    return 0L
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }

  override fun onReceive(p0: Context?, intent: Intent?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      when (intent?.action) {
        Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSms(intent)
      }
    }
  }

  private fun handleSms(intent: Intent) {
    for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
      var data =
              listOf(
                      sms.displayMessageBody,
                      sms.originatingAddress.toString(),
                      sms.timestampMillis.toString(),
              )
      eventSink?.success(data)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel = null
    eventSink = null
    context.contentResolver.unregisterContentObserver(mmsObserver)
  }

  override fun onAttachedToActivity(p0: ActivityPluginBinding) {
    activity = p0.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) {}

  override fun onDetachedFromActivity() {}
}
