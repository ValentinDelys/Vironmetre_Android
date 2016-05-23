package com.dii.polytech.vironmetre;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceScan extends AppCompatActivity {
    private final static String TAG = DeviceScan.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;                                                     //BluetoothAdapter represents the radio in the Smartphone
    private boolean mScanning;                                                                      //Keep track of whether there is a scan in progress
    private Handler mHandler;                                                                       //Handler used to stop scanning after time delay
    private static final int REQUEST_ENABLE_BT = 1;                                                 //Constant to identify response from Activity that enables Bluetooth
    private static final long SCAN_PERIOD = 10000;                                                  //Length of time in milliseconds to scan for BLE devices

    private ArrayList mLeDevicesList = null;                                                        //List adapter to hold list of BLE devices from a scan
    private ArrayAdapter mLeDevicesListAdapter = null;

    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHandler = new Handler();                                                                   //Create Handler to stop scanning

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {           //Check if BLE is supported
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();                 //Message that BLE not supported
            finish();                                                                               //End the app
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);   //Get the BluetoothManager
        mBluetoothAdapter = bluetoothManager.getAdapter();                                                          //Get a reference to the BluetoothAdapter (radio)

        if (mBluetoothAdapter == null) {                                                            //Check if we got the BluetoothAdapter
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();            //Message that Bluetooth not supported
            finish();                                                                               //End the app
        }
        else {
            // Register the BroadcastReceiver
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, filter);
        }

        //Instantiate LE devices list view, list and list adapter
        ListView mLeDevicesListView = (ListView)findViewById(R.id.DeviceScan_ListViewLeDevices);
        mLeDevicesList = new ArrayList();
        mLeDevicesListAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, mLeDevicesList);

        if (mLeDevicesListView != null) {
            mLeDevicesListView.setAdapter(mLeDevicesListAdapter);                                   //Set adapter list view

            mLeDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {       // Device selected in list adapter
                @Override
                // Start DeviceControl and pass the BLE device name and address to the activity
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    // Get the device MAC address, the last 17 chars in the View
                    String info = ((TextView) view).getText().toString();
                    String address = info.substring(info.length() - 17);

                    final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);      // Get the Bluetooth device from the Bluetooth adapter

                    if (!(device == null)) {                                                        // Ignore if device is not valid
                        final Intent intent = new Intent(DeviceScan.this, DeviceControl.class);     //Create Intent to start the DeviceControl
                        intent.putExtra(DeviceControl.EXTRAS_DEVICE_NAME, device.getName());        //Add BLE device name to the intent (for info, not needed)
                        intent.putExtra(DeviceControl.EXTRAS_DEVICE_ADDRESS, device.getAddress());  //Add BLE device address to the intent
                        if (mScanning) {                                                            //See if still scanning
                            scanDevice(false);                                                      //Stop the scan in progress
                        }
                        startActivity(intent);                                                      //Start the DeviceControl
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        if (mScanning) {                                                                            //See if still scanning
            scanDevice(false);                                                                      //Stop the scan in progress
        }
        unregisterReceiver(receiver);                                                               // Unregister the BroadcastReceiver
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu_device_scan; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_scan, menu);
        menu.findItem(R.id.menu_quit).setVisible(true);

        if(!mScanning) {
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_stop).setVisible(false);
        }
        else {
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_stop).setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {                                                                 //Get which menu_device_scan item was selected
            case R.id.menu_scan:                                                                    //Option to Scan chosen
                mLeDevicesListAdapter.clear();                                                      //Clear list of BLE devices found
                scanDevice(true);                                                                   //Start scanning
                break;
            case R.id.menu_stop:                                                                    //Option to Stop scanning chosen
                scanDevice(false);                                                                  //Stop the scan in progress
                break;
            case R.id.menu_quit:                                                                    //Option to Quit chosen
                if (mScanning) {                                                                    //See if still scanning
                    scanDevice(false);                                                              //Stop the scan in progress
                }
                finish();                                                                           //End the app
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    // Enable BT if not already enabled, initialize list of BLE devices, start scan for BLE devices
    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {                                                       //Check if BT is not enabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);             //Create an intent to get permission to enable BT
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);                              //Fire the intent to start the activity that will return a result based on user response
        }

        scanDevice(true);                                                                           //Start scanning for BLE devices
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    // Stop scan and clear device list
    @Override
    protected void onPause() {
        super.onPause();
        scanDevice(false);                                                                          //Stop scanning for BLE devices
        mLeDevicesListAdapter.clear();                                                              //Clear the list of BLE devices found during the scan
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Test response from request to enable BT adapter in case user did not enable
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {           //User chose not to enable Bluetooth.
            finish();                                                                               //Destroy the activity - end the application
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);                                      //Pass the activity result up to the parent method
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Scan for Bluetooth device for SCAN_PERIOD milliseconds.
    // The mLeScanCallback method is called each time a device is found during the scan
    private void scanDevice(final boolean enable) {
        if (enable) {                                                                               //Method was called with option to start scanning
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {                                                   //Create delayed runnable that will stop the scan when it runs after SCAN_PERIOD milliseconds
                @Override
                public void run() {
                    mScanning = false;                                                              //Indicate that we are not scanning - used for menu_device_scan Stop/Scan context
                    mBluetoothAdapter.cancelDiscovery();                                            //Stop scanning
                    invalidateOptionsMenu();                                                        //Indicate that the options menu_device_scan has changed, so should be recreated.
                }
            }, SCAN_PERIOD);

            mScanning = true;                                                                       //Indicate that we are busy scanning - used for menu_device_scan Stop/Scan context
            mBluetoothAdapter.startDiscovery();                                                     //Start scanning
            Toast.makeText(this, R.string.scan_in_progress, Toast.LENGTH_SHORT).show();             //Message scan in progress
        }
        else {                                                                                      //Method was called with option to stop scanning
            mScanning = false;                                                                      //Indicate that we are not scanning - used for menu_device_scan Stop/Scan context
            mBluetoothAdapter.cancelDiscovery();                                                    //Stop scanning
        }

        invalidateOptionsMenu();                                                                    //Indicate that the options menu_device_scan has changed, so should be recreated.
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if(!mLeDevicesList.contains(device.getName() + "\n" + device.getAddress())){        //First check that it is a new device not in the list
                    Log.d(TAG, "Found Bluetooth device: "
                            + device.getName() + " - " + device.getAddress());                      //Debug information to log the devices as they are found
                    mLeDevicesListAdapter.add(device.getName() + "\n" + device.getAddress());       //Add the device to the list adapter that will show all the available devices
                    mLeDevicesListAdapter.notifyDataSetChanged();                                   //Tell the list adapter that it needs to refresh the view
                }
            }
        }
    };
}
