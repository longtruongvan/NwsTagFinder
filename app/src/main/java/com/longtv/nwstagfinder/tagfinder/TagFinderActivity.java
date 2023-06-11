package com.longtv.nwstagfinder.tagfinder;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.longtv.nwstagfinder.BuildConfig;
import com.longtv.nwstagfinder.ModelBase;
import com.longtv.nwstagfinder.R;
import com.longtv.nwstagfinder.WeakHandler;
import com.longtv.nwstagfinder.helpers.DeviceListHelper;
import com.longtv.nwstagfinder.helpers.TagFinderHelper;
import com.longtv.nwstagfinder.interfaces.TagFinderCallBack;
import com.longtv.nwstagfinder.rfid.DeviceListActivity;
import com.longtv.nwstagfinder.utils.PermissionUtility;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthCountDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.Observable;
import com.uk.tsl.utils.StringHelper;

public class TagFinderActivity extends AppCompatActivity {
    private MenuItem mConnectMenuItem;
    private MenuItem mDisconnectMenuItem;

    // Debugging
    private static final String TAG = "TagFinderActivity";
    private static final boolean D = BuildConfig.DEBUG;

    // The seek bar used to adjust the RF Output Power for RFID commands
    private SeekBar mPowerSeekBar;

    // The text-based parameters
    private EditText mTargetTagEditText;

    private TextView mRssiTextView;
    private TextView mRssiTitleTextView;
    private TextView mCountTextView;
    private TextView mRssiSubtitleTextView;
    private TextView mRssiInstructionTextView;

    private TextView mResultTextView;
    private ScrollView mResultScrollView;
    private TagFinderHelper tagFinderHelper = TagFinderHelper.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_finder);

        mBluetoothPermissionsPrompt = (TextView) findViewById(R.id.bluetooth_permissions_prompt);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // The SeekBar provides an integer value for the antenna power
        mPowerSeekBar = (SeekBar) findViewById(R.id.powerSeekBar);
        mPowerSeekBar.setEnabled(false);

        // Set up the action buttons
        Button clearActionButton;

        // Set up the target EPC EditText
        mTargetTagEditText = (EditText) findViewById(R.id.targetTagEditText);
