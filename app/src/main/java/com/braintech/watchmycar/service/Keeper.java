/**
 * 
 */
package com.braintech.watchmycar.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.braintech.watchmycar.R;
import com.braintech.watchmycar.WatchMyCar;
import com.braintech.watchmycar.base.BluetoothConnector;
import com.braintech.watchmycar.base.EventTrigger;
import com.braintech.watchmycar.base.SMSsender;
import com.braintech.watchmycar.sensors.AccelerometerMonitor;
import com.braintech.watchmycar.sensors.BumpMonitor;
import com.braintech.watchmycar.sensors.MicrophoneMonitor;

/**
 * @author mapo
 *
 */
public class Keeper extends Service {

    private static final String TAG = Keeper.class.getSimpleName();

    private static final int ARDUINO_NOTIFICATIONS = 1;

    public static final String ACTION_START = "startService";
    public static final String ACTION_STOP = "stopService";

    // Message types sent from the BluetoothConnector Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Message types received from the activity messenger
    // MUST start by zero due the enum mapping
    public static final int CONNECT_TO = 0;
    public static final int GET_ARDUINO_STATUS = 1;
    public static final int REGISTER_LISTENER = 2;
    public static final int UNREGISTER_LISTENER = 3;
    public static final int REGISTER_HANDLER = 4;
    public static final int UNREGISTER_HANDLER = 5;
    public static final int ARM = 6;
    public static final int DISARM = 7;

    public static enum ACTION {
    	CONNECT_TO,
    	GET_ARDUINO_STATUS,
    	REGISTER_LISTENER,
    	UNREGISTER_LISTENER,
    	REGISTER_HANDLER,
    	UNREGISTER_HANDLER,
    	ARM,
        DISARM
    }

    public static final int ACCELEROMETER = EventTrigger.ACCELEROMETER;
    public static final int CAMERA = EventTrigger.CAMERA;
    public static final int MICROPHONE = EventTrigger.MICROPHONE;
    public static final int PRESSURE = EventTrigger.PRESSURE;
    public static final int LIGHT = EventTrigger.LIGHT;
    public static final int POWER = EventTrigger.POWER;
    public static final int BUMP = EventTrigger.BUMP;

    public static enum TRIGGER {
        ACCELEROMETER,
        CAMERA,
        MICROPHONE,
        PRESSURE,
        LIGHT,
        POWER,
        BUMP
    }

    // Bluetooth and ARDUINO statuses
    public static final int NONE = -1;
    public static final int ARDUINO_NOT_CONFIGURED = 0;
    public static final int BT_DISABLED = 1;
    public static final int ARDUINO_CONNECTED = 2;
    public static final int CONNECTING = 3;
    public static final int ARDUINO_DATA = 4;
    public static final int NOT_RUNNING = 5;
    public static final int TEXT_MESSAGE = 6;

    public static enum BT_STATUS {
    	ARDUINO_NOT_CONFIGURED,
    	BT_DISABLED,
    	ARDUINO_CONNECTED,
    	CONNECTING,
    	ARDUINO_DATA,
    	NOT_RUNNING,
        TEXT_MESSAGE
    }

    // Key names received
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADRESS = "device_address";
    public static final String TOAST = "toast";
    public static final String TEXT_MSG = "text";
    public static final String BOOL_MSG = "bool";

    // Key names sent
    public static final String KEY_ARDUINO_DATA = "ARDUINO_data";
    public static final String KEY_LOCATION_DATA = "location_data";

    public static final int DIST_FREQ_RATIO = 75000;

    // SMS data
    public static final String MY_NUMBER = "993191979";
    public static final String HER_NUMBER = "994151979";
    public static final long MIN_SMS_INTERVAL = 10 * 1000; // 10 seconds
    private long lastSMSsent = 0L;

    private static NotificationManager notifMgr;

    private Toast toast;
    private Vibrator vibrator;
    private boolean mustVibrate = false;

	private float defaultDuration = (float) 0.3;

    private boolean running = false;
    private volatile boolean armed = false;

    private int mBTarduinoStatus = NONE;

    private Messenger activityHandler = null;

