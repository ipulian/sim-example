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

import com.elvishew.xlog.XLog;
import com.ipusoft.context.AppContext;
import com.ipusoft.context.IpuSoftSDK;
import com.ipusoft.context.bean.AuthInfo;
import com.ipusoft.context.bean.SimRiskControlBean;
import com.ipusoft.context.bean.SysRecording;
import com.ipusoft.context.component.ToastUtils;
import com.ipusoft.context.config.Env;
import com.ipusoft.context.manager.PhoneManager;
import com.ipusoft.localcall.UploadFileObserve;
import com.ipusoft.localcall.bean.SysCallLog;
import com.ipusoft.localcall.bean.UploadResponse;
import com.ipusoft.localcall.constant.UploadStatus;
import com.ipusoft.localcall.manager.UploadManager;
import com.ipusoft.localcall.repository.CallLogRepo;
import com.ipusoft.localcall.repository.RecordingFileRepo;
import com.ipusoft.mmkv.datastore.CommonDataRepo;
import com.ipusoft.oss.AliYunUploadManager;
import com.ipusoft.permission.RxPermissionUtils;
import com.ipusoft.sim.RecordingDataRepo;
import com.ipusoft.sim.http.SimHttp;
import com.ipusoft.sim.iface.OnSimCallPhoneResultListener;
import com.ipusoft.utils.AppUtils;
import com.ipusoft.utils.ArrayUtils;
import com.ipusoft.utils.ExceptionUtils;
import com.ipusoft.utils.GsonUtils;
import com.ipusoft.utils.StringUtils;
import com.ipusoft.utils.SysRecordingUtils;
import com.tbruyelle.rxpermissions3.RxPermissions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    private Button btnDial, btnQueryCallLog, btnQueryRecording, btnUpload, btnUpload2, btnQueryList, btnQueryCount, btnStorageManagerPermission;

    private List<SysCallLog> callLogList;
    private List<File> fileList;

    private SysRecording sysRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        callLogList = new ArrayList<>();
        fileList = new ArrayList<>();

        btnDial = (Button) findViewById(R.id.btn_dial);
        btnQueryCallLog = (Button) findViewById(R.id.btn_query_calllog);
        btnQueryRecording = (Button) findViewById(R.id.btn_query_recording);
        btnUpload = (Button) findViewById(R.id.btn_upload);
        btnUpload2 = (Button) findViewById(R.id.btn_upload2);
        btnQueryCount = (Button) findViewById(R.id.btn_queryCount);
        btnQueryList = (Button) findViewById(R.id.btn_queryList);
        btnStorageManagerPermission = (Button) findViewById(R.id.btn_storage_manager_permission);

        btnDial.setOnClickListener(this);
        btnQueryCallLog.setOnClickListener(this);
        btnQueryRecording.setOnClickListener(this);
        btnDial.setOnClickListener(this);
        btnUpload.setOnClickListener(this);
        btnQueryCount.setOnClickListener(this);
        btnQueryList.setOnClickListener(this);
        btnUpload2.setOnClickListener(this);

        btnStorageManagerPermission.setOnClickListener(this);


        new RxPermissions(this).request(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS).subscribe(aBoolean -> {
            if (aBoolean) {

            }
        });


        initSDK();


        //TODO 受限于Android版本及国内ROM厂商的复杂性，目前没有任何办法可以完全获取话单的主叫号码,
        // 这里让终端用户自己设置自己的SIM卡号码，如果代码能取到，就用代码取到的，代码取不到，就用设置的。如果不设置，建议禁止外呼！！！
        CommonDataRepo.setLine1Number("18317893006");
        CommonDataRepo.setLine2Number("18317893007");

    }

    //初始化SDK（需要先获取 READ_PHONE_STATE 权限，否则会初始化失败）
    private void initSDK() {
        Log.d(TAG, "initSDK: --------->初始化SDK");
//        IpuSoftSDK.init(getApplication(),
//                Env.OPEN_DEV, new AuthInfo("4571122846924808", "6d8f5dff290bac1dd92708b638046125",
//                        "gw"));
//        IpuSoftSDK.init(getApplication(),
//                Env.OPEN_PRO, new AuthInfo("4615043207659530", "336ce5f4b07e8b656c62d53dba16f1d3",
//                        "15038779932"));
        IpuSoftSDK.init(getApplication(),
                Env.OPEN_PRO, new AuthInfo("4615043207659530", "336ce5f4b07e8b656c62d53dba16f1d3",
                        "18317893005"));


    }


    @Override
    public void onClick(View v) {
        if (v.getId() == btnDial.getId()) {//拨号

            EditText etPhone = (EditText) findViewById(R.id.et_phone);
            String phone = etPhone.getText().toString();
            if (StringUtils.isEmpty(phone)) {
                // 请输入号码
                return;
            }
            //TODO 需要先申请 CALL_PHONE READ_PHONE_NUMBERS READ_EXTERNAL_STORAGE READ_CALL_LOG 权限
            /*
             * CALL_PHONE 用于外呼
             * READ_PHONE_NUMBERS 获取主机号码
             * READ_EXTERNAL_STORAGE 获取通话录音
             * READ_CALL_LOG 获取通话记录
             */

            //是否打开通话录音的开关
            int hasSimRecordPermission = -1;//默认：未知状态（不知道是否打开）
            if (SysRecordingUtils.isHUAWEI()) {//华为，荣耀
                hasSimRecordPermission = RxPermissionUtils.checkHuaweiRecord();
            } else if (SysRecordingUtils.isMIUI()) {//小米，红米
                hasSimRecordPermission = RxPermissionUtils.checkXiaomiRecord();
            } else if (SysRecordingUtils.isOPPO()) {//OPPO
                hasSimRecordPermission = RxPermissionUtils.checkOppoRecord();
            } else if (SysRecordingUtils.isVIVO()) {//VIVO
                hasSimRecordPermission = RxPermissionUtils.checkViVoRecord();
            } else {
                //TODO 其他机型暂未找到解决方案
            }
            XLog.d(TAG + "---hasSimRecordPermission---->" + hasSimRecordPermission);

            if (hasSimRecordPermission == -1) {//未知状态

            } else if (hasSimRecordPermission == 0) {//可能未开启状态

            } else if (hasSimRecordPermission == 1) {//电话录音已经开启

            }

            boolean b = RxPermissionUtils.checkManageStoragePermission();
            if (!b) {
                //TODO 未获取文件访问权限，这里可以请提醒客户，务必打开权限，否则无法获取录音
                try {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + AppUtils.getAppPackageName()));
                            AppContext.getActivityContext().startActivity(intent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            AppContext.getActivityContext().startActivity(intent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // ToastUtils.showMessage("请手动打开");
                }
            }

            SimHttp.getInstance()
                    .callPhoneBySim(phone, new OnSimCallPhoneResultListener<SimRiskControlBean>() {
                        @Override
                        public void onSucceed(SimRiskControlBean simRiskControlBean) {
                            if (StringUtils.equals("1", simRiskControlBean.getHttpStatus())) {
                                if (simRiskControlBean.getType() == 0) {
                                    //调用系统的拨号组件外呼
                                    //simIndex：0轮拨，1卡1；2卡2
                                    PhoneManager.callPhoneBySim(MainActivity.this,
                                            simRiskControlBean.getPhone(), simRiskControlBean.getCallTime(),
                                            "这里传扩展字段，话单推送时会进行回传", 0);
                                } else {
                                    ToastUtils.showMessage("风控号码，禁止外呼");
                                    //TODO 自己实现相应的业务逻辑
                                }
                            } else {
                                //TODO 报错了
                                ToastUtils.showMessage(simRiskControlBean.getMsg());
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            Log.d("TAG", "throwable: ------" + GsonUtils.toJson(throwable));
                        }
                    });
        } else if (v.getId() == btnStorageManagerPermission.getId()) {//获取所有文件访问权限
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
        } else if (v.getId() == btnQueryCallLog.getId()) {//获取系统话单
            //只支持查询72小时内的操作系统通话记录列表
            new Thread(() -> {
                int page = 0;//分页从0开始计数，
                int pageSize = 15;//每页15条
                //总条数，TODO 前端业务自己根据数据总条数做分页
                int count = CallLogRepo.queryTotalCount();
                System.out.println("count----------->" + count);
                //查询数据
                callLogList = CallLogRepo.querySysCallLog(page, pageSize);
                System.out.println("sysCallLogs----------->" + GsonUtils.toJson(callLogList));
            }).start();
        } else if (v.getId() == btnQueryRecording.getId()) {//获取系统录音
            // TODO 需要先获取【所有文件访问权限】 否则获取不到任何录音文件
            new Thread(() -> {
                //只查询近3天的录音数据
                long timestamp = System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000;
                fileList = RecordingFileRepo.getInstance().queryRecordingFile(timestamp);
                if (fileList != null && fileList.size() != 0) {
                    Collections.sort(fileList, (o1, o2) -> (int) (o1.lastModified() - o2.lastModified()));
                    for (File file : fileList) {
                        System.out.println("fileName----------->" + file.getName());
                    }
                } else {
                    //TODO 没有查到相关录音数据
                }
            }).start();
        } else if (v.getId() == btnUpload.getId()) {//手动上传话单(可能包含录音文件)
            /**
             * 这里只是样例程序，仅作演示，话单和录音文件,需要上层代码逻辑去关联，或者让客户去手动关联
             */
            //TODO 这里的 SysCallLog 是通过【获取操作系统话单列表】返回的数据
            SysCallLog callLog = callLogList.get(0);
            System.out.println("callLog----->" + GsonUtils.toJson(callLog));
            //TODO 这里的 File 是通过【获取操作系统录音列表】返回的数据
            File file = fileList.get(0);
            System.out.println("file----->" + GsonUtils.toJson(file));
            //如果话单没有对应的录音文件,file传null

            //上传话单（录音）
            AliYunUploadManager.uploadRecording(callLog, file);
        } else if (v.getId() == btnQueryCount.getId()) { //查询上传记录的总条数，用于分页

            List<Integer> list = ArrayUtils.createList(
                    UploadStatus.UPLOAD_FAILED.getStatus(),
                    UploadStatus.UPLOAD_SUCCEED.getStatus(),
                    UploadStatus.UPLOAD_IGNORE.getStatus(),
                    UploadStatus.UPLOADING.getStatus());
            RecordingDataRepo.queryRecordCount(list, new RecordingDataRepo.OnRecordCountListener() {
                @Override
                public void recordCount(int count) {
                    Log.d(TAG, "recordCount:-------------->" + count);
                }
            });

        } else if (v.getId() == btnQueryList.getId()) {//分页查询上传记录

            List<Integer> list = ArrayUtils.createList(
                    UploadStatus.UPLOAD_FAILED.getStatus(),
                    UploadStatus.UPLOAD_SUCCEED.getStatus(),
                    UploadStatus.UPLOAD_IGNORE.getStatus(),
                    UploadStatus.UPLOADING.getStatus());
            RecordingDataRepo.queryRecordList(list, 0, 50, new RecordingDataRepo.OnRecordListListener() {
                @Override
                public void recordList(List<SysRecording> list) {
                    Log.d(TAG, "recordCount:-------------->" + GsonUtils.toJson(list));
                    if (ArrayUtils.isNotEmpty(list)) {
                        sysRecording = list.get(0);
                    }
                    Log.d(TAG, "recordList: -----------》" + GsonUtils.toJson(sysRecording));
                }
            });

        } else if (v.getId() == btnUpload2.getId()) {
            AliYunUploadManager.uploadRecording(sysRecording, new AliYunUploadManager.OnUploadListener() {
                @Override
                public void onSuccess(UploadResponse uploadResponse) {

                }

                @Override
                public void onError(Throwable e) {

                }
            });
        }
    }
}