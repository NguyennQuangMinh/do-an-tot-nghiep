package com.example.app_nhiptim;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Activity_020511 extends AppCompatActivity {
    private ImageView imgStatus020511, imgAppStatus;
    private EditText edtMaNguoiBenh, edtTenNguoiBenh;
    private LineChart lineChart_BPM, lineChart_SPO2;
    private TextView txtBPM_020511_ct, txtSP02_020511_ct;
    private ListView lstLichSu;
    private TextView txtLuu, txtReset;
    private ImageView imgBack;

    // khai báo kết nối MQTT
    MqttAndroidClient mqttAndroidClient;
    private static final String Tag = "";
    String clientID = MqttClient.generateClientId();
    MqttAndroidClient client;

    // handle kiểm tra thiết bị có kết nối tới mqtt hay không
    private Handler handler_mqtt;
    private Runnable checkConnectionRunnable;

    // line chart BPM
    private List<Entry> entries_BPM;
    private Handler handler_chart_BPM;
    private int xIndex_BPM = 0; // Biến để theo dõi chỉ số x

    // line chart SPO2
    private List<Entry> entries_SPO2;
    private Handler handler_chart_SPO2;
    private int xIndex_SPO2 = 0; // Biến để theo dõi chỉ số x

    //luu lich su vao list view
    private ArrayAdapter<String> adapter;
    private ArrayList<String> historyList;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_020511);
        // Ánh xạ
        imgStatus020511 = findViewById(R.id.imgStatus_020511);
        imgAppStatus = findViewById(R.id.imgAppStatus);
        edtMaNguoiBenh = findViewById(R.id.edtManguoibenh);
        edtTenNguoiBenh = findViewById(R.id.edtTennguoibenh);
        lineChart_BPM = findViewById(R.id.chart_BPM);
        lineChart_SPO2 = findViewById(R.id.chart_SPO2);
        txtBPM_020511_ct = findViewById(R.id.txtBPM_020511_ct);
        txtSP02_020511_ct = findViewById(R.id.txtSPO2_020511_ct);
        lstLichSu = findViewById(R.id.lstLichSu);
        txtLuu = findViewById(R.id.txtLuu);
        txtReset = findViewById(R.id.txtReset);
        imgBack = findViewById(R.id.imgBack);

        // tạo handler để biết thiết bị kết nối với mqtt hay không
        handler_mqtt = new Handler();

        //kết nối MQTT
        MQTT();
        imgAppStatus.setOnClickListener(v -> MQTT());

        // vẽ line chart
        Line_Chart_BPM();
        Line_Chart_SPO2();

        // luu lich su do theo thoi gian
        // Khởi tạo danh sách lịch sử
        historyList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        lstLichSu.setAdapter(adapter);

        // Tải dữ liệu từ SharedPreferences
        sharedPreferences = getSharedPreferences("HistoryPrefs_1", MODE_PRIVATE);
        loadHistory();

        // Lưu dữ liệu khi nhấn txtLuu
        txtLuu.setOnClickListener(v -> saveData());

        // Xóa dữ liệu khi nhấn vào item trong ListView
        lstLichSu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(position);
            }
        });

        // reset
        txtReset.setOnClickListener(v -> {
            // Tạo AlertDialog để hỏi người dùng
            new AlertDialog.Builder(Activity_020511.this)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có chắc muốn Reset không?")
                    .setNegativeButton("OK", (dialog, which) -> {
                        resetChart_BPM();//thực hiện reset biểu đồ BPM
                        resetChart_SPO2();////thực hiện reset biểu đồ SPO2
                        resetHistory();
                    })
                    .setPositiveButton("Hủy", (dialog, which) -> {
                        // Người dùng nhấn "Hủy", đóng dialog
                        dialog.dismiss();
                    })
                    .show();
        });
        imgBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String maNguoiBenh_2 = edtMaNguoiBenh.getText().toString();
                String tenNguoiBenh_2 = edtTenNguoiBenh.getText().toString();

                // Tạo intent để chuyển dữ liệu sang MainActivity
                Intent intent = new Intent(Activity_020511.this, MainActivity.class);
                intent.putExtra("maNguoiBenh_2", maNguoiBenh_2);
                intent.putExtra("tenNguoiBenh_2", tenNguoiBenh_2);

                // Bắt đầu MainActivity
                startActivity(intent);
            }
        });

        // hiển thị dữ liệu cũ lên lại edtMaNguoiBenh_2 và edt tenNguoiBenh_2
        Intent intent = getIntent();
        String maNguoiBenh_2 = intent.getStringExtra("maNguoiBenh_2");
        String tenNguoiBenh_2 = intent.getStringExtra("tenNguoiBenh_2");

        // Hiển thị dữ liệu lên TextView
        edtMaNguoiBenh.setText(maNguoiBenh_2);
        edtTenNguoiBenh.setText(tenNguoiBenh_2);
    }

    @Override
    public void onBackPressed() {
        // Gọi phương thức sendDataBack() khi nhấn nút "back" của điện thoại
        super.onBackPressed();
        String maNguoiBenh_2 = edtMaNguoiBenh.getText().toString();
        String tenNguoiBenh_2 = edtTenNguoiBenh.getText().toString();

        // Tạo intent để chuyển dữ liệu sang MainActivity
        Intent intent = new Intent(Activity_020511.this, MainActivity.class);
        intent.putExtra("maNguoiBenh_2", maNguoiBenh_2);
        intent.putExtra("tenNguoiBenh_2", tenNguoiBenh_2);

        // Bắt đầu MainActivity
        startActivity(intent);
    }

    // kết nối mqtt
    public void MQTT() {
        String clientID = MqttClient.generateClientId();
        final MqttAndroidClient client = new MqttAndroidClient(this.getApplicationContext(),
                "tcp://mqtt.eclipseprojects.io:1883", clientID);//"tcp://broker.hivemq.com:1883"
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
        options.setCleanSession(false);

        // Remove these lines if not required
        // options.setUserName("");
        // options.setPassword("".toCharArray());

        try {
            final IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Toast.makeText(Activity_020511.this, "Connected MQTT server", Toast.LENGTH_SHORT).show();
                    imgAppStatus.setBackgroundResource(R.drawable.connect);
                    SUB(client, "esp32_Max30102_020511");//thiết bị 020511
                    client.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            Toast.makeText(Activity_020511.this, "Mat ket noi Server", Toast.LENGTH_SHORT).show();
                            imgAppStatus.setBackgroundResource(R.drawable.disconnect);
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) throws Exception {
                            // lay du lieu BPM và SPO2 cua thiet bi 020510 tu MQTT ve
                            if(topic.equals("esp32_Max30102_020511"))
                            {
                                try {
                                    JSONObject jsonObject = new JSONObject(message.toString());
                                    int bpm = jsonObject.getInt("BPM");
                                    int spo2 = jsonObject.getInt("SPO2");

                                    // Cập nhật giá trị BPM và SpO2 lên TextView
                                    runOnUiThread(() -> {
                                        txtBPM_020511_ct.setText(String.valueOf(bpm));
                                        txtSP02_020511_ct.setText(String.valueOf(spo2));

                                        // Cập nhật trạng thái thiết bị 020510 khi có dữ liệu
                                        imgStatus020511.setBackgroundResource(R.drawable.point_connect);
                                    });

                                    // Reset thời gian chờ 5 giây mỗi khi nhận được dữ liệu
                                    resetConnectionTimeout();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                        }
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Toast.makeText(MainActivity.this, "that bai", Toast.LENGTH_SHORT).show();
                    Log.d(Tag, "onFailure");
                    imgAppStatus.setBackgroundResource(R.drawable.disconnect);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    } // end MQTT
    public void SUB( MqttAndroidClient client, String topic)
    {
        int qos = 1;
        try
        {
            IMqttToken subToken = client.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // the message was published
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                }
            });

        }
        catch (MqttException e)
        {
            e.printStackTrace();
        }
    }//end SUB
    // MQTT //

    // kiểm tra thiết bị có online hay không
    private void resetConnectionTimeout() {
        if (handler_mqtt != null && checkConnectionRunnable != null) {
            handler_mqtt.removeCallbacks(checkConnectionRunnable);
        }

        // Tạo một Runnable mới để kiểm tra kết nối sau 5 giây
        checkConnectionRunnable = () -> {
            //set img point về disconnect
            imgStatus020511.setBackgroundResource(R.drawable.point_disconnect);
            // Đặt lại giá trị của txtBPM_020510 và txtSPO2_020510 về "NaN"
            runOnUiThread(() -> {
                txtBPM_020511_ct.setText("NaN");
                txtSP02_020511_ct.setText("NaN");
            });
        };

        // Đặt lại thời gian chờ 15 giây
        handler_mqtt.postDelayed(checkConnectionRunnable, 7000);
    }
    // kiểm tra thiết bị có online hay không //


    // line chart BPM
    private void Line_Chart_BPM() {
        Description description = new Description();
        description.setText("BPM record");
        description.setPosition(150f, 15f);
        lineChart_BPM.setDescription(description);
        lineChart_BPM.getAxisRight().setDrawLabels(false);

        // Khởi tạo danh sách giá trị y
        entries_BPM = new ArrayList<>();

        XAxis xAxis = lineChart_BPM.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(4);
        xAxis.setGranularity(1f);

        YAxis yAxis = lineChart_BPM.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(180f);
        yAxis.setAxisLineWidth(2f);
        yAxis.setAxisLineColor(Color.BLACK);
        yAxis.setLabelCount(10);

        // Khởi tạo Handler và Random
        handler_chart_BPM = new Handler();

        // Bắt đầu cập nhật mỗi giây
        startUpdating_BPM();

        lineChart_BPM.invalidate(); // Cập nhật biểu đồ ngay lập tức
    }

    private void startUpdating_BPM() {
        handler_chart_BPM.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Lấy giá trị từ TextView và chuyển thành số
                    String bpmText = txtBPM_020511_ct.getText().toString();
                    float bpmValue = Float.parseFloat(bpmText);

                    // Kiểm tra nếu giá trị không phải là NaN thì thêm vào danh sách entries
                    if (!Float.isNaN(bpmValue)) {
                        entries_BPM.add(new Entry(xIndex_BPM++, bpmValue));

                        // Tạo LineDataSet và LineData
                        LineDataSet dataSet = new LineDataSet(entries_BPM, "BPM Data");
                        dataSet.setColor(Color.RED);

                        LineData lineData = new LineData(dataSet);
                        lineChart_BPM.setData(lineData);
                        lineChart_BPM.moveViewToX(lineData.getEntryCount()); // Tự động cuộn khi có nhiều dữ liệu
                        lineChart_BPM.invalidate(); // Cập nhật biểu đồ
                    }
                } catch (NumberFormatException e) {
                    // Trường hợp không chuyển đổi được (có thể là NaN hoặc dữ liệu không hợp lệ)
                    Log.e("Activity_020510", "Invalid BPM data: " + txtBPM_020511_ct.getText());
                }

                // Gọi lại phương thức này sau 1 giây
                handler_chart_BPM.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void resetChart_BPM() {
        // Đặt lại danh sách entries và chỉ số xIndex
        entries_BPM.clear();
        xIndex_BPM = 0;

        // Tạo LineDataSet và LineData mới
        LineDataSet dataSet = new LineDataSet(entries_BPM, "BPM Data");
        dataSet.setColor(Color.RED);

        LineData lineData = new LineData(dataSet);
        lineChart_BPM.setData(lineData); // Cập nhật dữ liệu cho biểu đồ
        lineChart_BPM.invalidate(); // Cập nhật biểu đồ
    }
    // line chart BPM //

    // line chart SPO2
    private void Line_Chart_SPO2() {
        Description description = new Description();
        description.setText("SPO2 record");
        description.setPosition(150f, 15f);
        lineChart_SPO2.setDescription(description);
        lineChart_SPO2.getAxisRight().setDrawLabels(false);

        // Khởi tạo danh sách giá trị y
        entries_SPO2 = new ArrayList<>();

        XAxis xAxis = lineChart_SPO2.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(4);
        xAxis.setGranularity(1f);

        YAxis yAxis = lineChart_SPO2.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(100f);
        yAxis.setAxisLineWidth(2f);
        yAxis.setAxisLineColor(Color.BLACK);
        yAxis.setLabelCount(10);

        // Khởi tạo Handler SPO2
        handler_chart_SPO2 = new Handler();

        // Bắt đầu cập nhật mỗi giây
        startUpdating_SPO2();

        lineChart_SPO2.invalidate(); // Cập nhật biểu đồ ngay lập tức
    }

    private void startUpdating_SPO2() {
        handler_chart_SPO2.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Lấy giá trị từ TextView và chuyển thành số
                    String SPO2Text = txtSP02_020511_ct.getText().toString();
                    float SPO2Value = Float.parseFloat(SPO2Text);

                    // Kiểm tra nếu giá trị không phải là NaN thì thêm vào danh sách entries
                    if (!Float.isNaN(SPO2Value)) {
                        entries_SPO2.add(new Entry(xIndex_SPO2++, SPO2Value));

                        // Tạo LineDataSet và LineData
                        LineDataSet dataSet = new LineDataSet(entries_SPO2, "SPO2 Data");
                        dataSet.setColor(Color.BLUE);

                        LineData lineData = new LineData(dataSet);
                        lineChart_SPO2.setData(lineData);
                        lineChart_SPO2.moveViewToX(lineData.getEntryCount()); // Tự động cuộn khi có nhiều dữ liệu
                        lineChart_SPO2.invalidate(); // Cập nhật biểu đồ
                    }
                } catch (NumberFormatException e) {
                    // Trường hợp không chuyển đổi được (có thể là NaN hoặc dữ liệu không hợp lệ)
                    Log.e("Activity_020511", "Invalid SPO2 data: " + txtSP02_020511_ct.getText());
                }

                // Gọi lại phương thức này sau 1 giây
                handler_chart_SPO2.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void resetChart_SPO2() {
        // Đặt lại danh sách entries và chỉ số xIndex
        entries_SPO2.clear();
        xIndex_SPO2 = 0;

        // Tạo LineDataSet và LineData mới
        LineDataSet dataSet = new LineDataSet(entries_SPO2, "SPO2 Data");
        dataSet.setColor(Color.BLUE);

        LineData lineData = new LineData(dataSet);
        lineChart_SPO2.setData(lineData); // Cập nhật dữ liệu cho biểu đồ
        lineChart_SPO2.invalidate(); // Cập nhật biểu đồ
    }
    // line chart SPO2//

    // lưu lịch sử đo theo thời gian
    private void saveData() {
        String bpmValue = txtBPM_020511_ct.getText().toString();
        String spo2Value = txtSP02_020511_ct.getText().toString();

        // Lấy thời gian hiện tại
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        String currentDateTime = sdf.format(new Date());

        // Tạo bản ghi mới với thời gian
        String record = "BPM: " + bpmValue + " - SPO2: " + spo2Value + " - Thời gian: " + currentDateTime;

        // Lưu vào danh sách và cập nhật ListView
        historyList.add(0, record); // Thêm vào đầu danh sách
        adapter.notifyDataSetChanged();
        saveHistory(); // Lưu vào SharedPreferences
    }

    private void loadHistory() {
        Set<String> set = sharedPreferences.getStringSet("History", new HashSet<>());
        historyList.clear();
        historyList.addAll(set);

        // Sort history in ascending order of time
        Collections.sort(historyList, new Comparator<String>() {
            @Override
            public int compare(String record1, String record2) {
                // Extract time from records
                String time1 = record1.substring(record1.lastIndexOf('-') + 1).trim();
                String time2 = record2.substring(record2.lastIndexOf('-') + 1).trim();

                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
                try {
                    Date date1 = sdf.parse(time1);
                    Date date2 = sdf.parse(time2);
                    // Compare for ascending order (date1.compareTo(date2))
                    return date1.compareTo(date2);
                } catch (ParseException e) {
                    return 0; // Handle parsing errors (e.g., log, default)
                }
            }
        });

        adapter.notifyDataSetChanged();
    }

    private void saveHistory() {
        Set<String> set = new HashSet<>(historyList);
        sharedPreferences.edit().putStringSet("History", set).apply();
    }

    private void confirmDelete(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa dữ liệu")
                .setMessage("Bạn có chắc chắn muốn xóa mục này không?")
                .setNegativeButton("Có", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Xóa item và cập nhật lịch sử
                        historyList.remove(position);
                        adapter.notifyDataSetChanged();
                        saveHistory(); // Lưu lại trạng thái mới của lịch sử
                    }
                })
                .setPositiveButton("Không", null)
                .show();
    }

    private void resetHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Reset lịch sử")
                .setMessage("Bạn có chắc chắn muốn reset toàn bộ lịch sử không?")
                .setNegativeButton("Có", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        historyList.clear();
                        adapter.notifyDataSetChanged();
                        saveHistory(); // Cập nhật lại SharedPreferences
                    }
                })
                .setPositiveButton("Không", null)
                .show();
    }
    // lưu lịch sử đo theo thời gian //

}