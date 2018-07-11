
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
import android.preference.PreferenceManager;

import com.braintech.watchmycar.R;

public class ApplicationPreferences {
	
    private SharedPreferences appSharedPrefs;

    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";
    public static final String OFF = "OFF";

    public static final String KEY_DELAY_TIME = "config_delay_time";
    public static final String KEY_SMS_NUMBER = "sms_number";
    public static final String KEY_NOTIFICATION_TIME = "notification_time";
    public static final String KEY_CONFIG_SOUND = "config_sound";
    public static final String KEY_CONFIG_MOVEMENT = "config_movement";

    private Context context;
	
    public ApplicationPreferences(Context context) {
        this.context = context;
        this.appSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public SharedPreferences getSharedPreferences() {
        return appSharedPrefs;
    }

    public int getTimerDelay () {
        return Integer.parseInt(appSharedPrefs.getString(KEY_DELAY_TIME,"30"));
    }

    public String getMicrophoneSensitivity() {
        return appSharedPrefs.getString(KEY_CONFIG_SOUND, MEDIUM);
    }

    public String getAccelerometerSensitivity() {
        return appSharedPrefs.getString(KEY_CONFIG_MOVEMENT, HIGH);
    }

    public String getAudioPath () {
        return "/watchmycar";
    }

    public int getAudioLength () {
        return 15000; //30 seconds
    }

}
