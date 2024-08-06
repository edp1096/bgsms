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
import android.util.Log
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

  override fun onAttachedToEngine(
          @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
  ) {
    context = flutterPluginBinding.applicationContext
    context.registerReceiver(this, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    channel = EventChannel(flutterPluginBinding.binaryMessenger, "readsms")
    channel!!.setStreamHandler(this)

    var data = listOf("메시지바디", "01011112222", "2024-08-07 05:40:29.000")
    eventSink?.success(data)
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
      Log.d("MmsObserver", "MMS content changed")

      // MMS 데이터 URI
      val uri = Uri.parse("content://mms/inbox")
      val cursor = context.contentResolver.query(uri, null, null, null, null)

      cursor?.use {
        if (it.moveToLast()) {
          do {
            val messageId = it.getString(it.getColumnIndex("_id"))
            val address = getMmsAddress(messageId)
            val subject = getMmsSubject(messageId)
            val body = getMmsBody(messageId)

            val data = listOf(body, address, subject)
            eventSink?.success(data)
          } while (it.moveToPrevious())
        }
      }
    }
  }

  private fun getMmsAddress(messageId: String): String {
    val uri = Uri.parse("content://mms/$messageId")
    val cursor = context.contentResolver.query(uri, arrayOf("address"), null, null, null)
    cursor?.use {
      if (it.moveToFirst()) {
        return it.getString(it.getColumnIndex("address"))
      }
    }
    return "Unknown"
  }

  private fun getMmsSubject(messageId: String): String {
    val uri = Uri.parse("content://mms/$messageId")
    val cursor = context.contentResolver.query(uri, arrayOf("subject"), null, null, null)
    cursor?.use {
      if (it.moveToFirst()) {
        return it.getString(it.getColumnIndex("subject"))
      }
    }
    return "No Subject"
  }

  private fun getMmsBody(messageId: String): String {
    val uri = Uri.parse("content://mms/$messageId")
    val cursor = context.contentResolver.query(uri, arrayOf("body"), null, null, null)
    cursor?.use {
      if (it.moveToFirst()) {
        return it.getString(it.getColumnIndex("body"))
      }
    }
    return "No Body"
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }

  override fun onReceive(p0: Context?, p1: Intent?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      for (sms in Telephony.Sms.Intents.getMessagesFromIntent(p1)) {
        var data =
                listOf(
                        sms.displayMessageBody,
                        sms.originatingAddress.toString(),
                        sms.timestampMillis.toString(),
                )
        eventSink?.success(data)
      }
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
