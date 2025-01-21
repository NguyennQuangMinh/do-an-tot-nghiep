// da hien thi duoc len man hinh oled
#if CONFIG_FREERTOS_UNICORE
#define ARDUINO_RUNNING_CORE 0
#else
#define ARDUINO_RUNNING_CORE 1
#endif
#define ESP_DRD_USE_SPIFFS true


#include <WiFi.h>
#include <FS.h>
#include <SPIFFS.h>
#include <WiFiManager.h>
#include <ArduinoJson.h>

#include "ssd1306h.h"
#include "MAX30102.h"
#include "Pulse.h"
#include <PubSubClient.h>

const char* mqttServer = "mqtt.eclipseprojects.io";//"broker.hivemq.com";
const int mqttPort = 1883;
const char *topic_publish = "esp32_Max30102_020510";

const char *ssid_sta = "102_020510";
const char *pass_sta = "1234567890";

const char *id_thietbi = "TEN THIET BI: 020510";
const char *id_staWifi = "ssid wifi: 102_020510";

// WiFi and MQTT clients
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

#define JSON_CONFIG_FILE "/test_config.json"
#define ESP_DRD_USE_SPIFFS true

bool shouldSaveConfig = false;

WiFiManager wm;

char ssid[32];
char pass[64];

// Routines to clear and set bits 
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif

SSD1306 oled; 
MAX30102 sensor;
Pulse pulseIR;
Pulse pulseRed;
MAFilter bpm;

TaskHandle_t TaskHandle_WiFi;
TaskHandle_t TaskHandle_MQTT;


/////////////////////////////////////////////////////////////////////////
#define AVERAGE_INTERVAL 10000 // Thời gian tính trung bình: 15 giây
unsigned long lastAverageTime = 0;

int sumBPM = 0;       // Tổng BPM
int sumSPO2 = 0;      // Tổng SPO2
int countSamples = 0; // Đếm số lượng mẫu
int averagedBPM = 0;  // BPM trung bình
int averagedSPO2 = 0; // SPO2 trung bình
//////////////////////////////////////////////////////////////////////////


#define OPTIONS 7
// Vẽ hình trái tim
static const uint8_t heart_bits[] PROGMEM = { 0x00, 0x00, 0x38, 0x38, 0x7c, 0x7c, 0xfe, 0xfe, 0xfe, 0xff, 
                                        0xfe, 0xff, 0xfc, 0x7f, 0xf8, 0x3f, 0xf0, 0x1f, 0xe0, 0x0f,
                                        0xc0, 0x07, 0x80, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 
                                        0x00, 0x00 };

//Bảng spo2 có giá trị gần đúng  -45.060*ratioAverage* ratioAverage + 30.354 *ratioAverage + 94.845 ;
const uint8_t spo2_table[184] PROGMEM =
        { 95, 95, 95, 96, 96, 96, 97, 97, 97, 97, 97, 98, 98, 98, 98, 98, 99, 99, 99, 99, 
          99, 99, 99, 99, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 
          100, 100, 100, 100, 99, 99, 99, 99, 99, 99, 99, 99, 98, 98, 98, 98, 98, 98, 97, 97, 
          97, 97, 96, 96, 96, 96, 95, 95, 95, 94, 94, 94, 93, 93, 93, 92, 92, 92, 91, 91, 
          90, 90, 89, 89, 89, 88, 88, 87, 87, 86, 86, 85, 85, 84, 84, 83, 82, 82, 81, 81, 
          80, 80, 79, 78, 78, 77, 76, 76, 75, 74, 74, 73, 72, 72, 71, 70, 69, 69, 68, 67, 
          66, 66, 65, 64, 63, 62, 62, 61, 60, 59, 58, 57, 56, 56, 55, 54, 53, 52, 51, 50, 
          49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 31, 30, 29, 
          28, 27, 26, 25, 23, 22, 21, 20, 19, 17, 16, 15, 14, 12, 11, 10, 9, 7, 6, 5, 
          3, 2, 1 } ;


void print_digit(int x, int y, long val, char c=' ', uint8_t field = 3,const int BIG = 2)
    {  
    uint8_t ff = field;
    do { 
        char ch = (val!=0) ? val%10+'0': c;
        oled.drawChar( x+BIG*(ff-1)*6, y, ch, BIG);
        val = val/10; 
        --ff;
    } while (ff>0);
}

/*
 *   Ghi, chia tỉ lệ và vẽ tần số xung nhịp PPG
 */
const uint8_t MAXWAVE = 72;

class Waveform {
  public:
    Waveform(void) {wavep = 0;}

