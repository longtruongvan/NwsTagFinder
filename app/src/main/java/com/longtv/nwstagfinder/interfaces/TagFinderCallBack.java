package com.longtv.nwstagfinder.interfaces;

public interface TagFinderCallBack {
    void appendMessageCallBack(String message);// cộng dồn từ ko replace nhé!
    void rssiMessageCallBack(String rssi);
    void rssiSubTitleCallBack(String subTitle);
    void rssiIntroduceCallBack(String introduce);
    void countMessageCallBack(String count);

//    private EditText mTargetTagEditText;
//
//    private TextView mRssiTextView;
//    private TextView mRssiTitleTextView;
//    private TextView mCountTextView;
//    private TextView mRssiSubtitleTextView;
//    private TextView mRssiInstructionTextView;
//
//    private TextView mResultTextView;
//    private ScrollView mResultScrollView;
}
