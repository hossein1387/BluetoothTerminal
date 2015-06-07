package com.example.hossein.bluetoothterminal;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import java.io.File;
import android.view.MenuInflater;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.os.Handler;
import android.os.Message;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

//==============================================================================
// Start Activity:
public class MainActivity extends Activity {
//==============================================================================
// Variables:
    public static final int NumberOfSamplesPerUpload = 1000;
    public int SampleCounter = 0;
    public static final byte NewLineChar = 13;
    private Handler handler = new Handler();
    private BluetoothAdapter mBluetoothAdapter = null;
    private boolean mEnablingBT;
    public int RecData = 0;
    public String toastText = "";
    public String recData="";
    public byte[] DataBuffer = new byte[1024];
    BluetoothSocket mmSocket;
    OutputStream mmOutputStream;
    int dataCount = 1;
    public boolean ConnectGreenIconVIS= true; // Connect circle button visibility
    private static BluetoothSerialService mSerialService = null;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int AUTH_REQUEST = 3;
    // Activity Result Keys
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int CONNECTION_LOST = 6;
    public static final int UNABLE_TO_CONNECT = 7;
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    private MenuItem mMenuItemConnect,mMenuItemClearGraph;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Storage File
    File folder,DataFile;
    String DataFileName = "DataFile.txt";
    //BOX Variables
    private boolean authentication = false,DoneUploading=true,mIsBound,SyncData=true;;
    public int FileUploadedToBox=0;

    //GraphView Vars:
    private double graph2LastXValue = 5d;
    private Handler updateDataHandler = new Handler();
    private LineGraphSeries<DataPoint> series1,series2,series3,series4;
    private int numberOfSamplesOnGraph = 50;

