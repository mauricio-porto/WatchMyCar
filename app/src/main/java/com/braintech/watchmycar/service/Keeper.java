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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.braintech.watchmycar.Constants;
import com.braintech.watchmycar.R;
import com.braintech.watchmycar.WatchMyCar;
import com.braintech.watchmycar.base.ApplicationPreferences;
import com.braintech.watchmycar.base.ConnectorCompanion;
import com.braintech.watchmycar.base.ConnectorRC;
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

    private ApplicationPreferences preferences = null;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    public static final String ACTION_START = "startService";
    public static final String ACTION_STOP = "stopService";

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

    // Bluetooth and COMPANION statuses
    public static final int NONE = -1;
    public static final int COMPANION_NOT_CONFIGURED = 0;
    public static final int BT_DISABLED = 1;
    public static final int COMPANION_CONNECTED = 2;
    public static final int CONNECTING = 3;
    public static final int COMPANION_DATA = 4;
    public static final int NOT_RUNNING = 5;
    public static final int TEXT_MESSAGE = 6;

    public static enum BT_STATUS {
        COMPANION_NOT_CONFIGURED,
    	BT_DISABLED,
        COMPANION_CONNECTED,
    	CONNECTING,
        COMPANION_DATA,
    	NOT_RUNNING,
        TEXT_MESSAGE
    }

    // Key names received
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADRESS = "device_address";
    public static final String TOAST = "toast";
    public static final String TEXT_MSG = "text";
    public static final String BOOL_MSG = "bool";

    // SMS data
    public static final String MY_NUMBER = "+5551993191979";
    public static final String HER_NUMBER = "+5551994151979";
    public static final long MIN_SMS_INTERVAL = 10 * 1000; // 10 seconds
    private long lastSMSsent = 0L;

    private Toast toast;

    private boolean running = false;
    private volatile boolean armed = false;
    private boolean usesCompanion;

    private int mBTcompanionStatus = NONE;

    private Messenger activityHandler = null;

    // MAC address of the COMPANION device
    private String companionBluetoothAddress = "";
    // Name of the connected device
    private String companionDeviceName = "";
    private boolean companionConnected = false;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    private ConnectorCompanion companionConnector;
    private ConnectorRC rcConnector;
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
        preferences = new ApplicationPreferences(getApplicationContext());

        if ((mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()) == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();   // TODO: localize!!!
            return;
        }

        this.toast = Toast.makeText(this, TAG, Toast.LENGTH_LONG);
        this.toast.setGravity(Gravity.CENTER, 0, 0);

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                switch (key) {
                    case ApplicationPreferences.KEY_CONFIG_MOVEMENT:
                        if (mAccelMonitor != null) {
                            mAccelMonitor.setSensivity(preferences.getAccelerometerSensitivity());
                        }
                        break;
                    case ApplicationPreferences.KEY_CONFIG_SOUND:
                        if (mMicMonitor != null) {
                            mMicMonitor.setSensivity(preferences.getSoundSensitivity());
                        }
                        break;
                    case ApplicationPreferences.KEY_SMS_NUMBER:
                        break;
                    case ApplicationPreferences.KEY_USE_ARDUINO:
                        usesCompanion = preferences.usesCompanion();
                        checkCompanion();
                        break;
                    default:
                        break;
                }
            }
        };
        preferences.getSharedPreferences().registerOnSharedPreferenceChangeListener(prefsListener);
        usesCompanion = preferences.usesCompanion();
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

    	checkCompanion();

        this.startSensors();
        this.running = true;
    }

    private void checkCompanion() {
        if (usesCompanion && this.mBTcompanionStatus != COMPANION_CONNECTED) {
            // Connect to the ARDUINO device

            if (!mBluetoothAdapter.isEnabled()) {
                this.mBTcompanionStatus = BT_DISABLED;
                return;
            }
            if (!this.connectKnownCompanion()) {
                this.mBTcompanionStatus = COMPANION_NOT_CONFIGURED;
                return;
            }
        } else if (this.mBTcompanionStatus == COMPANION_CONNECTED && !usesCompanion) {
            if (this.companionConnector != null) {
                this.companionConnector.stop();
            }
        }
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
        companionConnected = false;
        if (this.companionConnector != null) {
        	this.companionConnector.stop();
        }
        if (this.rcConnector != null) {
            this.rcConnector.stop();
        }
        this.stopSensors();
		this.running = false;
    }

    private boolean connectKnownCompanion() {
    	if (companionConnected) {
    		Log.d(TAG, "\n\n\n\n\n\nconnectCompanion():: companionConnected says it is already connected!!!! Wrong?!?!?!");
    		return true;
    	}
    	this.companionBluetoothAddress = preferences.getCompanionAddress();
        if (this.companionBluetoothAddress != null && this.companionBluetoothAddress.length() > 0) {
            this.connectCompanion(this.companionBluetoothAddress);
            return true;
        }
		return false;    	
    }

    private void connectCompanion(String deviceAddress) {
    	this.mBTcompanionStatus = CONNECTING;
        if (this.companionConnector == null) {
            this.companionConnector = new ConnectorCompanion(this, companionHandler);
        }
        this.companionConnector.connect(mBluetoothAdapter.getRemoteDevice(deviceAddress), true);
    }

    private void sendToCompanion(String msg) {
        if (this.companionConnector != null) {
            companionConnector.write(msg.getBytes());
        }
    }

    private PendingIntent buildIntent() {
        Intent intent = new Intent(this, WatchMyCar.class);

        //intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        return PendingIntent.getActivity(this, 0, intent, 0);
    }

    // The Handler that gets information back from the ConnectorRC
    private final Handler rcHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case ConnectorRC.STATE_CONNECTED:
                            Keeper.this.mBTcompanionStatus = COMPANION_CONNECTED;
                            companionConnected = true;
                            notifyBTState();
                            break;
                        case ConnectorRC.STATE_CONNECTING:
                            Keeper.this.mBTcompanionStatus = CONNECTING;
                            notifyBTState();
                            break;
                        case ConnectorRC.STATE_FAILED:
                            Keeper.this.mBTcompanionStatus = COMPANION_NOT_CONFIGURED;
                            notifyBTState();
                            break;
                        case ConnectorRC.STATE_LISTEN:
                        case ConnectorRC.STATE_NONE:
                            break;
                    }
                    break;
                case Constants.MESSAGE_READ:
                    Log.d(TAG, "\n\nData received.");
                    if (msg.arg1 > 0) {    // msg.arg1 contains the number of bytes read
                        //Log.d(TAG, "\tRead size: " + msg.arg1);
                        byte[] readBuf = (byte[]) msg.obj;
                        byte[] readBytes = new byte[msg.arg1];
                        System.arraycopy(readBuf, 0, readBytes, 0, msg.arg1);
                        Log.d(TAG, "\tAs Hex: " + asHex(readBytes));
                        // construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1).trim();
                        Log.d(TAG, "\tHere it is: " + readMessage);
                    }
                default:
                    break;
            }
        }
    };

    // The Handler that gets information back from the ConnectorCompanion
    private final Handler companionHandler = new Handler() {
    	int counter = 0;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case Constants.MESSAGE_STATE_CHANGE:
                Log.i(TAG, "MESSAGE_STATE_CHANGE: " +ConnectorCompanion.STATES.values()[msg.arg1]);
                switch (msg.arg1) {
                case ConnectorCompanion.STATE_CONNECTED:
                    Keeper.this.mBTcompanionStatus = COMPANION_CONNECTED;
                    companionConnected = true;
                    notifyBTState();
                    break;
                case ConnectorCompanion.STATE_CONNECTING:
                    Keeper.this.mBTcompanionStatus = CONNECTING;
                    notifyBTState();
                    break;
                case ConnectorCompanion.STATE_FAILED:
                case ConnectorCompanion.STATE_LISTEN:
                case ConnectorCompanion.STATE_NONE:
                    Keeper.this.mBTcompanionStatus = COMPANION_NOT_CONFIGURED;
                    notifyBTState();
                    break;
                }
                break;
            case Constants.MESSAGE_READ:
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
            case Constants.MESSAGE_DEVICE_NAME:
                // show the connected device's name
                companionDeviceName = msg.getData().getString(Constants.COMPANION_DEVICE_NAME);
                Keeper.this.toast.setText("Connected to " + companionDeviceName);
                Keeper.this.toast.show();
                break;
            case Constants.MESSAGE_DEVICE_ADDRESS:
                // save the connected device's address
                companionBluetoothAddress = msg.getData().getString(Constants.COMPANION_DEVICE_ADDRESS);
                preferences.setCompanionAddress(companionBluetoothAddress);
                break;
            case Constants.MESSAGE_TOAST:
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
        	if (this.mBTcompanionStatus > NONE) {
        		Log.d(TAG, "notifyBTState() - " + BT_STATUS.values()[this.mBTcompanionStatus]);
        	}
        	try {
				activityHandler.send(Message.obtain(null, this.mBTcompanionStatus, null));
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
            		connectKnownCompanion();
            	} else {
            		connectCompanion(rcvdAddress);
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
                sendToCompanion("A");
            	break;
            case DISARM:
                armed = false;
                sendToCompanion("D");
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
                    sendToCompanion("G");
                    alert("Movimento!");
                    break;
                case CAMERA:
                    break;
                case MICROPHONE:
                    sendToCompanion("G");
                    alert("Barulho!");
                    break;
                case PRESSURE:
                    break;
                case LIGHT:
                    break;
                case POWER:
                    break;
                case BUMP:
                    sendToCompanion("G");
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