    // MAC address of the ARDUINO device
    private String arduinoBluetoothAddress = "00:15:FF:F4:0F:AF";
    // Name of the connected device
    private String mConnectedDeviceName = "JY-MCU";
    private boolean arduinoConnected = false;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    private BluetoothConnector connector;
    private Notification notifier;

    private MicrophoneMonitor mMicMonitor = null;
    private BumpMonitor mBumpMonitor = null;
    private AccelerometerMonitor mAccelMonitor = null;

    /**
     * To show a notification on service start
     */
    private NotificationManager manager;
    private NotificationChannel mChannel;
    private final static String channelId = "keeper_id";
    private final static CharSequence channelName = "Keeper notifications";
    private final static String channelDescription = "Important messages from Keeper";

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return activityMsgListener.getBinder();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        if ((mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()) == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();   // TODO: localize!!!
            return;
        }
        notifMgr = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        this.toast = Toast.makeText(this, TAG, Toast.LENGTH_LONG);
        this.toast.setGravity(Gravity.CENTER, 0, 0);
        showNotification("Started", "WatchMyCar is running...");

        //this.notifier = new Notification(R.drawable.ic_launcher, "WatchMyCar is running...", System.currentTimeMillis());

        //this.notifier.setLatestEventInfo(this, "WatchMyCar", "Your guide friend", this.buildIntent());	// TODO: Localize!!!!
        //this.notifier.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    private void handleCommand(Intent intent) {
        if (ACTION_START.equals(intent.getAction())) {
            Log.d(TAG, "\n\nhandleCommand() - START ACTION");
            this.init();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "\n\nhandleCommand() - STOP ACTION");
            this.stopAll(); 
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        this.stopAll();
        this.running = false;
	    super.onDestroy();
    }

    private void init() {
    	Log.d(TAG, "init()\n\n\n\n");

    	// Connect to the ARDUINO device
        if (!mBluetoothAdapter.isEnabled()) {
            this.mBTarduinoStatus = BT_DISABLED;
            this.notifyUser("Select to enable bluetooth.", "Must enable bluetooth.");
            return;
        }
        if (!this.connectKnownDevice()) {
    		this.mBTarduinoStatus = ARDUINO_NOT_CONFIGURED;
    		this.notifyUser("Select to configure ARDUINO device.", "ARDUINO device not configured.");
        	return;
        }
        this.startSensors();

        this.notifyUser("WatchMyCar is running.", "WatchMyCar is running...");
        this.running = true;
    }

    private void startSensors() {
        this.mMicMonitor = new MicrophoneMonitor(this, sensorMessages);
        this.mBumpMonitor = new BumpMonitor(this, sensorMessages);
        this.mAccelMonitor = new AccelerometerMonitor(this, sensorMessages);
    }

    private void stopSensors() {
        if (this.mMicMonitor != null) {
            this.mMicMonitor.stop(this);
        }
        if (this.mBumpMonitor != null) {
            this.mBumpMonitor.stop(this);
        }
        if (this.mAccelMonitor != null) {
            this.mAccelMonitor.stop(this);
        }
    }

	private void stopAll() {
        Log.d(TAG, "\n\n\n\nstopAll()\n\n\n\n");
        arduinoConnected = false;
        if (this.connector != null) {
        	this.connector.stop();
        }
        this.stopSensors();
        this.notifyUser("Stopped. Select to start again.", "Stopping WatchMyCar.");
		this.running = false;
    }

    private boolean connectKnownDevice() {
    	if (arduinoConnected) {
    		Log.d(TAG, "\n\n\n\n\n\nconnectDevice():: arduinoConnected says it is already connected!!!! Wrong?!?!?!");
    		return true;
    	}
        this.restoreState();
        if (this.arduinoBluetoothAddress != null && this.arduinoBluetoothAddress.length() > 0) {
            this.connectDevice(this.arduinoBluetoothAddress);
            return true;
        }
		return false;    	
    }

    private void connectDevice(String deviceAddress) {
    	this.mBTarduinoStatus = CONNECTING;
        if (this.connector == null) {
            this.connector = new BluetoothConnector(this, btHandler);
        }
        this.connector.connect(mBluetoothAdapter.getRemoteDevice(deviceAddress));
    }

    private void sendToDevice(String msg) {
        if (this.connector != null) {
            connector.write(msg.getBytes());
        }
    }

    private void restoreState() {
        // Restore state
        SharedPreferences state = PreferenceManager.getDefaultSharedPreferences(this);
        this.arduinoBluetoothAddress = state.getString("ArduinoBluetoothAddress", "00:15:FF:F4:0F:AF");
    }

    private void storeState() {
        // Persist state
        SharedPreferences state = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = state.edit();
        editor.putString("ArduinoBluetoothAddress", this.arduinoBluetoothAddress);
        editor.commit();
    }

    private PendingIntent buildIntent() {
        Intent intent = new Intent(this, WatchMyCar.class);

        //intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        return PendingIntent.getActivity(this, 0, intent, 0);
    }

    /**
     * Show a notification
     */
    private void notifyUser(String action, String alert) {
        CharSequence serviceName = "WatchMyCar";  //super.getText(R.string.service_name);
        CharSequence actionText = action;
        CharSequence notificationText = alert;
        //this.notifier = new Notification(R.drawable.ic_launcher, notificationText, System.currentTimeMillis());
        //this.notifier.setLatestEventInfo(this, serviceName, actionText, this.buildIntent());	// TODO: Localize!!!!
        //notifMgr.notify(ARDUINO_NOTIFICATIONS, this.notifier);
        //this.thumpthump();
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification(String action, String alert) {

        Intent toLaunch = new Intent(getApplicationContext(), WatchMyCar.class);

        toLaunch.setAction(Intent.ACTION_MAIN);
        toLaunch.addCategory(Intent.CATEGORY_LAUNCHER);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        toLaunch,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        CharSequence text = alert;
        CharSequence info = action;

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(text)
                        .setContentInfo(info);

        mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        mBuilder.setContentIntent(resultPendingIntent);
        mBuilder.setWhen(System.currentTimeMillis());
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);

        startForeground(1, mBuilder.build());

    }

    /**
     * Show a toast with the given text.
     *
     * @param toastText string to show (if null, nothing will be shown)
     */
    private void showToast(String toastText) {
        if (toastText != null) {

            this.thumpthump();

            Message msg = btHandler.obtainMessage(MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(TOAST, toastText);
            msg.setData(bundle);
            btHandler.sendMessage(msg);

        }
    }

    private void thumpthump() {
/*
    	if (this.mustVibrate) {
    		this.vibrator.vibrate(new long[]{50, 200, 50, 50, 500, 200, 50, 50, 500, 200, 50, 50, 500}, -1);
    	}
*/
    }

    // The Handler that gets information back from the BluetoothConnector
    private final Handler btHandler = new Handler() {
    	int counter = 0;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothConnector.STATE_CONNECTED:
                    Keeper.this.mBTarduinoStatus = ARDUINO_CONNECTED;
                    arduinoConnected = true;
                    notifyBTState();
                    break;
                case BluetoothConnector.STATE_CONNECTING:
                    Keeper.this.mBTarduinoStatus = CONNECTING;
                    notifyBTState();
                    break;
                case BluetoothConnector.STATE_FAILED:
                	Keeper.this.mBTarduinoStatus = ARDUINO_NOT_CONFIGURED;
                    notifyBTState();
                	break;
                case BluetoothConnector.STATE_LISTEN:
                case BluetoothConnector.STATE_NONE:
                    break;
                }
                break;
            case MESSAGE_READ:
                Log.d(TAG, "\n\nData received.");
                if (msg.arg1 > 0) {	// msg.arg1 contains the number of bytes read
                	//Log.d(TAG, "\tRead size: " + msg.arg1);
                    byte[] readBuf = (byte[]) msg.obj;
                    byte[] readBytes = new byte[msg.arg1];
                    System.arraycopy(readBuf, 0, readBytes, 0, msg.arg1);
                    Log.d(TAG, "\tAs Hex: " + asHex(readBytes));
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1).trim();
                    Log.d(TAG, "\tHere it is: " + readMessage);
                    if (readMessage.contains("T")) {
                        alert("Caçamba!");
                    }
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                arduinoBluetoothAddress = msg.getData().getString(DEVICE_ADRESS);
                storeState();
                showToast("Connected to " + mConnectedDeviceName);
                break;
            case MESSAGE_TOAST:
            	Keeper.this.toast.setText(msg.getData().getString(TOAST));
            	Keeper.this.toast.show();
                break;
            }
        }
    };

    private String asHex(byte[] buf) {
    	char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    	char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i) {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }

    private void notifyNotRunning() {
        if (activityHandler != null) {
        	try {
				activityHandler.send(Message.obtain(null, NOT_RUNNING, null));
			} catch (RemoteException e) {
				// Nothing to do
			}
        }
    }

    private void notifyBTState() {
        if (activityHandler != null) {
        	if (this.mBTarduinoStatus > NONE) {
        		Log.d(TAG, "notifyBTState() - " + BT_STATUS.values()[this.mBTarduinoStatus]);
        	}
        	try {
				activityHandler.send(Message.obtain(null, this.mBTarduinoStatus, null));
			} catch (RemoteException e) {
				// Nothing to do
			}
        } else {
        	Log.d(TAG, "notifyBTState() - NO Activity handler to receive!");
        }
    }

    private void notifyMsg(String txt) {
        if (activityHandler != null) {
            Message msg = Message.obtain(null, TEXT_MESSAGE);
            Bundle bundle = new Bundle();
            bundle.putString("Message", txt);
            msg.setData(bundle);
        	try {
				activityHandler.send(msg);
			} catch (RemoteException e) {
				// Nothing to do
			}
        } else {
        	Log.d(TAG, "notifyMsg() - NO Activity handler to receive!");
        }
    }

    /**
     * Handler of incoming messages from clients, i.e., WatchMyCar activity.
     */
    final Handler activityMessages = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "Received activity message: " + ACTION.values()[msg.what]);
            switch (msg.what) {
            case GET_ARDUINO_STATUS:
            	break;
            case CONNECT_TO:
            	String rcvdAddress = msg.getData().getString(TEXT_MSG);
            	Log.i(TAG, "Received address: " + rcvdAddress);
            	if (rcvdAddress == null || rcvdAddress.length() == 0 ) {
            		connectKnownDevice();
            	} else {
            		connectDevice(rcvdAddress);
            	}
            	break;
            case REGISTER_LISTENER:
            	break;
            case UNREGISTER_LISTENER:
            	activityHandler = null;
            	break;
            case REGISTER_HANDLER:
            	activityHandler = msg.replyTo;
            	// TODO: MUST notify if we are running already
/*            	if (!running) {
            		notifyNotRunning();
            		break;
            	}
*/            	notifyBTState();
            	break;
            case UNREGISTER_HANDLER:
            	activityHandler = null;
            	break;
            case ARM:
                armed = true;
                sendToDevice("A");
            	break;
            case DISARM:
                armed = false;
                sendToDevice("D");
                break;
            default:
            	break;
            }
        }
    };
    final Messenger activityMsgListener = new Messenger(activityMessages);

    /**
     * Handler of incoming messages from sensors
     */
    private final Handler sensorMessages = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "Received sensor message: " + TRIGGER.values()[msg.what]);
            if (!armed) {
                Log.i(TAG, "Not armed!!!");
                return;
            }
            switch (msg.what) {
                case ACCELEROMETER:
                    sendToDevice("G");
                    alert("Acelerometro!");
                    break;
                case CAMERA:
                    break;
                case MICROPHONE:
                    sendToDevice("G");
                    alert("Barulho!");
                    break;
                case PRESSURE:
                    break;
                case LIGHT:
                    break;
                case POWER:
                    break;
                case BUMP:
                    sendToDevice("G");
                    alert("Sacudida!");
                    break;
            default:
                break;
            }
        }
    };
    final Messenger sensorMsgListener = new Messenger(sensorMessages);

    private void alert(String msg) {
        long now = System.currentTimeMillis();
        if ((now - lastSMSsent) > MIN_SMS_INTERVAL) {
            lastSMSsent = now;
            StringBuilder message = new StringBuilder();
            message.append("Alerta WatchMyCar: ").append(msg);
            String txt = message.toString();
            SMSsender.sendSMS(MY_NUMBER, txt);
            SMSsender.sendSMS(HER_NUMBER, txt);
            notifyMsg(txt);
        }
    }
}
