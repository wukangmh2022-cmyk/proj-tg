package com.glocalvision.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "glocalvision_settings";
    private static final String KEY_API_ID = "api_id";
    private static final String KEY_API_HASH = "api_hash";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_LOGIN_CODE = "login_code";
    private static final String KEY_LOGIN_SAVED = "login_saved";
    private static final String KEY_LLM_BASE_URL = "llm_base_url";
    private static final String KEY_LLM_API_KEY = "llm_api_key";
    private static final String KEY_LLM_MODEL = "llm_model";

    private EditText apiIdInput;
    private EditText apiHashInput;
    private EditText phoneInput;
    private EditText codeInput;
    private EditText llmBaseUrlInput;
    private EditText llmApiKeyInput;
    private EditText llmModelInput;
    private EditText requestInput;
    private EditText messagesInput;
    private EditText filterKeywordInput;

    private TextView loginStatusView;
    private TextView llmStatusView;
    private TextView outputView;
    private TextView channelPreviewView;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        bindViews();
        bindActions();
        loadPersistedSettings();
        initializeSampleDataIfNeeded();
        runInitialRender();
    }

    private void bindViews() {
        apiIdInput = findViewById(R.id.apiIdInput);
        apiHashInput = findViewById(R.id.apiHashInput);
        phoneInput = findViewById(R.id.phoneInput);
        codeInput = findViewById(R.id.codeInput);
        llmBaseUrlInput = findViewById(R.id.llmBaseUrlInput);
        llmApiKeyInput = findViewById(R.id.llmApiKeyInput);
        llmModelInput = findViewById(R.id.llmModelInput);
        requestInput = findViewById(R.id.requestInput);
        messagesInput = findViewById(R.id.messagesInput);
        filterKeywordInput = findViewById(R.id.filterKeywordInput);

        loginStatusView = findViewById(R.id.loginStatusView);
        llmStatusView = findViewById(R.id.llmStatusView);
        outputView = findViewById(R.id.outputView);
        channelPreviewView = findViewById(R.id.channelPreviewView);
    }

    private void bindActions() {
        Button saveLoginButton = findViewById(R.id.saveLoginButton);
        Button saveLlmButton = findViewById(R.id.saveLlmButton);
        Button testLlmButton = findViewById(R.id.testLlmButton);
        Button analyzeButton = findViewById(R.id.analyzeButton);
        Button filterButton = findViewById(R.id.filterButton);

        saveLoginButton.setOnClickListener(v -> saveLoginProfile());
        saveLlmButton.setOnClickListener(v -> saveLlmConfig());
        testLlmButton.setOnClickListener(v -> testLlmSource());
        analyzeButton.setOnClickListener(v -> runAnalysis());
        filterButton.setOnClickListener(v -> filterMessages());
    }

    private void loadPersistedSettings() {
        apiIdInput.setText(prefs.getString(KEY_API_ID, ""));
        apiHashInput.setText(prefs.getString(KEY_API_HASH, ""));
        phoneInput.setText(prefs.getString(KEY_PHONE, ""));
        codeInput.setText(prefs.getString(KEY_LOGIN_CODE, ""));

        llmBaseUrlInput.setText(prefs.getString(KEY_LLM_BASE_URL, "https://api.openai.com"));
        llmApiKeyInput.setText(prefs.getString(KEY_LLM_API_KEY, ""));
        llmModelInput.setText(prefs.getString(KEY_LLM_MODEL, "gpt-4.1-mini"));

        if (prefs.getBoolean(KEY_LOGIN_SAVED, false)) {
            loginStatusView.setText("Login profile saved: " + prefs.getString(KEY_PHONE, ""));
        } else {
            loginStatusView.setText("Not configured");
        }

        if (!TextUtils.isEmpty(prefs.getString(KEY_LLM_BASE_URL, ""))) {
            llmStatusView.setText("LLM source configured");
        } else {
            llmStatusView.setText("LLM not configured");
        }
    }

    private void initializeSampleDataIfNeeded() {
        if (TextUtils.isEmpty(requestInput.getText().toString().trim())) {
            requestInput.setText("监控XXX土狗交流群，搜集2年内入场出场信号，统计累计收益和回撤");
        }

        if (TextUtils.isEmpty(messagesInput.getText().toString().trim())) {
            messagesInput.setText(
                    "2025-01-12 09:20 KOL_A BTCUSDT buy at 41000\n" +
                            "2025-01-19 16:30 KOL_A BTCUSDT take profit at 43500\n" +
                            "2025-03-01 10:00 KOL_A ETHUSDT 建仓 2500\n" +
                            "2025-03-06 20:10 KOL_A ETHUSDT 止损 2320\n" +
                            "2025-05-10 12:00 KOL_A SOLUSDT 入场 145\n" +
                            "2025-05-22 18:15 KOL_A SOLUSDT 出场 178"
            );
        }
    }

    private void runInitialRender() {
        runAnalysis();
        renderChannelPreview(messagesInput.getText().toString(), "");
    }

    private void saveLoginProfile() {
        String apiId = apiIdInput.getText().toString().trim();
        String apiHash = apiHashInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (TextUtils.isEmpty(apiId) || TextUtils.isEmpty(apiHash) || TextUtils.isEmpty(phone)) {
            loginStatusView.setText("Login profile incomplete: api_id/api_hash/phone required");
            Toast.makeText(this, "请完整填写 API ID / API Hash / Phone", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString(KEY_API_ID, apiId)
                .putString(KEY_API_HASH, apiHash)
                .putString(KEY_PHONE, phone)
                .putString(KEY_LOGIN_CODE, code)
                .putBoolean(KEY_LOGIN_SAVED, true)
                .apply();

        loginStatusView.setText("Login profile saved (MVP local profile): " + phone);
        Toast.makeText(this, "登录配置已保存", Toast.LENGTH_SHORT).show();
    }

    private void saveLlmConfig() {
        String baseUrl = llmBaseUrlInput.getText().toString().trim();
        String apiKey = llmApiKeyInput.getText().toString().trim();
        String model = llmModelInput.getText().toString().trim();

        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(model)) {
            llmStatusView.setText("LLM config invalid: base_url and model required");
            Toast.makeText(this, "请填写 LLM Base URL 和 Model", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString(KEY_LLM_BASE_URL, baseUrl)
                .putString(KEY_LLM_API_KEY, apiKey)
                .putString(KEY_LLM_MODEL, model)
                .apply();

        llmStatusView.setText("LLM config saved: " + baseUrl + " | model=" + model);
        Toast.makeText(this, "LLM 源配置已保存", Toast.LENGTH_SHORT).show();
    }

    private void testLlmSource() {
        String baseUrl = llmBaseUrlInput.getText().toString().trim();
        String apiKey = llmApiKeyInput.getText().toString().trim();

        if (TextUtils.isEmpty(baseUrl)) {
            llmStatusView.setText("LLM test failed: base_url required");
            return;
        }

        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        llmStatusView.setText("Testing LLM source...");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(normalizedBaseUrl + "/v1/models");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");
                if (!TextUtils.isEmpty(apiKey)) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }

                int code = conn.getResponseCode();
                String firstLine = readFirstLine(conn, code >= 200 && code < 400);

                runOnUiThread(() -> llmStatusView.setText(
                        "LLM test HTTP " + code + " | " + trimForUi(firstLine)
                ));
            } catch (Exception e) {
                runOnUiThread(() -> llmStatusView.setText("LLM test failed: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private String readFirstLine(HttpURLConnection conn, boolean useInputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                useInputStream ? conn.getInputStream() : conn.getErrorStream()
        ))) {
            return reader.readLine();
        } catch (Exception e) {
            return "no response body";
        }
    }

    private String trimForUi(String input) {
        if (input == null) {
            return "";
        }
        String compact = input.trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 120) + "...";
    }

    private void runAnalysis() {
        String request = requestInput.getText().toString();
        String messages = messagesInput.getText().toString();
        String report = SignalAnalyzer.analyze(messages, request);

        if (TextUtils.isEmpty(report.trim())) {
            outputView.setText("No analysis result. Check input messages and request.");
            Toast.makeText(this, "未生成分析结果", Toast.LENGTH_SHORT).show();
            return;
        }

        outputView.setText(report);
        Toast.makeText(this, "分析完成", Toast.LENGTH_SHORT).show();
    }

    private void filterMessages() {
        String keyword = filterKeywordInput.getText().toString().trim();
        renderChannelPreview(messagesInput.getText().toString(), keyword);
    }

    private void renderChannelPreview(String messages, String keyword) {
        String[] lines = messages.split("\\r?\\n");
        StringBuilder out = new StringBuilder();

        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase();
        int matched = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            if (!TextUtils.isEmpty(lowerKeyword) && !line.toLowerCase().contains(lowerKeyword)) {
                continue;
            }

            matched++;
            out.append(matched)
                    .append(". ")
                    .append(line)
                    .append("\n");
        }

        if (matched == 0) {
            if (TextUtils.isEmpty(lowerKeyword)) {
                channelPreviewView.setText("No messages available");
            } else {
                channelPreviewView.setText("No messages matched keyword: " + keyword);
            }
            return;
        }

        channelPreviewView.setText(out.toString());
    }
}
