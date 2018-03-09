package com.braintech.watchmycar.sensors;

/**
 * Created by n8fr8 on 3/10/17.
 */

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.braintech.watchmycar.base.EventTrigger;
import com.braintech.watchmycar.base.PreferenceManager;
import com.braintech.watchmycar.sensors.media.MicSamplerTask;
import com.braintech.watchmycar.sensors.media.MicrophoneTaskFactory;


public final class MicrophoneMonitor implements MicSamplerTask.MicListener {


    private MicSamplerTask microphone;
    private final Handler mHandler;

    /**
     * Object used to fetch application dependencies
     */
    private PreferenceManager prefs;

    /**
     * Threshold for the decibels sampled
     */
    private double mNoiseThreshold = 70.0;

    private Context context;

    public MicrophoneMonitor(Context context, Handler svcHandler)
    {
        this.context = context;
        mHandler = svcHandler;

        prefs = new PreferenceManager(context);

        switch (prefs.getMicrophoneSensitivity()) {
            case "High":
                mNoiseThreshold = 40;
                break;
            case "Medium":
                mNoiseThreshold = 60;
                break;
            default:
                try {
                    //maybe it is a threshold value?
                    mNoiseThreshold = Double.parseDouble(prefs.getMicrophoneSensitivity());
                } catch (Exception ignored) {
                }
                break;
        }

        try {
            microphone = MicrophoneTaskFactory.makeSampler(context);
            microphone.setMicListener(this);
            microphone.execute();
        } catch (MicrophoneTaskFactory.RecordLimitExceeded e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }



    }

    public void stop (Context context)
    {
        if (microphone != null)
            microphone.cancel(true);
    }


    public void onSignalReceived(short[] signal) {

		/*
		 * We do and average of the 512 samples
		 */
        int total = 0;
        int count = 0;
        for (short peak : signal) {
            //Log.i("MicrophoneMonitor", "Sampled values are: "+peak);
            if (peak != 0) {
                total += Math.abs(peak);
                count++;
            }
        }
        //Log.i("MicrophoneMonitor", "Total value: " + total);
        int average = 0;
        if (count > 0) average = total / count;
		/*
		 * We compute a value in decibels
		 */
        double averageDB = 0.0;
        if (average != 0) {
            averageDB = 20 * Math.log10(Math.abs(average));
        }
        Log.d("MicrophoneMonitor", "averageDB is " + averageDB);

        if (averageDB > mNoiseThreshold) {
            Log.d("MicrophoneMonitor","\n\nWill trigger alarm!!!\n\n");
            mHandler.obtainMessage(EventTrigger.MICROPHONE).sendToTarget();
        }
    }

    public void onMicError() {
        Log.e("MicrophoneMonitor", "Microphone is not ready");
    }
}