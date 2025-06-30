package com.example.airtentiveapp.ui.bluetooth;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView; // Assuming you use TextViews
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.airtentiveapp.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Assuming your item layout is named 'item_bluetooth_device.xml'
// and has TextViews with ids: textViewDeviceName, textViewDeviceAddress

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder> {

    private final List<BluetoothDevice> deviceList;
    private final DeviceActionListener listener;
    private final Context context; // Store context for permission checks
    private final Map<String, Boolean> connectedDevices = new HashMap<>(); // Track connected devices by address
    private final Map<String, String> deviceData = new HashMap<>(); // Store latest data for each device

    public interface DeviceActionListener {
        void onConnectDevice(BluetoothDevice device);
        void onDisconnectDevice(BluetoothDevice device);
    }

    public BluetoothDeviceAdapter(List<BluetoothDevice> deviceList, DeviceActionListener listener, Context context) {
        this.deviceList = deviceList;
        this.listener = listener;
        this.context = context; // Initialize context
    }

    /**
     * Update the connection state of a device
     * @param device The device whose connection state changed
     * @param isConnected True if connected, false if disconnected
     */
    public void updateConnectionState(BluetoothDevice device, boolean isConnected) {
        if (device != null) {
            connectedDevices.put(device.getAddress(), isConnected);
            notifyDataSetChanged(); // Refresh the list to update button states
        }
    }

    /**
     * Check if a device is currently connected
     */
    public boolean isDeviceConnected(BluetoothDevice device) {
        if (device != null) {
            Boolean isConnected = connectedDevices.get(device.getAddress());
            return isConnected != null && isConnected;
        }
        return false;
    }

    /**
     * Update data for a specific device
     * @param device The device that sent the data
     * @param data The latest data from the device
     */
    public void updateDeviceData(BluetoothDevice device, String data) {
        if (device != null) {
            deviceData.put(device.getAddress(), data);
            // Find and update only the specific device's view instead of refreshing the entire list
            for (int i = 0; i < deviceList.size(); i++) {
                if (deviceList.get(i).getAddress().equals(device.getAddress())) {
                    // Use payload to indicate this is a data-only update
                    notifyItemChanged(i, "DATA_UPDATE");
                    break;
                }
            }
        }
    }

    /**
     * Get the latest data for a device
     */
    public String getDeviceData(BluetoothDevice device) {
        if (device != null) {
            return deviceData.get(device.getAddress());
        }
        return null;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate your item layout. Make sure you have R.layout.item_bluetooth_device
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device, parent, false);
        return new DeviceViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = deviceList.get(position);
        boolean isConnected = isDeviceConnected(device);
        holder.bind(device, listener, context, isConnected);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // If no payload, do a full rebind
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // With payload, do a partial update
            BluetoothDevice device = deviceList.get(position);

            // Check if this is a data-only update
            if (payloads.contains("DATA_UPDATE")) {
                String deviceLatestData = getDeviceData(device);
                if (deviceLatestData != null && !deviceLatestData.isEmpty()) {
                    try {
                        // Try to extract the numeric value for determining level
                        float sensorValue = 0;
                        String numericPart = deviceLatestData.replaceAll("[^0-9.]", "");
                        if (!numericPart.isEmpty()) {
                            sensorValue = Float.parseFloat(numericPart);
                        }

                        // Add µg/m³ unit to the data if it doesn't already include it
                        if (!deviceLatestData.contains("µg/m³")) {
                            deviceLatestData = deviceLatestData + " µg/m³";
                        }
                        holder.dustSensorDataTextView.setText(deviceLatestData);
                        holder.dustSensorDataTextView.setVisibility(View.VISIBLE);

                        // Set the dust level text and color dot
                        Object[] levelInfo = getDustLevelInfo(sensorValue);
                        String levelText = "● " + levelInfo[0]; // Prepend dot character
                        int colorCode = (int) levelInfo[1];

                        holder.dustSensorLevelTextView.setText(levelText);
                        holder.dustSensorLevelTextView.setTextColor(colorCode);
                        holder.dustSensorLevelTextView.setVisibility(View.VISIBLE);
                    } catch (NumberFormatException e) {
                        // If we can't parse a number, just show the raw data
                        holder.dustSensorDataTextView.setText(deviceLatestData);
                        holder.dustSensorLevelTextView.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    class DeviceViewHolder extends RecyclerView.ViewHolder {
        // Example: These should match the IDs in your item_bluetooth_device.xml
        TextView textViewDeviceName;
        TextView textViewDeviceAddress;
        TextView dustSensorDataTextView;

        TextView emptyDeviceTextView;

        TextView dustSensorLevelTextView; // TextView for dust sensor level
        android.widget.Button connectButton; // Added the Connect button

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            // Example:
            textViewDeviceName = itemView.findViewById(R.id.device_name);
            //textViewDeviceAddress = itemView.findViewById(R.id.device_address);
            dustSensorDataTextView = itemView.findViewById(R.id.device_dustsensor_data);
            dustSensorLevelTextView = itemView.findViewById(R.id.device_dustsensor_data_level);
            emptyDeviceTextView = itemView.findViewById(R.id.emptyDeviceTextView); // TextView for empty state
            connectButton = itemView.findViewById(R.id.connect_button); // Find the Connect button
        }

        public void bind(final BluetoothDevice device, final DeviceActionListener listener, Context context, boolean isConnected) {
            String deviceNameStr;
            // Check for BLUETOOTH_CONNECT permission before accessing device name on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    deviceNameStr = device.getName();
                } else {
                    deviceNameStr = "Name Hidden (No Permission)";
                }
            } else {
                //noinspection MissingPermission
                deviceNameStr = device.getName(); // For older versions, direct access is often fine after discovery
            }

            if (deviceNameStr == null || deviceNameStr.isEmpty()) {
                textViewDeviceName.setText("Unknown Device");
            } else {
                textViewDeviceName.setText(deviceNameStr);
            }

            // We need to get the adapter from the parent class
            BluetoothDeviceAdapter adapter = BluetoothDeviceAdapter.this;

            // Display the latest data for this device if available
            String deviceLatestData = adapter.getDeviceData(device);
            if (deviceLatestData != null && !deviceLatestData.isEmpty()) {
                try {
                    // Try to extract the numeric value for determining level
                    float sensorValue = 0;
                    String numericPart = deviceLatestData.replaceAll("[^0-9.]", "");
                    if (!numericPart.isEmpty()) {
                        sensorValue = Float.parseFloat(numericPart);
                    }

                    // Add µg/m³ unit to the data if it doesn't already include it
                    if (!deviceLatestData.contains("µg/m³")) {
                        deviceLatestData = deviceLatestData + " µg/m³";
                    }
                    dustSensorDataTextView.setText(deviceLatestData);
                    dustSensorDataTextView.setVisibility(View.VISIBLE);

                    // Set the dust level text and color dot
                    Object[] levelInfo = getDustLevelInfo(sensorValue);
                    String levelText = "● " + levelInfo[0]; // Prepend dot character
                    int colorCode = (int) levelInfo[1];

                    dustSensorLevelTextView.setText(levelText);
                    dustSensorLevelTextView.setTextColor(colorCode);
                    dustSensorLevelTextView.setVisibility(View.VISIBLE);
                } catch (NumberFormatException e) {
                    // If we can't parse a number, just show the raw data
                    dustSensorDataTextView.setText(deviceLatestData);
                    dustSensorLevelTextView.setVisibility(View.GONE);
                }
            } else {
                // If no data available yet, show default text for all devices
                dustSensorDataTextView.setText("Waiting for data...");
                dustSensorDataTextView.setVisibility(View.VISIBLE); // Always visible
                dustSensorLevelTextView.setVisibility(View.GONE);
            }

            // Update button text and listener based on connection state
            if (isConnected) {
                connectButton.setText("Connected");
                connectButton.setOnClickListener(v -> listener.onDisconnectDevice(device));
            } else {
                connectButton.setText("Connect");
                connectButton.setOnClickListener(v -> listener.onConnectDevice(device));
            }

            // Remove the click listener from the entire item
            itemView.setOnClickListener(null);
        }
    }

    /**
     * Helper method to determine dust level text and color based on sensor value
     * @param dustValue The dust sensor value in µg/m³
     * @return Object array with [levelText, colorCode]
     */
    private Object[] getDustLevelInfo(float dustValue) {
        String levelText;
        int colorCode;

        if (dustValue > 200) {
            levelText = "Hazardous";
            colorCode = Color.RED;
        } else if (dustValue >= 100) {
            levelText = "Unhealthy";
            colorCode = Color.rgb(255, 165, 0); // Orange color
        } else {
            levelText = "Healthy";
            colorCode = Color.GREEN;
        }

        return new Object[]{levelText, colorCode};
    }
}
