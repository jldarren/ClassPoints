package top.ligoudaner.classpoints.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager; // 新增
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import top.ligoudaner.classpoints.MainActivity;
import top.ligoudaner.classpoints.db.AppDatabase;

/**
 * 前台服务，用于保证 SyncServer 在后台或锁屏状态下依然能够运行。
 */
public class SyncService extends Service {
    public static final String ACTION_DATA_CHANGED = "top.ligoudaner.classpoints.ACTION_DATA_CHANGED";
    private static final String CHANNEL_ID = "sync_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private SyncServer syncServer;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock; // 新增

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 申请 WakeLock 保证 CPU 不在锁屏时休眠
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClassPoints:SyncServer");
        wakeLock.acquire();

        // 申请 WifiLock 保证休眠时 Wi-Fi 不断连
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ClassPoints:WifiLock");
        } else {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "ClassPoints:WifiLock");
        }
        wifiLock.acquire();

        AppDatabase db = AppDatabase.getDatabase(this);
        syncServer = new SyncServer(8080, db);
        syncServer.setOnDataChangeListener(() -> {
            Intent intent = new Intent(ACTION_DATA_CHANGED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        });
        try {
            syncServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // 使用系统自带图标确保可见性
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("班级积分通 同步服务")
                .setContentText("同步服务正在运行，支持网页版实时同步")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // 设置为正在进行的工作
                .setContentIntent(pendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (syncServer != null) {
            syncServer.stop();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sync Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
