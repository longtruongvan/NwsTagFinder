//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.longtv.nwstagfinder.tagfinder;

import com.longtv.nwstagfinder.ModelBase;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.FindTagCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.AsciiSelfResponderCommandBase;
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthCountDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.SignalStrengthResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder;
import com.uk.tsl.utils.StringHelper;

/**
 * A class to illustrate how to return the RSSI for a target tag using either the
 * FindTag command or the InventoryCommand
 */
public class TagFinderModel extends ModelBase {

    // The instances used to issue commands
    private InventoryCommand mInventoryCommand;
    private FindTagCommand mFindTagCommand;

    // The responder to capture incoming RSSI responses
    private SignalStrengthResponder mSignalStrengthResponder;

    // The switch state responder
    private SwitchResponder mSwitchResponder;

    private boolean mUseFindTagCommand = false;


    public String getTargetTagEpc() {
        return mTargetTagEpc;
    }

    public void setTargetTagEpc(String targetTagEpc) {
        if (targetTagEpc != null) {
            mTargetTagEpc = targetTagEpc.toUpperCase();
        }
    }

    private String mTargetTagEpc = null;

    // True if the User is scanning
    public boolean isScanning() {
        return mScanning;
    }

    public void setScanning(boolean scanning) {
        mScanning = scanning;
    }

    private boolean mScanning = false;


    /**
     * @return the delegate for the raw signal strength responses in dBm
     */
    public ISignalStrengthReceivedDelegate getRawSignalDelegate() {
        return mSignalStrengthResponder.getRawSignalStrengthReceivedDelegate();
    }

    /**
     * @param delegate the delegate for the raw signal strength responses in dBm
     */
    public void setRawSignalDelegate(ISignalStrengthReceivedDelegate delegate) {
        mSignalStrengthResponder.setRawSignalStrengthReceivedDelegate(delegate);
    }

    /**
     * @return the delegate for the percentage signal strength responses in range 0 - 100 %
     */
    public ISignalStrengthReceivedDelegate getPercentageSignalDelegate() {
        return mSignalStrengthResponder.getPercentageSignalStrengthReceivedDelegate();
    }

    /**
     * @param delegate the delegate for the percentage signal strength responses in range 0 - 100 %
     */
    public void setPercentageSignalDelegate(ISignalStrengthReceivedDelegate delegate) {
        mSignalStrengthResponder.setPercentageSignalStrengthReceivedDelegate(delegate);
    }

    /**
     * @return the delegate for the signal strength transponder count delegate
     */
    public ISignalStrengthCountDelegate getSignalStrengthCountDelegate() {
        return mSignalStrengthResponder.getSignalStrengthCountDelegate();
    }

    /**
     * @param delegate the delegate for the signal strength transponder count delegate
     */
    public void setSignalStrengthCountDelegate(ISignalStrengthCountDelegate delegate) {
        mSignalStrengthResponder.setSignalStrengthCountDelegate(delegate);
    }


    // Control
    private boolean mEnabled;

    public boolean enabled() {
        return mEnabled;
    }

    public void setEnabled(boolean state) {
        boolean oldState = mEnabled;
        mEnabled = state;

        // Update the commander for state changes
        if (oldState != state) {
            if (mEnabled) {
                // Listen for transponders
                getCommander().addResponder(mSignalStrengthResponder);
                // Listen for trigger
                getCommander().addResponder(mSwitchResponder);

            } else {
                // Stop listening for trigger
                getCommander().addResponder(mSwitchResponder);
                // Stop listening for transponders
                getCommander().removeResponder(mSignalStrengthResponder);
            }

        }
    }

    /**
     * @return true if the current Reader supports the .ft (Find Tag) command
     */
    public boolean isFindTagCommandAvailable() {
        return mUseFindTagCommand;
    }


    /**
     * A class to demonstrate the use of the AsciiProtocol library to read and write to transponders
     */
    public TagFinderModel() {
        mInventoryCommand = InventoryCommand.synchronousCommand();
        mFindTagCommand = FindTagCommand.synchronousCommand();
        mSignalStrengthResponder = new SignalStrengthResponder();
        mSwitchResponder = new SwitchResponder();
        mSwitchResponder.setSwitchStateReceivedDelegate(new ISwitchStateReceivedDelegate() {
            @Override
            public void switchStateReceived(SwitchState switchState) {
                // When trigger released
                if (switchState == SwitchState.OFF) {
                    mScanning = false;
                    // Fake a signal report for both percentage and RSSI to indicate action stopped
                    if (mSignalStrengthResponder.getRawSignalStrengthReceivedDelegate() != null) {
                        mSignalStrengthResponder.getRawSignalStrengthReceivedDelegate().signalStrengthReceived(null);
                    }
                    if (mSignalStrengthResponder.getPercentageSignalStrengthReceivedDelegate() != null) {
                        mSignalStrengthResponder.getPercentageSignalStrengthReceivedDelegate().signalStrengthReceived(null);
                    }
                } else if (switchState == SwitchState.SINGLE) {
                    mScanning = true;
                }
            }
        });

    }

