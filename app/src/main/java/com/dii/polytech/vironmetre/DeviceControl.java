package com.dii.polytech.vironmetre;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.List;

public class DeviceControl extends AppCompatActivity {

    private final static String TAG = DeviceControl.class.getSimpleName();                          //Get name of activity to tag debug and warning messages

    public static final String EXTRAS_DEVICE_NAME =     "DEVICE_NAME";                              //Name passed by intent that lanched this activity
    public static final String EXTRAS_DEVICE_ADDRESS =  "DEVICE_ADDRESS";                           //MAC address passed by intent that lanched this activity

    public static final byte NOSENSOR =  0x00;
    public static final byte BMP085   =  0x77;
    public static final byte TSL2561   = 0x29;

    private static final String VIRONMETRE_SERVICE =                "00002000-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_SENSOR =             "00003000-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_REQUEST =            "00003001-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_RESPONSE =           "00003002-0000-1000-8000-00805f9b34fb";
    public static final String MLDP_DATA_PRIVATE_CHAR =             "00035b03-58e6-07dd-021a-08123a000301"; //Characteristic for MLDP Data, properties - notify, write
    public static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";	//Special UUID for descriptor needed to enable notifications

    private static final byte CMD_READ  = 0x52; // 'R'
    private static final byte CMD_WRITE = 0x57; // 'W'

    private BluetoothLeService mBluetoothLeService;                                                 //Service to handle BluetoothGatt connection to the RN4020 module
    private BluetoothGattCharacteristic mCharactRequest, mCharactResponse, mCharactSensor;          //The BLE characteristic used for Vironmetre data transfers

    private TextView mDeviceName = null;
    private TextView mSensorName = null;
    private TextView mConnectionState = null;                                                       //TextViews to show connection state
    private String incomingMessage;                                                                 //String to hold the incoming message from the Vironmetre characteristic
    private boolean mConnected = false;                                                             //Indicator of an active Bluetooth connection
    private String mDeviceAddress;                                                                  //String for the Bluetooth MAC address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);

        Log.d("Service",VIRONMETRE_SERVICE.substring(4,8));

        final Intent intent = getIntent();                                                          //Get the Intent that launched this activity
        String DeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);                              //Get the BLE device name from the Intent
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);                              //Get the BLE device MAC address from the Intent

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(DeviceName);                                                           //Set the title of the Toolbar to the name of the BLE device
            setSupportActionBar(toolbar);
        }

        mDeviceName = (TextView)findViewById(R.id.DeviceControl_TextViewSelectedDeviceName);
        if (mDeviceName != null) {
            mDeviceName.setText(DeviceName + " - " + mDeviceAddress);
        }

        mConnectionState = (TextView)findViewById(R.id.DeviceControl_TextViewConnectionState);
        if (mConnectionState != null) {
            mConnectionState.setText(R.string.device_control_disconnected);
        }

        mSensorName = (TextView)findViewById(R.id.DeviceControl_TextViewSensorName);
        if (mSensorName != null) {
            mSensorName.setText(R.string.device_control_no_sensor_detected);
        }

        incomingMessage = new String();                                                             //Create new string to hold incoming message data

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);                      //Create Intent to start the BluetoothLeService
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);                       //Create and bind the new service to mServiceConnection object that handles service connect and disconnect

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    // Register the GATT receiver
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());                        //Register broadcast receiver to handles events fired by the service: connected, disconnected, etc.
        if (mBluetoothLeService != null) {                                                          //Check that service is running
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);                     //Ask the service to connect to the GATT server hosted on the Bluetooth LE device
            Log.d(TAG, "Connect request result = " + result);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);                                                    //Activity paused so unregister the broadcast receiver
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);                                                          //Activity ending so unbind the service (this will end the service if no other activities are bound to it)
        mBluetoothLeService = null;                                                                 //Not bound to a service
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu_device_scan is different depending on whether connected or not
    // Show Connect option if not connected or show Disconnect option if we are connected
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_control, menu);                                //Show the Options menu_device_scan
        if (mConnected) {                                                                           //See if connected
            menu.findItem(R.id.menu_connect).setVisible(false);                                     // then dont show disconnect option
            menu.findItem(R.id.menu_disconnect).setVisible(true);                                   // and do show connect option
        }
        else {                                                                                      //If not connected
            menu.findItem(R.id.menu_connect).setVisible(true);                                      // then show connect option
            menu.findItem(R.id.menu_disconnect).setVisible(false);                                  // and don't show disconnect option
        }

        menu.findItem(R.id.menu_quit).setVisible(true);
        menu.findItem(R.id.menu_mode).setVisible(true);
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Connect or disconnect to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                                 //Get which menu_device_scan item was selected
            case R.id.menu_connect:                                                                 //Option to Connect chosen
                mBluetoothLeService.connect(mDeviceAddress);                                        //Call method to connect to our selected device
                return true;
            case R.id.menu_disconnect:                                                              //Option to Disconnect chosen
                mBluetoothLeService.disconnect();                                                   //Call method to disconnect
                return true;
            case R.id.menu_quit:                                                                    //Option to Quit chosen
                finish();                                                                           //End the app
                break;

