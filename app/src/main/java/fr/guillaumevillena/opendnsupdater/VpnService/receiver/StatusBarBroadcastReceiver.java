package fr.guillaumevillena.opendnsupdater.VpnService.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Method;

import fr.guillaumevillena.opendnsupdater.OpenDnsUpdater;
import fr.guillaumevillena.opendnsupdater.activity.GlobalSettingsActivity;


public class StatusBarBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = StatusBarBroadcastReceiver.class.getSimpleName();
    public static String STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION = "org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION";
    public static String STATUS_BAR_BTN_SETTINGS_CLICK_ACTION = "org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION)) {
            OpenDnsUpdater.deactivateService(context);
        }
        if (intent.getAction().equals(STATUS_BAR_BTN_SETTINGS_CLICK_ACTION)) {
            Intent settingsIntent = new Intent(context, GlobalSettingsActivity.class);
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            try {
                Object statusBarManager = context.getSystemService("statusbar");
                Method collapse = statusBarManager.getClass().getMethod("collapsePanels");
                collapse.invoke(statusBarManager);
            } catch (Exception e) {
                Log.e(TAG, "onReceive: ", e);
            }
        }
    }
}
