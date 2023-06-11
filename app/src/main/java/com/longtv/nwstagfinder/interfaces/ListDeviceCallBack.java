package com.longtv.nwstagfinder.interfaces;

public interface ListDeviceCallBack {
    void returnSelectedReader(int readerIndex, int action);

    void hideBluetoothPermissionsPrompt(boolean isPromptVisible);

    void updateReaderListOnResume();

    void notifyItemInserted(int index);

    void setSelectedRowIndex(int index);

    void notifyItemChanged(int index);
    void notifyItemRemoved(int index);
}
