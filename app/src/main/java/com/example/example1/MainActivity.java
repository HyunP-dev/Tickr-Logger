package com.example.example1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.wahoofitness.connector.capabilities.Heartrate;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements WahooServiceListener {
    private static final String TAG = "WahooActivity";

    private TextView tvHRrate;

    private TextView outputTxt;
    private ToggleButton togglebtn;
    private TextView bpmTxt;
    private Button initbtn;

    // Sensor Data 여기서 확인
    private long changedValue_Time = 0;
    private Heartrate.Data hrData = null;

    // 블루투스 전용 서비스
    private WahooService mWahooService;
    private boolean registeredListener = false;

    // 스레드로 ~초마다 센서 가져올지 결정
    private Thread thread_collData;

    // 파일 입출력 (SAF) 관련
    private int WRITE_REQUEST_CODE = 43;

    private ParcelFileDescriptor pfd;
    private FileOutputStream fileOutputStream;

    @SuppressLint("BatteryLife")
    private void ignoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        if (pm.isIgnoringBatteryOptimizations(packageName)) return;
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivityForResult(intent, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();
        ignoreBatteryOptimizations();

        togglebtn = findViewById(R.id.togglebtn);
        togglebtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (WahooService.getInstance() == null) {
                    Log.d("togglebtn", "getInstance() is null");
                    togglebtn.setChecked(false);
                    return;
                }

                if (WahooService.getInstance().getHRData() == null) {
                    Log.d("togglebtn", "getHRData() is null");
                    togglebtn.setChecked(false);
                    return;
                }

                thread_collData = new Thread_collData();
                thread_collData.start();
            } else {
                thread_collData.interrupt();
            }
        });

        initbtn = findViewById(R.id.initbtn);
        initbtn.setOnClickListener(this::checkState);

        bpmTxt = findViewById(R.id.bpmTxt);

        Intent startIntent = new Intent(this, WahooService.class);
        startService(startIntent);

        Log.i(TAG, "Started service");
        outputTxt = findViewById(R.id.outputTxt);
    }

    // 센서, Wahoo 리스너 등록
    public void checkState(View view) {
//        if (!swSensor.isChecked()) return;

        if (registeredListener) return;
        Log.d("WahooService", "service instance checking...");
        if (WahooService.getInstance() == null) return;
        Log.d("WahooService", "service instance is not null.");
        WahooService service = WahooService.getInstance();
        service.addListener(this);
        registeredListener = true;
        service.startDiscovery();
    }

    // Wahoo Service 출력, 로깅
    @Override
    public void wahooEvent(String str) {
        outputTxt.setText(str);
        Log.i(TAG, str);
    }

    // SAF File/Doc 저장 위치 설정 (구글 드라이브에도 가능)
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void StartRecode() {
        try {
            long now = System.currentTimeMillis();
            Date date = new Date(now);
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
            String formatDate = sdfNow.format(date);

            // SAF 파일 편집
            String fileName = formatDate + "__HRVData__.csv";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            startActivityForResult(intent, WRITE_REQUEST_CODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // SAF 저장 위치 결과값( Url을 가져옴)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            addText(uri);
        }
    }

    // SAF Stream 연결
    public void addText(Uri uri) {
        try {
            pfd = this.getContentResolver().openFileDescriptor(uri, "w");
            fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    // SAF 출력
    public void putString(String st) throws IOException {
        if (fileOutputStream != null) {
            st = st + "\n";
            fileOutputStream.write(st.getBytes());
        }
    }

    // SAF Destroy
    public void finishRecord() throws IOException {
        postToastMessage("저장되었습니다.");
        fileOutputStream.close();
        pfd.close();
    }

    // 스레드에서 Toast 기능을 사용하기 위함
    public void postToastMessage(final String message) {
        Handler handler;
        handler = new Handler((Looper.getMainLooper()));
        handler.post(() -> Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show());
    }

    // 데이터 수집용 스레드
    private class Thread_collData extends Thread {
        private static final String TAG = "Thread_collData";
        int cnt;
        String result = new String();
        String[] coll = new String[15];

        public Thread_collData() {
            cnt = 0;
            coll = new String[5];
            result = new String();
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public void run() {
            try {
                StartRecode();
                //잠시 대기..
                //스레드가 너무 빨라서 0~2열이 안들어가는 문제가 있음..
                try {
                    boolean waitFlag = true;
                    while (waitFlag) {
                        Thread.sleep(2000);
                        waitFlag = false;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                coll[0] = String.valueOf(cnt); // 시행 횟수
                coll[1] = "ms"; // ms 초
                coll[2] = "HeartRate(bps),HeartRate(bpm) "; // bps, bpm
                coll[3] = "AvgHeartrate(bps),AvgHeartrate(bpm)"; // avg bps, avg bpm
                coll[4] = "AccumulatedBeats"; // 누적 박동수
                cnt++;

                result = Arrays.toString(coll);
                Log.i(TAG, result);

                putString(result);
                while (true) {
                    try {
                        hrData = WahooService.getInstance().getHRData();
                        coll[0] = String.valueOf(cnt); // 시행 횟수
                        coll[1] = String.valueOf(changedValue_Time); // ms 초
                        coll[2] = String.valueOf(hrData.getHeartrate()); // bps , bpm
                        runOnUiThread(() -> {
                            Log.d("text", String.valueOf(hrData.getHeartrate()));
                            bpmTxt.setText(String.valueOf(hrData.getHeartrate()).split(",")[1].split("/")[0].trim());
                        });
                        coll[3] = String.valueOf(hrData.getAvgHeartrate()); // 평균 bps, 평균 bpm
                        coll[4] = String.valueOf(hrData.getAccumulatedBeats()); // 누적 박동수
                        cnt++;
                        result = Arrays.toString(coll);
                        putString(result);
                        Log.i(TAG, result);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        finishRecord();
                        e.printStackTrace();
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 앱 시작시 권한 상승 동의 ( 어플을 새로 받으면, 저장장치, 미디어 위치 등 따로 권한 주세요.)
    private void checkPermission() {
        String[] permissions = new String[] {
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        permissions = Arrays.stream(permissions)
                .filter(permission -> ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED)
                .toArray(String[]::new);
        if (permissions.length == 0) return;
        requestPermissions(permissions, 0);
    }
}