    //
    // Reset the reader configuration to default command values
    //
    public void resetDevice() {
        if (getCommander().isConnected()) {
            try {
                sendMessageNotification("\nInitialising reader...\n");

                performTask(new Runnable() {
                    @Override
                    public void run() {
                        getCommander().executeCommand(new FactoryDefaultsCommand());

                        // Test for presence of the FindTag command
                        mFindTagCommand.setResetParameters(TriState.YES);
                        mFindTagCommand.setTakeNoAction(TriState.YES);
                        mCommander.executeCommand(mFindTagCommand);
                        // If Reader responded without error then we can use the special command otherwise use the standard inventory command
                        mUseFindTagCommand = mFindTagCommand.isSuccessful();

                        // Now prepare first find parameters
                        updateTargetParameters();

                        sendMessageNotification("\nDone.\n");
                    }
                });

            } catch (Exception e) {
                sendMessageNotification("Unable to perform action: " + e.getMessage());
            }
        }
    }


    //
    // Reconfigure the Reader to target the current
    //
    public void updateTarget() {
        if (!this.isBusy()) {
            try {
                sendMessageNotification("\nUpdating target...");

                performTask(new Runnable() {
                    @Override
                    public void run() {
                        updateTargetParameters();
                    }
                });

            } catch (Exception e) {
                sendMessageNotification("Unable to perform action: " + e.getMessage());
            }
        }
    }

    //
    // Reconfigure the Reader to target the current
    //
    public void updateTargetParameters() {
        if (getCommander().isConnected()) {
            // Configure the switch actions
            SwitchActionCommand switchActionCommand = SwitchActionCommand.synchronousCommand();
            switchActionCommand.setResetParameters(TriState.YES);
            switchActionCommand.setAsynchronousReportingEnabled(TriState.YES);

            // Only change defaults if there is a valid target tag
            if (!StringHelper.isNullOrEmpty(mTargetTagEpc)) {
                // Configure the single press switch action for the appropriate command
                switchActionCommand.setSinglePressAction(mUseFindTagCommand ? SwitchAction.FIND_TAG : SwitchAction.INVENTORY);
                // Lower the repeat delay to maximise the response rate
                switchActionCommand.setSinglePressRepeatDelay(10);
            }

            mCommander.executeCommand(switchActionCommand);


            // Now adjust the commands to target the chosen tag
            boolean succeeded = false;

            if (StringHelper.isNullOrEmpty(mTargetTagEpc)) {
                succeeded = true;
            } else {

                String targetEpcData = mTargetTagEpc;
                // Pad data if not whole number of bytes
                if (targetEpcData.length() % 2 == 1) {
                    targetEpcData += "0";
                }

                if (mUseFindTagCommand) {
                    mFindTagCommand = FindTagCommand.synchronousCommand();
                    mFindTagCommand.setResetParameters(TriState.YES);

                    // Only configure if target valid
                    if (!StringHelper.isNullOrEmpty(mTargetTagEpc) && mEnabled) {
                        mFindTagCommand.setSelectData(targetEpcData);
                        mFindTagCommand.setSelectLength(mTargetTagEpc.length() * 4);
                        mFindTagCommand.setSelectOffset(0x20);

                    }
//                else
//                {
//                    mFindTagCommand.setTriggerOverride(StringHelper.isNullOrEmpty(mTargetTagEpc) ? StartStop.STOP : StartStop.NOT_SPECIFIED);
//                }

                    mFindTagCommand.setTakeNoAction(TriState.YES);

                    //mFindTagCommand.setReadParameters(TriState.YES);
                    getCommander().executeCommand(mFindTagCommand);

                    succeeded = mFindTagCommand.isSuccessful();

                } else {
                    // Configure the inventory
                    mInventoryCommand = InventoryCommand.synchronousCommand();
                    mInventoryCommand.setResetParameters(TriState.YES);
                    mInventoryCommand.setTakeNoAction(TriState.YES);

                    // Only configure if target valid
                    if (!StringHelper.isNullOrEmpty(mTargetTagEpc) && mEnabled) {
                        mInventoryCommand.setIncludeTransponderRssi(TriState.YES);

                        mInventoryCommand.setQuerySession(QuerySession.SESSION_0);
                        mInventoryCommand.setQueryTarget(QueryTarget.TARGET_B);

                        mInventoryCommand.setInventoryOnly(TriState.NO);

                        mInventoryCommand.setSelectData(targetEpcData);
                        mInventoryCommand.setSelectOffset(0x20);
                        mInventoryCommand.setSelectLength(mTargetTagEpc.length() * 4);
                        mInventoryCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
                        mInventoryCommand.setSelectTarget(SelectTarget.SESSION_0);

                        mInventoryCommand.setUseAlert(TriState.NO);
                    }

                    getCommander().executeCommand(mInventoryCommand);
                    succeeded = mInventoryCommand.isSuccessful();
                }
            }

            if (succeeded) {
                sendMessageNotification("updated\n");
            } else {
                sendMessageNotification("\n !!! update failed - ensure only hex characters used !!!\n");
            }
        }
    }

    //----------------------------------------------------------------------------------------------
    //
    //----------------------------------------------------------------------------------------------


    /**
     * Check the given command for errors and report them via the model message system
     *
     * @param command The command to check
     */
    private void reportErrors(AsciiSelfResponderCommandBase command) {
        if (!command.isSuccessful()) {
            sendMessageNotification(String.format(
                    "%s failed!\nError code: %s\n", command.getClass().getSimpleName(), command.getErrorCode()));
            for (String message : command.getMessages()) {
                sendMessageNotification(message + "\n");
            }
        }

    }

}
