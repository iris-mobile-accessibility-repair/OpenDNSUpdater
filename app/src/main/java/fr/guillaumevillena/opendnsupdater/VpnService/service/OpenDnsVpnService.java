package fr.guillaumevillena.opendnsupdater.VpnService.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import androidx.core.app.NotificationCompat;
import fr.guillaumevillena.opendnsupdater.OpenDnsUpdater;
import fr.guillaumevillena.opendnsupdater.R;
import fr.guillaumevillena.opendnsupdater.VpnService.provider.Provider;
import fr.guillaumevillena.opendnsupdater.VpnService.provider.UdpProvider;
import fr.guillaumevillena.opendnsupdater.VpnService.receiver.StatusBarBroadcastReceiver;
import fr.guillaumevillena.opendnsupdater.VpnService.util.server.DNSServerHelper;
import fr.guillaumevillena.opendnsupdater.activity.GlobalSettingsActivity;
import fr.guillaumevillena.opendnsupdater.activity.MainActivity;


public class OpenDnsVpnService extends VpnService implements Runnable {
    public static final String ACTION_ACTIVATE = "fr.guillaumevillena.opendnsupdater.VpnService.OpenDnsVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "fr.guillaumevillena.opendnsupdater.VpnService.OpenDnsVpnService.ACTION_DEACTIVATE";

    private static final int NOTIFICATION_ACTIVATED = 0;

    private static final String TAG = "OpenDnsVpnService";
    private static final String CHANNEL_ID = "opendnsupdater_channel_1";
    private static final String CHANNEL_NAME = "opendnsupdate_channel";

    public static String primaryServer;
    public static String secondaryServer;
    private static boolean activated = false;
    public HashMap<String, String> dnsServers;
    private NotificationCompat.Builder notification = null;
    private boolean running = false;
    private long lastUpdate = 0;
    private boolean statisticQuery;
    private Provider provider;
    private ParcelFileDescriptor descriptor;
    private Thread mThread = null;

