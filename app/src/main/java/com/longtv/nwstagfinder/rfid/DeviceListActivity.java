
package com.longtv.nwstagfinder.rfid;

import com.longtv.nwstagfinder.R;
import com.longtv.nwstagfinder.helpers.DeviceListHelper;
import com.longtv.nwstagfinder.helpers.TagFinderHelper;
import com.longtv.nwstagfinder.interfaces.ListDeviceCallBack;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.utils.Observable;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

/**
 *
 */
public class DeviceListActivity extends Activity {
    // Member fields
    private RecyclerView mRecyclerView;
    private ReaderViewAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private TextView mBluetoothPermissionsPrompt;
    private DeviceListHelper deviceListHelper = DeviceListHelper.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.reader_list);

        mBluetoothPermissionsPrompt = (TextView) findViewById(R.id.bluetooth_permissions_prompt);
        mRecyclerView = (RecyclerView) findViewById(R.id.reader_recycler_view);

        RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(itemDecoration);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        DeviceListHelper.initialize(this, new ListDeviceCallBack() {
            @Override
            public void returnSelectedReader(int readerIndex, int action) {
                TagFinderHelper.connectDevice(readerIndex, action);
                finish();// nếu ở activity thì finish còn ở Flutter thì dùng method channel finish
            }

            @Override
            public void hideBluetoothPermissionsPrompt(boolean isPromptVisible) {
                mBluetoothPermissionsPrompt.setVisibility(isPromptVisible ? View.VISIBLE : View.GONE);
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void updateReaderListOnResume() {
                if (mAdapter != null) {
                    // Reapply the selected Reader in case the Reader list has been changed while paused
                    mAdapter.setSelectedRowIndex(-1);
                    mAdapter.notifyDataSetChanged();
                    int readerIndex = ReaderManager.sharedInstance().getReaderList().list().indexOf(DeviceListHelper.mSelectedReader);
                    mAdapter.setSelectedRowIndex(readerIndex);
                }
            }

            @Override
            public void notifyItemInserted(int index) {
                mAdapter.notifyItemInserted(index);
            }

            @Override
            public void setSelectedRowIndex(int index) {
                mAdapter.setSelectedRowIndex(-1);
            }

            @Override
            public void notifyItemChanged(int index) {
                mAdapter.notifyItemChanged(index);
            }

            @Override
            public void notifyItemRemoved(int index) {
                mAdapter.notifyItemRemoved(index);
            }
        });
        DeviceListHelper.mReaders = ReaderManager.sharedInstance().getReaderList();
        mAdapter = new ReaderViewAdapter(DeviceListHelper.mReaders);
        mRecyclerView.setAdapter(mAdapter);

        ItemClickSupport.addTo(mRecyclerView).setOnItemClickListener(new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                int oldIndex = mAdapter.getSelectedRowIndex();
//                mAdapter.setSelectedRowIndex(position);
//                if( oldIndex == position )
//                {
//                    // Offer disconnect
//                    offerDisconnect(mReaders.list().get(position), position);
//                }
//                else
//                {
//                    // Warn about disconnection of other reader
//                    if( oldIndex >= 0 )
//                    {
//                        offerChange(mReaders.list().get(oldIndex), oldIndex, mReaders.list().get(position), position);
//                    }
//                    else
//                    {
//                        returnSelectedReader(position, DEVICE_CONNECT);
//                    }
//                }

                DeviceListHelper.connectDevice(getApplicationContext(), oldIndex, position);
            }
        });

    }

    @Override
    protected void onDestroy() {
        DeviceListHelper.onDestroy();
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_list, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_add_new) {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onPause() {
        DeviceListHelper.onPause();
        super.onPause();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        DeviceListHelper.onResume(this);
        super.onResume();
    }
}
