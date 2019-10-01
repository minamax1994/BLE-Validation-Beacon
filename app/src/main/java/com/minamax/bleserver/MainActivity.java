package com.minamax.bleserver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends Activity {
    private static final String TAG = "PeripheralActivity";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattServer gattServer;

    private TextView messageTv;

    private MediaPlayer vmp, imp;
    private String validationResponseMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        messageTv = findViewById(R.id.msg);

        vmp = MediaPlayer.create(this, R.raw.valid);
        imp = MediaPlayer.create(this, R.raw.invalid);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, "No Advertising Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        gattServer = bluetoothManager.openGattServer(this, mGattServerCallback);

        initServer();
        startAdvertising();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAdvertising();
        shutdownServer();
    }

    private void initServer() {
        BluetoothGattService service = new BluetoothGattService(DeviceProfile.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic setMessageCharacteristic =
                new BluetoothGattCharacteristic(DeviceProfile.CHARACTERISTIC_VALIDATION_UUID,
                        //Read+write permissions
                        BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(setMessageCharacteristic);

        gattServer.addService(service);
    }

    private void shutdownServer() {
        if (gattServer == null) return;
        gattServer.close();
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(TAG, "onConnectionStateChange "
                    + DeviceProfile.getStatusDescription(status) + " "
                    + DeviceProfile.getStateDescription(newState));
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG, "onCharacteristicReadRequest " + characteristic.getUuid().toString());

            if (DeviceProfile.CHARACTERISTIC_VALIDATION_UUID.equals(characteristic.getUuid())) {
                gattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        getValidationResponseMessage());
            }

            gattServer.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null);
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, "onCharacteristicWriteRequest " + characteristic.getUuid().toString());

            if (DeviceProfile.CHARACTERISTIC_VALIDATION_UUID.equals(characteristic.getUuid())) {
                final String incomingMessage = new String(value, Charset.forName("UTF-8"));

                gattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value);

                if (incomingMessage.equals("-1")) {
                    imp.start();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            setValidationResponseMessage("INVALID");
                            notifyDevice(device);
                        }
                    });
                    gattServer.cancelConnection(device);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setValidationResponseMessage("");
                        }
                    }, 3000);
                } else {
                    ValidationService validationService = RetrofitInstance.getRetrofitInstance().create(ValidationService.class);
                    Call<Integer> call = validationService.validate(incomingMessage);
                    call.enqueue(new Callback<Integer>() {
                        @Override
                        public void onResponse(Call<Integer> call, Response<Integer> response) {
                            if (response.body() == 0) {
                                imp.start();
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        setValidationResponseMessage("INVALID");
                                        notifyDevice(device);
                                    }
                                });
                            } else {
                                vmp.start();
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        setValidationResponseMessage("VALID");
                                        notifyDevice(device);
                                    }
                                });
                            }
                            gattServer.cancelConnection(device);
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    setValidationResponseMessage("");
                                }
                            }, 3000);
                        }

                        @Override
                        public void onFailure(Call<Integer> call, Throwable t) {
                            imp.start();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    setValidationResponseMessage("SERVER ERROR");
                                    notifyDevice(device);
                                }
                            });
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    setValidationResponseMessage("");
                                }
                            }, 3000);
                        }
                    });

//                if (!incomingMessage.contains("f")) {
//                    vmp.start();
//                    handler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            setValidationResponseMessage("VALID");
//                        }
//                    });
//                } else {
//                    imp.start();
//                    handler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            setValidationResponseMessage("INVALID");
//                        }
//                    });
//                }
//                gattServer.cancelConnection(device);
//                handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        setValidationResponseMessage("");
//                    }
//                }, 3000);
                }
            }
        }
    };

    private void startAdvertising() {
        if (bluetoothLeAdvertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
//                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(DeviceProfile.SERVICE_UUID))
                .build();

        bluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertising() {
        if (bluetoothLeAdvertiser == null) return;
        bluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Peripheral Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "Peripheral Advertise Failed: " + errorCode);
        }
    };

    private Handler handler = new Handler();

    private void notifyDevice(BluetoothDevice device) {
        BluetoothGattCharacteristic validationCharacteristic = gattServer.getService(DeviceProfile.SERVICE_UUID)
                .getCharacteristic(DeviceProfile.CHARACTERISTIC_VALIDATION_UUID);
        validationCharacteristic.setValue(getValidationResponseMessage());
        gattServer.notifyCharacteristicChanged(device, validationCharacteristic, false);
    }

    private byte[] getValidationResponseMessage() {
        return validationResponseMessage.getBytes(Charset.forName("UTF-8"));
    }

    private void setValidationResponseMessage(String responseMessage) {
        validationResponseMessage = responseMessage;
        if (responseMessage.equals("VALID")) {
            messageTv.setText(responseMessage);
            messageTv.setTextColor(getResources().getColor(R.color.valid));
        } else {
            messageTv.setText(responseMessage);
            messageTv.setTextColor(getResources().getColor(R.color.invalid));
        }
    }
}