      void record(int waveval) {
        waveval = waveval/8;        
        waveval += 128;              
        waveval = waveval<0? 0 : waveval;
        waveform[wavep] = (uint8_t) (waveval>255)?255:waveval; 
        wavep = (wavep+1) % MAXWAVE;
      }
  
      void scale() {
        uint8_t maxw = 0;
        uint8_t minw = 255;
        for (int i=0; i<MAXWAVE; i++) { 
          maxw = waveform[i]>maxw?waveform[i]:maxw;
          minw = waveform[i]<minw?waveform[i]:minw;
        }
        uint8_t scale8 = (maxw-minw)/4 + 1;  
        uint8_t index = wavep;
        for (int i=0; i<MAXWAVE; i++) {
          disp_wave[i] = 31-((uint16_t)(waveform[index]-minw)*8)/scale8;
          index = (index + 1) % MAXWAVE;
        }
      }

void draw(uint8_t X) {
  for (int i=0; i<MAXWAVE; i++) {
    uint8_t y = disp_wave[i];
    oled.drawPixel(X+i, y);
    if (i<MAXWAVE-1) {
      uint8_t nexty = disp_wave[i+1];
      if (nexty>y) {
        for (uint8_t iy = y+1; iy<nexty; ++iy)  
        oled.drawPixel(X+i, iy);
        } 
        else if (nexty<y) {
          for (uint8_t iy = nexty+1; iy<y; ++iy)  
          oled.drawPixel(X+i, iy);
          }
       }
    } 
}

private:
    uint8_t waveform[MAXWAVE];
    uint8_t disp_wave[MAXWAVE];
    uint8_t wavep = 0;
    
} wave;

int  beatAvg;
int  SPO2, SPO2f;

bool filter_for_graph = false;
bool draw_Red = false;

uint8_t istate = 0;
uint8_t sleep_counter = 0;

void draw_oled(int msg) {
    oled.firstPage();
    do{
    switch(msg){
        case 0:  oled.drawStr(10,0,F("Device error"),1); 
                 break;
        case 1:  oled.drawStr(6,10,F("FINGER"),2); 
                 oled.drawXBMP(84,0,16,16,heart_bits);
                 oled.drawXBMP(108,0,16,16,heart_bits);
                 oled.drawStr(84,14,F("Display"),1); 
                 oled.drawStr(84,24,F("BPM  O2"),1); 
                 break;
       case 2:   
                //  print_digit(86,0,beatAvg);
                print_digit(86, 5, averagedBPM, ' ', 3, 2); // Kích thước chữ lớn hơn
                wave.draw(8);
                //print_digit(98,16,SPO2f,' ',3,1);
                //oled.drawChar(116,16,'%');
                print_digit(98,24,averagedSPO2,' ',3,1);
                oled.drawChar(116,24,'%',1,1);
                break;
        case 3:  oled.drawStr(2,6,F(id_thietbi),1);
                 oled.drawStr(0,18,F(id_staWifi),1);
                 break;
       }
    } while (oled.nextPage());
}

void setup(void) {
  Serial.begin(115200);  // Khởi động Serial với tốc độ 115200 bps
  oled.init();
  oled.fill(0x00);
  draw_oled(3);
  delay(3000); 
  if (!sensor.begin())  {
    draw_oled(0);
    while (1);
  }
  sensor.setup(); 

  xTaskCreate(
    Task_initWiFi,
    "WiFi Task",   // Name for humans
    8192,  // Stack size in bytes
    NULL,
    1,  // Priority
    &TaskHandle_WiFi
  );

  xTaskCreate(
    Task_connectMqtt,
    "mqtt task",   // Name for humans
    2048,  // Stack size in bytes
    NULL,
    1,  // Priority
    &TaskHandle_MQTT
  );

}

long lastBeat = 0;    
long displaytime = 0; 

unsigned long lastMqttPublishTime = 0;
const unsigned long mqttPublishInterval = 1000; // 1000 ms = 1 second

unsigned long previousMillis_WiFi = 0;