//        mTargetTagEditText.addTextChangedListener(mTargetTagEditTextChangedListener);
//        mTargetTagEditText.setOnFocusChangeListener(mTargetTagFocusChangedListener);
        // Set up the RSSI display
        mRssiTextView = (TextView) findViewById(R.id.rssiTextView);
        mRssiTitleTextView = (TextView) findViewById(R.id.rssiTitleTextView);
        mCountTextView = (TextView) findViewById(R.id.countTextView);
        mRssiSubtitleTextView = (TextView) findViewById(R.id.rssiSubtitleTextView);
        mRssiInstructionTextView = (TextView) findViewById(R.id.rssiInstructionTextView);

        // Set up the results area
        mResultTextView = (TextView) findViewById(R.id.resultTextView);
        mResultScrollView = (ScrollView) findViewById(R.id.resultScrollView);


        //at first , check permission
        if (!PermissionUtility.hasPermission(this)) {
            PermissionUtility.requestPermission(this, 1);
        } else {
            tagFinderHelper.initialize(this, new TagFinderCallBack() {
                @Override
                public void appendMessageCallBack(String message) {
                    final String msg = message;
                    mResultScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            // Select the last row so it will scroll into view...
                            mResultTextView.append(msg);
                            mResultScrollView.post(new Runnable() {
                                public void run() {
                                    mResultScrollView.fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    });
                }

                @Override
                public void rssiMessageCallBack(String rssi) {
                    mRssiTextView.setText(rssi);
                }

                @Override
                public void rssiSubTitleCallBack(String subTitle) {
                    mRssiSubtitleTextView.setText(subTitle);
                }

                @Override
                public void rssiIntroduceCallBack(String introduce) {
                    mRssiInstructionTextView.setText(introduce);
                }

                @Override
                public void countMessageCallBack(String count) {
                    mCountTextView.setText(count);
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allPermissionsGranted = true;

            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (grantResults.length > 0 && allPermissionsGranted) {
                tagFinderHelper.initialize(this, new TagFinderCallBack() {
                    @Override
                    public void appendMessageCallBack(String message) {
                        final String msg = message;
                        mResultScrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                // Select the last row so it will scroll into view...
                                mResultTextView.append(msg);
                                mResultScrollView.post(new Runnable() {
                                    public void run() {
                                        mResultScrollView.fullScroll(View.FOCUS_DOWN);
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void rssiMessageCallBack(String rssi) {
                        mRssiTextView.setText(rssi);
                    }

                    @Override
                    public void rssiSubTitleCallBack(String subTitle) {
                        mRssiSubtitleTextView.setText(subTitle);
                    }

                    @Override
                    public void rssiIntroduceCallBack(String introduce) {
                        mRssiInstructionTextView.setText(introduce);
                    }

                    @Override
                    public void countMessageCallBack(String count) {
                        mCountTextView.setText(count);
                    }
                });
            } else {
                Toast.makeText(this, "Authorization denied. The application cannot work.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case DeviceListHelper.SELECT_DEVICE_REQUEST:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    int readerIndex = data.getExtras().getInt(DeviceListHelper.EXTRA_DEVICE_INDEX);
                    Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);

                    int action = data.getExtras().getInt(DeviceListHelper.EXTRA_DEVICE_ACTION);

                    tagFinderHelper.connectDevice(readerIndex, action);
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_tag_finder, menu);

        mConnectMenuItem = menu.findItem(R.id.connect_reader_menu_item);
        mDisconnectMenuItem = menu.findItem(R.id.disconnect_reader_menu_item);

        return true;
    }

    /**
     * Prepare the menu options
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (TagFinderHelper.getCommander() != null) {
            boolean isConnected = TagFinderHelper.getCommander().isConnected();
            mDisconnectMenuItem.setEnabled(isConnected);
        }

        mConnectMenuItem.setEnabled(true);
        mConnectMenuItem.setTitle((TagFinderHelper.mReader != null && TagFinderHelper.mReader.isConnected() ? R.string.change_reader_menu_item_text : R.string.connect_reader_menu_item_text));

        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    protected void onPause() {
        super.onPause();
        TagFinderHelper.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TagFinderHelper.onResume(this);
    }

    @Override
    protected void onDestroy() {
        TagFinderHelper.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {

            case R.id.connect_reader_menu_item:
                // Launch the DeviceListActivity to see available Readers
                TagFinderHelper.mIsSelectingReader = true;
                int index = -1;
                if (TagFinderHelper.mReader != null) {
                    index = ReaderManager.sharedInstance().getReaderList().list().indexOf(TagFinderHelper.mReader);
                }
                Intent selectIntent = new Intent(this, DeviceListActivity.class);
                if (index >= 0) {
                    selectIntent.putExtra(DeviceListHelper.EXTRA_DEVICE_INDEX, index);
                }
                startActivityForResult(selectIntent, DeviceListHelper.SELECT_DEVICE_REQUEST);
                return true;

            case R.id.disconnect_reader_menu_item:
                if (TagFinderHelper.mReader != null) {
                    TagFinderHelper.mReader.disconnect();
                    TagFinderHelper.mReader = null;
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private TextView mBluetoothPermissionsPrompt;

    private void checkForBluetoothPermission() {
        // Older permissions are granted at install time
        if (Build.VERSION.SDK_INT < 31) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            mBluetoothPermissionsPrompt.setVisibility(View.VISIBLE);
            if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected. In this UI,
                // include a "cancel" or "no thanks" button that allows the user to
                // continue using your app without granting the permission.
                offerBluetoothPermissionRationale();
            } else {
                requestPermissionLauncher.launch(bluetoothPermissions);
            }
        } else {
            mBluetoothPermissionsPrompt.setVisibility(View.GONE);
        }
    }

    private final String[] bluetoothPermissions = new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};

    void offerBluetoothPermissionRationale() {
        // Older permissions are granted at install time
        if (Build.VERSION.SDK_INT < 31) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Permission is required to connect to TSL Readers over Bluetooth")
                .setTitle("Allow Bluetooth?");

        builder.setPositiveButton("Show Permission Dialog", new DialogInterface.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.S)
            public void onClick(DialogInterface dialog, int id) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
            }
        });


        AlertDialog dialog = builder.create();
        dialog.show();
    }


    void showBluetoothPermissionDeniedConsequences() {
        // Note: When permissions have been denied, this will be invoked everytime checkForBluetoothPermission() is called
        // In your app, we suggest you limit the number of times the User is notified.
        Toast.makeText(this, "This app will not be able to connect to TSL Readers via Bluetooth.", Toast.LENGTH_LONG).show();
    }


    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissionsGranted ->
            {
                //boolean allGranted = permissionsGranted.values().stream().reduce(true, Boolean::logicalAnd);
                boolean allGranted = true;
                for (boolean isGranted : permissionsGranted.values()) {
                    allGranted = allGranted && isGranted;
                }

                if (allGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.

                    // Update the ReaderList which will add any unknown reader, firing events appropriately
                    if (ReaderManager.sharedInstance() != null) {
                        ReaderManager.sharedInstance().updateList();
                        mBluetoothPermissionsPrompt.setVisibility(View.GONE);
                    }
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    showBluetoothPermissionDeniedConsequences();
                }
            });

}
