package ones.demo.aqimonitorapp;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    ArrayList<String> countyArrayLists;
    ArrayList<String> siteArrayLists;
    ArrayList<MyLocation> locationArrayLists;
    ArrayAdapter countyArrayAdapter;
    ArrayAdapter siteArrayAdapter;
    Spinner countySpinner, siteSpinner;
    static final String db_name = "Demo";
    static final String db_tableName = "AirQuality";
    SQLiteDatabase db;
    static String myUrl = "http://opendata2.epa.gov.tw/AQI.json";
    static String myUrl2 = "http://opendata.epa.gov.tw/ws/Data/AQXSite/?format=json";
    public LocationManager locationManager;
    public LocationListener locationListener;
    int msecond = 0;
    float distance = 0;
    MyLocation myCurrentLocation;
    String currentSiteName = "";
    String currentCounty = "";

    TextView updateDateTextView;
    TextView AQITextView;
    TextView SO2TextView;
    TextView NO2TextView;
    TextView COTextView;
    TextView O3TextView;
    TextView PM2DOT5TextView;
    TextView PM10TextView;
    TextView workStationTextView;

    Thread databaseUpdateThread;
    boolean databaseUpdateActive = false;
    Thread uiDataUpdateThread;
    boolean uiDataUpdateActive = false;

    // 編號表格欄位名稱，固定不變
    public static final String KEY_ID = "_id";

    // 計算兩點距離
    private final double EARTH_RADIUS = 6378137.0;

    private double gps2m(double lat_a, double lng_a, double lat_b, double lng_b) {
        double radLat1 = (lat_a * Math.PI / 180.0);
        double radLat2 = (lat_b * Math.PI / 180.0);
        double a = radLat1 - radLat2;
        double b = (lng_a - lng_b) * Math.PI / 180.0;
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.pow(Math.sin(b / 2), 2)));
        s = s * EARTH_RADIUS;
        s = Math.round(s * 10000) / 10000;
        return s;
    }

    public void onSearch(View view)
    {
         String county = countyArrayLists.get(countySpinner.getSelectedItemPosition());
         String sitename = siteArrayLists.get(siteSpinner.getSelectedItemPosition());

         currentSiteName = sitename;
         currentCounty = county;

        AQITextView.setText("擷取中...");
        SO2TextView.setText("擷取中...");
        NO2TextView.setText("擷取中...");
        COTextView.setText("擷取中...");
        O3TextView.setText("擷取中...");
        PM2DOT5TextView.setText("擷取中...");
        PM10TextView.setText("擷取中...");
        workStationTextView.setText("未知 工作站資訊");
    }

    public void onUIDataUpdate()
    {
        //Log.i("State","UIDataUpdating...");

          Cursor c =db.rawQuery("SELECT * FROM " + db_tableName+" WHERE SiteName ='" + currentSiteName +"'", null);
          c.moveToFirst();    // 移到第 1 筆資料

          String note = "";
          int AQI = Integer.parseInt(c.getString(5));
          if(AQI>=0 && AQI <=50) {
              note = "良好";
              //AQITextView.setTextColor(Color.GREEN);
          }
          else if(AQI>=51 && AQI <=100){
              note = "普通";
              //AQITextView.setTextColor(Color.YELLOW);
          }
          else if(AQI>=101 && AQI <=150)  {
              note = "對敏感族群不健康";
              //AQITextView.setTextColor(Color.rgb(255,100,0));
          }
          else if(AQI>=151 && AQI <=200) {
              note = "對所有族群不健康";
              //AQITextView.setTextColor(Color.RED);
          }
          else if(AQI>=201 && AQI <=300){
              note = "非常不健康";
              //AQITextView.setTextColor(Color.rgb(255,0,255));
          }
          else if(AQI>=301 && AQI <=500)  {
              note = "危害";
             // AQITextView.setTextColor(Color.rgb(150,100,255));

          }


          AQITextView.setText(c.getString(5)+" "+note);
          SO2TextView.setText(c.getString(6)+" ppb");
          NO2TextView.setText(c.getString(7)+" ppb");
          COTextView.setText(c.getString(8)+" ppm");
          O3TextView.setText(c.getString(9)+" ppm");
          PM2DOT5TextView.setText(c.getString(10)+" μg/m3");
          PM10TextView.setText(c.getString(11)+" μg/m3");
          updateDateTextView.setText("最新觀測時間: "+c.getString(12));
          workStationTextView.setText(currentSiteName+" 工作站資訊");

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1){

            if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, msecond, distance, locationListener);
                }
            }
        }
    }

    public void onCreateDB()
    {
        db = openOrCreateDatabase(db_name, Context.MODE_PRIVATE, null);

        String deleteTable = "DROP TABLE IF EXISTS " + db_tableName;
        db.execSQL(deleteTable);

        //"TWD97Lon":"121.1504500000","TWD97Lat":"22.7553580000"

        String createTable = "CREATE TABLE IF NOT EXISTS " +
                             db_tableName +" (" +
                             KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                             "SiteName VARCHAR(32), " +
                             "County VARCHAR(32), " +
                             "Lon REAL DEFAULT 0.0," +
                             "Lat REAL DEFAULT 0.0, " +
                             "AQI VARCHAR(16) DEFAULT 'ND'," +
                             "SO2 VARCHAR(16) DEFAULT 'ND', " +
                             "NO2 VARCHAR(16) DEFAULT 'ND', " +
                             "CO VARCHAR(16) DEFAULT 'ND', " +
                             "O3 VARCHAR(16) DEFAULT 'ND'," +
                             "PM2DOT5 VARCHAR(16) DEFAULT 'ND'," +
                             "PM10 VARCHAR(16) DEFAULT 'ND', " +
                             "PublishTime VARCHAR(48))";
        db.execSQL(createTable);

        onDatabaseInitial();
    }

    public void onDatabaseInitial()
    {
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.mode = 1;
        downloadTask.execute(myUrl);
    }

    public void onDatabaseUpdate(){

        DownloadTask downloadTask = new DownloadTask();
        downloadTask.mode = 3;
        downloadTask.execute(myUrl);

    }

    public class DownloadTask extends AsyncTask<String, Void, String>
    {
        URL url;
        HttpURLConnection httpURLConnection;
        String result = "";
        int mode = 0;

        @Override
        protected String doInBackground(String... urls) {
            try {
                url = new URL(urls[0]);
                result = "";
                httpURLConnection = (HttpURLConnection)url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                int data = inputStreamReader.read();

                while(data != -1)
                {
                    char current = (char)data;
                    result += current;
                    data = inputStreamReader.read();
                }

                return  result;

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            //Log.i("Result", result.toString());

            try {
                if(mode == 1)
                {
                        JSONArray jsonArray = new JSONArray(result);
                        db.beginTransaction();
                        for(int i=0; i<jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);

                            ContentValues contentValues = new ContentValues();
                            contentValues.put("SiteName", jsonObject.getString("SiteName"));
                            contentValues.put("County", jsonObject.getString("County"));
                            contentValues.put("Lon",0.0);
                            contentValues.put("Lat",0.0);
                            contentValues.put("AQI", jsonObject.getString("AQI"));
                            contentValues.put("SO2", jsonObject.getString("SO2"));
                            contentValues.put("NO2", jsonObject.getString("NO2"));
                            contentValues.put("CO", jsonObject.getString("CO"));
                            contentValues.put("O3", jsonObject.getString("O3"));
                            contentValues.put("PM2DOT5", jsonObject.getString("PM2.5"));
                            contentValues.put("PM10", jsonObject.getString("PM10"));
                            contentValues.put("PublishTime", jsonObject.getString("PublishTime"));

                            db.insert(db_tableName, null, contentValues);

                        }
                        db.setTransactionSuccessful();
                        db.endTransaction();

                        DownloadTask downloadTask2 = new DownloadTask();
                        downloadTask2.mode = 2;
                        downloadTask2.execute(myUrl2);

                }
                else if(mode == 2)
                {

                    JSONArray jsonArray = new JSONArray(result);
                    db.beginTransaction();
                    for(int i=0; i<jsonArray.length(); i++)
                    {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String SiteName = jsonObject.getString("SiteName");

                        double Lon = jsonObject.getDouble("TWD97Lon");
                        double Lat = jsonObject.getDouble("TWD97Lat");

                        ContentValues contentValues = new ContentValues();
                        contentValues.put("Lon",Lon);
                        contentValues.put("Lat",Lat);

                        db.update(db_tableName, contentValues, "SiteName='" + SiteName+"'", null);


                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();

                    //Log.i("Initial State","Done");

                    countyArrayLists.clear();
                    siteArrayLists.clear();
                    Cursor c=db.rawQuery("SELECT * FROM "+db_tableName, null);
                    double minDistance = -1;
                    String recentSiteName = "";
                    String recentCounty = "";
                    String recentUpdateTime = "";
                    c.moveToFirst();    // 移到第 1 筆資料
                    do{        // 逐筆讀出資料
                        double tempLongitude = Double.parseDouble(c.getString(3));
                        double tempLatitude = Double.parseDouble(c.getString(4));
                        double tempDistance = gps2m(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude(),tempLatitude, tempLongitude);
                        if(minDistance == -1 || tempDistance < minDistance)
                        {
                            minDistance = tempDistance;
                            recentSiteName = c.getString(1);
                            recentCounty = c.getString(2);
                        }
                        recentUpdateTime = c.getString(12);
                        boolean isFind = false;
                        for(int i=0; i<countyArrayLists.size(); i++) if(countyArrayLists.get(i).equals(c.getString(2))) isFind =true;
                        if(isFind == false) countyArrayLists.add(c.getString(2));
                    } while(c.moveToNext());    // 有一下筆就繼續迴圈
                   // updateDateTextView.setText("最新觀測時間: "+recentUpdateTime);
                    countyArrayAdapter.notifyDataSetChanged();

                    //Log.i("Recent SiteName",recentSiteName);
                    //Log.i("Recent County",recentCounty);

                    Cursor c2 =db.rawQuery("SELECT SiteName FROM " + db_tableName+" WHERE County ='" + recentCounty+"'", null);
                    c2.moveToFirst();    // 移到第 1 筆資料
                    do{        // 逐筆讀出資料
                        siteArrayLists.add(c2.getString(0));
                        //Log.i("SiteName",c2.getString(0));
                    } while(c2.moveToNext());    // 有一下筆就繼續迴圈
                    siteArrayAdapter.notifyDataSetChanged();

                    currentSiteName = recentSiteName;
                    currentCounty = recentCounty;

                    onUIDataUpdate();

                    for(int i=0; i<siteArrayLists.size(); i++)
                    {
                        if(recentSiteName.equals(siteArrayLists.get(i)))
                        {
                            siteSpinner.setSelection(i);
                            break;
                        }
                    }
                    for(int i=0; i<countyArrayLists.size(); i++)
                    {
                        if(recentCounty.equals(countyArrayLists.get(i)))
                        {
                            countySpinner.setSelection(i);
                            break;
                        }
                    }

                    databaseUpdateThread = new databaseUpdateThread();
                    databaseUpdateActive = true;
                    databaseUpdateThread.start();

                }
                else if(mode == 3)
                {
                    JSONArray jsonArray = new JSONArray(result);
                    db.beginTransaction();
                    for(int i=0; i<jsonArray.length(); i++)
                    {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);

                        ContentValues contentValues = new ContentValues();
                        contentValues.put("AQI", jsonObject.getString("AQI"));
                        contentValues.put("SO2", jsonObject.getString("SO2"));
                        contentValues.put("NO2", jsonObject.getString("NO2"));
                        contentValues.put("CO", jsonObject.getString("CO"));
                        contentValues.put("O3", jsonObject.getString("O3"));
                        contentValues.put("PM2DOT5", jsonObject.getString("PM2.5"));
                        contentValues.put("PM10", jsonObject.getString("PM10"));
                        contentValues.put("PublishTime", jsonObject.getString("PublishTime"));

                        db.update(db_tableName, contentValues, "SiteName='" + jsonObject.getString("SiteName") +"'", null);

                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();

                    onUIDataUpdate();

                    //Log.i("Update State","Done");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        countyArrayLists = new ArrayList<String>();
        siteArrayLists = new ArrayList<String>();
        locationArrayLists = new ArrayList<MyLocation>();
        countySpinner = (Spinner)findViewById(R.id.countrySpinner);
        countyArrayAdapter = new ArrayAdapter(MainActivity.this, R.layout.support_simple_spinner_dropdown_item, countyArrayLists);
        siteSpinner = (Spinner)findViewById(R.id.siteSpinner);
        siteArrayAdapter = new ArrayAdapter(MainActivity.this, R.layout.support_simple_spinner_dropdown_item, siteArrayLists);
        countySpinner.setAdapter(countyArrayAdapter);
        siteSpinner.setAdapter(siteArrayAdapter);
        myCurrentLocation = new MyLocation(0,0);

        updateDateTextView = (TextView)findViewById(R.id.updateDateTextView);
        AQITextView = (TextView)findViewById(R.id.AQITextViewField);
        SO2TextView = (TextView)findViewById(R.id.SO2TextViewField);
        NO2TextView = (TextView)findViewById(R.id.NO2TextViewField);
        COTextView = (TextView)findViewById(R.id.COTextViewField);
        O3TextView = (TextView)findViewById(R.id.O3TextViewField);
        PM2DOT5TextView = (TextView)findViewById(R.id.PM2DOT5TextViewField);;
        PM10TextView = (TextView)findViewById(R.id.PM10TextViewField);
        workStationTextView = (TextView)findViewById(R.id.workStationTextView);

        countyArrayLists.clear();
        siteArrayLists.clear();

        onCreateDB();
        //onWebDataStore();

        countySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("Conuty",countyArrayLists.get(position).toString()+" has tapped!");
                String county = countyArrayLists.get(position).toString();

                Cursor c2 =db.rawQuery("SELECT SiteName FROM " + db_tableName+" WHERE County ='" + county +"'", null);
                siteArrayLists.clear();
                c2.moveToFirst();    // 移到第 1 筆資料
                do{        // 逐筆讀出資料
                    siteArrayLists.add(c2.getString(0));
                    //Log.i("SiteName",c2.getString(0));
                } while(c2.moveToNext());    // 有一下筆就繼續迴圈
                siteArrayAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /*
        siteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("SiteName",siteArrayLists.get(position).toString()+" has tapped!");
                currentSiteName = siteArrayLists.get(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
*/

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
               // Log.i("Location", String.valueOf(location.getLongitude())+":"+String.valueOf(location.getLatitude()));
                myCurrentLocation.setLatitude(location.getLatitude());
                myCurrentLocation.setLongitude(location.getLongitude());

                Cursor c=db.rawQuery("SELECT * FROM "+db_tableName, null);
                double minDistance = -1;
                String recentSiteName = "";
                String recentCounty = "";

                c.moveToFirst();    // 移到第 1 筆資料
                do{        // 逐筆讀出資料
                    double tempLongitude = Double.parseDouble(c.getString(3));
                    double tempLatitude = Double.parseDouble(c.getString(4));
                    double tempDistance = gps2m(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude(),tempLatitude, tempLongitude);
                    if(minDistance == -1 || tempDistance < minDistance)
                    {
                        minDistance = tempDistance;
                        recentSiteName = c.getString(1);
                        recentCounty = c.getString(2);
                    }
                } while(c.moveToNext());    // 有一下筆就繼續迴圈

                if(!recentSiteName.equals(currentSiteName)) {
                    currentSiteName = recentSiteName;
                    currentCounty = recentCounty;

                    siteArrayLists.clear();
                    Cursor c2 = db.rawQuery("SELECT SiteName FROM " + db_tableName + " WHERE County ='" + recentCounty + "'", null);
                    c2.moveToFirst();    // 移到第 1 筆資料
                    do {        // 逐筆讀出資料
                        siteArrayLists.add(c2.getString(0));
                        //Log.i("SiteName",c2.getString(0));
                    } while (c2.moveToNext());    // 有一下筆就繼續迴圈
                    siteArrayAdapter.notifyDataSetChanged();

                    onUIDataUpdate();

                    for (int i = 0; i < siteArrayLists.size(); i++) {
                        if (recentSiteName.equals(siteArrayLists.get(i))) {
                            siteSpinner.setSelection(i);
                            break;
                        }
                    }
                    for (int i = 0; i < countyArrayLists.size(); i++) {
                        if (recentCounty.equals(countyArrayLists.get(i))) {
                            countySpinner.setSelection(i);
                            break;
                        }
                    }
                }


            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };


        if (Build.VERSION.SDK_INT < 23) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, msecond, distance, locationListener);
        } else{
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            } else{
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, msecond, distance, locationListener);
                Location lastKnowLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                //Log.i("LastKnowLocation", String.valueOf(lastKnowLocation.getLongitude())+":"+String.valueOf(lastKnowLocation.getLatitude()));
                myCurrentLocation.setLatitude(lastKnowLocation.getLatitude());
                myCurrentLocation.setLongitude(lastKnowLocation.getLongitude());
            }
        }


    }

    class databaseUpdateThread extends Thread {

        @Override
        public void run() {
            super.run();
            while(databaseUpdateActive){
                try {
                    //Log.i("Database","Updating....");
                    Thread.sleep(1000);
                    Message message = new Message();
                    mHandler.handleMessage(message);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            onDatabaseUpdate();
        }
    };
}