//            /*Test IHM des capteurs*/
//            case R.id.menu_sensor_bmp085:
//            case R.id.menu_sensor_tsl2561:
//            case R.id.menu_sensor_adxl345:
//            case R.id.menu_sensor_hmc5883l:
//            case R.id.menu_sensor_tcs3414cs:
//                updateConnectedSensor((String) item.getTitle());
//                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {		            //Create new ServiceConnection interface to handle connection and disconnection

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {	            //Service connects
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();          //Get a link to the service
            if (!mBluetoothLeService.initialize()) {                                                //See if the service did not initialize properly
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();																            //End the application
            }
            mBluetoothLeService.connect(mDeviceAddress);                                            //Connects to the device selected and passed to us by the DeviceScan
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {                            //Service disconnects
            mBluetoothLeService = null;                                                             //Not bound to a service
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Intent filter to add Intent values that will be broadcast by the BluetoothLeService to the mGattUpdateReceiver BroadcastReceiver
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();                                       //Create intent filter for actions received by broadcast receiver
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITTEN);
        return intentFilter;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // BroadcastReceiver handles various events fired by the BluetoothLeService service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();                                               //Get the action that was broadcast by the intent that was received

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {                          //Service has connected to BLE device
                mConnected = true;                                                                  //Record the new connection state
                updateConnectionState(R.string.connected);                                          //Update the display to say "Connected"
                invalidateOptionsMenu();                                                            //Force the Options menu_device_scan to be regenerated to show the disconnect option
            }

            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {		            //Service has disconnected from BLE device
                mConnected = false;                                                                 //Record the new connection state
                updateConnectionState(R.string.disconnected);                                       //Update the display to say "Disconnected"
                invalidateOptionsMenu();                                                            //Force the Options menu_device_scan to be regenerated to show the connect option
            }

            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {           //Service has discovered GATT services on BLE device
                findVironmetreGattService(mBluetoothLeService.getSupportedGattServices()); 	            //Show all the supported services and characteristics on the user interface
            }

            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {                     //Service has found new data available on BLE device
                byte[] bytes = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);             //Get the value of the characteristic


                if(bytes[0] == 1)                                                                   //Sensor detected
                {
                    switch(bytes[1]){
                        case BMP085:
                            updateConnectedSensor(BMP085);
                            break;
                        case TSL2561:
                            updateConnectedSensor(TSL2561);
                            break;

                        default:
                            ViewFlipper ViewFlipper = (ViewFlipper)findViewById(R.id.DeviceControl_ViewFlipper);
                            ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_NoSensor)));
                            mSensorName.setText(R.string.device_control_unknown_sensor);

                            break;
                    }
            }

                else
                {
                    updateConnectedSensor(NOSENSOR);
                }
            }

            //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
            else if (BluetoothLeService.ACTION_DATA_WRITTEN.equals(action)) {			            //Service has found new data available on BLE device
            }
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    private void updateConnectedSensor(final byte sensorName) {

//        if (mSensorName != null) {
//            mSensorName.setText(sensorName);
//        }

        Typeface dSeg7 = Typeface.createFromAsset(getAssets(),"fonts/DSEG7Classic-Regular.ttf");

        ViewFlipper ViewFlipper = (ViewFlipper)findViewById(R.id.DeviceControl_ViewFlipper);
        if (ViewFlipper != null) {
//            if (mSensorName != null) {
                switch (sensorName){
                    case NOSENSOR:
                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_NoSensor)));
                        mSensorName.setText(R.string.device_control_no_sensor_detected);
                        break;

                    case BMP085:
                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_SensorBMP085)));
                        mSensorName.setText(R.string.sensor_bmp085);

                        TextView temperatureBMP085 = (TextView)findViewById(R.id.SensorBMP085_Temperature);
                        if (temperatureBMP085 != null) {
                            temperatureBMP085.setText("21.7");
                            temperatureBMP085.setTypeface(dSeg7);
                        }

                        TextView pressureBMP085 = (TextView)findViewById(R.id.SensorBMP085_Pressure);
                        if (pressureBMP085 != null) {
                            pressureBMP085.setText("1013");
                            pressureBMP085.setTypeface(dSeg7);
                        }
                        break;