    private ArrayList<Integer> dataPacket = new ArrayList<Integer>();
    private int dataPacketCounter =0;
    private int packetLostCounter = 0;
    private static int packetLostThreshold = 10;

//==============================================================================


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView ConnectedImg = (ImageView) findViewById(R.id.ConnectionStatIcon);
        TextView ConnectionStat = (TextView) findViewById(R.id.ConnectionTextStat);
        ConnectedImg.setVisibility(View.GONE);
        ConnectionStat.setText("Not Connected");
        handler.postDelayed(runnable, 100);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mSerialService = new BluetoothSerialService(this, mHandlerBT);
        if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
            return;
        }
        folder = getExternalFilesDir("BlueToothTerminal");
        DataFile = new File(folder,DataFileName);
        if (DataFile.exists()){
            DataFile.delete();
            DataFile = new File(folder,DataFileName);
        }
        Log.e("DataStorage", DataFile.getAbsolutePath());

        if (!isNetworkAvailable()){
            Toast.makeText(this, "No Internet Connection Found! \nPlease check your connectivity.", Toast.LENGTH_LONG).show();
        }
        else{
            Toast.makeText(this, "Internet Connection Found!", Toast.LENGTH_LONG).show();
        }

    }

    //==============================================================================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        mMenuItemConnect = menu.getItem(0);
        mMenuItemClearGraph = menu.getItem(1);
        return true;
    }

    //==============================================================================
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                }
                else
                if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
                    mSerialService.stop();
                    mSerialService.start();
                }
                return true;
            case R.id.Settings:
                Toast.makeText(getApplicationContext(), "Hello!", Toast.LENGTH_SHORT).show();
                return true;
        }
        return false;
    }
    //==============================================================================
    @Override
    public void onStart() {
        super.onStart();
        mEnablingBT = false;
        GraphView graph1 = (GraphView) findViewById(R.id.graph1);
        GraphView graph2 = (GraphView) findViewById(R.id.graph2);
        GraphView graph3 = (GraphView) findViewById(R.id.graph3);
        GraphView graph4 = (GraphView) findViewById(R.id.graph4);
        // data
        series1 = new LineGraphSeries<DataPoint>();
        series2 = new LineGraphSeries<DataPoint>();
        series3 = new LineGraphSeries<DataPoint>();
        series4 = new LineGraphSeries<DataPoint>();
        graph1.removeAllSeries();graph1.addSeries(series1);
        graph2.removeAllSeries();graph2.addSeries(series2);
        graph3.removeAllSeries();graph3.addSeries(series3);
        graph4.removeAllSeries();graph4.addSeries(series4);
        // customize a little bit viewport
        //series1.resetData(0);
        Viewport viewport1 = graph1.getViewport();viewport1.setYAxisBoundsManual(true);viewport1.setMinY(0);viewport1.setMaxY(3.5);viewport1.setScrollable(true);
        Viewport viewport2 = graph2.getViewport();viewport2.setYAxisBoundsManual(true);viewport2.setMinY(0);viewport2.setMaxY(3.5);viewport2.setScrollable(true);
        Viewport viewport3 = graph3.getViewport();viewport3.setYAxisBoundsManual(true);viewport3.setMinY(0);viewport3.setMaxY(3.5);viewport3.setScrollable(true);
        Viewport viewport4 = graph4.getViewport();viewport4.setYAxisBoundsManual(true);viewport4.setMinY(0);viewport4.setMaxY(3.5);viewport4.setScrollable(true);
        series1.setTitle("sensor1");graph1.getLegendRenderer().setVisible(true); graph1.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);series1.setColor(Color.RED);
        series2.setTitle("sensor2");graph2.getLegendRenderer().setVisible(true); graph2.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);series2.setColor(Color.GREEN);
        series3.setTitle("sensor3");graph3.getLegendRenderer().setVisible(true); graph3.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);series3.setColor(Color.BLUE);
        series4.setTitle("sensor4");graph4.getLegendRenderer().setVisible(true); graph4.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);series4.setColor(Color.YELLOW);
    }
    //==============================================================================
    @Override
    protected synchronized void onResume() {
        super.onResume();
        TextView ConnectionStat = (TextView) findViewById(R.id.ConnectionTextStat);
        ImageView ConnectedImg = (ImageView) findViewById(R.id.ConnectionStatIcon);
        if(!mEnablingBT){
            if ( (mBluetoothAdapter != null)  && (!mBluetoothAdapter.isEnabled()) ) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.alert_dialog_warning_title)
                        .setCancelable( false )
                        .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mEnablingBT = true;
                                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                            }
                        })
                        .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishDialogNoBluetooth();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
            if (mSerialService != null) {
                if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
                    mSerialService.start();
                }
            }
            if(getConnectionState() == BluetoothSerialService.STATE_CONNECTED){
                ConnectionStat.setText("Connected To: "+ mConnectedDeviceName);
            }else{
                ConnectionStat.setText("Not Connected");
                ConnectedImg.setVisibility(View.GONE);
            }
        }
    }
    //==============================================================================
    @Override
    protected void onRestart() {
        super.onRestart();
        setContentView(R.layout.activity_main);
    }
    //==============================================================================
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Do you really want to exit?").setCancelable(false).setPositiveButton("Quit",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mSerialService.stop();
                        MainActivity.this.finish();
                    }
                }).setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();

    }
    //==============================================================================
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSerialService != null)
            mSerialService.stop();

    }
    //==============================================================================
    // On Activity Result:
    //==============================================================================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mSerialService.connect(device);
                }
                break;

        }
    }

    //==============================================================================
    //Handles:
    //==============================================================================
    private Runnable updateDataThread = new Runnable() {
        @Override
        public void run() {
     /* do what you need to do */
            //appendDataToGraph();
        }
    };


        private final Handler mHandlerBT = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            ImageView ConnectedImg = (ImageView) findViewById(R.id.ConnectionStatIcon);
            TextView ConnectionStat = (TextView) findViewById(R.id.ConnectionTextStat);
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            break;

                        case BluetoothSerialService.STATE_CONNECTING:
                            ConnectionStat.setText("Connecting...");
                            break;

                        case BluetoothSerialService.STATE_LISTEN:
                            break;
                        case BluetoothSerialService.STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    dataPacketCounter+=1;
                    String message = msg.obj.toString();
