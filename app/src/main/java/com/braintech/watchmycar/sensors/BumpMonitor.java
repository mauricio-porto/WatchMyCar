package com.braintech.watchmycar.sensors;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Handler;
import android.util.Log;

import com.braintech.watchmycar.base.EventTrigger;

/**
 * Use the Significant Motion trigger sensor on API 18+
 *
 * Created by rockgecko on 27/12/17.
 */
@TargetApi(18)
public class BumpMonitor {

    // For shake motion detection.
    private SensorManager sensorMgr;

    /**
     * Accelerometer sensor
     */
    private Sensor bumpSensor;

    /**
     * Last update of the accelerometer
     */
    private long lastUpdate = -1;

    private final Handler mHandler;

    private final static int CHECK_INTERVAL = 1000;

    public BumpMonitor(Context context, Handler svcHandler) {


        mHandler = svcHandler;

        //context.bindService(new Intent(context, Keeper.class), mConnection, Context.BIND_ABOVE_CLIENT);

        sensorMgr = (SensorManager) context.getSystemService(Activity.SENSOR_SERVICE);
        bumpSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

        if (bumpSensor == null) {
            Log.i("BumpMonitor", "Warning: no significant motion sensor");
        } else {
            boolean registered = sensorMgr.requestTriggerSensor(sensorListener, bumpSensor);
            Log.i("BumpMonitor", "Significant motion sensor registered: "+registered);
        }

    }


    public void stop(Context context) {
        sensorMgr.cancelTriggerSensor(sensorListener, bumpSensor);
    }

    private TriggerEventListener sensorListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            Log.i("BumpMonitor", "Sensor triggered");
            //value[0] = 1.0 when the sensor triggers. 1.0 is the only allowed value.
            long curTime = System.currentTimeMillis();
            // only allow one update every 100ms.
            if (event.sensor.getType() == Sensor.TYPE_SIGNIFICANT_MOTION) {
                if ((curTime - lastUpdate) > CHECK_INTERVAL) {
                    lastUpdate = curTime;

                    /*
                     * Send Alert
                     */
                    mHandler.obtainMessage(EventTrigger.BUMP).sendToTarget();
                }
            }
            //re-register the listener (it finishes after each event)
            boolean registered = sensorMgr.requestTriggerSensor(sensorListener, bumpSensor);
            Log.i("BumpMonitor", "Significant motion sensor re-registered: "+registered);

        }
    };
}