void loop() {
    // Thời gian hiện tại
    long now = millis();  
    // Đo và hiển thị dữ liệu cảm biến
    sensor.check();
    if (!sensor.available()) return;

    uint32_t irValue = sensor.getIR(); 
    uint32_t redValue = sensor.getRed();
    sensor.nextSample();

    // Kiểm tra nếu không phát hiện tay
    if (irValue < 5000) {
        draw_oled(sleep_counter <= 50 ? 1 : 3); 
        delay(200);
        ++sleep_counter;
        if (sleep_counter > 100) {
            sleep_counter = 0;
        }

        // Nếu không phát hiện tay, trả giá trị BPM và SPO2 về 0 và gửi nếu có kết nối WiFi và MQTT
        if (mqttClient.connected() && WiFi.status() == WL_CONNECTED && now - previousMillis_WiFi >= 5000) {
            String payload = String("{\"BPM\":0,\"SPO2\":0}");
            mqttClient.publish(topic_publish, payload.c_str());
            Serial.println("Data sent: " + payload);
            previousMillis_WiFi = now;
        }

        return;  // Kết thúc hàm loop, không thực hiện phần còn lại
    } else {
        sleep_counter = 0;
        int16_t IR_signal, Red_signal;
        bool beatRed, beatIR;

        if (!filter_for_graph) {
            IR_signal = pulseIR.dc_filter(irValue);
            Red_signal = pulseRed.dc_filter(redValue);
            beatRed = pulseRed.isBeat(pulseRed.ma_filter(Red_signal));
            beatIR = pulseIR.isBeat(pulseIR.ma_filter(IR_signal));        
        } else {
            IR_signal = pulseIR.ma_filter(pulseIR.dc_filter(irValue));
            Red_signal = pulseRed.ma_filter(pulseRed.dc_filter(redValue));
            beatRed = pulseRed.isBeat(Red_signal);
            beatIR = pulseIR.isBeat(IR_signal);
        }

        wave.record(draw_Red ? -Red_signal : -IR_signal); 

        if (draw_Red ? beatRed : beatIR) {
            long btpm = 60000 / (now - lastBeat);
            if (btpm > 0 && btpm < 200) beatAvg = bpm.filter((int16_t)btpm);
            lastBeat = now; 

            // Tính tỉ lệ SPO2
            long numerator = (pulseRed.avgAC() * pulseIR.avgDC()) / 256;
            long denominator = (pulseRed.avgDC() * pulseIR.avgAC()) / 256;
            int RX100 = (denominator > 0) ? (numerator * 100) / denominator : 999;

            // Công thức
            SPO2f = (10400 - RX100 * 17 + 50) / 100;  
            
            // from table
            if ((RX100 >= 0) && (RX100 < 184)) {
                SPO2 = pgm_read_byte_near(&spo2_table[RX100]);
            }

            // Lưu dữ liệu BPM và SPO2 để tính trung bình
            sumBPM += beatAvg;
            sumSPO2 += SPO2;
            countSamples++;

            Serial.print("BPM: ");
            Serial.println(btpm);
            Serial.print("SPO2: ");
            Serial.println(SPO2);
        }

        // Cập nhật giá trị trung bình mỗi AVERAGE_INTERVAL giây
        if (now - lastAverageTime >= AVERAGE_INTERVAL) {
            if (countSamples > 0) {
              averagedBPM = sumBPM / countSamples;
              averagedSPO2 = sumSPO2 / countSamples;
              if( averagedSPO2 > 10 && averagedSPO2 < 85){
                averagedSPO2 = 90;
              }

              // Hiển thị giá trị trung bình
              Serial.print("Averaged BPM: ");
              Serial.println(averagedBPM);
              Serial.print("Averaged SPO2: ");
              Serial.println(averagedSPO2);
              // Reset bộ đếm
              sumBPM = 0;
              sumSPO2 = 0;
              countSamples = 0;
              lastAverageTime = now;
            }

        }

        if (now - displaytime > 50) {
            displaytime = now;
            wave.scale();
            draw_oled(2);
        }

        // Gửi dữ liệu lên MQTT nếu có kết nối và mỗi 5000 ms
        if (mqttClient.connected() && WiFi.status() == WL_CONNECTED && now - previousMillis_WiFi >= 5000) {
            String payload = String("{\"BPM\":") + String(averagedBPM) + String(",\"SPO2\":") + String(averagedSPO2) + String("}");
            mqttClient.publish(topic_publish , payload.c_str());
            Serial.println("Data sent: " + payload);
            previousMillis_WiFi = now;
        }
    }
}

// task connect mqtt
void Task_connectMqtt(void *pvParameters)
{
  mqttClient.setServer(mqttServer, mqttPort);
  for(;;)
  {
    if (!mqttClient.connected()) {
      reconnect_mqtt();
    }
    mqttClient.loop();
    vTaskDelay(1000/portTICK_PERIOD_MS);
  }
}
// reconnect mqtt
void reconnect_mqtt() {
  Serial.println("Connecting to MQTT Broker...");
  while (!mqttClient.connected()) {
    Serial.println("Attempting MQTT connection...");
    String clientID = "ESP32Client-";
    clientID += String(random(0xffff), HEX);
    if (mqttClient.connect(clientID.c_str())) {
      Serial.println("Connected to MQTT Broker");
    } else {
      Serial.print("Failed, rc=");
      Serial.print(mqttClient.state());
      Serial.println(" try again in 2 seconds");
      vTaskDelay(2000/portTICK_PERIOD_MS);
    }
  }
}


