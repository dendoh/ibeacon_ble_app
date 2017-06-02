package com.example.jimtseng.ibeacon_ble_app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.util.TimeUtils;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttService;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.lang.StringBuilder;
import java.lang.String;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_COARSE = 2528;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters = new ArrayList<>();
    private BluetoothGatt mGatt;
    private Handler mHandler;
    private ScanResult SharedScanResult;
    final String TAG = "iBeaconBLE";
    String topic = "AirSensor";
    private String clientID = MqttClient.generateClientId();
    private MqttAndroidClient client;
    private TextView LogTextView;
    private ScanFilter beacon_filter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LogTextView = (TextView)findViewById(R.id.logtextid);
        LogTextView.setText("Start logging\n");
        mHandler = new Handler();
        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_CODE_COARSE);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        Log.d(TAG, "getting BT adapter....");
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        Resources res = getResources();
        String beacons_array[] = res.getStringArray(R.array.beacons);
        for (String beacon : beacons_array) {
            beacon_filter = new ScanFilter.Builder().setDeviceAddress(beacon).build();
            if (mBluetoothAdapter.checkBluetoothAddress(beacon)) {
                Log.d(TAG, "valid beacon addr");
            } else {
                Log.d(TAG, "invalid beacon addr");
            }
            Log.d(TAG, "Adding filter<" + beacon + ">");
            filters.add(beacon_filter);
        }
        try {
            client = new MqttAndroidClient(this.getApplicationContext(), "tcp://192.168.1.105:1883", clientID);
            IMqttToken token = client.connect();
            Log.d(TAG, "connecting mqtt broker");
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    Log.d(TAG, "onSuccess");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.d(TAG, "onFailure");

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.d(TAG,"connect() has exception");
        }
    }
    @Override
    protected void onStart(){
        super.onStart();
        Log.d(TAG,"onStart...");
       // scanLeDevice(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "BT is not enabled, requesting to enable");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            finish();
        } else {
            if(Build.VERSION.SDK_INT >= 23) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    scanLeDevice(true);
                }
            } else {
                scanLeDevice(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mBluetoothAdapter!=null&&mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        client.close();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
              if(requestCode == 1 && resultCode == Activity.RESULT_CANCELED) {
                  Log.d(TAG, "BT cannot be enabled");
                  finish();
                  return;
              }
    }
    private void scanLeDevice(boolean enable) {
        Log.d(TAG, "scanLeDevice enable="+enable);
        if(enable){
            Log.d(TAG, "Starting scan");
            mLEScanner.startScan(filters,settings,mLEScanCallback_new);
           // mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.d(TAG, "Stopping scan");
            mLEScanner.stopScan(mLEScanCallback_new);
           // mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

private ScanCallback mLEScanCallback_new = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        SharedScanResult = result;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String payload_for_log = String.format(
                        "[TimeTAG]" + SharedScanResult.getTimestampNanos()
                                +" BLEDevice:" + SharedScanResult.getDevice().toString()
                                + " Record:" + bytesToHex(SharedScanResult.getScanRecord().getBytes())
                                + " rssi:" + SharedScanResult.getRssi()
                                + "\n");
                String payload_for_mqtt = String.format(
                        SharedScanResult.getTimestampNanos()
                                +"," + SharedScanResult.getDevice().toString()
                                + "," + bytesToHex(SharedScanResult.getScanRecord().getBytes())
                                + "," + SharedScanResult.getRssi());
                topic = "AirSensor/0"; //ToDo: Use paired atmotube MAC ID as topic
                Log.d(TAG, payload_for_log);
                LogTextView.append(payload_for_log);
                try {
                    MqttMessage message = new MqttMessage(payload_for_mqtt.getBytes());
                    if(client.isConnected()) {
                        client.publish(topic,payload_for_mqtt.getBytes(),0,false);
                        Log.d(TAG, "Sent payload");
                    }
                } catch (MqttException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Error sending payload");

                }
            }
        });
      //  Log.d(TAG, "BLEDevice:" + result.getDevice().toString() + " Record:" + bytesToHex(result.getScanRecord().getBytes()) + " rssi:" + result.getRssi());
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        for (ScanResult sr : results) {
            Log.d(TAG, "[Batch]BLEDevice:" + sr.getDevice().toString() + " Record:" + bytesToHex(sr.getScanRecord().getBytes()) + " rssi:" + sr.getRssi());
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        Log.e(TAG,"Scan error code:"+errorCode);
    }
};

    private static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("0x%02x-", b));
        }
        return builder.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
            Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }
    }
}
