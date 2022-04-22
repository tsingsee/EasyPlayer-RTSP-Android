package org.easydarwin.easyplayer.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayout;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RadioGroup;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.data.VideoSource;
import org.easydarwin.easyplayer.fragments.PlayFragment;

/**
 * 4分屏/9分屏
 * */
public class MultiPlayActivity extends AppCompatActivity implements PlayFragment.OnDoubleTapListener {

    public static final String EXTRA_URL = "extra-url";
    public static final int REQUEST_SELECT_ITEM_TO_PLAY = 2001;

    ResultReceiver rr = new ResultReceiver(new Handler());

    private int mNextPlayHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_multi_play);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        ImageButton btn = (ImageButton) findViewById(R.id.toolbar_back);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        String url = getIntent().getStringExtra(EXTRA_URL);
        int transportMode = getIntent().getIntExtra(VideoSource.TRANSPORT_MODE, 0);
        int sendOption = getIntent().getIntExtra(VideoSource.SEND_OPTION, 0);

        if (!TextUtils.isEmpty(url)) {
            addVideoToHolder(url, transportMode, sendOption, R.id.play_fragment_holder1);
        }

        RadioGroup rg = findViewById(R.id.switch_display_wnd_radio_group);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                GridLayout grid = findViewById(R.id.fragment_container_grid);

                if (checkedId == R.id.display_4_wnd) {
                    while (grid.getChildCount() > 4) {
                        grid.removeViewAt(grid.getChildCount() - 1);
                    }

                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View v = grid.getChildAt(i);
                        GridLayout.LayoutParams p = (GridLayout.LayoutParams) v.getLayoutParams();
                        p.columnSpec = GridLayout.spec(i % 2, 1.0f);
                        p.rowSpec = GridLayout.spec(i / 2, 1.0f);
                        v.setId(i + 1);
                    }

                    grid.setColumnCount(2);
                    grid.setRowCount(2);
                } else if (checkedId == R.id.display_9_wnd) {
                    grid.setColumnCount(3);
                    grid.setRowCount(3);

                    while (grid.getChildCount() < 9) {
                        View view = LayoutInflater.from(MultiPlayActivity.this).inflate(R.layout.grid_item, grid, false);
                        grid.addView(view);
                    }

                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View v = grid.getChildAt(i);
                        GridLayout.LayoutParams p = (GridLayout.LayoutParams) v.getLayoutParams();
                        p.columnSpec = GridLayout.spec(i % 3, 1.0f);
                        p.rowSpec = GridLayout.spec(i / 3, 1.0f);
                        v.setId(i + 1);
                    }
                }
            }
        });

        GridLayout grid = findViewById(R.id.fragment_container_grid);
        grid.removeAllViews();

        if (rg.getCheckedRadioButtonId() == R.id.display_4_wnd) {
            // add 4 windows
            grid.setColumnCount(2);
            grid.setRowCount(2);
            for (int i = 0; i < 4; i++) {
                View view = LayoutInflater.from(this).inflate(R.layout.grid_item, grid, false);
                GridLayout.LayoutParams p = (GridLayout.LayoutParams) view.getLayoutParams();
                grid.addView(view);
                view.setId(i + 1);
            }
        } else {
            // add 9 windows
            grid.setColumnCount(3);
            grid.setRowCount(3);

            for (int i = 0; i < 9; i++) {
                View view = LayoutInflater.from(this).inflate(R.layout.grid_item, grid, false);
                grid.addView(view);
                view.setId(i + 1);
            }
        }

        setViewLayoutByConfiguration(getResources().getConfiguration());
    }

    public void onAddVideoSource(View view) {
        Intent intent = new Intent(this, PlayListActivity.class);
        intent.putExtra(PlayListActivity.EXTRA_BOOLEAN_SELECT_ITEM_TO_PLAY, true);
        startActivityForResult(intent, REQUEST_SELECT_ITEM_TO_PLAY);

        ViewGroup p = (ViewGroup) view.getParent();
        mNextPlayHolder = p.getId();
    }

    private void addVideoToHolder(String url, int transportMode, int sendOption, int holder){
        PlayFragment f = PlayFragment.newInstance(url, transportMode, sendOption, rr);

        /**
         * 铺满全屏
         */
        f.setScaleType(PlayFragment.ASPECT_RATIO_CENTER_CROPS);
        f.setOnDoubleTapListener(this);
        FragmentManager manager = getSupportFragmentManager();
        manager.beginTransaction().add(holder, f).commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_ITEM_TO_PLAY) {
            if (resultCode == RESULT_OK) {
                String url = data.getStringExtra("url");
                int transportMode = data.getIntExtra(VideoSource.TRANSPORT_MODE, 0);
                int sendOption = data.getIntExtra(VideoSource.SEND_OPTION, 0);
                addVideoToHolder(url, transportMode, sendOption, mNextPlayHolder);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setViewLayoutByConfiguration(newConfig);
    }

    void setViewLayoutByConfiguration(Configuration newConfig) {
        View container = findViewById(R.id.fragment_container_grid);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.dimensionRatio = null;

            findViewById(R.id.toolbar).setVisibility(View.GONE);
            findViewById(R.id.switch_display_wnd_radio_group).setVisibility(View.GONE);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//隐藏状态栏
        } else {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.dimensionRatio = "1:1";

            findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
            findViewById(R.id.switch_display_wnd_radio_group).setVisibility(View.VISIBLE);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//显示状态栏
        }

        container.requestLayout();
    }

    @Override
    public void onDoubleTab(PlayFragment f) {
        GridLayout grid = findViewById(R.id.fragment_container_grid);

        for (int i = 0; i < grid.getChildCount(); i++) {
            View view = grid.getChildAt(i);

            if (view.getId() == f.getId()) {
                view.setVisibility(View.VISIBLE);
                continue;
            } else {
                if (view.getVisibility() == View.VISIBLE) {
                    view.setVisibility(View.GONE);
                    f.setScaleType(PlayFragment.FILL_WINDOW);
                } else {
                    view.setVisibility(View.VISIBLE);
                    f.setScaleType(PlayFragment.ASPECT_RATIO_CENTER_CROPS);
                }
            }
        }
    }

    @Override
    public void onSingleTab(PlayFragment f) {

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
