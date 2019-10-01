package com.minamax.bleserver;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;

import java.util.UUID;


public class DeviceProfile {

    public static UUID SERVICE_UUID = UUID.fromString("4DF91029-B356-463E-9F48-BAB077BF3EF5");
    public static UUID CHARACTERISTIC_VALIDATION_UUID = UUID.fromString("3B66D024-2336-4F22-A980-8095F4898C42");

    public static String getStateDescription(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State " + state;
        }
    }

    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status " + status;
        }
    }
}
