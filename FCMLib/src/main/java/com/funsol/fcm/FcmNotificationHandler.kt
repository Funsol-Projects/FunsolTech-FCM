package com.funsol.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger


/**
 * Handles sending notifications related to Firebase Cloud Messaging (FCM) for the app.
 * This includes handling notifications for cross-promotion and simple notifications.
 *
 * @property context The context of the application.
 */
class FcmNotificationHandler(private val context: Context) {

    private val TAG = "FcmNotificationHandler"

    /**
     * Sends a notification based on the provided details.
     *
     * @param icon The URL of the icon image for the notification.
     * @param title The title of the notification.
     * @param shortDesc A short description for the notification.
     * @param image The URL of the image for the notification (optional).
     * @param longDesc A long description for the notification (optional).
     * @param storePackage The package name of the app to open on notification click.
     * @param crossPromotion Whether the notification is part of a cross-promotion campaign.
     */
    fun sendNotification(
        icon: String,
        title: String,
        shortDesc: String,
        image: String?,
        longDesc: String?,
        storePackage: String,
        crossPromotion: Boolean
    ) {
        val channelId = context.getString(R.string.default_notification_channel_id)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Determine the intent based on whether the app is installed or not
        val intent = if (!isAppInstalled(storePackage)) {
            createPlayStoreIntent(storePackage)
        } else {
            createOpenAppIntent(storePackage)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            AtomicInteger().incrementAndGet(), // Unique request code for each notification
            intent.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationID = System.currentTimeMillis().toInt() // Timestamp-based ID

        if (crossPromotion) {
            sendCrossPromotionNotification(
                title, shortDesc, icon, image, pendingIntent, channelId, notificationManager, notificationID
            )
        } else {
            sendSimpleNotification(
                title, shortDesc, icon, image, pendingIntent, channelId, notificationManager, notificationID
            )
        }
    }

    /**
     * Sends a cross-promotion notification.
     *
     * @param title The title of the notification.
     * @param shortDesc The short description of the notification.
     * @param icon The URL of the icon image.
     * @param image The URL of the image for the notification.
     * @param pendingIntent The intent to launch on clicking the notification.
     * @param channelId The ID of the notification channel.
     * @param notificationManager The system notification manager.
     * @param notificationID The unique ID for the notification.
     */
    private fun sendCrossPromotionNotification(
        title: String,
        shortDesc: String,
        icon: String,
        image: String?,
        pendingIntent: PendingIntent,
        channelId: String,
        notificationManager: NotificationManager,
        notificationID: Int
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.firebase_notification_view).apply {
            setTextViewText(R.id.tv_title, title)
            setTextViewText(R.id.tv_short_desc, shortDesc)
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setAutoCancel(true)

        notificationManager.notify(notificationID, notificationBuilder.build())
        loadImagesIntoViews(icon, image, remoteViews, notificationID, notificationBuilder, notificationManager)
    }

    /**
     * Sends a simple notification with the provided details.
     *
     * @param title The title of the notification.
     * @param shortDesc The short description of the notification.
     * @param icon The URL of the icon image.
     * @param image The URL of the image for the notification (optional).
     * @param pendingIntent The intent to launch on clicking the notification.
     * @param channelId The ID of the notification channel.
     * @param notificationManager The system notification manager.
     * @param notificationID The unique ID for the notification.
     */
    private fun sendSimpleNotification(
        title: String,
        shortDesc: String,
        icon: String,
        image: String?,
        pendingIntent: PendingIntent,
        channelId: String,
        notificationManager: NotificationManager,
        notificationID: Int
    ) {
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setContentTitle(title)
            .setContentText(shortDesc)
            .setAutoCancel(true)

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Unhandled exception in coroutine: ${exception.message}", exception)
        }

        CoroutineScope(Dispatchers.Main + exceptionHandler).launch {
            // Load icon asynchronously
            if (!image.isNullOrEmpty()) {

                Picasso.get().load(image).into(object : com.squareup.picasso.Target {
                    override fun onBitmapLoaded(bitmap: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                        val style = NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                        notificationBuilder.setStyle(style)

                        notificationManager.notify(notificationID, notificationBuilder.build())
                    }

                    override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                        Log.e(TAG, "Failed to load image: ${e?.message}")
                        notificationManager.notify(notificationID, notificationBuilder.build())
                    }

                    override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                })

            } else {
                notificationManager.notify(notificationID, notificationBuilder.build())
            }
        }
    }

    /**
     * Loads images asynchronously into notification views.
     *
     * @param icon The URL of the icon image.
     * @param image The URL of the image for the notification.
     * @param remoteViews The RemoteViews object for the notification.
     * @param notificationID The unique ID for the notification.
     * @param notificationBuilder The notification builder.
     * @param notificationManager The system notification manager.
     */
    private fun loadImagesIntoViews(
        icon: String,
        image: String?,
        remoteViews: RemoteViews,
        notificationID: Int,
        notificationBuilder: NotificationCompat.Builder,
        notificationManager: NotificationManager
    ) {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Unhandled exception in coroutine: ${exception.message}", exception)
        }

        CoroutineScope(Dispatchers.Main + exceptionHandler).launch {
            // Load icon asynchronously
            Picasso.get().load(icon).into(object : com.squareup.picasso.Target {
                override fun onBitmapLoaded(largeIcon: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                    remoteViews.setImageViewBitmap(R.id.iv_icon, largeIcon)

                    // Load big image if available
                    if (!image.isNullOrEmpty()) {
                        Picasso.get().load(image).into(object : com.squareup.picasso.Target {
                            override fun onBitmapLoaded(bigImage: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                                remoteViews.setViewVisibility(R.id.iv_feature, View.VISIBLE)
                                remoteViews.setImageViewBitmap(R.id.iv_feature, bigImage)
                                notificationManager.notify(notificationID, notificationBuilder.build())
                            }

                            override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                                Log.e(TAG, "Failed to load big image: ${e?.message}")
                                notificationManager.notify(notificationID, notificationBuilder.build())
                            }

                            override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                        })
                    } else {
                        notificationManager.notify(notificationID, notificationBuilder.build())
                    }
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                    Log.e(TAG, "Failed to load icon: ${e?.message}")
                    notificationManager.notify(notificationID, notificationBuilder.build())
                }

                override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
            })
        }
    }

    /**
     * Checks if the specified app is installed on the device.
     *
     * @param packageName The package name of the app to check.
     * @return `true` if the app is installed and enabled, `false` otherwise.
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Creates an intent to open the specified app.
     * If the app is not found, it creates an intent to open the Play Store page for the app.
     *
     * @param packageName The package name of the app to open.
     * @return The intent to open the app or its Play Store page.
     */
    private fun createOpenAppIntent(packageName: String): Intent {
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?: createPlayStoreIntent(packageName)
    }

    /**
     * Creates an intent to open the Play Store page for the specified app.
     *
     * @param packageName The package name of the app.
     * @return The intent to open the Play Store page.
     */
    private fun createPlayStoreIntent(packageName: String): Intent {
        return try {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        } catch (e: ActivityNotFoundException) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        }
    }
}