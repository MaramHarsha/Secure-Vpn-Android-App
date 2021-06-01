package com.technoupdate.securevpn;

import android.app.Application;

import com.google.android.gms.ads.MobileAds;
import com.technoupdate.securevpn.utils.DataUtil;

public class GlobalApp extends Application {

    private static GlobalApp instance;
    private static boolean isImportToOpenVPN = false;
    private DataUtil dataUtil;

    public static String getResourceString(int resId) {
        return instance.getString(resId);
    }

    public static GlobalApp getInstance() {
        return instance;
    }

    public static boolean isIsImportToOpenVPN() {
        return isImportToOpenVPN;
    }

    public DataUtil getDataUtil() {
        return dataUtil;
    }

    @Override
    public void onCreate() {
        instance = this;
        dataUtil = new DataUtil(this);
        MobileAds.initialize(this);
        super.onCreate();
    }
}