//                    Log.e("ReadMsgString", "Received Data String: " + message);
                    if (dataPacketCounter <= 5){
                        if (!((message.equals("<S>")) || (message.equals("<E>")))){
                            message = message.replace(">", "");
                            message = message.replace("<", "");
                            int data =Integer.parseInt(message);;
                            dataPacket.add(data);
                        }else if(message.equals("<S>")){
                            dataPacketCounter = 1;
                        }
                    }else{
                        if(!(msg.obj.equals("<E>"))){

                            Log.e("ReadMsgString", "Packet lost! Packet was not received in correct format!");
                            packetLostCounter+=1;
                            dataPacket.clear();
                            dataPacketCounter = 0;
                            if (packetLostCounter>packetLostThreshold){
                                Toast.makeText(getApplicationContext(), "More than " + packetLostThreshold + " packets were lost. Turning Bluetooth off.", Toast.LENGTH_SHORT).show();
                                packetLostCounter = 0;
                                mSerialService.stop();
                            }
                        }else{
                            //Toast.makeText(getApplicationContext(), "Packet was received in correct format!", Toast.LENGTH_SHORT).show();
                            Log.e("ReadMsgString", "Packet was received in correct format!");
                            appendDataToGraph(dataPacket);
                            UpdateData(dataPacket);
                            dataPacket.clear();
                            dataPacketCounter = 0;
                        }
                    }

                    break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    ConnectionStat.setText("Connected To: "+ mConnectedDeviceName);
                    Toast.makeText(getApplicationContext(), "Connected to "+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    if (mMenuItemConnect != null) {
                        mMenuItemConnect.setTitle(R.string.disconnect);
                    }
                    break;

                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

                case CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(), "Device connection was lost",Toast.LENGTH_SHORT).show();
                    ConnectedImg.setVisibility(View.GONE);
                    ConnectGreenIconVIS = false;
                    ConnectionStat.setText("Not connected");
                    if (mMenuItemConnect != null) {
                        mMenuItemConnect.setTitle(R.string.connect);
                    }
                    break;

                case UNABLE_TO_CONNECT:
                    ConnectGreenIconVIS = false;
                    ConnectionStat.setText("Not connected");
                    Toast.makeText(getApplicationContext(), "Unable to connect device",Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };

    //==============================================================================
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            ImageView ConnectedImg = (ImageView) findViewById(R.id.ConnectionStatIcon);
            if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED){
                if (ConnectGreenIconVIS){
                    ConnectedImg.setVisibility(View.GONE);
                    ConnectGreenIconVIS = false;
                }else{
                    ConnectedImg.setVisibility(View.VISIBLE);
                    ConnectGreenIconVIS = true;
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    //==============================================================================
    //User Methods:
    //==============================================================================
    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable( false )
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //==============================================================================
    public int getConnectionState() {
        return mSerialService.getState();
    }

    //==============================================================================
    public void UpdateData(List<Integer> data) {

        updateDataHandler.postDelayed(updateDataThread, 100);
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
        String strDate = sdf.format(c.getTime());
        StoreDataToFile(DataFile,strDate+": "+"<"+ data.get(0)+">"+"<"+ data.get(1)+">"+"<"+ data.get(2)+">"+"<"+ data.get(3)+">"+"\u00b0C");
        SampleCounter++;
    }
    //==============================================================================
    public void StoreDataToFile (File MyFile, String data){
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(MyFile,true);
            fileOutputStream.write(data.getBytes());
            fileOutputStream.write("\n\r".getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (fileOutputStream!=null){
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //==============================================================================
    public void message(String message){
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

    }

    //==============================================================================
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }



    private void appendDataToGraph(List<Integer> data) {
        // here, we choose to display max 10 points on the viewport and we scroll to end
        graph2LastXValue += 1d;
        series1.appendData(new DataPoint(graph2LastXValue, (data.get(0)/65536.0)*3.3), true, numberOfSamplesOnGraph);
        series2.appendData(new DataPoint(graph2LastXValue, (data.get(1)/65536.0)*3.3), true, numberOfSamplesOnGraph);
        series3.appendData(new DataPoint(graph2LastXValue, (data.get(2) / 65536.0) * 3.3), true, numberOfSamplesOnGraph);
        series4.appendData(new DataPoint(graph2LastXValue, (data.get(3)/65536.0)*3.3), true, numberOfSamplesOnGraph);
    }

}
