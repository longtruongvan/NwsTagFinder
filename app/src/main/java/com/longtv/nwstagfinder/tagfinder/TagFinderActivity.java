package com.longtv.nwstagfinder.tagfinder;

import static com.longtv.nwstagfinder.rfid.DeviceListActivity.EXTRA_DEVICE_ACTION;
import static com.longtv.nwstagfinder.rfid.DeviceListActivity.EXTRA_DEVICE_INDEX;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.longtv.nwstagfinder.rfid.DeviceListActivity;
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
    // The Reader currently in use
    private Reader mReader = null;

    private boolean mIsSelectingReader = false;

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

    //Create model class derived from ModelBase
    private TagFinderModel mModel;

    private SignalPercentageConverter mPercentageConverter = new SignalPercentageConverter();


    /**
     * @return the current AsciiCommander
     */
    protected AsciiCommander getCommander() {
        return AsciiCommander.sharedInstance();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_finder);

        mBluetoothPermissionsPrompt = (TextView) findViewById(R.id.bluetooth_permissions_prompt);

        mGenericModelHandler = new GenericHandler(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // The SeekBar provides an integer value for the antenna power
        mPowerSeekBar = (SeekBar) findViewById(R.id.powerSeekBar);
        mPowerSeekBar.setEnabled(false);

        // Set up the action buttons
        Button clearActionButton;

        // Set up the target EPC EditText
        mTargetTagEditText = (EditText) findViewById(R.id.targetTagEditText);
        mTargetTagEditText.addTextChangedListener(mTargetTagEditTextChangedListener);
        mTargetTagEditText.setOnFocusChangeListener(mTargetTagFocusChangedListener);
        // Set up the RSSI display
        mRssiTextView = (TextView) findViewById(R.id.rssiTextView);
        mRssiTitleTextView = (TextView) findViewById(R.id.rssiTitleTextView);
        mCountTextView = (TextView) findViewById(R.id.countTextView);
        mRssiSubtitleTextView = (TextView) findViewById(R.id.rssiSubtitleTextView);
        mRssiInstructionTextView = (TextView) findViewById(R.id.rssiInstructionTextView);

        // Set up the results area
        mResultTextView = (TextView) findViewById(R.id.resultTextView);
        mResultScrollView = (ScrollView) findViewById(R.id.resultScrollView);


        // Ensure the shared instance of AsciiCommander exists
        AsciiCommander.createSharedInstance(getApplicationContext());

        final AsciiCommander commander = getCommander();

        // Ensure that all existing responders are removed
        commander.clearResponders();

        // Add the LoggerResponder - this simply echoes all lines received from the reader to the log
        // and passes the line onto the next responder
        // This is ADDED FIRST so that no other responder can consume received lines before they are logged.
        commander.addResponder(new LoggerResponder());

        // Add responder to enable the synchronous commands
        commander.addSynchronousResponder();

        // Configure the ReaderManager when necessary
        ReaderManager.create(getApplicationContext());

        // Add observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);


        // Create a (custom) model and configure its commander and handler
        mModel = new TagFinderModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);

        mModel.setRawSignalDelegate(new ISignalStrengthReceivedDelegate() {
            @Override
            public void signalStrengthReceived(Integer level) {
                final String value = level == null ? "---" : String.format("%d %%", mPercentageConverter.asPercentage(level));
                mRssiTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        mRssiTextView.setText(mModel.isScanning() ? value : "---");
                    }
                });
            }
        });

        mModel.setPercentageSignalDelegate(new ISignalStrengthReceivedDelegate() {
            @Override
            public void signalStrengthReceived(Integer level) {
                final String value = level == null ? "---" : level.toString() + "%";
                mRssiTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        mRssiTextView.setText(mModel.isScanning() ? value : "---");
                    }
                });
            }
        });

        mModel.setSignalStrengthCountDelegate(new ISignalStrengthCountDelegate() {
            @Override
            public void signalStrengthCount(Integer transponderCount) {
                mCountTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        mCountTextView.setText(getString(R.string.count_label_text) + transponderCount);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkForBluetoothPermission();
    }

    //----------------------------------------------------------------------------------------------
    // Menu
    //----------------------------------------------------------------------------------------------

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
        boolean isConnected = getCommander().isConnected();
        mDisconnectMenuItem.setEnabled(isConnected);

        mConnectMenuItem.setEnabled(true);
        mConnectMenuItem.setTitle((mReader != null && mReader.isConnected() ? R.string.change_reader_menu_item_text : R.string.connect_reader_menu_item_text));

        return super.onPrepareOptionsMenu(menu);
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
                mIsSelectingReader = true;
                int index = -1;
                if (mReader != null) {
                    index = ReaderManager.sharedInstance().getReaderList().list().indexOf(mReader);
                }
                Intent selectIntent = new Intent(this, DeviceListActivity.class);
                if (index >= 0) {
                    selectIntent.putExtra(EXTRA_DEVICE_INDEX, index);
                }
                startActivityForResult(selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST);
                return true;

            case R.id.disconnect_reader_menu_item:
                if (mReader != null) {
                    mReader.disconnect();
                    mReader = null;
                }

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //----------------------------------------------------------------------------------------------
    // Pause & Resume life cycle
    //----------------------------------------------------------------------------------------------

    @Override
    public synchronized void onPause() {
        super.onPause();

        mModel.setEnabled(false);

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommanderMessageReceiver);

        // Disconnect from the reader to allow other Apps to use it
        // unless pausing when USB device attached or using the DeviceListActivity to select a Reader
        if (!mIsSelectingReader && !ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
            mReader.disconnect();
        }

        ReaderManager.sharedInstance().onPause();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        mModel.setEnabled(true);

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).registerReceiver(mCommanderMessageReceiver, new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));

        // Remember if the pause/resume was caused by ReaderManager - this will be cleared when ReaderManager.onResume() is called
        boolean readerManagerDidCauseOnPause = ReaderManager.sharedInstance().didCauseOnPause();

        // The ReaderManager needs to know about Activity lifecycle changes
        ReaderManager.sharedInstance().onResume();

        // The Activity may start with a reader already connected (perhaps by another App)
        // Update the ReaderList which will add any unknown reader, firing events appropriately
        ReaderManager.sharedInstance().updateList();

        // Locate a Reader to use when necessary
        AutoSelectReader(!readerManagerDidCauseOnPause);

        mIsSelectingReader = false;

        displayReaderState();
        UpdateUI();
    }

    //
    // Automatically select the Reader to use
    //
    private void AutoSelectReader(boolean attemptReconnect) {
        ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
        Reader usbReader = null;
        if (readerList.list().size() >= 1) {
            // Currently only support a single USB connected device so we can safely take the
            // first CONNECTED reader if there is one
            for (Reader reader : readerList.list()) {
                if (reader.hasTransportOfType(TransportType.USB)) {
                    usbReader = reader;
                    break;
                }
            }
        }

        if (mReader == null) {
            if (usbReader != null) {
                // Use the Reader found, if any
                mReader = usbReader;
                getCommander().setReader(mReader);
            }
        } else {
            // If already connected to a Reader by anything other than USB then
            // switch to the USB Reader
            IAsciiTransport activeTransport = mReader.getActiveTransport();
            if (activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
                appendMessage("Disconnecting from: " + mReader.getDisplayName() + "\n");
                mReader.disconnect();

                mReader = usbReader;

                // Use the Reader found, if any
                getCommander().setReader(mReader);
            }
        }

        // Reconnect to the chosen Reader
        if (mReader != null
                && !mReader.isConnecting()
                && (mReader.getActiveTransport() == null || mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
            // Attempt to reconnect on the last used transport unless the ReaderManager is cause of OnPause (USB device connecting)
            if (attemptReconnect) {
                if (mReader.allowMultipleTransports() || mReader.getLastTransportType() == null) {
                    // Reader allows multiple transports or has not yet been connected so connect to it over any available transport
                    if (mReader.connect()) {
                        appendMessage("Connecting to: " + mReader.getDisplayName() + "\n");
                    } else {
                        appendMessage("Unable to start connecting to: " + mReader.getDisplayName() + "\n");
                    }
                } else {
                    // Reader supports only a single active transport so connect to it over the transport that was last in use
                    if (mReader.connect(mReader.getLastTransportType())) {
                        appendMessage("Connecting (over last transport) to: " + mReader.getDisplayName() + "\n");
                    } else {
                        appendMessage("Unable to start connecting to: " + mReader.getDisplayName() + "\n");
                    }
                }
            }
        }
    }


    // ReaderList Observers
    Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
        @Override
        public void update(Observable<? extends Reader> observable, Reader reader) {
            // See if this newly added Reader should be used
            AutoSelectReader(true);
        }
    };

    Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
        @Override
        public void update(Observable<? extends Reader> observable, Reader reader) {
        }
    };

    Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
        @Override
        public void update(Observable<? extends Reader> observable, Reader reader) {
            // Was the current Reader removed
            if (reader == mReader) {
                mReader = null;

                // Stop using the old Reader
                getCommander().setReader(mReader);
            }
        }
    };


    //----------------------------------------------------------------------------------------------
    // Model notifications
    //----------------------------------------------------------------------------------------------

    private static class GenericHandler extends WeakHandler<TagFinderActivity> {
        public GenericHandler(TagFinderActivity t) {
            super(t);
        }

        @Override
        public void handleMessage(Message msg, TagFinderActivity t) {
            try {
                switch (msg.what) {
                    case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
                        if (t.mModel.error() != null) {
                            t.appendMessage("\n Task failed:\n" + t.mModel.error().getMessage() + "\n\n");
                        }
                        t.UpdateUI();
                        break;

                    case ModelBase.MESSAGE_NOTIFICATION:
                        String message = (String) msg.obj;
                        t.appendMessage(message);
                        break;

                    default:
                        break;
                }
            } catch (Exception e) {
            }

        }
    }

    ;

    // The handler for model messages
    private static GenericHandler mGenericModelHandler;

    //----------------------------------------------------------------------------------------------
    // UI state and display update
    //----------------------------------------------------------------------------------------------

    // Append the given message to the bottom of the results area
    private void appendMessage(String message) {
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

    private void displayReaderState() {

        String connectionMsg = "Reader: ";
        switch (getCommander().getConnectionState()) {
            case CONNECTED:
                connectionMsg += getCommander().getConnectedDeviceName();
                break;
            case CONNECTING:
                connectionMsg += "Connecting...";
                break;
            default:
                connectionMsg += "Disconnected";
        }
        setTitle(connectionMsg);
    }


    //
    // Set the state for the UI controls
    //
    private void UpdateUI() {
        boolean isConnected = getCommander().isConnected();
        boolean canIssueCommand = isConnected & !mModel.isBusy();

        mRssiSubtitleTextView.setText(String.format("Using: %s ASCII command", mModel.isFindTagCommandAvailable() ? "\".ft\" - Find Tag" : "\".iv\" - Inventory"));
        String instructions = "";
        if (isConnected) {
            if (StringHelper.isNullOrEmpty(mModel.getTargetTagEpc())) {
                instructions = "Enter a full or partial EPC.";
            } else {
                instructions = "Pull trigger to scan";
            }
        } else {
            instructions = "Connect a TSL Reader";
        }
        mRssiInstructionTextView.setText(instructions);

    }


    //----------------------------------------------------------------------------------------------
    // AsciiCommander message handling
    //----------------------------------------------------------------------------------------------

    //
    // Handle the messages broadcast from the AsciiCommander
    //
    private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String connectionStateMsg = getCommander().getConnectionState().toString();
            Log.d("", "AsciiCommander state changed - isConnected: " + getCommander().isConnected() + " (" + connectionStateMsg + ")");
            Log.d("TFA", String.format("IsConnecting: %s", mReader == null ? "No Reader" : mReader.isConnecting()));

            if (getCommander() != null) {
                displayReaderState();

                if (getCommander().isConnected()) {
                    appendMessage("Connected to: " + mReader.getDisplayName() + "\n");
                    mModel.resetDevice();
                    // Use a new converter to reset dynamic range
                    mPercentageConverter = new SignalPercentageConverter();
                } else if (getCommander().getConnectionState() == ConnectionState.DISCONNECTED) {
                    // A manual disconnect will have cleared mReader
                    if (mReader != null) {
                        // See if this is from a failed connection attempt
                        if (!mReader.wasLastConnectSuccessful()) {
                            // Unable to connect so have to choose reader again
                            mReader = null;
                        }
                    }
                }

                UpdateUI();
            }
        }
    };

    //----------------------------------------------------------------------------------------------
    // Handler for
    //----------------------------------------------------------------------------------------------

    private TextWatcher mTargetTagEditTextChangedListener = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String value = s.toString();

            mModel.setTargetTagEpc(value);
            UpdateUI();
        }
    };


    //----------------------------------------------------------------------------------------------
    // Handler for when editing has finished on the target tag
    //----------------------------------------------------------------------------------------------

    private View.OnFocusChangeListener mTargetTagFocusChangedListener =
            new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        mModel.setTargetTagEpc(mTargetTagEditText.getText().toString());
                        mModel.updateTarget();
                    }
                }
            };

    //
    // Handle Intent results
    //
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case DeviceListActivity.SELECT_DEVICE_REQUEST:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    int readerIndex = data.getExtras().getInt(EXTRA_DEVICE_INDEX);
                    Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);

                    int action = data.getExtras().getInt(EXTRA_DEVICE_ACTION);

                    // If already connected to a different reader then disconnect it
                    if (mReader != null) {
                        if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_DISCONNECT) {
                            mReader.disconnect();
                            if (action == DeviceListActivity.DEVICE_DISCONNECT) {
                                mReader = null;
                            }
                        }
                    }

                    // Use the Reader found
                    if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_CONNECT) {
                        mReader = chosenReader;
                        getCommander().setReader(mReader);
                    }
                }
                break;
        }
    }

    //----------------------------------------------------------------------------------------------
    // Bluetooth permissions checking
    //----------------------------------------------------------------------------------------------

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
                    ReaderManager.sharedInstance().updateList();
                    mBluetoothPermissionsPrompt.setVisibility(View.GONE);
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
