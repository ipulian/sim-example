package com.ipusoft.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.ipusoft.context.IpuSoftSDK;
import com.ipusoft.context.bean.AuthInfo;
import com.ipusoft.context.bean.SimRiskControlBean;
import com.ipusoft.context.component.ToastUtils;
import com.ipusoft.context.config.Env;
import com.ipusoft.context.manager.PhoneManager;
import com.ipusoft.sim.http.SimHttp;
import com.ipusoft.sim.iface.OnSimCallPhoneResultListener;
import com.ipusoft.utils.AppUtils;
import com.ipusoft.utils.GsonUtils;
import com.ipusoft.utils.StringUtils;
import com.tbruyelle.rxpermissions3.RxPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    private Button btnDial, btnStorageManagerPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDial = (Button) findViewById(R.id.btn_dial);
        btnDial.setOnClickListener(this);

        btnStorageManagerPermission = (Button) findViewById(R.id.btn_storage_manager_permission);
        btnStorageManagerPermission.setOnClickListener(this);


        new RxPermissions(this).request(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS).subscribe(aBoolean -> {
            if (aBoolean) {

            }
        });

        initSDK();
    }

    //初始化SDK（需要先获取 READ_PHONE_STATE 权限，否则会初始化失败）
    private void initSDK() {
        Log.d(TAG, "initSDK: --------->初始化SDK");
        IpuSoftSDK.init(getApplication(),
                Env.OPEN_DEV, new AuthInfo("4571122846924808", "6d8f5dff290bac1dd92708b638046125",
                        "gw"));
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == btnDial.getId()) {
            EditText etPhone = (EditText) findViewById(R.id.et_phone);
            String phone = etPhone.getText().toString();
            if (StringUtils.isEmpty(phone)) {
                ToastUtils.showLoading("请输入号码");
                return;
            }
            //TODO 需要先申请 CALL_PHONE READ_PHONE_NUMBERS READ_EXTERNAL_STORAGE READ_CALL_LOG 权限
            /*
             * CALL_PHONE 用于外呼
             * READ_PHONE_NUMBERS 获取主机号码
             * READ_EXTERNAL_STORAGE 获取通话录音
             * READ_CALL_LOG 获取通话记录
             */
            SimHttp.getInstance()
                    .callPhoneBySim(phone, new OnSimCallPhoneResultListener<SimRiskControlBean>() {
                        @Override
                        public void onSucceed(SimRiskControlBean simRiskControlBean) {
                            if (simRiskControlBean.getType() == 0) {
                                //调用系统的拨号组件外呼
                                PhoneManager.callPhoneBySim(simRiskControlBean.getPhone(), simRiskControlBean.getCallTime());
                            } else {
                                ToastUtils.showMessage("风控号码，禁止外呼");
                                //TODO 自己实现相应的业务逻辑
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            Log.d("TAG", "throwable: ------" + GsonUtils.toJson(throwable));
                        }
                    });
        } else if (v.getId() == btnStorageManagerPermission.getId()) {
            //检查存储管理权限(用于获取录音)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
                //TODO 已经有权限了
            } else {
                //引导打开存储管理权限
                try {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + AppUtils.getAppPackageName()));
                            startActivity(intent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    ToastUtils.showMessage("请手动打开");
                }
            }
        }
    }
}