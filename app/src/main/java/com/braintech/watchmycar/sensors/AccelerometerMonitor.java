package com.braintech.watchmycar.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

import com.braintech.watchmycar.base.ApplicationPreferences;
import com.braintech.watchmycar.base.EventTrigger;

/**
 * Created by n8fr8 on 3/10/17.
 */
public class AccelerometerMonitor implements SensorEventListener {

    private static final String TAG = AccelerometerMonitor.class.getSimpleName();

    // For shake motion detection.
    private SensorManager sensorMgr;

    /**
     * Accelerometer sensor
     */
    private Sensor accelerometer;

    /**
     * Last update of the accelerometer
     */
    private long lastUpdate = 0;

    /**
     * Current accelerometer values
     */
    private float accel_values[];

    /**
     * Last accelerometer values
     */
    private float last_accel_values[];

    /**
     * Data field used to retrieve application prefences
     */
    private ApplicationPreferences prefs;

    private final Handler mHandler;

    /**
     * Shake threshold
     */
    private float shakeThreshold = 0.5f;

    private float mAccelCurrent = SensorManager.GRAVITY_EARTH;
    private float mAccelLast = SensorManager.GRAVITY_EARTH;
    private float mAccel = 0.00f;
    /**
     * Text showing accelerometer values
     */
    private int maxAlertPeriod = 30;
    private int remainingAlertPeriod = 0;
    private boolean alert = false;
    private final static int CHECK_INTERVAL = 100;

    public AccelerometerMonitor(Context context, Handler svcHandler) {
        prefs = new ApplicationPreferences(context);
        mHandler = svcHandler;

		/*
         * Set sensitivity value
		 */
        try {
            shakeThreshold = Integer.parseInt(prefs.getAccelerometerSensitivity());
        } catch (Exception e) {
            // Let the default value
        }

        sensorMgr = (SensorManager) context.getSystemService(Activity.SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer == null) {
            Log.i("AccelerometerFrament", "Warning: no accelerometer");
        } else {
            sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Safe not to implement

    }

    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        // only allow one update every 100ms.
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdate) > CHECK_INTERVAL) {

                //Log.d(TAG, "Time to check...");
                lastUpdate = curTime;

                accel_values = event.values.clone();

                if (alert && remainingAlertPeriod > 0) {
                    remainingAlertPeriod = remainingAlertPeriod - 1;
                } else {
                    alert = false;
                }

                //if (last_accel_values != null) {

                    mAccelLast = mAccelCurrent;
                    mAccelCurrent = (float) Math.sqrt(accel_values[0] * accel_values[0] + accel_values[1] * accel_values[1]
                            + accel_values[2] * accel_values[2]);
                    float delta = mAccelCurrent - mAccelLast;
                    mAccel = mAccel * 0.9f + delta;

                    //Log.d(TAG, "mAccel is " + mAccel + ", and shakeThreshold is " + shakeThreshold);

                    if (mAccel > shakeThreshold) {
						/*
						 * Send Alert
						 */

                        alert = true;
                        remainingAlertPeriod = maxAlertPeriod;
                        mHandler.obtainMessage(EventTrigger.ACCELEROMETER).sendToTarget();
                    }
                //}
                //last_accel_values = accel_values.clone();
            }
        }
    }

    public void stop(Context context) {
        sensorMgr.unregisterListener(this);
    }

}
