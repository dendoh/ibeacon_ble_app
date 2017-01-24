package com.example.jimtseng.ibeacon_ble_app;

import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttService;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.lang.StringBuilder;
import java.lang.String;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    final String TAG = "iBeaconBLE";
    final String topic = "test";
    private String clientID = MqttClient.generateClientId();
    private MqttAndroidClient client = new MqttAndroidClient(MainActivity.this, "tcp://192.168.1.238:1883", clientID);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        Log.d(TAG, "getting BT adapter....");
        mBluetoothAdapter = bluetoothManager.getAdapter();
        Log.d(TAG, "connecting mqtt broker");
        try {
            IMqttToken token = client.connect();
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
        }
    }
    @Override
    protected void onStart(){
        super.onStart();
        Log.d(TAG,"onStart...");
        scanLeDevice(true);
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG,"onResume...");
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "BT is not enabled, requesting to enable");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        scanLeDevice(true);
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
      //      Log.d(TAG,"Scheduling stopscan");
      //      mHandler.postDelayed(new Runnable() {
      //          @Override
      //          public void run() {
      //              Log.d(TAG,"Delayed Stopping scan");
      //              mBluetoothAdapter.stopLeScan(mLeScanCallback);

      //          }
      //      }, 10000);
            Log.d(TAG, "Starting scan");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.d(TAG, "Stopping scan");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "Found device " + device.toString() + "," + "rssi= "+rssi);
            Log.d(TAG, "scanRecord:" + bytesToHex(scanRecord));
            try {
                String payload = "Device:" + device.toString() + "payload:" + bytesToHex(scanRecord);
                MqttMessage message = new MqttMessage("test payload".getBytes());
                client.publish(topic, message);
                Log.d(TAG, "Sent payload");
            } catch (MqttException e) {
                e.printStackTrace();
                Log.d(TAG, "Error sending payload");

            }
        }
    };

    private static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
