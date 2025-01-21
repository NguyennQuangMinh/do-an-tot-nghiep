package com.example.app_nhiptim;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

import android.os.Handler;



import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {
    // thiết bị 020510
    private TextView txtBPM_020510, txtSPO2_020510;
    private TextView txtMaNguoiBenh_1, txtTenNguoiBenh_1;
    private ImageView imgAppStatus, imgStatus_020510;
    private RelativeLayout rlt020510;

    // thiết bị 020511
    private TextView txtBPM_020511, txtSPO2_020511;
    private TextView txtMaNguoiBenh_2, txtTenNguoiBenh_2;
    private ImageView   imgStatus_020511;
    private RelativeLayout rlt020511;

    // khai báo kết nối MQTT
    MqttAndroidClient mqttAndroidClient;
    private static final String Tag = "";
    String clientID = MqttClient.generateClientId();
    MqttAndroidClient client;

    // handle kiểm tra thiết bị có kết nối tới mqtt hay không
    private Handler handler_mqtt;
    private Runnable checkConnectionRunnable_020510;
    private Runnable checkConnectionRunnable_020511;
    // lưu giá trị mã người bệnh và tên người bệnh
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AnhXa();

        // tạo handler để biết thiết bị kết nối với mqtt hay không
        handler_mqtt = new Handler();

        //kết nối MQTT
        MQTT();
        imgAppStatus.setOnClickListener(v -> MQTT());

        //chuyển activity chi tiết thiết bị 020510
        rlt020510.setOnClickListener(v -> {
            String maNguoiBenh_1 = txtMaNguoiBenh_1.getText().toString();
            String tenNguoiBenh_1 = txtTenNguoiBenh_1.getText().toString();

            Intent x1 = new Intent(MainActivity.this, Activity_020510.class);

            x1.putExtra("maNguoiBenh_1", maNguoiBenh_1);
            x1.putExtra("tenNguoiBenh_1", tenNguoiBenh_1);

            startActivity(x1);
        });

        //chuyển activity chi tiết thiết bị 020511
        rlt020511.setOnClickListener(v -> {
            String maNguoiBenh_2 = txtMaNguoiBenh_2.getText().toString();
            String tenNguoiBenh_2 = txtTenNguoiBenh_2.getText().toString();

            Intent x1 = new Intent(MainActivity.this, Activity_020511.class);

            x1.putExtra("maNguoiBenh_2", maNguoiBenh_2);
            x1.putExtra("tenNguoiBenh_2", tenNguoiBenh_2);

            startActivity(x1);
        });

        MAandTenBN();// Nhận dữ liệu từ Intent để hiển thị mã và tên người bệnh

    }

    private void AnhXa() {
        // Ánh xạ thiết bị 1
        txtBPM_020510 = findViewById(R.id.txtBPM_020510);
        txtSPO2_020510 = findViewById(R.id.txtSPO2_020510);
        txtMaNguoiBenh_1 = findViewById(R.id.txtMaNguoiBenh_1);
        txtTenNguoiBenh_1 = findViewById(R.id.txtTenNguoiBenh_1);
        imgAppStatus = findViewById(R.id.imgAppStatus);
        imgStatus_020510 = findViewById(R.id.imgStatus_020510);
        rlt020510 = findViewById(R.id.rlt020510);

        // Ánh xạ thiết bị 2
        txtBPM_020511 = findViewById(R.id.txtBPM_020511);
        txtSPO2_020511 = findViewById(R.id.txtSPO2_020511);
        txtMaNguoiBenh_2 = findViewById(R.id.txtMaNguoiBenh_2);
        txtTenNguoiBenh_2 = findViewById(R.id.txtTenNguoiBenh_2);
        imgStatus_020511 = findViewById(R.id.imgStatus_020511);
        rlt020511 = findViewById(R.id.rlt020511);
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
                    Toast.makeText(MainActivity.this, "Connected MQTT server", Toast.LENGTH_SHORT).show();
                    imgAppStatus.setBackgroundResource(R.drawable.connect);
                    SUB(client, "esp32_Max30102_020510"); // thiết bị 020510
                    SUB(client, "esp32_Max30102_020511"); // thiết bị 020511
                    client.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            Toast.makeText(MainActivity.this, "Mat ket noi Server", Toast.LENGTH_SHORT).show();
                            imgAppStatus.setBackgroundResource(R.drawable.disconnect);
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) throws Exception {
                            // lay du lieu BPM và SPO2 cua thiet bi 020510 tu MQTT ve
                            if(topic.equals("esp32_Max30102_020510"))
                            {
                                try {
                                    JSONObject jsonObject = new JSONObject(message.toString());
                                    int bpm_020510 = jsonObject.getInt("BPM");
                                    int spo2_020510 = jsonObject.getInt("SPO2");

                                    // Cập nhật giá trị BPM và SpO2 lên TextView
                                    runOnUiThread(() -> {
                                        txtBPM_020510.setText(String.valueOf(bpm_020510));
                                        txtSPO2_020510.setText(String.valueOf(spo2_020510 + "%"));

                                        // Cập nhật trạng thái thiết bị 020510 khi có dữ liệu
                                        imgStatus_020510.setBackgroundResource(R.drawable.point_connect);
                                    });

                                    // Reset thời gian chờ 5 giây mỗi khi nhận được dữ liệu
                                    resetConnectionTimeout_020510();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            if(topic.equals("esp32_Max30102_020511"))
                            {
                                try {
                                    JSONObject jsonObject = new JSONObject(message.toString());
                                    int bpm_020511 = jsonObject.getInt("BPM");
                                    int spo2_020511 = jsonObject.getInt("SPO2");

                                    // Cập nhật giá trị BPM và SpO2 lên TextView
                                    runOnUiThread(() -> {
                                        txtBPM_020511.setText(String.valueOf(bpm_020511));
                                        txtSPO2_020511.setText(String.valueOf(spo2_020511 + "%"));

                                        // Cập nhật trạng thái thiết bị 020510 khi có dữ liệu
                                        imgStatus_020511.setBackgroundResource(R.drawable.point_connect);
                                    });

                                    // Reset thời gian chờ 5 giây mỗi khi nhận được dữ liệu
                                    resetConnectionTimeout_020511();
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

    // kết nối mqtt//


    // kiểm tra kết nối thiết bị 020510
    private void resetConnectionTimeout_020510() {
        if (handler_mqtt != null && checkConnectionRunnable_020510 != null) {
            handler_mqtt.removeCallbacks(checkConnectionRunnable_020510);
        }

        // Tạo một Runnable mới để kiểm tra kết nối sau 7 giây
        checkConnectionRunnable_020510 = () -> {
            //set img point về disconnect
            imgStatus_020510.setBackgroundResource(R.drawable.point_disconnect);
            // Đặt lại giá trị của txtBPM_020510 và txtSPO2_020510 về "NaN"
            runOnUiThread(() -> {
                txtBPM_020510.setText("NaN");
                txtSPO2_020510.setText("NaN");
            });
        };

        // Đặt lại thời gian chờ 7 giây
        handler_mqtt.postDelayed(checkConnectionRunnable_020510, 7000);
    }
    // kiểm tra kết nối thiết bị 020510 //

    // kiểm tra kết nối thiết bị 020511
    private void resetConnectionTimeout_020511() {
        if (handler_mqtt != null && checkConnectionRunnable_020511 != null) {
            handler_mqtt.removeCallbacks(checkConnectionRunnable_020511);
        }

        // Tạo một Runnable mới để kiểm tra kết nối sau 7 giây
        checkConnectionRunnable_020511 = () -> {
            //set img point về disconnect
            imgStatus_020511.setBackgroundResource(R.drawable.point_disconnect);
            // Đặt lại giá trị của txtBPM_020511 và txtSPO2_020511 về "NaN"
            runOnUiThread(() -> {
                txtBPM_020511.setText("NaN");
                txtSPO2_020511.setText("NaN");
            });
        };

        // Đặt lại thời gian chờ 7 giây
        handler_mqtt.postDelayed(checkConnectionRunnable_020511, 7000);
    }
    // kiểm tra kết nối thiết bị 020511 //


    // cập nhật mã và tên bệnh nhân
    private void MAandTenBN() {
        Intent intent = getIntent();

        String maNguoiBenh_1 = intent.getStringExtra("maNguoiBenh_1");
        String tenNguoiBenh_1 = intent.getStringExtra("tenNguoiBenh_1");

        String maNguoiBenh_2 = intent.getStringExtra("maNguoiBenh_2");
        String tenNguoiBenh_2 = intent.getStringExtra("tenNguoiBenh_2");

        // Hiển thị dữ liệu lên TextView
        txtMaNguoiBenh_1.setText(maNguoiBenh_1);
        txtTenNguoiBenh_1.setText(tenNguoiBenh_1);

        txtMaNguoiBenh_2.setText(maNguoiBenh_2);
        txtTenNguoiBenh_2.setText(tenNguoiBenh_2);

        // Tạo hoặc truy cập SharedPreferences
        sharedPreferences = getSharedPreferences("PatientData", MODE_PRIVATE);

        // Gọi hàm để tải dữ liệu từ SharedPreferences
        loadPatientData();

        if (maNguoiBenh_1 != null && tenNguoiBenh_1 != null) {
            // Cập nhật TextView với dữ liệu mới
            txtMaNguoiBenh_1.setText(maNguoiBenh_1);
            txtTenNguoiBenh_1.setText(tenNguoiBenh_1);

            // Gọi hàm để lưu dữ liệu vào SharedPreferences
            savePatientData_1(maNguoiBenh_1, tenNguoiBenh_1);
        }
        if (maNguoiBenh_2 != null && tenNguoiBenh_2 != null) {
            // Cập nhật TextView với dữ liệu mới
            txtMaNguoiBenh_2.setText(maNguoiBenh_2);
            txtTenNguoiBenh_2.setText(tenNguoiBenh_2);

            // Gọi hàm để lưu dữ liệu vào SharedPreferences
            savePatientData_2(maNguoiBenh_2, tenNguoiBenh_2);
        }
    }
    // cập nhật mã và tên bệnh nhân //

    // Hàm lưu dữ liệu mã và tên bệnh nhân 1 vào SharedPreferences
    private void savePatientData_1(String maNguoiBenh, String tenNguoiBenh) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("maNguoiBenh_1", maNguoiBenh);
        editor.putString("tenNguoiBenh_1", tenNguoiBenh);
        editor.apply();
    }

    // Hàm lưu dữ liệu mã và tên bệnh nhân 2 vào SharedPreferences
    private void savePatientData_2(String maNguoiBenh, String tenNguoiBenh) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("maNguoiBenh_2", maNguoiBenh);
        editor.putString("tenNguoiBenh_2", tenNguoiBenh);
        editor.apply();
    }

    // Hàm lấy dữ liệu từ SharedPreferences và hiển thị lên TextView
    private void loadPatientData() {
        String maNguoiBenh_1 = sharedPreferences.getString("maNguoiBenh_1", "");
        String tenNguoiBenh_1 = sharedPreferences.getString("tenNguoiBenh_1", "");
        txtMaNguoiBenh_1.setText(maNguoiBenh_1);
        txtTenNguoiBenh_1.setText(tenNguoiBenh_1);

        String maNguoiBenh_2 = sharedPreferences.getString("maNguoiBenh_2", "");
        String tenNguoiBenh_2 = sharedPreferences.getString("tenNguoiBenh_2", "");
        txtMaNguoiBenh_2.setText(maNguoiBenh_2);
        txtTenNguoiBenh_2.setText(tenNguoiBenh_2);
    }
}