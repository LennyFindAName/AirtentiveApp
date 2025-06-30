package com.example.airtentiveapp.ui.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.airtentiveapp.databinding.FragmentBluetoothBinding;
import com.example.airtentiveapp.ui.shared.SharedBluetoothViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("SetTextI18n")
public class BluetoothFragment extends Fragment implements BluetoothDeviceAdapter.DeviceActionListener {
    private static final String TAG = "BluetoothActivity";
    private FragmentBluetoothBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<Intent> requestBluetoothEnableLauncher;
    private ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothDeviceAdapter deviceAdapter;
    // Map to store multiple bluetooth clients, keyed by device address
    private Map<String, BluetoothClient> bluetoothClients = new HashMap<>();
    private SharedBluetoothViewModel sharedBluetoothViewModel;

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.i(TAG, "Receiver triggered with action: " + action);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }

                if (device != null) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                            || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

                        String deviceName = getDeviceNameSafe(device);
                        String deviceAddress = device.getAddress();

                        // Avoid duplicates by address
                        boolean alreadyFound = false;
                        for (BluetoothDevice d : devices) {
                            if (d.getAddress().equals(deviceAddress)) {
                                alreadyFound = true;
                                break;
                            }
                        }

                        if (!alreadyFound) {
                           if (!alreadyFound && (deviceName.equals("DustSensor") || deviceName.equals("CANTFINDANAME") || deviceName.equals("LAPTOP-M8VIQSNJ"))) {
                            devices.add(device);
                            Log.i(TAG, "Tìm thấy thiết bị: " + deviceName + " - " + deviceAddress);
                            // Notify adapter of data change to refresh RecyclerView
                            deviceAdapter.notifyDataSetChanged();
                         }
                        }

                    } else {
                        // If permission denied, just add with address only if not already added
                        boolean alreadyFound = false;
                        for (BluetoothDevice d : devices) {
                            if (d.getAddress().equals(device.getAddress())) {
                                alreadyFound = true;
                                break;
                            }
                        }

                        if (!alreadyFound) {
                            devices.add(device);
                            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, can't get name for " + device.getAddress());
                            deviceAdapter.notifyDataSetChanged();
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.i(TAG, "Discovery Started...");
                Toast.makeText(requireContext(), "Đang truy quét thiết bị ...", Toast.LENGTH_LONG).show();
                //binding.textViewStatus.setText("Đang truy quét thiết bị ...");
                binding.buttonScan.setEnabled(false);
                devices.clear();  // Clear device list before new scan
                deviceAdapter.notifyDataSetChanged();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.i(TAG, "Discovery Finished.");
                Toast.makeText(requireContext(), "Truy quét hoàn tất", Toast.LENGTH_LONG).show();
                binding.buttonScan.setEnabled(true);

                if (devices.isEmpty()) {
                    Toast.makeText(requireContext(), "Không tìm thấy thiết bị nào.", Toast.LENGTH_LONG).show();
                    // Show empty state text view when no devices are found
                    binding.emptyDeviceTextView.setVisibility(View.VISIBLE);
                } else {
                    // Hide empty state when devices are found
                    binding.emptyDeviceTextView.setVisibility(View.GONE);
                }
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        BluetoothManager bluetoothManager;
        super.onCreate(savedInstanceState);
        binding = FragmentBluetoothBinding.inflate(getLayoutInflater());

        // Get the shared Bluetooth ViewModel
        sharedBluetoothViewModel = new ViewModelProvider(requireActivity()).get(SharedBluetoothViewModel.class);

        binding.recyclerViewDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        // Pass the list of devices to your adapter's constructor
        deviceAdapter = new BluetoothDeviceAdapter(devices, this, requireContext());
        binding.recyclerViewDevices.setAdapter(deviceAdapter);

        // Initialize empty state view - hide it initially
        binding.emptyDeviceTextView.setVisibility(View.GONE);

        //binding.textViewReceivedData.setMovementMethod(new ScrollingMovementMethod()); // Make it scrollable

        bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        initializeActivityResultLaunchers();

        binding.buttonScan.setOnClickListener(v -> checkPermissionsAndInitiateScan());

        // Register the BroadcastReceiver for Bluetooth discovery
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED); // For Android 13+
        } else {
            requireContext().registerReceiver(discoveryReceiver, filter);
        }

        return binding.getRoot();
    }

    private void onDeviceClicked(BluetoothDevice device) {
        String deviceAddress = device.getAddress();

        // Check if already connected to this device
        if (bluetoothClients.containsKey(deviceAddress)) {
            Toast.makeText(requireContext(), "Already connected to: " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Đang kết nối với: " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "BLUETOOTH_SCAN permission needed to cancel discovery.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Cancel discovery before connecting
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // Create new BluetoothClient for this device
        BluetoothClient bluetoothClient = new BluetoothClient(requireContext());
        bluetoothClient.setCallback(new BluetoothDataCallback() {
            @Override
            public void onDataReceived(String data) {
                // Check if fragment is still attached to avoid crashes
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Only update UI if binding is still valid
                        if (binding != null) {
                            // Update the specific device data in the adapter instead of the shared TextView
                            deviceAdapter.updateDeviceData(device, data);

                            // Also update the shared ViewModel for other fragments that might need it
                            //sharedBluetoothViewModel.setBluetoothData(data);
                        }
                    });
                } else {
                    // Fragment is detached, but we can still update the ViewModel
                    // for other fragments to see the data
                    sharedBluetoothViewModel.setBluetoothData(data);
                    Log.d(TAG, "Data received when fragment detached: " + data);
                }
            }

            @Override
            public void onConnected() {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Show toast message instead of updating text binding
                        Toast.makeText(getContext(), "Kết nối thành công với " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();

                        // Store the client in the map once connection is successful
                        bluetoothClients.put(deviceAddress, bluetoothClient);

                        // Update the adapter to show the device as connected
                        deviceAdapter.updateConnectionState(device, true);
                    });
                }
            }

            @Override
            public void onConnectionFailed(Exception e) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Show toast message instead of updating text binding
                        Toast.makeText(getContext(), "Kết nối thất bại, vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
        bluetoothClient.connectToDevice(device);
    }

    // DeviceActionListener interface methods
    @Override
    public void onConnectDevice(BluetoothDevice device) {
        // Same as previous onDeviceClicked but with connection tracking
        connectToDevice(device);
    }

    @Override
    public void onDisconnectDevice(BluetoothDevice device) {
        Toast.makeText(requireContext(), "Ngắt kết nối: " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();

        // If we have an active client, disconnect it
        BluetoothClient client = bluetoothClients.get(device.getAddress());
        if (client != null) {
            client.disconnect();
            bluetoothClients.remove(device.getAddress());

            // Update the adapter to show the device as disconnected
            deviceAdapter.updateConnectionState(device, false);

            // Clear the received data text
           /* if (binding != null) {
                binding.textViewReceivedData.setText("");
            }*/
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        String deviceAddress = device.getAddress();

        // Check if already connected to this device
        if (bluetoothClients.containsKey(deviceAddress)) {
            Toast.makeText(requireContext(), "Already connected to: " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Đang kết nối với: " + getDeviceNameSafe(device), Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "BLUETOOTH_SCAN permission needed to cancel discovery.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Cancel discovery before connecting
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // Connect to device using BluetoothClient
        BluetoothClient bluetoothClient = new BluetoothClient(requireContext());
        bluetoothClient.setCallback(new BluetoothDataCallback() {
            @Override
            public void onDataReceived(String data) {
                // Check if fragment is still attached to avoid crashes
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Only update UI if binding is still valid
                        if (binding != null) {
                            // Update the specific device data in the adapter instead of the shared TextView
                            deviceAdapter.updateDeviceData(device, data);

                            // Also update the shared ViewModel for other fragments that might need it
                            sharedBluetoothViewModel.setBluetoothData(data);
                        }
                    });
                } else {
                    // Fragment is detached, but we can still update the ViewModel
                    // for other fragments to see the data
                    sharedBluetoothViewModel.setBluetoothData(data);
                    Log.d(TAG, "Data received when fragment detached: " + data);
                }
            }

            @Override
            public void onConnected() {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Show toast message
                        Toast.makeText(getContext(), "Kết nối thành công", Toast.LENGTH_SHORT).show();

                        // Update the adapter to show the device as connected
                        deviceAdapter.updateConnectionState(device, true);
                    });
                }
            }

            @Override
            public void onConnectionFailed(Exception e) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Show toast message
                        Toast.makeText(getContext(), "Kết nối thất bại, vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
        bluetoothClient.connectToDevice(device);
    }

    private String getDeviceNameSafe(BluetoothDevice device) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            String name = device.getName();
            return (name != null && !name.isEmpty()) ? name : "Unknown Device";
        }
        return "Name Hidden (No Permission)";
    }

    private void initializeActivityResultLaunchers() {
        requestBluetoothEnableLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Toast.makeText(requireContext(), "Bluetooth đã kích hoạt", Toast.LENGTH_SHORT).show();
                startScanningForDevices();
            } else {
                Toast.makeText(requireContext(), "Bluetooth từ chối kích hoạt", Toast.LENGTH_SHORT).show();
                //binding.textViewStatus.setText("Bluetooth bị từ chối kích hoạt");
            }
        });

