package com.braintech.watchmycar.base;

import android.telephony.SmsManager;
import android.util.Log;

/**
 * Created by mapo on 20/03/18.
 */

public final class SMSsender {

    private static final String TAG = SMSsender.class.getSimpleName();

    public static void sendSMS(String number, String msg) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, msg, null ,null);
        } catch (Exception e) {
            Log.e(TAG, "Could not send SMS", e);
        }
    }
}
