package org.easydarwin.player.simpleplayer;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.TextureView;
import android.widget.EditText;

import org.easydarwin.video.EasyPlayerClient;

public class MainActivity extends AppCompatActivity {

    private EasyPlayerClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextureView textureView = findViewById(R.id.texture_view);
        /**
         * 参数说明
         * 第一个参数为Context,第二个参数为KEY
         * 第三个参数为的textureView,用来显示视频画面
         * 第四个参数为一个ResultReceiver,用来接收SDK层发上来的事件通知;
         * 第五个参数为I420DataCallback,如果不为空,那底层会把YUV数据回调上来.
         */
        client = new EasyPlayerClient(this, BuildConfig.KEY, textureView, null, null);
        final EditText et = new EditText(this);
        et.setHint("请输入RTSP地址");
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        et.setText(sp.getString("url",null));

        new AlertDialog.Builder(this).setView(et).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String url = et.getText().toString();
                if (!TextUtils.isEmpty(url)){
                    client.play(url);
                    sp.edit().putString("url", url).apply();
                }
            }
        }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).show();

    }
}
