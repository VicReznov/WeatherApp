package com.example.weatherapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class MainActivity extends AppCompatActivity {

    private GpsTracker gpsTracker;

    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    String[] REQUIRED_PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private String x = "", y = "", address = "";
    private String date = "", time = "";
    private String weather = "", temperature = "", localName = "";


    SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    TextView home;
    TextView weath;
    TextView temper;
    ImageView img;
    Button refreshBtn;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //bind view
        home = (TextView) findViewById(R.id.home);
        weath = findViewById(R.id.weather);
        temper = findViewById(R.id.temper);
        img = findViewById(R.id.img);
        refreshBtn = (Button) findViewById(R.id.refreshBtn);

        if (!checkLocationServicesStatus()){
            showDialogForLocationServiceSetting();
        } else {
            checkRunTimePermission();
        }

        //bind listener
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //동네이름
                gpsTracker = new GpsTracker(MainActivity.this);
                double latitude = gpsTracker.getLatitude();
                double longitude = gpsTracker.getLongitude();

                address = getCurrentAddress(latitude, longitude);
                String[] local = address.split(" ");
                localName = local[3];

                readExcel(localName); //행정시 이름으로 격자값 구하기

                // date와 time에 값을 넣어야함
                //현재 시간을 구해야한다
                long now = System.currentTimeMillis();
                Date d = new Date(now);
                //시간을 가져오고 싶은 형식으로 가져온다
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd.HH");
                String getTime = simpleDateFormat.format(d);

                String[] array = getTime.split("\\.");
                date = array[0];
                time = array[1] + "00";

                WeatherData wd = new WeatherData();
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String[] weathers = wd.lookUpWeather(date, time, x, y);
                            weather = weathers[0];
                            temperature = weathers[1];
                            setComponent();
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });

                //날씨 이미지
            }
        });
    }


    //ActivityCompat.requestPermissions를 사용한 퍼미션 요청의 결과를 리턴받는 메소드
    @Override
    public void onRequestPermissionsResult(int permsRequestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(permsRequestCode, permissions, grantResults);

        if (permsRequestCode == PERMISSIONS_REQUEST_CODE && grantResults.length == REQUIRED_PERMISSIONS.length) {
            //요청코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            boolean check_result = true;
            //모든 퍼미션을 허용했는지 체크합니다.
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }

            if (check_result) {
                //위치 값을 가져올 수 있다
                ;
            } else {
                // 거부한 퍼미션이 있다면 앱을 사용할 수 없는 이유를 설명해주고 앱을 종료한다. 2가지 이유 표시
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0]) || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])) {
                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해 주세요.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해 주세요.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    void checkRunTimePermission(){
        //런타임 퍼미션 처리
        //1. 위치 퍼미션을 가지고 있는지 체크
        int hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if(hasFineLocationPermission == PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED){
            // 2. 이미 퍼미션을 가지고 있다면
            // 3. 위치값을 가져올 수 있다
        } else {
            //2. 퍼미션 요청을 허용한 적이 없다면 퍼미션 요청이 필요합니다. 2가지 경우(3-1, 4-1)가 있습니다
            //3-1. 사용자가 퍼미션 거부를 한 적이 있는 경우에는
            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, REQUIRED_PERMISSIONS[0])){
                //3-2. 요청을 진행하기 전에 사용자에게 퍼미션이 필요한 이유를 설명해 줄 필요가 있다.
                Toast.makeText(MainActivity.this, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                //3-3. 사용자에게 퍼미션 요청을 합니다. 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            } else {
                //4-1. 사용자가 퍼미션 거부를 한 적이 없는 경우에는 퍼미션 요청을 바로 합니다.
                //요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }
    }



    //여기서부터는 GPS 활성화를 위한 메소드들
    private void showDialogForLocationServiceSetting(){

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화");
        builder.setMessage("앱을 사용하기 위해서는 위치 서비스가 필요합니다.\n"+"위치 설정을 수정하실래요?");
        builder.setCancelable(true);
        builder.setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){

            case GPS_ENABLE_REQUEST_CODE:

                //사용자가 GPS 활성 시켰는지 검사
                if (checkLocationServicesStatus()){
                    Log.d("@@@", "onActivityResult : GPS 활성화 되어있음");
                    checkRunTimePermission();
                    return;
                }
                break;
        }

    }

    public String getCurrentAddress(double latitude, double longitude){
        //지오코더...GPS를 주소로 변환
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses;

        try{
            addresses = geocoder.getFromLocation(latitude, longitude, 7);
        } catch(IOException ioException){
            Toast.makeText(this, "지오코더 서비스 사용불가", Toast.LENGTH_LONG).show();
            return "지오코더 서비스 사용불가";
        } catch(IllegalArgumentException illegalArgumentException){
            Toast.makeText(this, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show();
            return "잘못된 GPS 좌표";
        }

        if (addresses == null || addresses.size() == 0){
            Toast.makeText(this, "주소 미발견", Toast.LENGTH_LONG).show();
            return "주소 미발견";
        }
        Address address = addresses.get(0);
        return address.getAddressLine(0).toString()+"\n";
    }

    public boolean checkLocationServicesStatus(){
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void readExcel(String localName) {
        try {
            InputStream is = getBaseContext().getResources().getAssets().open("local_name.xls");
            Workbook wb = Workbook.getWorkbook(is);
            localName = localName.substring(0, localName.length()-1);

            if (wb != null) {
                Sheet sheet = wb.getSheet(0); //시트 불러오기
                if (sheet != null) {
                    int colTotal = sheet.getColumns(); //전체 컬럼
                    int rowIndexStart = 1;
                    int rowTotal = 3774;

                    System.out.println(rowTotal);
                    System.out.println(colTotal);
                    Log.e("1", "1111111111");
                    for (int row = rowIndexStart; row < rowTotal; row++) {
                        Log.e("2", "2222222222");
                            String contents = sheet.getCell(4, row).getContents();
                        if (contents.contains(localName)) {
                           x = sheet.getCell(5, row).getContents();
                           y = sheet.getCell(6, row).getContents();
                           break;
                        }

//                        String contents = sheet.getCell(3, row).getContents();
//                        Log.e("확인", contents);
//                        if (contents.contains(localName)) {
//                            x = sheet.getCell(5, row).getContents();
//                            y = sheet.getCell(6, row).getContents();
//                            break;
//                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.i("READ_EXCEL1", e.getMessage());
            e.printStackTrace();
        } catch (BiffException e) {
            Log.i("READ_EXCEL1", e.getMessage());
            e.printStackTrace();
        }
        Log.i("격자값", "x = " + x + " y = " + y);
    }

    public void setComponent(){
        home.setText(localName);
        weath.setText(weather);
        temper.setText(temperature);

        switch(weather){
            case "맑음":
                img.setImageResource(R.drawable.sunny);
                break;
            case "구름 많음":
                img.setImageResource(R.drawable.many_cloud);
                break;
            case "흐림":
                img.setImageResource(R.drawable.cloudy);
                break;
            default:
                img.setImageResource(R.drawable.rain);
                break;
        }

        Toast.makeText(MainActivity.this, "새로고침 되었습니다.", Toast.LENGTH_SHORT).show();
    }



}

