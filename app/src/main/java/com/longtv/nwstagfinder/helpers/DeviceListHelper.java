package com.longtv.nwstagfinder.helpers;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.longtv.nwstagfinder.interfaces.ListDeviceCallBack;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.utils.Observable;

public class DeviceListHelper {
    private static DeviceListHelper instance;
    // Debugging
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;

    // Intent request codes
    public static final int SELECT_DEVICE_REQUEST = 0x5344;


    /// Return Intent extra
    public static String EXTRA_DEVICE_INDEX = "tsl_device_index";
    public static String EXTRA_DEVICE_ACTION = "tsl_device_action";

    /// Actions requested for the chosen device
    public static int DEVICE_CONNECT = 1;
    public static int DEVICE_CHANGE = 2;
    public static int DEVICE_DISCONNECT = 3;

    public static ObservableReaderList mReaders;
    public static Reader mSelectedReader = null;
    private static ListDeviceCallBack listDeviceCallBack;

    public static int selectedRowIndex = -1;

    private static final Observable.Observer<Reader> mAddedObserver = (observable, reader) -> {
        if (D) {
            Log.d(TAG, "Reader arrived");
        }
        int readerIndex = ReaderManager.sharedInstance().getReaderList().list().indexOf(reader);
        listDeviceCallBack.notifyItemInserted(readerIndex);

        // If the new Reader is connected over USB then this will be auto selected and
        if (reader.hasTransportOfType(TransportType.USB)) {
            returnSelectedReader(readerIndex, mSelectedReader != null ? DEVICE_CHANGE : DEVICE_CONNECT);
        }
    };

    private static final Observable.Observer<Reader> mUpdatedObserver = (observable, reader) -> {
        if (D) {
            Log.d(TAG, "Reader updated");
        }
        int readerIndex = ReaderManager.sharedInstance().getReaderList().list().indexOf(reader);
        // A Reader has changed - check to see if it is the currently selected Reader and no longer connected
            if (!reader.isConnected() && selectedRowIndex == readerIndex) {
                listDeviceCallBack.setSelectedRowIndex(-1);
            }
            listDeviceCallBack.notifyItemChanged(readerIndex);
    };

    private static final Observable.Observer<Reader> mRemovedObserver = (observable, reader) -> {
        if (D) {
            Log.d(TAG, "Reader Removed");
        }
        int readerIndex = ReaderManager.sharedInstance().getReaderList().list().indexOf(reader);
            if (selectedRowIndex == readerIndex) {
                listDeviceCallBack.setSelectedRowIndex(-1);
            }
            listDeviceCallBack.notifyItemRemoved(readerIndex);
    };

    private DeviceListHelper() {

    }

    public static DeviceListHelper getInstance() {
        return new DeviceListHelper();
    }


    public static void initialize(Context context, ListDeviceCallBack callBack) {
        mReaders = ReaderManager.sharedInstance().getReaderList();
        listDeviceCallBack = callBack;
        // Configure the ReaderManager when necessary
        ReaderManager.create(context);

        // Add observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

    }

    public static void connectDevice(Context context, int oldIndex, int position) {
        if (oldIndex == position) {
            // Offer disconnect
            offerDisconnect(context, mReaders.list().get(position), position);
        } else {
            // Warn about disconnection of other reader
            if (oldIndex >= 0) {
                offerChange(context, mReaders.list().get(oldIndex), oldIndex, mReaders.list().get(position), position);
            } else {
                returnSelectedReader(position, DEVICE_CONNECT);
            }
        }
    }


    private static void offerDisconnect(Context context, Reader reader, int index) {
        final int confirmedIndex = index;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage("From:  " + reader.getDisplayName())
                .setTitle("Disconnect?");

        builder.setPositiveButton("Disconnect", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                returnSelectedReader(confirmedIndex, DEVICE_DISCONNECT);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private static void offerChange(Context context, Reader oldReader, int oldIndex, Reader newReader, int newIndex) {
        final int restoreIndex = oldIndex;
        final int confirmedIndex = newIndex;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(String.format("From:  %s\n\nTo:  %s", oldReader.getDisplayName(), newReader.getDisplayName()))
                .setTitle("Change Reader?");

        builder.setPositiveButton("Change", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                returnSelectedReader(confirmedIndex, DEVICE_CHANGE);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled - revert to previous
//                mAdapter.setSelectedRowIndex(restoreIndex);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private static void returnSelectedReader(int readerIndex, int action) {
        listDeviceCallBack.returnSelectedReader(readerIndex, action);
    }

    public static void onResume(Context context) {
        ReaderManager.sharedInstance().onResume();

        // Set Bluetooth permissions prompt visibility
        boolean isPromptVisible = false;
        if (Build.VERSION.SDK_INT >= 31) {
            isPromptVisible = (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED);
        } else {
            isPromptVisible = (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED);
        }
        listDeviceCallBack.hideBluetoothPermissionsPrompt(isPromptVisible);


        // The Activity may start with a reader already connected (perhaps by another App)
        // Update the ReaderList which will add any unknown reader, firing events appropriately
        ReaderManager.sharedInstance().updateList();
        listDeviceCallBack.updateReaderListOnResume();
    }

    public static void onPause() {
        ReaderManager.sharedInstance().onPause();
    }

    public static void onDestroy() {
        // Remove observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);
    }
}