// ket noi wifi
void saveConfigFile() {
  Serial.println(F("Saving configuration..."));
  StaticJsonDocument<512> json;
  
  File configFile = SPIFFS.open(JSON_CONFIG_FILE, "w");
  if (!configFile) {
    Serial.println("Failed to open config file for writing");
  }

  serializeJsonPretty(json, Serial);
  if (serializeJson(json, configFile) == 0) {
    Serial.println(F("Failed to write to file"));
  }
  configFile.close();
}

bool loadConfigFile() {
  Serial.println("Mounting File System...");

  if (SPIFFS.begin(false) || SPIFFS.begin(true)) {
    Serial.println("Mounted file system");
    if (SPIFFS.exists(JSON_CONFIG_FILE)) {
      Serial.println("Reading config file");
      File configFile = SPIFFS.open(JSON_CONFIG_FILE, "r");
      if (configFile) {
        Serial.println("Opened configuration file");
        StaticJsonDocument<512> json;
        DeserializationError error = deserializeJson(json, configFile);
        serializeJsonPretty(json, Serial);
        if (!error) {
          Serial.println("Parsing JSON");
          const char* savedSsid = json["ssid"];
          const char* savedPass = json["pass"];
          if (savedSsid && savedPass) {
            strncpy(ssid, savedSsid, sizeof(ssid));
            strncpy(pass, savedPass, sizeof(pass));
          }
          return true;
        } else {
          Serial.println("Failed to load JSON config");
        }
      }
    }
  } else {
    Serial.println("Failed to mount FS");
  }

  return false;
}

void saveConfigCallback() {
  Serial.println("Should save config");
  shouldSaveConfig = true;
}

void configModeCallback(WiFiManager *myWiFiManager) {
  Serial.println("Entered Configuration Mode");
  Serial.print("Config SSID: ");
  Serial.println(myWiFiManager->getConfigPortalSSID());
  Serial.print("Config IP Address: ");
  Serial.println(WiFi.softAPIP());
}

// Task inti WiFi
void Task_initWiFi(void *pvParameters){
  bool forceConfig = false;
  bool spiffsSetup = loadConfigFile();
  if (!spiffsSetup) {
    Serial.println(F("Forcing config mode as there is no saved config"));
    forceConfig = true;
  }
  // sau khi vao che do STA thi se dung task mqtt
  vTaskSuspend(TaskHandle_MQTT);

  WiFi.mode(WIFI_STA);

  // Uncomment to clear saved WiFi settings
  //wm.resetSettings();

  wm.setSaveConfigCallback(saveConfigCallback);
  wm.setAPCallback(configModeCallback);

  if (forceConfig) {
    if (!wm.startConfigPortal(ssid_sta, pass_sta)) {
      Serial.println("Failed to connect and hit timeout");
    }
  } else {
    if (!wm.autoConnect(ssid_sta, pass_sta)) {
      Serial.println("Failed to connect and hit timeout");
    }
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  strncpy(ssid, wm.getWiFiSSID().c_str(), sizeof(ssid));
  strncpy(pass, wm.getWiFiPass().c_str(), sizeof(pass));
  Serial.print("SSID: ");
  Serial.println(ssid);
  Serial.print("Password: ");
  Serial.println(pass);

  if (shouldSaveConfig) {
    saveConfigFile();
  }

  bool cnt = true;
  // // ket noi WiFi thanh cong se mo lai task mqtt
  if(WiFi.status() == WL_CONNECTED)
  {
    vTaskResume(TaskHandle_MQTT);
  }

  for(;;)
  {
    if(WiFi.status() != WL_CONNECTED && cnt == true)
    {
      reconnectWiFi();
    }
    vTaskDelay(1000 / portTICK_PERIOD_MS);
  }
}

// reconnect WiFi
void reconnectWiFi(){
  Serial.println("WiFi lost connection. Attempting to reconnect...");
  WiFi.begin(ssid, pass);
  vTaskDelay(3000 / portTICK_PERIOD_MS);
  while (WiFi.status() != WL_CONNECTED) {
    vTaskDelay(1000 / portTICK_PERIOD_MS);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("Reconnected to WiFi");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
}