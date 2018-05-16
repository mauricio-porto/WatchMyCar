
/*
 * Copyright (c) 2017 Nathanial Freitas
 *
 *   This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.braintech.watchmycar.base;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.braintech.watchmycar.R;

public class ApplicationPreferences {
	
    private SharedPreferences appSharedPrefs;
    private Editor prefsEditor;

    public static final String LOW = "Low";
    public static final String MEDIUM = "Medium";
    public static final String HIGH = "High";
    public static final String OFF = "Off";

    private static final String APP_SHARED_PREFS="com.braintech.watchmycar";
    private static final String ACCELEROMETER_ACTIVE="accelerometer_active";
    private static final String ACCELEROMETER_SENSITIVITY="accelerometer_sensibility";
    private static final String MICROPHONE_ACTIVE="microphone_active";
    private static final String MICROPHONE_SENSITIVITY="microphone_sensitivity";
    public static final String CONFIG_SOUND = "config_sound";
    public static final String CONFIG_TIME_DELAY = "config_delay_time";
    public static final String SMS_ACTIVE = "sms_active";
    public static final String SMS_NUMBER = "sms_number";
    private static final String TIMER_DELAY="timer_delay";
    private static final String DIR_PATH = "/secureit";

    public static final String NOTIFICATION_TIME = "notification_time";

    private Context context;
	
    public ApplicationPreferences(Context context) {
        this.context = context;
        this.appSharedPrefs = context.getSharedPreferences(APP_SHARED_PREFS, Activity.MODE_PRIVATE);
        this.prefsEditor = appSharedPrefs.edit();
    }

    public SharedPreferences getSharedPreferences() {
        return appSharedPrefs;
    }

    public void activateAccelerometer(boolean active) {
    	prefsEditor.putBoolean(ACCELEROMETER_ACTIVE, active);
    	prefsEditor.commit();
    }
    
    public boolean getAccelerometerActivation() {
    	return appSharedPrefs.getBoolean(ACCELEROMETER_ACTIVE, true);
    }
    
    public void setAccelerometerSensitivity(String sensitivity) {
    	prefsEditor.putString(ACCELEROMETER_SENSITIVITY, sensitivity);
    	prefsEditor.commit();
    }
    
    public String getAccelerometerSensitivity() {
    	return appSharedPrefs.getString(ACCELEROMETER_SENSITIVITY, HIGH);
    }

    public void activateMicrophone(boolean active) {
    	prefsEditor.putBoolean(MICROPHONE_ACTIVE, active);
    	prefsEditor.commit();
    }
    
    public boolean getMicrophoneActivation() {
    	return appSharedPrefs.getBoolean(MICROPHONE_ACTIVE, true);
    }
    
    public void setMicrophoneSensitivity(String sensitivity) {
    	prefsEditor.putString(MICROPHONE_SENSITIVITY, sensitivity);
    	prefsEditor.commit();
    }
    
    public String getMicrophoneSensitivity() {
    	return appSharedPrefs.getString(MICROPHONE_SENSITIVITY, MEDIUM);
    }
    
    public void activateSms(boolean active) {
    	prefsEditor.putBoolean(SMS_ACTIVE, active);
    	prefsEditor.commit();
    }
    
    public boolean getSmsActivation() {
    	return appSharedPrefs.getBoolean(SMS_ACTIVE, false);
    }
    
    public void setSmsNumber(String number) {

    	prefsEditor.putString(SMS_NUMBER, number);
    	prefsEditor.commit();
    }
    
    public String getSmsNumber() {
    	return appSharedPrefs.getString(SMS_NUMBER, "");
    }

    public int getTimerDelay ()
    {
        return appSharedPrefs.getInt(TIMER_DELAY,30);
    }

    public void setTimerDelay (int delay)
    {
        prefsEditor.putInt(TIMER_DELAY,delay);
        prefsEditor.commit();
    }

    public String getDirPath() {
    	return DIR_PATH;
    }
    
    public String getSMSText() {
        return context.getString(R.string.intrusion_detected);
    }

    public String getImagePath ()
    {
        return "/phoneypot";
    }

    public int getMaxImages ()
    {
        return 10;
    }

    public String getAudioPath ()
    {
        return "/phoneypot"; //phoneypot is the old code name for Haven
    }

    public int getAudioLength ()
    {
        return 15000; //30 seconds
    }

    public int getNotificationTimeMs () {
        return appSharedPrefs.getInt(NOTIFICATION_TIME,-1); //time in minutes times by seconds
    }

    public void setNotificationTimeMs (int notificationTimeMs) {
        prefsEditor.putInt(NOTIFICATION_TIME,notificationTimeMs);
        prefsEditor.commit();
    }

}
