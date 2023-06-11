package com.longtv.nwstagfinder.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.longtv.nwstagfinder.BuildConfig;
import com.longtv.nwstagfinder.ModelBase;
import com.longtv.nwstagfinder.R;
import com.longtv.nwstagfinder.WeakHandler;
import com.longtv.nwstagfinder.interfaces.TagFinderCallBack;
import com.longtv.nwstagfinder.rfid.DeviceListActivity;
import com.longtv.nwstagfinder.tagfinder.SignalPercentageConverter;
import com.longtv.nwstagfinder.tagfinder.TagFinderModel;
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

public class TagFinderHelper {
    private static TagFinderHelper instance;
    public static Reader mReader = null;
    public static boolean mIsSelectingReader = false;
    // Debugging
    private static final String TAG = "TagFinderHelper";
    private static final boolean D = BuildConfig.DEBUG;
    private static GenericHandler mGenericModelHandler;

    private String rssiText = "";

    //Create model class derived from ModelBase
    private static TagFinderModel mModel;
    private static TagFinderCallBack tagFinderCallBack;

    private static SignalPercentageConverter mPercentageConverter = new SignalPercentageConverter();

    // ReaderList Observers
    private static final Observable.Observer<Reader> mAddedObserver = (observable, reader) -> {
        // See if this newly added Reader should be used
        AutoSelectReader(true);
    };

    private static final Observable.Observer<Reader> mUpdatedObserver = (observable, reader) -> {
    };

    private static final Observable.Observer<Reader> mRemovedObserver = (observable, reader) -> {
        // Was the current Reader removed
        if (reader == mReader) {
            mReader = null;

            // Stop using the old Reader
            getCommander().setReader(mReader);
        }
    };

    private static class GenericHandler extends WeakHandler<Context> {
        public GenericHandler(Context t) {
            super(t);
        }

        @Override
        public void handleMessage(Message msg, Context t) {
            try {
                switch (msg.what) {
                    case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
                        if (mModel.error() != null) {
                            appendMessage("\n Task failed:\n" + mModel.error().getMessage() + "\n\n");
                        }
                        updateUI();
                        break;

                    case ModelBase.MESSAGE_NOTIFICATION:
                        String message = (String) msg.obj;
                        appendMessage(message);
                        break;

                    default:
                        break;
                }
            } catch (Exception e) {
            }

        }
    }

    private TagFinderHelper() {

    }

    public static TagFinderHelper getInstance() {
        if (instance == null) {
            instance = new TagFinderHelper();
        }
        return instance;
    }

    //
    // Handle the messages broadcast from the AsciiCommander
    //
    private static BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
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

                updateUI();
            }
        }
    };

    private static void displayReaderState() {
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
//        setTitle(connectionMsg); // title app bar
    }

    /**
     * @return the current AsciiCommander
     */
    public static AsciiCommander getCommander() {
        return AsciiCommander.sharedInstance();
    }

    public static void initialize(Context context, TagFinderCallBack callBack) {
        mGenericModelHandler = new GenericHandler(context);
        tagFinderCallBack = callBack;
        setupTagFinderHandler(context);
    }

    private static void setupTagFinderHandler(Context context) {
        // Ensure the shared instance of AsciiCommander exists
        AsciiCommander.createSharedInstance(context);

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
        ReaderManager.create(context);

        // Add observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);


        // Create a (custom) model and configure its commander and handler
        mModel = new TagFinderModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);

        mModel.setRawSignalDelegate(level -> {
            final String value = level == null ? "---" : String.format("%d %%", mPercentageConverter.asPercentage(level));
            tagFinderCallBack.rssiMessageCallBack(mModel.isScanning() ? value : "---");
        });

        mModel.setPercentageSignalDelegate(new ISignalStrengthReceivedDelegate() {
            @Override
            public void signalStrengthReceived(Integer level) {
                final String value = level == null ? "---" : level.toString() + "%";
                tagFinderCallBack.rssiMessageCallBack(mModel.isScanning() ? value : "---");
            }
        });

        mModel.setSignalStrengthCountDelegate(new ISignalStrengthCountDelegate() {
            @Override
            public void signalStrengthCount(Integer transponderCount) {
                tagFinderCallBack.countMessageCallBack(context.getString(R.string.count_label_text) + transponderCount);
            }
        });
    }

    /**
     * Automatically select the Reader to use
     */
    private static void AutoSelectReader(boolean attemptReconnect) {
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

    /**
     * Append the given message to the bottom of the results area
     */
    private static void appendMessage(String message) {
        tagFinderCallBack.appendMessageCallBack(message);
    }


    /**
     * Set the state for the UI controls
     */
    private static void updateUI() {
        boolean isConnected = getCommander().isConnected();
        boolean canIssueCommand = isConnected & !mModel.isBusy();
        String subTitle =(String.format("Using: %s ASCII command", mModel.isFindTagCommandAvailable() ? "\".ft\" - Find Tag" : "\".iv\" - Inventory"));
        tagFinderCallBack.rssiSubTitleCallBack(subTitle);
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
        tagFinderCallBack.rssiIntroduceCallBack(instructions);
    }

    public static synchronized void onResume(Context context) {
        if (mModel != null) {
            mModel.setEnabled(true);
        }

        if (mCommanderMessageReceiver == null) {
            return;
        }
        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(context).registerReceiver(mCommanderMessageReceiver, new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));

        if (ReaderManager.sharedInstance() == null) {
            return;
        }
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
        updateUI();
    }

    public static synchronized void onPause(Context context) {
        if (mModel != null) {
            mModel.setEnabled(false);
        }

        if (mCommanderMessageReceiver != null) {
            // Register to receive notifications from the AsciiCommander
            LocalBroadcastManager.getInstance(context).unregisterReceiver(mCommanderMessageReceiver);
        }

        if (ReaderManager.sharedInstance() != null) {
            // Disconnect from the reader to allow other Apps to use it
            // unless pausing when USB device attached or using the DeviceListActivity to select a Reader
            if (!mIsSelectingReader && !ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
                mReader.disconnect();
            }

            ReaderManager.sharedInstance().onPause();
        }
    }

    public static void onDestroy() {
        if (ReaderManager.sharedInstance() != null && ReaderManager.sharedInstance().getReaderList() != null) {
            // Remove observers for changes
            ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
            ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
            ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);
        }
    }

    public static void connectDevice(int readerIndex,int action){
        Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);

        // If already connected to a different reader then disconnect it
        if (mReader != null) {
            if (action == DeviceListHelper.DEVICE_CHANGE || action == DeviceListHelper.DEVICE_DISCONNECT) {
                mReader.disconnect();
                if (action == DeviceListHelper.DEVICE_DISCONNECT) {
                    mReader = null;
                }
            }
        }

        // Use the Reader found
        if (action == DeviceListHelper.DEVICE_CHANGE || action == DeviceListHelper.DEVICE_CONNECT) {
            mReader = chosenReader;
            getCommander().setReader(mReader);
        }
    }

    public static void disconnect(){
        if (mReader != null) {
            mReader.disconnect();
            mReader = null;
        }
    }

    public static void searchTagHex(String value){
        if(mModel!=null){
            mModel.setTargetTagEpc(value);
            updateUI();
        }
    }
}
