/*******************************************************************************
 * Copyright (c) 1999, 2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 */
package paho.mqtt.java.example;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PahoExampleActivity extends AppCompatActivity {
    private HistoryAdapter mAdapter;

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "tcp://127.0.0.1:1883";

    String clientId = "AndroidClient";
    final String subscriptionTopic = "gw/#";
    private String deviceNodeId = "";

    Set<String> deviceSet = new HashSet<>();
    private List<String> deviceList;
    private String curSelDeviceEUI64 = "";

    private Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!publishGetDeviceMessage()) {
                timerHandler.postDelayed(this, 1000);
            }
        }
    };
    private Spinner mSpinner;
    private ArrayAdapter<String> mArrayAdapter;
    private String data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);

        Button btn1 = findViewById(R.id.button1);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            System.out.println("### btn1 click!!\n");
            publishOpenNetworkMessage();
            }
        });

        Button btn2 = findViewById(R.id.button2);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            System.out.println("### btn2 click!!\n");
            publishCloseNetworkMessage();
            }
        });

        Button btn3 = findViewById(R.id.button3);
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            System.out.println("### btn3 click!!\n");
            publishLightOnMessage();
            }
        });

        Button btn4 = findViewById(R.id.button4);
        btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            System.out.println("### btn4 click!!\n");
            publishLightOffMessage();
            }
        });

        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.history_recycler_view);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new HistoryAdapter(new ArrayList<String>());
        mRecyclerView.setAdapter(mAdapter);

        getGatewayEUI64();
        getDeviceEUI64();

        mSpinner = (Spinner)findViewById(R.id.spinner1);
        deviceList = new ArrayList<>(deviceSet);
        mArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, deviceList);
        mArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(mArrayAdapter);

        //mSpinner.setSelection(0, true);

        // add listener
        mSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                data = mSpinner.getItemAtPosition(position).toString();
                Log.e("TAG", "" + data);
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
                Log.e("TAG", "onNothingSelected");
            }
        });

        mSpinner.setOnTouchListener(new Spinner.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.e("TAG", "onTouch() is invoked!");
                //view.setVisibility(View.INVISIBLE);
                return false;
            }
        });

        mSpinner.setOnFocusChangeListener(new Spinner.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                Log.e("TAG", " onFocusChange() is invoked!");
            }
        });

        mSpinner.setVisibility(View.VISIBLE);

        clientId = clientId + System.currentTimeMillis();

        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    addToHistory("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic();
                } else {
                    addToHistory("Connected to: " + serverURI);
                    timerHandler.postDelayed(timerRunnable, 1000);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                addToHistory("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to connect to: " + serverUri);
                }
            });

        } catch (MqttException ex) {
            ex.printStackTrace();
        }

    }

    private void addToHistory(String mainText) {
        System.out.println("LOG: " + mainText);
        mAdapter.add(mainText);
        Snackbar.make(findViewById(android.R.id.content), mainText, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        return super.onOptionsItemSelected(item);
    }

    public void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addToHistory("Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to subscribe");
                }
            });

            // THIS DOES NOT WORK!
            mqttAndroidClient.subscribe(subscriptionTopic, 2, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // message Arrived!
                    System.out.println("Message: " + topic + " : " + new String(message.getPayload()));
                    String[] strArr = topic.split("/");

                    if (strArr.length >= 2) {
                        String gatewayEUI64 = strArr[1];
                        System.out.println("==== get EUI64 is " + strArr[1]);
                        saveGatewayEUI64(gatewayEUI64);
                    }

                    if (strArr.length >= 3) {
                        if (strArr[2].equals("devicejoined")) // Message when a node joins
                        {
                            try {
                                JSONObject jsonObject = new JSONObject(message.toString());
                                deviceNodeId = jsonObject.getString("nodeId");
                                String deviceEndpoint = jsonObject.getString("deviceEndpoint");
                                JSONObject obj = new JSONObject(deviceEndpoint.toString());
                                String deviceEUI64 = obj.getString("eui64").substring(2);
                                deviceSet.add(deviceEUI64);
                                saveDeviceEUI64();
                                updateDeviceList();
                                System.out.println("===Howrd=== eui64: " + deviceEUI64 + " 加入网络!");
                                addToHistory("eui64: " + deviceEUI64 + " 加入网络!");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        else if (strArr[2].equals("deviceleft")) // Message when a node leaves
                        {
                            try {
                                JSONObject rootObject = new JSONObject(message.toString());
                                String deviceEUI64 = rootObject.getString("eui64").substring(2);
                                deviceSet.remove(deviceEUI64);
                                saveDeviceEUI64();
                                System.out.println("===Howrd=== eui64: " + deviceEUI64 + " 退出网络!");
                                addToHistory("eui64: " + deviceEUI64 + " 退出网络!");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        else if (strArr[2].equals("devices")) // Responds to a request for the currently connected devices
                        {
                            try {
                                JSONObject rootObject = new JSONObject(message.toString());
                                JSONArray deviceTab = rootObject.getJSONArray("devices");
                                if (deviceTab.length() <= 0)
                                {
                                    deviceSet.clear();
                                }
                                for (int i = 0; i < deviceTab.length(); i++) {
                                    JSONObject deviceObject = deviceTab.getJSONObject(i);
                                    JSONObject deviceEndpoint = deviceObject.getJSONObject("deviceEndpoint");
                                    String deviceEUI64 = deviceEndpoint.getString("eui64").substring(2);
                                    deviceSet.add(deviceEUI64);
                                    System.out.println("NodeID: " + deviceEUI64 + " 添加成功!");
                                    addToHistory("NodeID: " + deviceEUI64 + " 添加成功!");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            saveDeviceEUI64();
                            updateDeviceList();
                        }
                    }
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public String getGatewayEUI64()
    {
        // get saved GatewayEUI64 info
        Context ctx = PahoExampleActivity.this;

        SharedPreferences spHost = ctx.getSharedPreferences("host", MODE_PRIVATE);
        return spHost.getString("GatewayEUI64", "");
    }

    public void saveGatewayEUI64(String str)
    {
        // save GatewayEUI64 info
        Context ctx = PahoExampleActivity.this;

        SharedPreferences spHost = ctx.getSharedPreferences("host", MODE_PRIVATE);
        SharedPreferences.Editor spEditor = spHost.edit();
        spEditor.putString("GatewayEUI64", str);
        spEditor.commit();
    }

    public void getDeviceEUI64()
    {
        // get saved DeviceEUI64 info
        Context ctx = PahoExampleActivity.this;
        SharedPreferences spDevice = ctx.getSharedPreferences("device", MODE_PRIVATE);
        deviceSet = new HashSet<>(spDevice.getStringSet("DeviceEUI64", new HashSet<String>()));
    }

    public void saveDeviceEUI64()
    {
        // save DeviceEUI64 info
        Context ctx = PahoExampleActivity.this;

        SharedPreferences spDevice = ctx.getSharedPreferences("device", MODE_PRIVATE);
        SharedPreferences.Editor spEditor = spDevice.edit();
        spEditor.putStringSet("DeviceEUI64", deviceSet);
        spEditor.commit();
    }

    public void publishOpenNetworkMessage() {

        String gatewayEUI64 = getGatewayEUI64();

        if (gatewayEUI64.length() <= 0) {
            Toast.makeText(PahoExampleActivity.this, "连接Host中，请稍等...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MqttMessage message = new MqttMessage();
            String publishTopic = "gw/" + gatewayEUI64 + "/commands";
            String publishMessage = "{" +
                    "\"commands\":[{ \"command\":" +
                    "\"plugin network-creator-security open-network\"," +
                    "\"postDelayMs\":100" +
                    "}]" +
                    "}";
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishCloseNetworkMessage() {

        String gatewayEUI64 = getGatewayEUI64();

        if (gatewayEUI64.length() <= 0) {
            Toast.makeText(PahoExampleActivity.this, "连接Host中，请稍等...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MqttMessage message = new MqttMessage();
            String publishTopic = "gw/" + gatewayEUI64 + "/commands";
            String publishMessage = "{" +
                    "\"commands\":[{ \"command\":" +
                    "\"plugin network-creator-security close-network\"," +
                    "\"postDelayMs\":100" +
                    "}]" +
                    "}";
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishLightOnMessage() {

        String gatewayEUI64 = getGatewayEUI64();

        if (deviceSet.size() <= 0) {
            Toast.makeText(PahoExampleActivity.this, "连接Host中，请稍等...", Toast.LENGTH_SHORT).show();
            return;
        }

        curSelDeviceEUI64 = getDeviceListSelStr();//data;

        try {
            MqttMessage message = new MqttMessage();
            String publishTopic = "gw/" + gatewayEUI64 + "/commands";
            String publishMessage = "{" +
                    "\"commands\":[{" +
                    "\"command\":\"zcl on-off on\"," +
                    "\"postDelayMs\":0" +
                    "},{" +
                    "\"command\":\"plugin device-table send {" +
                    curSelDeviceEUI64 +
                    "} 1\"," +
                    "\"postDelayMs\":0" +
                    "}]" +
                    "}";
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishLightOffMessage() {

        String gatewayEUI64 = getGatewayEUI64();

        if (deviceSet.size() <= 0) {
            Toast.makeText(PahoExampleActivity.this, "连接Host中，请稍等...", Toast.LENGTH_SHORT).show();
            return;
        }

        curSelDeviceEUI64 = getDeviceListSelStr();//data;

        try {
            MqttMessage message = new MqttMessage();
            String publishTopic = "gw/" + gatewayEUI64 + "/commands";
            String publishMessage = "{" +
                    "\"commands\":[{" +
                    "\"command\":\"zcl on-off off\"," +
                    "\"postDelayMs\":0" +
                    "},{" +
                    "\"command\":\"plugin device-table send {" +
                    curSelDeviceEUI64 +
                    "} 1\"," +
                    "\"postDelayMs\":0" +
                    "}]" +
                    "}";
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean publishGetDeviceMessage() {

        String gatewayEUI64 = getGatewayEUI64();

        if (gatewayEUI64.length() <= 0) {
            Toast.makeText(PahoExampleActivity.this, "连接Host中，请稍等...", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            MqttMessage message = new MqttMessage();
            String publishTopic = "gw/" + gatewayEUI64 + "/publishstate";
            String publishMessage = "{}";
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("===Howrd=== GetDeviceMessage Published");
            System.out.println("### message: " + message);
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void updateDeviceList()
    {
//        deviceList = new ArrayList<>(deviceSet);
//        ArrayAdapter<String> mArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceList);
//        mArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        mSpinner.setAdapter(mArrayAdapter);
        mArrayAdapter.notifyDataSetChanged();
//        mSpinner.setVisibility(View.VISIBLE);
    }

    private String getDeviceListSelStr()
    {
        return mSpinner.getSelectedItem().toString();
    }
}
