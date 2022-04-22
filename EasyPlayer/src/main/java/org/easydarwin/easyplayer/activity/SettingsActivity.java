package org.easydarwin.easyplayer.activity;

import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CompoundButton;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.databinding.ActivitySettingBinding;
import org.easydarwin.easyplayer.util.SPUtil;

/**
 * 设置页
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_setting);

        setSupportActionBar(mBinding.toolbar);
        mBinding.toolbarBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mBinding.udpSwitch.setChecked(SPUtil.getUDPMode(this));
        mBinding.audioSwitch.setChecked(SPUtil.getAutoAudio(this));
        mBinding.recordSwitch.setChecked(SPUtil.getAutoRecord(this));
        mBinding.codecSwitch.setChecked(SPUtil.getswCodec(this));

        mBinding.udpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SPUtil.setUDPMode(SettingsActivity.this, isChecked);
            }
        });

        mBinding.audioSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SPUtil.setAutoAudio(SettingsActivity.this, isChecked);
            }
        });

        mBinding.recordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SPUtil.setAutoRecord(SettingsActivity.this, isChecked);
            }
        });

        mBinding.codecSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SPUtil.setswCodec(SettingsActivity.this, isChecked);
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }
}
