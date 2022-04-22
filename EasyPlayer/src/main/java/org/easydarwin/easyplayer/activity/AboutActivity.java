package org.easydarwin.easyplayer.activity;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String EP_URL = "https://github.com/EasyDSS/EasyPlayer";

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_about);

        setSupportActionBar(binding.toolbar);
        binding.toolbarBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 版本信息
        binding.version.setText("EasyPlayer RTSP播放器");
        binding.version.append("(");

        SpannableString ss = new SpannableString("激活码永久有效");
        ss.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.color_green)),
                0,
                ss.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        binding.version.append(ss);
        binding.version.append(")");

        binding.darwinTv2.setText("您也可以升级到我们的EasyPlayer Pro全功能版 本，支持HTTP/RTSP/RTMP/HLS等多种流媒体协议！");
        SpannableString ss2 = new SpannableString("戳我");
        ss2.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.color_theme2)),
                0,
                ss2.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        binding.darwinTv2.append(ss2);
        binding.darwinTv2.append("扫描如下的下载：");

        binding.darwinTv.setText("项目地址：");
        SpannableString ss3 = new SpannableString(EP_URL);
        ss3.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.color_theme2)),
                0,
                ss3.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        binding.darwinTv.append(ss3);

        binding.darwinTv2.setOnClickListener(this);
        binding.darwinTv.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent= new Intent();
        intent.setAction("android.intent.action.VIEW");
        Uri content_url = Uri.parse(EP_URL);

        switch (v.getId()) {
            case R.id.darwin_tv:
                content_url = Uri.parse(EP_URL);
                break;
            case R.id.darwin_tv2:
                content_url = Uri.parse(EP_URL);
                break;
        }

        intent.setData(content_url);
        startActivity(intent);
    }
}