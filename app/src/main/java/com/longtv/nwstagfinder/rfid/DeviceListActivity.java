
package com.longtv.nwstagfinder.rfid;

import com.longtv.nwstagfinder.R;
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
 */
public class DeviceListActivity extends Activity
{
    // Member fields
    private RecyclerView mRecyclerView;
    private ReaderViewAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private TextView mBluetoothPermissionsPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.reader_list);

        mBluetoothPermissionsPrompt = (TextView)findViewById(R.id.bluetooth_permissions_prompt);
        mRecyclerView = (RecyclerView) findViewById(R.id.reader_recycler_view);

        RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(itemDecoration);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        //mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mRecyclerView.setAdapter( mAdapter );


        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);


        // See if there is a reader currently in use
        Intent intent = getIntent();
        int startIndex = intent.getIntExtra(EXTRA_DEVICE_INDEX, -1);
        if( startIndex >= 0 )
        {
            mSelectedReader = ReaderManager.sharedInstance().getReaderList().list().get(startIndex);
            mRecyclerView.scrollToPosition(startIndex);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_list, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_add_new)
        {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
