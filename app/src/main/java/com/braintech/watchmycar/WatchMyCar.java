package com.braintech.watchmycar;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.braintech.watchmycar.service.Keeper;

public class WatchMyCar extends AppCompatActivity {

    private static final String TAG = WatchMyCar.class.getSimpleName();

	// Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    private boolean isConfigured = false;

    private Keeper keeper;

    private boolean hasPermissions = false;
    private boolean receiverSvcConnected = false;
    private boolean isBound = false;
    private boolean serviceRunning = false;
    private Messenger messageReceiver = null;

    private static final int PERMISSION_RECORD_AUDIO = 0;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_START_SERVICE = 3;

    private TextView mTextMessage;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_my_car);

		// Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();   // TODO: localize!!!
            finish();
            return;
        }

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if (ContextCompat.checkSelfPermission(WatchMyCar.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(WatchMyCar.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_RECORD_AUDIO);
            return;
        }

        this.startKeeper();
    }


     @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_RECORD_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    this.hasPermissions = true;
                    this.startKeeper();
                } else {
                    // Permission denied
                    Toast.makeText(this, "\uD83D\uDE41", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        //this.stopKeeper();
        super.onDestroy();
    }

	private void startKeeper() {
		Log.d(TAG, "\t\t\t\t\tWILL START!!!!");
		Intent intent = new Intent(Keeper.ACTION_START);
		intent.setClass(this, Keeper.class);
		startService(intent);
    }

    private void stopKeeper() {
        Log.d(TAG, "\t\t\t\t\tWILL STOP!!!!");
        Intent intent = new Intent(Keeper.ACTION_STOP);
        intent.setClass(this, Keeper.class);
        stopService(intent);
    }

        @Override
    protected void onResume() {
        super.onResume();
        if (!this.isBound) {
            Intent serviceIntent = new Intent(this, Keeper.class);
        	this.isBound = this.bindService(serviceIntent, this.btReceiverConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unbindKeeper();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	String[] results = {"OK","CANCELED","FIRST_USER"};
        Log.d(TAG, "onActivityResult with code: " + ((resultCode < 2)?results[1+resultCode]:"User defined"));
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When BluetoothDeviceList returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras().getString(BluetoothDeviceList.EXTRA_DEVICE_ADDRESS);
                // Attempt to connect to the device
                Log.d(TAG, "\n\n\n\nonActivityResult() - O ENDERECO DO DEVICE EH: " + address + " e receciverSvcConnected diz: " + this.receiverSvcConnected + "\n\n\n\n");
            	if (address != null) {
            		this.sendTextToService(Keeper.CONNECT_TO, address);
            	}
            	break;
            }
            // User did not enable Bluetooth or an error occurred
            Log.d(TAG, "\t\t\tHRM selection failed. Giving up...");
            Toast.makeText(this, R.string.none_paired, Toast.LENGTH_SHORT).show();
            finish();
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so attempt to connect a device
            	this.sendTextToService(Keeper.CONNECT_TO, null);
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        }
    }

    /**
     * Handler of incoming messages from Keeper.
     */
   final Handler serviceMessages = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	if (msg.what < 0) {
        		return;
        	}
            Log.i(TAG, "Received message: " + Keeper.BT_STATUS.values()[msg.what]);
            switch (msg.what) {
            case Keeper.ARDUINO_DATA:
            	break;
            case Keeper.BT_DISABLED:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                break;
            case Keeper.ARDUINO_NOT_CONFIGURED:
                // Launch the BluetoothDeviceList to see devices and do scan
                //Intent serverIntent = new Intent(GuideDroid.this, BluetoothDeviceList.class);
                //startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            	break;
            case Keeper.ARDUINO_CONNECTED:
            	break;
            case Keeper.CONNECTING:
                //Toast.makeText(GuideDroid.this, R.string.title_connecting, Toast.LENGTH_SHORT).show();
            	break;
            case Keeper.NOT_RUNNING:
            	serviceRunning = false;
            	//startActivityForResult(new Intent().setClass(CardioTalk.this, Controller.class), REQUEST_START_SERVICE);
            	break;
            default:
            	break;
            }
        }
    };
    final Messenger serviceMsgReceiver = new Messenger(serviceMessages);

    private void sendTextToService(int what, String text) {
        if (messageReceiver != null) {
            Message msg = Message.obtain(null, what);
            Bundle bundle = new Bundle();
            bundle.putString(Keeper.TEXT_MSG, text);
            msg.setData(bundle);
        	try {
				messageReceiver.send(msg);
			} catch (RemoteException e) {
				// Nothing to do
			}
        } else {
        	Log.d(TAG, "sendTextToService() - NO Service handler to receive!");
        }
    }

    private ServiceConnection btReceiverConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Keeper connected");
            if (service == null) {
            	Log.e(TAG, "Connection to the Keeper service failed. Giving up...");
            	return;
            }
        	receiverSvcConnected = true;

        	messageReceiver = new Messenger(service);
            try {
                Message msg = Message.obtain(null, Keeper.REGISTER_HANDLER);
                msg.replyTo = serviceMsgReceiver;
                messageReceiver.send(msg);
            } catch (RemoteException e) {
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Keeper disconnected");
            receiverSvcConnected = false;
        }

    };

    private void unbindKeeper() {
    	Log.d(TAG, "unbindBluetoothReceiver() - supposing it is bound");
    	if (this.isBound) {
            if (messageReceiver  != null) {
                try {
                    Message msg = Message.obtain(null, Keeper.UNREGISTER_HANDLER);
                    msg.replyTo = serviceMsgReceiver;
                    messageReceiver.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            this.unbindService(btReceiverConnection);
    	} else {
    		Log.d(TAG, "unbindHRMReceiver() - \tBut it was not!!!");
    	}
    	this.receiverSvcConnected = false;
    	this.isBound = false;
    }
}
