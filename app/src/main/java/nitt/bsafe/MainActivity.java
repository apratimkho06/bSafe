package nitt.bsafe;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    TextView tvcontact1,tvcontact2,tvcontact3,mLabel;
    TextView tvphone1,tvphone2,tvphone3;
    ImageView imageView;
    Switch check;

    Location gpsLocation;

    AppLocationService appLocationService;

    public static final String TAG = "MainActivity";

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvcontact1 = (TextView) findViewById(R.id.tvcontact1);
        tvcontact2 = (TextView) findViewById(R.id.tvcontact2);
        tvcontact3 = (TextView) findViewById(R.id.tvcontact3);
        tvphone1 = (TextView) findViewById(R.id.tvphone1);
        tvphone2 = (TextView) findViewById(R.id.tvphone2);
        tvphone3 = (TextView) findViewById(R.id.tvphone3);
        imageView = (ImageView) findViewById(R.id.imageView);
        mLabel = (TextView) findViewById(R.id.textView4);

        check = (Switch) findViewById(R.id.switch1);

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);

        tvcontact1.setText(sharedPreferences.getString("name1",""));
        tvcontact2.setText(sharedPreferences.getString("name2",""));
        tvcontact3.setText(sharedPreferences.getString("name3",""));
        tvphone1.setText(sharedPreferences.getString("phone1",""));
        tvphone2.setText(sharedPreferences.getString("phone2",""));
        tvphone3.setText(sharedPreferences.getString("phone3",""));

        appLocationService = new AppLocationService(MainActivity.this);

        tvcontact1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(intent,2015);
            }
        });

        tvcontact2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(intent,2016);
            }
        });

        tvcontact3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(intent,2017);
            }
        });

        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("name1",tvcontact1.getText().toString());
                    editor.putString("name2",tvcontact2.getText().toString());
                    editor.putString("name3",tvcontact3.getText().toString());
                    editor.putString("phone1",tvphone1.getText().toString());
                    editor.putString("phone2",tvphone2.getText().toString());
                    editor.putString("phone3",tvphone3.getText().toString());
                    editor.apply();

                    Toast.makeText(getApplicationContext(),"Done!",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.enableBT:
                try{
                    findBT();
                    openBT();
                } catch (IOException e) {

                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter==null) {
            Toast.makeText(getApplicationContext(),"No bluetooth adapter is available",Toast.LENGTH_SHORT).show();
            Log.d(TAG,"No bluetooth adapter is available");
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth,0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size()>0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("HC-05")) {
                    mmDevice = device;
                    break;
                }
            }
        }
        Log.d(TAG,"Bluetooth device found");
        //Toast.makeText(getApplicationContext(),"Bluetooth device found",Toast.LENGTH_LONG).show();
        mLabel.setText("Bluetooth device found");
        UUID muuid = mmDevice.getUuids()[0].getUuid();
        mLabel.setText(String.valueOf(muuid));
        Log.d(TAG,mmDevice.getName());
    }

    void openBT() throws IOException {
        Log.d(TAG,"openBT");
        //UUID uuid = UUID.fromString("00001115-0000-1000-8000-00805F9B34FB");
        UUID uuid = UUID.fromString(String.valueOf(mmDevice.getUuids()[0].getUuid()));
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        Log.d(TAG,"Bluetooth Opened");
        //Toast.makeText(getApplicationContext(),"Bluetooth Opened",Toast.LENGTH_SHORT).show();
        mLabel.setText("Bluetooth Opened");
    }

    void beginListenForData() {
        Log.d(TAG,"beginListenForData");
        final Handler handler = new Handler();
        final byte delimiter = 78;

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer  = new byte[1024];
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()&&!stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable>0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i=0;i<bytesAvailable;i++) {
                                byte b = packetBytes[i];
                                if (b==delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer,0,encodedBytes,0,encodedBytes.length);
                                    final String data = new String(encodedBytes,"US-ASCII");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG,data);
                                            Toast.makeText(getApplicationContext(),data,Toast.LENGTH_SHORT).show();
                                            mLabel.setText(data);
                                            if (data.equals("Emergency_SOS")) {
                                                mLabel.setText("MSG sending");
                                                if (gpsLocation!=null) {
                                                    mLabel.setText("MSG Sent!");
                                                    double lat = gpsLocation.getLatitude();
                                                    double lon = gpsLocation.getLongitude();
                                                    String result = "Latitude: " + gpsLocation.getLatitude() +
                                                            " Longitude: " + gpsLocation.getLongitude();
                                                    Toast.makeText(getApplicationContext(),result,Toast.LENGTH_SHORT).show();
                                                    LocationAddress locationAddress = new LocationAddress();
                                                    locationAddress.getAddressFromLocation(lat,lon,getApplicationContext(),new GeocoderHandler());

                                                    try {
                                                        SmsManager smsManager = SmsManager.getDefault();
                                                        smsManager.sendTextMessage(tvphone1.getText().toString(),null,result,null,null);
                                                        smsManager.sendTextMessage(tvphone2.getText().toString(),null,result,null,null);
                                                        smsManager.sendTextMessage(tvphone3.getText().toString(),null,result,null,null);
                                                        Toast.makeText(getApplicationContext(),"SMS Sent!",Toast.LENGTH_SHORT).show();
                                                    }catch (Exception e) {
                                                        Toast.makeText(getApplicationContext(),"SMS Failed, please try again later!",Toast.LENGTH_SHORT).show();
                                                        e.printStackTrace();
                                                    }
                                                }

                                            }
                                        }
                                    });
                                    break;
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException e) {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }

    /*void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.d(TAG,"Bluetooth closed");
        Toast.makeText(getApplicationContext(),"Bluetooth closed",Toast.LENGTH_SHORT).show();
    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==2015 && resultCode==RESULT_OK) {
            Uri uri = data.getData();
            Cursor cursor = getContentResolver().query(uri,null,null,null,null);
            cursor.moveToFirst();
            int phone1 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
            int name1 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            Log.d("phone number",cursor.getString(phone1));
            tvcontact1.setText(cursor.getString(name1));
            tvphone1.setText(cursor.getString(phone1));
        }

        if (requestCode==2016 && resultCode==RESULT_OK) {
            Uri uri = data.getData();
            Cursor cursor = getContentResolver().query(uri,null,null,null,null);
            cursor.moveToFirst();
            int phone2 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
            int name2 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            Log.d("phone number",cursor.getString(phone2));
            tvcontact2.setText(cursor.getString(name2));
            tvphone2.setText(cursor.getString(phone2));
        }

        if (requestCode==2017 && resultCode==RESULT_OK) {
            Uri uri = data.getData();
            Cursor cursor = getContentResolver().query(uri,null,null,null,null);
            cursor.moveToFirst();
            int phone3 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
            int name3 = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            Log.d("phone number",cursor.getString(phone3));
            tvcontact3.setText(cursor.getString(name3));
            tvphone3.setText(cursor.getString(phone3));
        }
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch (msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    writeMessage = writeMessage.substring(begin,end);
                    break;
            }
        }
    };

    @Override
    protected void onStart() {
        gpsLocation = appLocationService.getLocation(LocationManager.GPS_PROVIDER);
        super.onStart();
    }

    public void getGPSLocation(View view) {
        //Location gpsLocation = appLocationService.getLocation(LocationManager.GPS_PROVIDER);
        if (gpsLocation!=null) {
            double lat = gpsLocation.getLatitude();
            double lon = gpsLocation.getLongitude();
            String result = "Latitude: " + gpsLocation.getLatitude() +
                    " Longitude: " + gpsLocation.getLongitude();
            Toast.makeText(getApplicationContext(),result,Toast.LENGTH_SHORT).show();
            LocationAddress locationAddress = new LocationAddress();
            locationAddress.getAddressFromLocation(lat,lon,getApplicationContext(),new GeocoderHandler());

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(tvphone1.getText().toString(),null,result,null,null);
                smsManager.sendTextMessage(tvphone2.getText().toString(),null,result,null,null);
                smsManager.sendTextMessage(tvphone3.getText().toString(),null,result,null,null);
                Toast.makeText(getApplicationContext(),"SMS Sent!",Toast.LENGTH_SHORT).show();
            }catch (Exception e) {
                Toast.makeText(getApplicationContext(),"SMS Failed, please try again later!",Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void showSettingAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(
                MainActivity.this);
        alertDialog.setTitle("SETTINGS");
        alertDialog.setMessage("Enable Location Provider! Go to settings menu?");
        alertDialog.setPositiveButton("Settings",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        MainActivity.this.startActivity(intent);
                    }
                });
        alertDialog.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }


    private class GeocoderHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            String locationAddress;
            switch (msg.what) {
                case 1:
                    Bundle bundle = msg.getData();
                    locationAddress = bundle.getString("address");
                    break;
                default:
                        locationAddress = null;
            }
            Toast.makeText(getApplicationContext(),locationAddress,Toast.LENGTH_SHORT).show();
        }
    }
}
