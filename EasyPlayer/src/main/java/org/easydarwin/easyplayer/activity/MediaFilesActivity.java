package org.easydarwin.easyplayer.activity;

import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.MenuItem;
import android.view.View;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.databinding.ActivityMediaFilesBinding;
import org.easydarwin.easyplayer.fragments.LocalFileFragment;

/**
 * 录像和截图
 * */
public class MediaFilesActivity extends AppCompatActivity {

    private ActivityMediaFilesBinding mDataBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        mDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_media_files);

        final String url = getIntent().getStringExtra("play_url");

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mDataBinding.toolbarBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mDataBinding.viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public int getCount() {
                return 2;
            }

            public Fragment getItem(int position) {
                Bundle args = new Bundle();
                args.putBoolean(LocalFileFragment.KEY_IS_RECORD, position == 1);
                args.putString(LocalFileFragment.KEY_URL, url);
                return Fragment.instantiate(MediaFilesActivity.this, LocalFileFragment.class.getName(), args);
            }

            @Override
            public CharSequence getPageTitle(int position) {
                String text = position == 0 ? "抓拍" : "录像";
                SpannableStringBuilder ssb = new SpannableStringBuilder(text);

                ForegroundColorSpan fcs = new ForegroundColorSpan(getResources().getColor(R.color.color_theme2));//字体颜色设置为绿色
                ssb.setSpan(fcs, 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);//设置字体颜色
                ssb.setSpan(new RelativeSizeSpan(1f), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                return ssb;
            }
        });

        //取消严格模式  FileProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
