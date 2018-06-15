package com.aagallag.wagonhacker;

/*
 * A lot of code stolen from:
 * https://github.com/joelwass/Android-BLE-Scan-Example
 */

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

public class MainActivity extends Activity {
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static UUID UUID_503 = UUID.fromString("00000503-0000-1000-8000-00805f9b34fb");

    // Themes
    private final static int THEME_DEFAULT = R.style.DarkTheme;
    private final static String PREF_CURRENT_THEME = "CurrentTheme";

    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mBtScanner;
    private SharedPreferences mPref;

    private ArrayList<BluetoothDevice> wagons = new ArrayList<>();
    private TextView textViewStatus;
    private EditText editTextPayload;
    private ScrollView scrollViewLog;
    private boolean attackEnabled;
    private String payload;
    private AtomicBoolean attackThreadRunning = new AtomicBoolean();

    // Buttons
    private ArrayList<Button> buttons = new ArrayList<>();
    private Button buttonStartScan;
    private Button buttonStopScan;
    private Button buttonWannacry;
    private Button buttonDysentery;
    private Button buttonPayload;
    private Button buttonStopAttack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Theme initialization
        mPref = PreferenceManager
                .getDefaultSharedPreferences(this);
        int currentTheme = mPref.getInt(PREF_CURRENT_THEME, THEME_DEFAULT);
        setTheme(currentTheme);

        // Set content views..
        setContentView(R.layout.activity_main);

        // find views
        textViewStatus = findViewById(R.id.textViewStatus);
        editTextPayload = findViewById(R.id.editTextPayload);
        scrollViewLog = findViewById(R.id.scrollViewLog);
        buttonStartScan = findViewById(R.id.buttonStartScan);
        buttons.add(buttonStartScan);
        buttonStopScan = findViewById(R.id.buttonStopScan);
        buttons.add(buttonStopScan);
        buttonWannacry = findViewById(R.id.buttonWannacry);
        buttons.add(buttonWannacry);
        buttonDysentery = findViewById(R.id.buttonDysentery);
        buttons.add(buttonDysentery);
        buttonPayload = findViewById(R.id.buttonPayload);
        buttons.add(buttonPayload);
        buttonStopAttack = findViewById(R.id.buttonStopAttack);
        buttons.add(buttonStopAttack);

        // Setup/Enable Bluetooth
        mBtManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = mBtManager.getAdapter();
        if (mBtAdapter != null && !mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        } else if (mBtAdapter != null) {
            mBtScanner = mBtAdapter.getBluetoothLeScanner();
        } else {
            // Something is wrong with Bluetooth
            // TODO: Error handling here
        }

        // Request location permission
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Checkbox for theme selection
        int currentTheme = mPref.getInt(PREF_CURRENT_THEME, THEME_DEFAULT);
        MenuItem isDarkTheme = menu.findItem(R.id.isDarkTheme);
        switch (currentTheme) {
            case R.style.DarkTheme:
                isDarkTheme.setChecked(true);
                break;
            case R.style.LightTheme:
                isDarkTheme.setChecked(false);
                break;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.isDarkTheme:
                if (item.isChecked()) {
                    item.setChecked(false);
                    SharedPreferences.Editor edit = mPref.edit();
                    edit.putInt(PREF_CURRENT_THEME, R.style.LightTheme);
                    edit.commit();
                } else {
                    item.setChecked(true);
                    SharedPreferences.Editor edit = mPref.edit();
                    edit.putInt(PREF_CURRENT_THEME, R.style.DarkTheme);
                    edit.commit();
                }
                recreate();
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                logMsg("Bluetooth enabled...");
                mBtScanner = mBtAdapter.getBluetoothLeScanner();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //do nothing
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (device.getName() == null) {
                return;
            }

            if (device.getName().equals("503WAGON")) {
                String bdaddr = device.getAddress();
                for (BluetoothDevice d : wagons) {
                    if (d.getAddress().equals(bdaddr)){
                        return;
                    }
                }

                wagons.add(device);
                String l = String.format("[%d] Discovered a 503WAGON: [%s]", (wagons.size()-1), bdaddr);
                logMsg(l);
            }
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == STATE_CONNECTED) {
                gatt.discoverServices();
            } else {
                attackThreadRunning.set(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID_503);
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID_503);
                characteristic.setValue(payload);
                gatt.writeCharacteristic(characteristic);
            } else {
                attackThreadRunning.set(false);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            gatt.disconnect();
            attackThreadRunning.set(false);
            logMsg(String.format("Attack on [%s]", gatt.getDevice().getAddress()));
        }
    };

    private void startScanning() {
        disableButtons(buttonStopScan);
        wagons = new ArrayList<>();
        logMsg("Scanning...");

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                mBtScanner.startScan(leScanCallback);
            }
        });
    }

    private void stopScanning() {
        logMsg("Stopping scan...");

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                mBtScanner.stopScan(leScanCallback);
            }
        });
        enableButtons();
    }

    private void attackThread(BluetoothDevice device) {
        attackThreadRunning.set(true);
        device.connectGatt(getApplicationContext(), false, mGattCallback);
    }

    private void startAttack() {
        disableButtons(buttonStopAttack);
        attackEnabled = true;
        logMsg("Attacking " + wagons.size() + " wagons with " + payload);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                for (BluetoothDevice device : wagons) {
                    if (!attackEnabled) {
                        logMsg("Halting attack early...");
                        return;
                    }
                    attackThread(device);
                    while (attackThreadRunning.get()) {}
                    logMsg(String.format("Finished attack on [%s]", device.getAddress()));
                }
                logMsg("Finished attack on all devices...");
                enableButtons();
            }
        });
    }

    private void startWannacry() {
        payload = "WannaCry";
        startAttack();
    }

    private void startDysentery() {
        payload = "DYSENTERY";
        startAttack();
    }

    private void startCustomPayload() {
        payload = editTextPayload.getText().toString();
        startAttack();
    }

    private void stopAttack() {
        attackEnabled = false;
        logMsg("Stopping attack...");
        enableButtons();
    }

    private void logMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewStatus.append(msg + "\n");
                scrollViewLog.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void disableButtons(final Button ignoreButton) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Button button : buttons) {
                    if (button.equals(ignoreButton)) {
                        continue;
                    }
                    button.setEnabled(false);
                }
            }
        });
    }

    private void enableButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Button button : buttons) {
                    button.setEnabled(true);
                }
            }
        });
    }

    /*
     * OnClick methods
     */
    public void onClick_StartScan(View view) {
        startScanning();
    }

    public void onClick_StopScan(View view) {
        stopScanning();
    }

    public void onClick_AttackWannacry(View view) {
        startWannacry();
    }

    public void onClick_AttackDysentery(View view) {
        startDysentery();
    }

    public void onClick_AttackStop(View view) {
        stopAttack();
    }

    public void onClick_CustomPayload(View view) {
        startCustomPayload();
    }
}