    public static boolean isActivated() {
        return activated;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    activated = true;
                    if (OpenDnsUpdater.getPrefs().getBoolean("settings_notification", true)) {

                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                            manager.createNotificationChannel(channel);
                            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
                        } else {
                            builder = new NotificationCompat.Builder(this);
                        }

                        Intent deactivateIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION);
                        deactivateIntent.setClass(this, StatusBarBroadcastReceiver.class);
                        Intent settingsIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION);
                        settingsIntent.setClass(this, StatusBarBroadcastReceiver.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0,
                                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setWhen(0)
                                .setContentTitle(getResources().getString(R.string.notice_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.drawable.ic_icon)
                                .setColor(getResources().getColor(R.color.colorPrimary)) //backward compatibility
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setTicker(getResources().getString(R.string.notice_activated))
                                .setContentIntent(pIntent)
                                .addAction(R.drawable.ic_clear, getResources().getString(R.string.button_text_deactivate),
                                        PendingIntent.getBroadcast(this, 0,
                                                deactivateIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                                .addAction(R.drawable.ic_settings, getResources().getString(R.string.action_settings),
                                        PendingIntent.getBroadcast(this, 0,
                                                settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                        Notification notification = builder.build();

                        manager.notify(NOTIFICATION_ACTIVATED, notification);

                        this.notification = builder;
                    }

                    DNSServerHelper.buildPortCache();

                    if (this.mThread == null) {
                        this.mThread = new Thread(this, "OpenDnsUpdater");
                        this.running = true;
                        this.mThread.start();
                    }
                    return START_STICKY;
                case ACTION_DEACTIVATE:
                    stopThread();
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopThread();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopThread() {
        Log.d(TAG, "stopThread");
        activated = false;
        boolean shouldRefresh = false;
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (mThread != null) {
                running = false;
                shouldRefresh = true;
                if (provider != null) {
                    provider.shutdown();
                    mThread.interrupt();
                    provider.stop();
                } else {
                    mThread.interrupt();
                }
                mThread = null;
            }
            if (notification != null) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                notification = null;
            }
            dnsServers = null;
        } catch (Exception e) {
            Log.e(TAG, "stopThread: ", e);
        }
        stopSelf();

        if (shouldRefresh) {
            DNSServerHelper.clearPortCache();
            Log.i(TAG, "OpenDnsUpdater VPN service has stopped");
        }

    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    private InetAddress addDnsServer(Builder builder, String format, byte[] ipv6Template, String addr) throws UnknownHostException {
        int size = dnsServers.size();
        size++;
        if (addr.contains("/")) {//https uri
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, addr);
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        }
        InetAddress address = InetAddress.getByName(addr);
        if (address instanceof Inet6Address && ipv6Template == null) {
            Log.i(TAG, "addDnsServer: Ignoring DNS server " + address);
        } else if (address instanceof Inet4Address) {
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, address.getHostAddress());
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        } else if (address instanceof Inet6Address) {
            ipv6Template[ipv6Template.length - 1] = (byte) (size + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            dnsServers.put(i6addr.getHostAddress(), address.getHostAddress());
            return i6addr;
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        try {
            Builder builder = new Builder()
                    .setSession("OpenDnsUpdater")
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, GlobalSettingsActivity.class),
                            PendingIntent.FLAG_ONE_SHOT));
            String format = null;
            for (String prefix : new String[]{"10.0.0", "192.0.2", "198.51.100", "203.0.113", "192.168.50"}) {
                try {
                    builder.addAddress(prefix + ".1", 24);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                format = prefix + ".%d";
                break;
            }

            boolean advanced = OpenDnsUpdater.getPrefs().getBoolean("settings_advanced_switch", false);
            statisticQuery = OpenDnsUpdater.getPrefs().getBoolean("settings_count_query_times", false);
            byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            if (primaryServer.contains(":") || secondaryServer.contains(":")) {//IPv6
                try {
                    InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                    Log.d(TAG, "configure: Adding IPv6 address" + addr);
                    builder.addAddress(addr, 120);
                } catch (Exception e) {
                    Log.e(TAG, "run: ", e);
                    ipv6Template = null;
                }
            } else {
                ipv6Template = null;
            }

            InetAddress aliasPrimary;
            InetAddress aliasSecondary;
            if (advanced) {
                dnsServers = new HashMap<>();
                aliasPrimary = addDnsServer(builder, format, ipv6Template, primaryServer);
                aliasSecondary = addDnsServer(builder, format, ipv6Template, secondaryServer);
            } else {
                aliasPrimary = InetAddress.getByName(primaryServer);
                aliasSecondary = InetAddress.getByName(secondaryServer);
            }

            InetAddress primaryDNSServer = aliasPrimary;
            InetAddress secondaryDNSServer = aliasSecondary;
            Log.i(TAG, "OpenDnsUpdater VPN service is listening on " + primaryServer + " as " + primaryDNSServer.getHostAddress());
            Log.i(TAG, "OpenDnsUpdater VPN service is listening on " + secondaryServer + " as " + secondaryDNSServer.getHostAddress());
            builder.addDnsServer(primaryDNSServer).addDnsServer(secondaryDNSServer);

            if (advanced) {
                builder.setBlocking(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            descriptor = builder.establish();
            Log.i(TAG, "OpenDnsUpdater VPN service is started");

            if (advanced) {
                provider = new UdpProvider(descriptor, this);
                provider.start();
                provider.process();
            } else {
                while (running) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "run: ", e);
        } finally {
            Log.d(TAG, "quit");
            stopThread();
        }
    }

    public void providerLoopCallback() {
        if (statisticQuery) {
            updateUserInterface();
        }
    }

    private void updateUserInterface() {
        long time = System.currentTimeMillis();
        if (time - lastUpdate >= 1000) {
            lastUpdate = time;
            if (notification != null) {
                notification.setContentTitle(getResources().getString(R.string.notice_queries) + " " + String.valueOf(provider.getDnsQueryTimes()));
                NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(NOTIFICATION_ACTIVATED, notification.build());
            }
        }
    }


}
