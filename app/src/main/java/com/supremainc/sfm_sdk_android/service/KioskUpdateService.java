package com.supremainc.sfm_sdk_android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.supremainc.sfm_sdk_android.R;
import com.supremainc.sfm_sdk_android.SignalRService;
import com.supremainc.sfm_sdk_android.dto.signalr.FingerprintEnrollmentEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.InterimCutoffEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchAggregateEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.KeySwitchEventDto;
import com.supremainc.sfm_sdk_android.dto.signalr.VaultIncidentBroadcastDto;
import com.supremainc.sfm_sdk_android.util.AppUpdateManager;

import java.io.File;

/**
 * Persistent foreground service that listens for KioskPatchReady via SignalR
 * and silently installs APK updates — regardless of which screen is active.
 *
 * Started once from SplashActivity and kept alive for the full app session.
 */
public class KioskUpdateService extends Service {

    private static final String TAG = "KioskUpdateService";
    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "kiosk_update_service";

    private SignalRService signalRService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        initializeSignalR();
        Log.i(TAG, "KioskUpdateService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (signalRService != null) signalRService.stop();
        Log.i(TAG, "KioskUpdateService stopped");
    }

    // ==================== SIGNALR ====================

    private void initializeSignalR() {
        signalRService = new SignalRService(this);
        signalRService.initialize(new SignalRService.SignalREventListener() {

            @Override
            public void onKioskPatchReady() {
                Log.i(TAG, "╔════════════════════════════════════════════════════════════");
                Log.i(TAG, "║ KIOSK PATCH READY — checking FTP folder for APK update");
                Log.i(TAG, "╚════════════════════════════════════════════════════════════");

                mainHandler.post(() -> Toast.makeText(KioskUpdateService.this,
                        "App update received. Installing...", Toast.LENGTH_LONG).show());

                new Thread(() -> AppUpdateManager.checkForUpdate(KioskUpdateService.this,
                        new AppUpdateManager.UpdateCheckCallback() {
                            @Override
                            public void onUpdateAvailable(AppUpdateManager.VersionInfo versionInfo, File apkFile) {
                                Log.i(TAG, "Update found: v" + versionInfo.versionName
                                        + " (code " + versionInfo.versionCode + ") — starting silent install");
                                AppUpdateManager.silentInstall(KioskUpdateService.this, apkFile);
                            }

                            @Override
                            public void onNoUpdateNeeded() {
                                Log.i(TAG, "KioskPatchReady received but no newer APK found in FTP folder");
                            }

                            @Override
                            public void onCheckFailed(String error) {
                                Log.w(TAG, "Update check failed: " + error);
                            }
                        })).start();
            }

            @Override
            public void onConnectionClosed() {
                Log.w(TAG, "SignalR connection closed — retrying in 5s");
                mainHandler.postDelayed(KioskUpdateService.this::connectSignalR, 5000);
            }

            // ── Unused events (no-op) ──
            @Override public void onManualOverrideEvent(SignalRService.ManualOverrideEventData e) {}
            @Override public void onDoorStateChanged(SignalRService.DoorStateData e) {}
            @Override public void onVaultIncident(SignalRService.VaultIncidentData i) {}
            @Override public void onVaultIncidentBroadcast(VaultIncidentBroadcastDto i) {}
            @Override public void onKeySwitchChanged(KeySwitchEventDto e) {}
            @Override public void onKeySwitchAggregate(KeySwitchAggregateEventDto e) {}
            @Override public void onInterimCutoffReached(InterimCutoffEventDto e) {}
            @Override public void onFingerprintEnrollmentCompleted(FingerprintEnrollmentEventDto e) {}
        });

        connectSignalR();

        boolean isOwner = AppUpdateManager.isDeviceOwner(this);
        Log.i(TAG, "Device Owner: " + (isOwner ? "YES — silent install enabled" : "NO — will prompt on update"));
    }

    private void connectSignalR() {
        if (signalRService == null || signalRService.isConnected()) return;

        signalRService.start(new SignalRService.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "✓ SignalR connected — listening for KioskPatchReady");
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "✗ SignalR connection failed: " + errorMessage + " — retrying in 5s");
                mainHandler.postDelayed(KioskUpdateService.this::connectSignalR, 5000);
            }
        });
    }

    // ==================== FOREGROUND NOTIFICATION ====================

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kiosk Update Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GEMS Kiosk")
                .setContentText("Monitoring for updates")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .build();

        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        : 0
        );
    }
}
