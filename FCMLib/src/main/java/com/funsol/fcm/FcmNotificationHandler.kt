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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles the creation and display of Firebase Cloud Messaging (FCM) notifications.
 *
 * This class is responsible for building and displaying notifications based on FCM message data.
 * It also handles image loading for notification views and manages intents for app-related actions.
 *
 * @param context The [Context] used for accessing system services and resources.
 */
class FcmNotificationHandler(private val context: Context) {

    private val TAG = "FcmNotificationHandler"

    /**
     * Handles the display of notifications for both cross-promotion and simple notifications.
     *
     * @param icon The URL of the notification's icon.
     * @param title The title of the notification.
     * @param shortDesc The short description or body of the notification.
     * @param image The URL of the notification's large image, if any.
     * @param longDesc The long description of the notification, if applicable.
     * @param storePackage The package name of the app to be promoted or opened.
     * @param crossPromotion A flag indicating if this is a cross-promotion notification.
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

        // Intent for the notification action
        val intent = if (!isAppInstalled(storePackage)) {
            createPlayStoreIntent(storePackage) // Opens Play Store if app is not installed
        } else {
            createOpenAppIntent(storePackage) // Opens the app if installed
        }

        // PendingIntent for the notification
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        if (crossPromotion) {
            // Cross-promotion notification flow
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

            val notificationID = AtomicInteger().incrementAndGet()
            notificationManager.notify(notificationID, notificationBuilder.build())

            // Load images asynchronously
            loadImagesIntoViews(icon, image, remoteViews, notificationID, notificationBuilder)
        } else {
            // Simple notification flow
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(pendingIntent)
                .setContentTitle(title)
                .setContentText(shortDesc)
                .setAutoCancel(true)

            // If an image URL is provided, use it for BigPictureStyle
            if (!image.isNullOrEmpty()) {
                Picasso.get().load(image).into(object : com.squareup.picasso.Target {
                    override fun onBitmapLoaded(bitmap: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                        val style = NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)

                        // Load the large icon asynchronously
                        Picasso.get().load(icon).into(object : com.squareup.picasso.Target {
                            override fun onBitmapLoaded(largeIcon: android.graphics.Bitmap?, from: Picasso.LoadedFrom?) {
                                style.bigLargeIcon(largeIcon)
                                notificationBuilder.setStyle(style)

                                // Show the notification after both images are loaded
                                val notificationID = AtomicInteger().incrementAndGet()
                                notificationManager.notify(notificationID, notificationBuilder.build())
                            }

                            override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                                Log.e(TAG, "Failed to load large icon: ${e?.message}")
                            }

                            override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                        })
                    }

                    override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                        Log.e(TAG, "Failed to load big picture: ${e?.message}")
                    }

                    override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                })
            } else {
                // Show notification without images
                val notificationID = AtomicInteger().incrementAndGet()
                notificationManager.notify(notificationID, notificationBuilder.build())
            }
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

    /**
     * Loads images into the notification views using Picasso.
     *
     * @param icon URL of the notification icon.
     * @param image Optional URL of the feature image.
     * @param remoteViews The [RemoteViews] to update with images.
     * @param notificationID The ID of the notification.
     * @param notificationBuilder The [NotificationCompat.Builder] used to build the notification.
     */
    private fun loadImagesIntoViews(
        icon: String,
        image: String?,
        remoteViews: RemoteViews,
        notificationID: Int,
        notificationBuilder: NotificationCompat.Builder
    ) {
        try {
            Picasso.get().load(icon).into(remoteViews, R.id.iv_icon, notificationID, notificationBuilder.build())
            if (!image.isNullOrEmpty()) {
                remoteViews.setViewVisibility(R.id.iv_feature, View.VISIBLE)
                Picasso.get().load(image).into(remoteViews, R.id.iv_feature, notificationID, notificationBuilder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading images into notification views: ${e.message}")
        }
    }
}