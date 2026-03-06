package com.glocalvision.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
    private static final String DEFAULT_CHANNEL_NAME = "Alpha Signals / BTC Room";

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

    private TextView channelNameView;
    private TextView channelMetaView;
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

        channelNameView = findViewById(R.id.channelNameView);
        channelMetaView = findViewById(R.id.channelMetaView);
        loginStatusView = findViewById(R.id.loginStatusView);
        llmStatusView = findViewById(R.id.llmStatusView);
        outputView = findViewById(R.id.outputView);
        channelPreviewView = findViewById(R.id.channelPreviewView);
    }

    private void bindActions() {
        Button saveLoginButton = findViewById(R.id.saveLoginButton);
        Button saveLlmButton = findViewById(R.id.saveLlmButton);
        Button testLlmButton = findViewById(R.id.testLlmButton);
        Button filterButton = findViewById(R.id.filterButton);
        Button aiExtractButton = findViewById(R.id.aiExtractButton);

        saveLoginButton.setOnClickListener(v -> saveLoginProfile());
        saveLlmButton.setOnClickListener(v -> saveLlmConfig());
        testLlmButton.setOnClickListener(v -> testLlmSource());
        filterButton.setOnClickListener(v -> filterMessages());
        aiExtractButton.setOnClickListener(v -> runAiExtraction());
    }

    private void loadPersistedSettings() {
        channelNameView.setText(DEFAULT_CHANNEL_NAME);

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
            requestInput.setText("提取当前频道过去2年的交易信号，按交易对整理进出场与利润，并指出缺失证据");
        }

        if (TextUtils.isEmpty(messagesInput.getText().toString().trim())) {
            messagesInput.setText(
                    "2025-01-12 09:20 KOL_A BTCUSDT buy at 41000\n" +
                            "2025-01-19 16:30 KOL_A BTCUSDT take profit at 43500\n" +
                            "2025-03-01 10:00 KOL_A ETHUSDT 建仓 2500\n" +
                            "2025-03-06 20:10 KOL_A ETHUSDT 止损 2320\n" +
                            "2025-05-10 12:00 KOL_A SOLUSDT 入场 145\n" +
                            "2025-05-22 18:15 KOL_A SOLUSDT 出场 178\n" +
                            "2025-05-29 19:00 some noise message about macro sentiment\n" +
                            "2025-06-02 08:00 BTCUSDT 继续看多 but wait for pullback"
            );
        }
    }

    private void runInitialRender() {
        updateChannelMeta();
        renderChannelPreview(messagesInput.getText().toString(), "");
        outputView.setText(buildInitialOutput());
    }

    private String buildInitialOutput() {
        String localDraft = SignalAnalyzer.analyze(
                messagesInput.getText().toString(),
                requestInput.getText().toString()
        );

        return "Tap the AI button next to the channel name.\n\n"
                + "Hybrid path:\n"
                + "1. local evidence prefilter\n"
                + "2. optional LLM extraction\n"
                + "3. fallback to rule-based report if no LLM is configured\n\n"
                + localDraft;
    }

    private void updateChannelMeta() {
        channelMetaView.setText(SignalAnalyzer.buildChannelMeta(messagesInput.getText().toString()));
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

        loginStatusView.setText("Login profile saved (TDLib phase pending): " + phone);
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

        llmStatusView.setText("Testing LLM source...");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(buildModelsUrl(baseUrl));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");
                if (!TextUtils.isEmpty(apiKey)) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }

                int code = conn.getResponseCode();
                String firstLine = trimForUi(readBody(conn, code >= 200 && code < 400));

                runOnUiThread(() -> llmStatusView.setText(
                        "LLM test HTTP " + code + " | " + firstLine
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

    private void runAiExtraction() {
        String request = requestInput.getText().toString().trim();
        String messages = messagesInput.getText().toString().trim();
        String channelName = channelNameView.getText().toString().trim();

        if (TextUtils.isEmpty(messages)) {
            outputView.setText("Current channel is empty. Paste or sync some messages first.");
            Toast.makeText(this, "当前频道没有消息", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(request)) {
            request = "提取当前频道的交易信号并按交易对整理进出场与利润";
        }

        updateChannelMeta();

        String localDraft = SignalAnalyzer.analyze(messages, request);
        String evidencePack = SignalAnalyzer.buildEvidencePack(messages, 220);

        outputView.setText("Running AI extraction for " + channelName + " ...");

        String baseUrl = llmBaseUrlInput.getText().toString().trim();
        String model = llmModelInput.getText().toString().trim();

        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(model)) {
            outputView.setText(localDraft + "\n\nLLM not configured. AI button is using local extraction only.");
            Toast.makeText(this, "未配置 LLM，已降级为本地抽取", Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = llmApiKeyInput.getText().toString().trim();
        String prompt = SignalAnalyzer.buildAiExtractionPrompt(channelName, request, evidencePack, localDraft);

        new Thread(() -> {
            try {
                String aiResult = callChatCompletions(baseUrl, apiKey, model, prompt);
                String finalRequest = request;
                runOnUiThread(() -> {
                    outputView.setText(aiResult + "\n\n---\nLocal draft reference:\n" + localDraft);
                    Toast.makeText(this, "AI extraction complete", Toast.LENGTH_SHORT).show();
                    requestInput.setText(finalRequest);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    outputView.setText(localDraft + "\n\nLLM call failed: " + e.getMessage());
                    Toast.makeText(this, "LLM 调用失败，已显示本地结果", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void filterMessages() {
        renderChannelPreview(
                messagesInput.getText().toString(),
                filterKeywordInput.getText().toString().trim()
        );
    }

    private void renderChannelPreview(String messages, String keyword) {
        updateChannelMeta();

        String[] lines = messages.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase();
        int matched = 0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
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

    private String callChatCompletions(String baseUrl, String apiKey, String model, String prompt) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(buildChatCompletionsUrl(baseUrl));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            if (!TextUtils.isEmpty(apiKey)) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("temperature", 0.2);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "You are glocalVision channel extraction AI. Work only from supplied evidence. Output concise markdown with per-symbol entry and exit pairs, profit analysis, unresolved gaps, and confidence notes."));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            payload.put("messages", messages);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            String response = readBody(conn, code >= 200 && code < 400);

            if (code < 200 || code >= 400) {
                throw new IllegalStateException("HTTP " + code + " | " + trimForUi(response));
            }

            JSONObject root = new JSONObject(response);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IllegalStateException("No choices returned by model");
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IllegalStateException("Missing assistant message");
            }

            String content = message.optString("content", "");
            if (TextUtils.isEmpty(content)) {
                throw new IllegalStateException("Empty assistant content");
            }

            return content.trim();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildModelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/models";
        }
        return normalized + "/v1/models";
    }

    private String buildChatCompletionsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String readBody(HttpURLConnection conn, boolean useInputStream) {
        InputStream stream = null;
        try {
            stream = useInputStream ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                return "";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            reader.close();
            return out.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String trimForUi(String input) {
        if (input == null) {
            return "";
        }
        String compact = input.trim().replace('\n', ' ');
        if (compact.length() <= 140) {
            return compact;
        }
        return compact.substring(0, 140) + "...";
    }
}