//                    case "ADXL345":
//                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_SensorADXL345)));
//                        mSensorName.setText(R.string.sensor_adxl345);
//                        break;
//
//                    case "HMC5883L":
//                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_SensorHMC5883L)));
//                        mSensorName.setText(R.string.sensor_hmc5883l);
//                        break;
//
//                    case "TCS3414CS":
//                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_SensorTCS3414CS)));
//                        mSensorName.setText(R.string.sensor_tcs3414cs);
//                        break;
//
//                    case "TSL2561":
//                        ViewFlipper.setDisplayedChild(ViewFlipper.indexOfChild(findViewById(R.id.DeviceControl_SensorTSL2561)));
//                        mSensorName.setText(R.string.sensor_tsl2561);
//
//                        TextView luminosityTSL2561 = (TextView)findViewById(R.id.SensorTSL2561_Luminosity);
//                        if (luminosityTSL2561 != null) {
//                            luminosityTSL2561.setText("175");
//                            luminosityTSL2561.setTypeface(dSeg7);
//                        }
//
//                        break;
                    default:
                        break;
                }
//            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text with connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mConnected){
                    mConnectionState.setText(R.string.device_control_connected);
                } else{
                    mConnectionState.setText(R.string.device_control_disconnected);
                }
            }
        });
    }

    public boolean ReadBytes(byte[] readBuffer, byte length)
    {
        if (length == 0)
            length = (byte)readBuffer.length;

        ArrayList data = new ArrayList<>();
        data.add(CMD_READ);
        data.add(length);

        // Send info to microcontroller
        mCharactRequest.setValue(data.toString().getBytes());
        mBluetoothLeService.writeCharacteristic(mCharactRequest);

        return true;
    }

    public boolean WriteBytes(byte[] writeBuffer, byte length)
    {
        if(length == 0)
            length = (byte)writeBuffer.length;

        ArrayList data = new ArrayList<>();
        data.add(CMD_WRITE);
        data.add(writeBuffer);
        data.add(length);

        // Send info to microcontroller
        mCharactRequest.setValue(data.toString().getBytes());
        mBluetoothLeService.writeCharacteristic(mCharactRequest);

        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Iterate through the supported GATT Services/Characteristics to see if the MLDP service is supported
    private void findVironmetreGattService(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {                                                                         //Verify that list of GATT services is valid
            Log.d(TAG, "findVironmetreGattService found no Services");
            return;
        }
        mCharactSensor = null;
        String uuid;                                                                                        //String to compare received UUID with desired known UUIDs

        for (BluetoothGattService gattService : gattServices) {                                             //Test each service in the list of services
            uuid = gattService.getUuid().toString();                                                        //Get the string version of the service's UUID

            Log.d(TAG, "Found Gatt Service = " + uuid);

            if (uuid.equals(VIRONMETRE_SERVICE)) {                                                          //See if it matches the UUID of the Vironmetre service
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();   //If so then get the service's list of characteristics
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {                //Test each characteristic in the list of characteristics
                    uuid = gattCharacteristic.getUuid().toString();                                         //Get the string version of the characteristic's UUID
                    Log.d(TAG,"Characteristic = " + uuid);
                    if (uuid.equals(CHARACTERISTIC_REQUEST)) {                                              //See if it matches the UUID of the Vironmetre request characteristic
                        mCharactRequest = gattCharacteristic;                                                     //If so then save the reference to the characteristic
                        Log.d(TAG, "Found Vironmetre request characteristics");
                    }
                    else if (uuid.equals(CHARACTERISTIC_RESPONSE)) {                                            //See if UUID matches the UUID of the Vironmetre response characteristic
                        mCharactResponse = gattCharacteristic;                                                  //If so then save the reference to the characteristic
                        Log.d(TAG, "Found Vironmetre response characteristics");
                    }
                    else if (uuid.equals(CHARACTERISTIC_SENSOR)) {                                            //See if UUID matches the UUID of the Vironmetre sensor characteristic
                        mCharactSensor = gattCharacteristic;                                                  //If so then save the reference to the characteristic
                        Log.d(TAG, "Found Vironmetre sensor characteristics");
                    }
                    final int characteristicProperties = gattCharacteristic.getProperties();                             //Get the properties of the characteristic
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) {                //See if the characteristic has the Notify property
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);                     //If so then enable notification in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) {              //See if the characteristic has the Indicate property
                        mBluetoothLeService.setCharacteristicIndication(gattCharacteristic, true);                       //If so then enable notification (and indication) in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) {                //See if the characteristic has the Write (acknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);                //If so then set the write type (write with acknowledge) in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {    //See if the characteristic has the Write (unacknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);            //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                    }
                }
                break;
            }
        }
        if (mCharactSensor == null) {                                                                    //See if the Vironmetre data characteristic was not found
            Toast.makeText(this, R.string.vironmetre_not_supported, Toast.LENGTH_SHORT).show();          //If so then show an error message
            Log.d(TAG, "findVironmetreGattService found no Vironmetre service");
            //finish();                                                                                  //and end the activity
        }
    }

}
