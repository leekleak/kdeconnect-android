/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.findmyphone;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore;
import org.kde.kdeconnect.helpers.DeviceHelper;
import org.kde.kdeconnect.helpers.LifecycleHelper;
import org.kde.kdeconnect.helpers.NotificationHelper;
import org.kde.kdeconnect.plugins.Plugin;
import org.kde.kdeconnect.plugins.PluginInfo;
import org.kde.kdeconnect_tp.R;

import java.io.IOException;

public class FindMyPhonePlugin extends Plugin {

    public final static String PACKET_TYPE_FINDMYPHONE_REQUEST = "kdeconnect.findmyphone.request";
    public final static String PREFERENCES_NAME = "FindMyPhonePlugin_preferences";

    private final TelephonySettingsDataStore telephonySettingsDataStore;

    public FindMyPhonePlugin(Context context, Device device, DeviceHelper deviceHelper, TelephonySettingsDataStore telephonySettingsDataStore) {
        super(context, device);
        this.telephonySettingsDataStore = telephonySettingsDataStore;
    }

    private NotificationManager notificationManager;
    private int notificationId;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private int previousVolume = -1;
    private PowerManager powerManager;
    private FlashlightManager flashlightManager;

    @NonNull
    @Override
    public PluginInfo getPluginInfo() {
        return FindMyPhonePluginInfo.INSTANCE;
    }

    @Override
    public boolean onCreate() {
        notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
        notificationId = (int) System.currentTimeMillis();
        audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        powerManager = ContextCompat.getSystemService(context, PowerManager.class);
        flashlightManager = new FlashlightManager(context);

        Uri ringtone;
        String ringtoneString = telephonySettingsDataStore.getRingtoneUriBlockingBlocking();
        if (ringtoneString.isEmpty()) {
            ringtone = Settings.System.DEFAULT_RINGTONE_URI;
        } else {
            ringtone = Uri.parse(ringtoneString);
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, ringtone);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build();
            mediaPlayer.setWakeMode(context, PowerManager.SCREEN_DIM_WAKE_LOCK); // Prevent screen turning off, requires WAKE_LOCK permission
            mediaPlayer.setAudioAttributes(audioAttributes);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
        } catch (Exception e) {
            Log.e("FindMyPhoneActivity", "Exception", e);
            return false;
        }

        return true;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer.isPlaying()) {
            stopPlaying();
        }
        audioManager = null;
        mediaPlayer.release();
        mediaPlayer = null;

        flashlightManager = null;
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || LifecycleHelper.isInForeground()) {
            Intent intent = new Intent(context, FindMyPhoneActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.getDeviceId());
            context.startActivity(intent);
        } else {
            if (powerManager.isInteractive()) {
                startPlaying();
                startFlashing();
                showBroadcastNotification();
            } else {
                showActivityNotification();
            }
        }
        return true;
    }

    private void showBroadcastNotification() {
        Intent intent = new Intent(context, FindMyPhoneReceiver.class);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setAction(FindMyPhoneReceiver.ACTION_FOUND_IT);
        intent.putExtra(FindMyPhoneReceiver.EXTRA_DEVICE_ID, device.getDeviceId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createNotification(pendingIntent);
    }

    private void showActivityNotification() {
        Intent intent = new Intent(context, FindMyPhoneActivity.class);
        intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.getDeviceId());

        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        createNotification(pi);
    }

    private void createNotification(PendingIntent pendingIntent) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NotificationHelper.Channels.HIGHPRIORITY);
        notification
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(false)
                .setFullScreenIntent(pendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentTitle(context.getString(R.string.findmyphone_found));
        notification.setGroup("BackgroundService");

        notificationManager.notify(notificationId, notification.build());
    }

    void startPlaying() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            // Make sure we are heard even when the phone is silent, restore original volume later
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            mediaPlayer.start();
        }
    }

    void startFlashing() {
        if (isFlashlightEnabledInSettings() && PluginInfo.isPermissionGranted(context, Manifest.permission.CAMERA)) {
            flashlightManager.startFlashing();
        }
    }

    void hideNotification() {
        notificationManager.cancel(notificationId);
    }

    void stopPlaying() {
        if (audioManager == null) {
            // The Plugin was destroyed (probably the device disconnected)
            return;
        }

        if (previousVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
        }
        mediaPlayer.stop();
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void stopFlashing() {
        flashlightManager.stopFlashing();
    }

    boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    private boolean isFlashlightEnabledInSettings() {
        return telephonySettingsDataStore.getFlashlightEnabledBlockingBlocking();
    }
}
