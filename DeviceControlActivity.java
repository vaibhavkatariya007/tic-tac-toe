/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */

public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    public static TextView mDataField;
    private TextView mAtrValue;
    private TextView mDeviceAvail;
    private TextView mUIDValue;
    private TextView mDataValue;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private EditText mWriteBox;
    private EditText mWriteKey;
    private Button btnPowerON;
    private Button btnPowerOFF;
    private Button btnGetATR;
    private Button btnGetATS;
    private Button btnGetUID;
    private Button btnSetKey;
    private Button btnClearUI;
    private Button btnTransmit;

    public static String flag;
    private static Boolean enableFlag = false;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            try {
                if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                    mConnected = true;
                    updateConnectionState(R.string.connected);
                    invalidateOptionsMenu();
                } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                    mConnected = false;
                    flag = "powerOFF";
                    updateConnectionState(R.string.disconnected);
                    invalidateOptionsMenu();
                    clearUI();
                } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                    // Show all the supported services and characteristics on the user interface.
                    //displayGattServices(mBluetoothLeService.getSupportedGattServices());
                } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                    displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                }
            }
            catch (Exception e){

                Log.w(TAG,e.getMessage());
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };


    private void clearUI() {
        //mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.button_control);

        mUIDValue = (TextView) findViewById(R.id.data_UID);
        mDataValue	 = (TextView) findViewById(R.id.data_value);
        mWriteBox = (EditText) findViewById(R.id.setCommand);
        mWriteKey = (EditText) findViewById(R.id.setKey);
        btnPowerON = (Button) findViewById(R.id.power_on);
        btnPowerOFF = (Button) findViewById(R.id.power_off);
        btnGetATR = (Button) findViewById(R.id.btn_atr);
        btnGetATS = (Button) findViewById(R.id.btn_ats);
        btnGetUID = (Button) findViewById(R.id.btn_UID);
        btnSetKey = (Button) findViewById(R.id.btn_setKey);
        btnClearUI = (Button) findViewById(R.id.btn_clear);
        btnTransmit = (Button) findViewById(R.id.btn_transmit);
        mWriteKey.setEnabled(false);
        mWriteBox.setEnabled(false);
        btnPowerON.setEnabled(true);
        btnPowerOFF.setEnabled(false);
        btnGetATR.setEnabled(false);
        btnGetATS.setEnabled(false);
        btnGetUID.setEnabled(false);
        btnSetKey.setEnabled(false);
        btnClearUI.setEnabled(false);
        btnTransmit.setEnabled(false);
        //changeTextPowerOFF();

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);


        // Sets up UI references.
        /*
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
    mConnectionState = (TextView) findViewById(R.id.connection_state);
    */

        mAtrValue = (TextView) findViewById(R.id.data_atr);
        mDeviceAvail = (TextView) findViewById(R.id.card_presence);
        mDeviceAvail.setText("Smart Card is absent");
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            //enable ble notification
            if (mBluetoothLeService != null) {
                enableFlag = true;
                //mDataField = (TextView) findViewById(R.id.card_presence);
                mBluetoothLeService.setCharacteristicNotification(true);
            }
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            flag="powerOFF";
            //enable ble notification
            if (mBluetoothLeService != null) {
                mBluetoothLeService.setCharacteristicNotification(true);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mConnectionState.setText(resourceId);
            }
        });
    }


    private void displayData(String data) {

          if (data != null) {
            if(data.length()<=494){
                displayFinalPartData1(data);
            }
            else{
                String finalData = getFinalString(data);
                displayFinalData(finalData);
               // displayFinalData(data);
            }

          }
    }

    public static String getFinalString(String s) {
        int len = s.length();

        //int k=len-494;
        String str="";
        char[] finalCharArr;
        char[] charArray = s.toCharArray();
            if(len >=494) {
                finalCharArr = new char[len - 247];
            }
            else{
                finalCharArr = new char[len];

            }
        int j=0;
        for(int i=248;i<len;i++){
            finalCharArr[j]=charArray[i];
            j++;

        }

        for (Character c : finalCharArr)
            str += c.toString();

        return str;
    }

    private void displayFinalPartData1(String data) {
        if(flag != null){
            if(flag == "powerON"){
                changeTextPowerON();
            }
            else if(flag == "powerOFF"){
                changeTextPowerOFF();
            }
            else{
                mDataField.setText(data);
            }
        }

    }

    private void displayFinalData(String data) {
        int b0 = Integer.parseInt(data.substring(0,2));
        int b1=Integer.parseInt(data.substring(3,5));
        int b2=Integer.parseInt(data.substring(6,8));
        int b3=Integer.parseInt(data.substring(9,11));
        int b4=Integer.parseInt(data.substring(12,14));

        if(b0== 80 || b0==81){
            if(b1 == 00){
                //error
            }
            else {
                if(b2==00){

                   String finalString =  data.substring(15, 15+((b4*2)+(b4)));

                   if(flag != null){
                       if(flag == "powerON"){
                           changeTextPowerON();
                       }
                       else if(flag == "powerOFF"){
                           changeTextPowerOFF();
                       }
                       else{
                           mDataField.setText(finalString);
                       }
                   }
                }
                else{
                    if(b3==00){
                        //error
                    }
                }
            }
        }else{
            displayFinalPartData1(data);
        }

        }


    private String[] getStringArray(String data) {
        String[] s= new String[247];
        int j=0;
        char[] tempArr = data.toCharArray();
        for (int i=0;i<data.length();i+=3){
            s[j]= String.valueOf(tempArr[i]+tempArr[i+1]);
            j++;
        }
        return s;
    }

    private byte[] updatedByes(int len, byte[] b) {
        byte [] b1= new byte[len];
        int i;
        int j=0;
        for(i=5;i<len+5;i++){
            b1[j]=b[i];
            j++;
        }
        return  b1;
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClickWrite(View v) {
        if (mBluetoothLeService != null) {
            if (!TextUtils.isEmpty(mWriteBox.getText().toString().trim())) {
                mDataField = (TextView) findViewById(R.id.data_value);
                String strData = mWriteBox.getText().toString().trim();
                int len=strData.length();
                byte [] b;
                if(len % 2 == 1){
                    String newStr =  updatedOddString(strData);
                    b=hexStringToByteArray(newStr);
                }
                else{
                    b=hexStringToByteArray(strData);
                }

                mBluetoothLeService.writeCustomCharacteristic(b);
            } else {
                Toast.makeText(DeviceControlActivity.this, "Please enter a value", Toast.LENGTH_SHORT).show();
            }

        }
    }

    public void onClickWriteKey(View v) {
        if (mBluetoothLeService != null) {
            String strData = mWriteKey.getText().toString().trim();
            if (!TextUtils.isEmpty(strData)) {


                int len=strData.length();
                byte [] b;
                if(len % 2 == 1){
                    String newStr =  updatedOddString(strData);
                    b=hexStringToByteArray(newStr);
                }
                else{
                    b=hexStringToByteArray(strData);
                }

                mBluetoothLeService.writeCustomCharacteristic(b);
            } else {
                Toast.makeText(DeviceControlActivity.this, "Please enter a value", Toast.LENGTH_SHORT).show();
            }

        }
    }

    public static String updatedOddString(String s) {
        int len = s.length();
        String str="";

        char[] charArray = s.toCharArray();
        Character x = charArray[len-1];
        charArray[len -1]='0';

        for (Character c : charArray)
            str += c.toString();

        str +=x;
        return str;
    }


    public void onClickRead(View v) {
        mDataField = (TextView) findViewById(R.id.data_value);
        if (mBluetoothLeService != null) {
            mBluetoothLeService.readCustomCharacteristic();
        }
    }

    public void onClickClearResponse(View v){
        mDataField = (TextView) findViewById(R.id.data_value);
        mDataField.setText(R.string.no_data);
    }

    public void onClickNotified(View v){
        mDataField = (TextView) findViewById(R.id.data_value);
        if (mBluetoothLeService != null) {
            mBluetoothLeService.setCharacteristicNotification(true);
        }
    }

    public  void onPowerOFF(View v){
        if (mBluetoothLeService != null) {
            flag="powerOFF";
            byte [] b = hexStringToByteArray("630000");

          mBluetoothLeService.writeCustomCharacteristic(b);
              }
    }

    public void onPowerON(View v){
        if (mBluetoothLeService != null) {
            flag="powerON";
            byte [] b = hexStringToByteArray("620000");
            //mBluetoothLeService.setCharacteristicNotification(true);
            mBluetoothLeService.writeCustomCharacteristic(b);



        }
    }

    public void getATR(View v){
        flag= "write";
        if (mBluetoothLeService != null) {
            mDataField = (TextView) findViewById(R.id.data_atr);
            byte [] b = hexStringToByteArray("610000");
           // mBluetoothLeService.setCharacteristicNotification(true);
            mBluetoothLeService.writeCustomCharacteristic(b);
        }
    }

    public void getATS(View v){
        flag= "write";
        if (mBluetoothLeService != null) {
            mDataField = (TextView) findViewById(R.id.data_atr);
            byte [] b = hexStringToByteArray("640000");
            mBluetoothLeService.writeCustomCharacteristic(b);
        }
    }

    public void getUID(View v){
        flag= "write";
        if (mBluetoothLeService != null) {
            mDataField = (TextView) findViewById(R.id.data_UID);
            byte [] b = hexStringToByteArray("650000");
            mBluetoothLeService.writeCustomCharacteristic(b);
        }
    }

    public void onClickClear(View v){

        mDataValue.setText("");
        mWriteBox.setText("");
    }


    public void changeTextPowerON()
    {
        mDeviceAvail.setText("Smart Card is present");
        mWriteKey.setEnabled(true);
        mWriteBox.setEnabled(true);
        btnPowerON.setEnabled(false);
        btnPowerOFF.setEnabled(true);
        btnGetATR.setEnabled(true);
        btnGetATS.setEnabled(true);
        btnGetUID.setEnabled(true);
        btnSetKey.setEnabled(true);
        btnClearUI.setEnabled(true);
        btnTransmit.setEnabled(true);
    }

    public void changeTextPowerOFF()
    {
        mDeviceAvail.setText("Smart Card is absent");
        mWriteKey.setEnabled(false);
        mWriteBox.setEnabled(false);
        btnPowerON.setEnabled(true);
        btnPowerOFF.setEnabled(false);
        btnGetATR.setEnabled(false);
        btnGetATS.setEnabled(false);
        btnGetUID.setEnabled(false);
        btnSetKey.setEnabled(false);
        btnClearUI.setEnabled(false);
        btnTransmit.setEnabled(false);
        mUIDValue.setText("");
        mDataValue.setText("");
        mWriteBox.setText("");
        mWriteKey.setText("");
        mAtrValue.setText("");

       // mBluetoothLeService.setCharacteristicNotification(false);

    }


    public static byte[] hexStringToByteArrayDisplay(String s) {
        int len = s.length();

       int k = len-494;

        byte[] data = new byte[247];
        int y=0,j=0;
        for(int i = k; i <len; i-=2){
            data[j] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
            //data[j] = (byte) ((Character.digit(s.charAt(i-1), 16) << 4) + Character.digit(s.charAt(i), 16));

            j++;
        }

        return data;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        len= len/2;
        byte[] data = new byte[len];
        int y=0;
        for(int i = 0; i < len*2; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
            if(data[i/2]==80){
                 y=i/2;

            }
        }

        return data;
    }

    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length*2];
        int v;

        for(int j=0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v>>>4];
            hexChars[j*2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }



}