//        requestMultiplePermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
//            boolean allPermissionsGranted = true;
//            for (Boolean granted : permissions.values()) {
//                if (Boolean.FALSE.equals(granted)) {
//                    allPermissionsGranted = false;
//                    break;
//                }
//            }
//            if (allPermissionsGranted) {
//                Toast.makeText(BluetoothActivity.this, "Permissions Granted", Toast.LENGTH_SHORT).show();
//                checkAndEnableBluetooth();
//            } else {
//                Toast.makeText(BluetoothActivity.this, "Some permissions were denied. Cannot scan.", Toast.LENGTH_LONG).show();
//                binding.textViewStatus.setText("Status: Permissions denied");
//            }
//        });

        try {
            requestMultiplePermissionsLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        List<String> deniedPermissions = new ArrayList<>();

                        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                            if (Boolean.FALSE.equals(entry.getValue())) {
                                deniedPermissions.add(entry.getKey());
                            }
                        }

                        if (!deniedPermissions.isEmpty()) {
                            Toast.makeText(requireContext(), "Quyền truy cập cho phép:", Toast.LENGTH_SHORT).show();
                            checkAndEnableBluetooth();
                        } else {
                            String deniedList = TextUtils.join(", ", deniedPermissions);
                            String message = "Quyền truy cập từ chối: " + deniedList;
                            Log.e(TAG, "Quyền truy cập từ chối: " + deniedList);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            //binding.textViewStatus.setText("Trạng thái: " + message);
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error registering ActivityResultLauncher: " + e.getMessage());
        }

    }


    private void checkPermissionsAndInitiateScan() {
        List<String> requiredPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT); // For device name and connection
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION); // Always for classic BT discovery

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            requestMultiplePermissionsLauncher.launch(missingPermissions.toArray(new String[0]));
        } else {
            checkAndEnableBluetooth();
        }
    }

    private void checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            //binding.textViewStatus.setText("Thiêt bị không hỗ trợ Bluetooth.");
            Toast.makeText(requireContext(), "Thiêt bị không hỗ trợ Bluetooth.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    requestBluetoothEnableLauncher.launch(enableBtIntent);
                } else {
                    Toast.makeText(requireContext(), "Quyền truy cập Bluetooth phải được cho phép", Toast.LENGTH_LONG).show();
                    //binding.textViewStatus.setText("Quyền truy cập Bluetooth phải được cho phép");
                    // Optionally, re-trigger permission request here or guide user
                }
            } else {
                requestBluetoothEnableLauncher.launch(enableBtIntent);
            }
        } else {
            startScanningForDevices();
        }
    }

    private void startScanningForDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            //binding.textViewStatus.setText("Vui lòng bật Bluetooth để quét thiết bị.");
            Toast.makeText(requireContext(), "Bluetooth chưa được bật", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for necessary scan permissions before starting discovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                //binding.textViewStatus.setText("Quyền truy cập Bluetooth bị thiếu");
                Toast.makeText(requireContext(), "Quyền truy cập Bluetooth bị thiếu.", Toast.LENGTH_LONG).show();
                // Optionally, re-trigger permission request
                return;
            }
        } else { // For older versions, BLUETOOTH_ADMIN is the primary concern for starting discovery
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                //binding.textViewStatus.setText("Quyền truy cập Bluetooth Admin bị thiếu");
                Toast.makeText(requireContext(), "Quyền truy cập Bluetooth Admin bị thiếu", Toast.LENGTH_LONG).show();
                return;
            }
        }
        // Also ensure location permission is granted for discovery
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //binding.textViewStatus.setText("Quyền truy cập vị trí bị thiếu");
            Toast.makeText(requireContext(), "Quyền truy cập vị trí bị thiếu", Toast.LENGTH_LONG).show();
            // Optionally, re-trigger permission request
            return;
        }

        ensureLocationEnabled();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery(); // Always cancel ongoing discovery before starting a new one
        }

        boolean discoveryStarted = bluetoothAdapter.startDiscovery();
        if (discoveryStarted) {
            //binding.textViewStatus.setText("Đang truy quét thiết bị ...");
            Log.i(TAG, "Attempting to start discovery...");
        } else {
           // binding.textViewStatus.setText("Quáy trình truy quét không thành công.");
            Log.e(TAG, "Failed to start discovery. Check permissions and BT state carefully.");
        }
    }

    private void ensureLocationEnabled() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationServicesEnabled = LocationManagerCompat.isLocationEnabled(locationManager);
        if (!isLocationServicesEnabled) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Enable Location")
                    .setMessage("Location services are required for Bluetooth scanning. Enable now?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Take user to Location Settings
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        dialog.dismiss();
                        // Optionally: update UI status
                       // binding.textViewStatus.setText("Truy quét không thể thực hiện vì dịch vụ vị trí chưa được bật.");
                    })
                    .show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver
        try {
            requireContext().unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered or already unregistered.", e);
        }

        // Cancel discovery if it's running
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

            } else { // For older versions, BLUETOOTH_ADMIN is needed to cancel
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

            }
        }
    }
}

