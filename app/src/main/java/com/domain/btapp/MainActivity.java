package com.domain.btapp;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
    // UI widgets
    Button listen, send, listDevices, selectPicture;
    TextView status;
    ImageView imageView, selectedImage_tv;

    private Dialog dialog;
    int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final String APP_NAME = "ImageSender";
    private static final UUID MY_UUID = UUID.fromString("9cffa9df-f2d6-46c9-9065-521f362e0ba1");

    //private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;

    private static int RESULT_LOAD_IMAGE = 1;
    //private static final int PICK_FROM_GALERY = 2;
    Bitmap thumbnail = null;

    String imagePath;

    private Cursor cursor;
    /*
     * Column index for the Thumbnails Image IDs.
     */
    private int columnIndex;
    GridView sdcardImages;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupUI();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }

        scanMode();
        selectedPictures();

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            // Request permissions
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, RESULT_LOAD_IMAGE);
        } else {
            // Permission has already been granted
            // Set up an array of the Thumbnail Image ID column we want
            String[] projection = {MediaStore.Images.Thumbnails._ID};
            // Create the cursor pointing to the SDCard
            cursor = getContentResolver().query( MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all rows
                    null,
                    null);
        }

        // Get the column index of the Thumbnails Image ID
        columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID);
        sdcardImages = (GridView) findViewById(R.id.GridView);
        ImageAdapter gridadapter = new ImageAdapter(this);
        sdcardImages.setAdapter(gridadapter);


        // Set up a click listener
        sdcardImages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parent, View v, int position, long id) {
                // Get the data location of the image
                String[] projection = {MediaStore.Images.Media.DATA};
                cursor = getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection, // Which columns to return
                        null,       // Return all rows
                        null,
                        null);
                columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToPosition(position);
                // Get image filename
                imagePath = cursor.getString(columnIndex);
                // Use this path to do further processing, i.e. full screen display
            }
        });

        // Sends message
        send.setOnClickListener(v -> runOnUiThread(() -> {
            Message message = Message.obtain();
            message.what = STATE_CONNECTED;
            if(thumbnail != null && status.getText().equals(message.what)){
                Log.d("SENT IMAGE", "Image sent");
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                byte[] imageByte = stream.toByteArray();

                int subArraysize = 400;

                sendReceive.write(String.valueOf(imageByte.length).getBytes());

                for (int i = 0; i < imageByte.length; i+=subArraysize)
                {
                    byte[] tempArray;
                    tempArray = Arrays.copyOfRange(imageByte, i, Math.min(imageByte.length, i+subArraysize));
                    sendReceive.write(tempArray);
                }
            }
            else{
                Toast.makeText(this, "Image is empty", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void setupUI()
    {
        listen = findViewById(R.id.listen);
        send = findViewById(R.id.send);
        status = findViewById(R.id.status);
        listDevices = findViewById(R.id.listDevices);
        imageView = findViewById(R.id.imageview);
        selectPicture = findViewById(R.id.select_image);
        selectedImage_tv = findViewById(R.id.selected_image_image_view);

        // Set button listeners
        // Lists devices
        listDevices.setOnClickListener(v -> {
            showPrinterPickDialog();
            //unregisterReceiver(discoveryFinishReceiver);
        });

        listen.setOnClickListener(v -> {
            ServerClass serverClass = new ServerClass();
            serverClass.start();
        });

        // Sends message
        /*send.setOnClickListener(v -> runOnUiThread(() -> {
            Message message = Message.obtain();
            if(thumbnail != null && message.what == STATE_CONNECTED){
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                byte[] imageByte = stream.toByteArray();

                int subArraysize = 400;

                sendReceive.write(String.valueOf(imageByte.length).getBytes());

                for (int i = 0; i < imageByte.length; i+=subArraysize)
                {
                    byte[] tempArray;
                    tempArray = Arrays.copyOfRange(imageByte, i, Math.min(imageByte.length, i+subArraysize));
                    sendReceive.write(tempArray);
                }
            }
        }));*/
    }

    private void scanMode()
    {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        IntentFilter scanFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        //registerReceiver(broadcastReceiverScan, scanFilter);
    }

    private void selectedPictures() {
        selectPicture.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {
                // Request permissions
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, RESULT_LOAD_IMAGE);
            } else {
                // Permission has already been granted
                selectedImage();
            }
        });
    }

    private final BroadcastReceiver broadcastReceiverScan = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
            {
                final int modeValue = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                switch (modeValue)
                {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Toast.makeText(getApplicationContext(), "Device is not discoverable but can receive connection ", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Toast.makeText(getApplicationContext(), "Device is in discoverable mode ", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Toast.makeText(getApplicationContext(), "Device is not discoverable and can not receive connection", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    };

    private void selectedImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_LOAD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data && data.getData() != null)
        {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn,null,null,null);
            if (cursor != null && cursor.moveToNext())
            {
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                String picturePath = cursor.getString(columnIndex);
                cursor.close();
                thumbnail = (BitmapFactory.decodeFile(picturePath));
                Log.d("IMAGE PATH", picturePath );
                //imageView.setImageBitmap(thumbnail);
                runOnUiThread(() -> {
                    selectedImage_tv.setImageBitmap(thumbnail);
                });
            }
        }
    }

    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            Log.d("Dicovering cancel", "cancelling discovering");
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        bluetoothAdapter.startDiscovery();
        Log.d("Dicovering start", "Started discovering");

        //Initializing bluetooth adapters
        //ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate list views and attach the adapters
        ListView listView = dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = dialog.findViewById(R.id.discoveredDeviceList);
        //listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        String[] strings = new String[pairedDevices.size()];
        btArray = new BluetoothDevice[pairedDevices.size()];
        int index = 0;
        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices)
            {
                btArray[index] = device;
                strings[index] = device.getName();
                index++;
            }
            ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, strings);
            listView.setAdapter(pairedDevicesAdapter);
        }

        //Handling list view item click event
        listView.setOnItemClickListener((parent, view, position, id) -> {
            bluetoothAdapter.cancelDiscovery();
            ClientClass clientClass = new ClientClass(btArray[position]);
            clientClass.start();

            status.setText("Connecting");
            //unregisterReceiver(broadcastReceiverScan);
            dialog.dismiss();
        });

        listView2.setOnItemClickListener((adapterView, view, i, l) -> {
            bluetoothAdapter.cancelDiscovery();
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);

            connectToDevice(address);
            //unregisterReceiver(broadcastReceiverScan);
            dialog.dismiss();
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());
        dialog.setCancelable(false);
        dialog.show();
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        ClientClass clientClass = new ClientClass(device);
        clientClass.start();

        status.setText("Connecting");
    }

    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add("no device found");
                }
            }
        }
    };

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what)
            {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case  STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    //TODO: Receive the message
                    byte[] readBuff = (byte[])msg.obj;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(readBuff, 0, msg.arg1);
                    imageView.setImageBitmap(bitmap);
                    break;
            }
            return true;
        }
    });

    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;

        ServerClass(){
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public void run(){
            BluetoothSocket socket = null;

            while (true)
            {
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket = serverSocket.accept();
                } catch (Exception e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if (socket != null)
                {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);

                    //TODO send and receive code
                    sendReceive = new SendReceive(socket);
                    sendReceive.start();
                    break;
                }
            }
        }
    }

    private class ClientClass extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        ClientClass(BluetoothDevice device1) {
            device = device1;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void run() {
            //TODO: cancel discovery
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                //when connected send and receive
                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (Exception e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        SendReceive(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (Exception e) {
                e.printStackTrace();
            }

            inputStream = tempIn;
            outputStream = tempOut;
        }

        public void run() {
            byte[] buffer = null;
            int numberOfBytes = 0;
            int index = 0;
            boolean flag = true;

            while (true)
            {
                if (flag)
                {
                    try {
                        byte[] temp = new byte[inputStream.available()];
                        if (inputStream.read(temp) > 0) {
                            numberOfBytes = Integer.parseInt(new String(temp, "UTF-8"));
                            buffer = new byte[numberOfBytes];
                            flag = false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        byte[] data = new byte[inputStream.available()];
                        int numbers = inputStream.read(data);
                        //Copy data into buffer array
                        System.arraycopy(data, 0, buffer, index, numbers);
                        index = index + numbers;

                        if(index == numberOfBytes){
                            handler.obtainMessage(STATE_MESSAGE_RECEIVED, numberOfBytes, -1, buffer).sendToTarget();
                            flag = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        void write(byte[] bytes)
        {
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Adapter for our image files.
     */
    private class ImageAdapter extends BaseAdapter {
        private Context context;
        public ImageAdapter(Context localContext) {
            context = localContext;
        }
        public int getCount() {
            return cursor.getCount();
        }
        public Object getItem(int position) {
            return position;
        }
        public long getItemId(int position) {
            return position;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView picturesView;
            if (convertView == null) {
                picturesView = new ImageView(context);
            } else {
                picturesView = (ImageView)convertView;
            }
                // Move cursor to current position
                cursor.moveToPosition(position);
                // Get the current value for the requested column
                int imageID = cursor.getInt(columnIndex);
                // Set the content of the image based on the provided URI
                picturesView.setImageURI(Uri.withAppendedPath(
                        MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, "" + imageID));
                Log.d("hello", String.valueOf(Uri.withAppendedPath(
                        MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, "" + imageID)));
                //picturesView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                picturesView.setPadding(8, 8, 8, 8);
                picturesView.setLayoutParams(new GridView.LayoutParams(parent.getWidth()/2, parent.getHeight()/2));

            return picturesView;
        }
    }
}

