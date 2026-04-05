package com.fanyi.app;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText inputText;
    private Button translateBtn;
    private ProgressBar progressBar;
    private LinearLayout resultsLayout;

    private TextView zhResult, enResult, frResult;
    private TextView zhLabel, enLabel, frLabel;

    private final String API_KEY = "34c1a4c87e2f4665b9b5bb0f9e0932cb.do6Zl8FEsus2Ybgp";
    private final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private final String MODEL = "glm-4-flashx-250414";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText   = findViewById(R.id.input_text);
        translateBtn = findViewById(R.id.translate_btn);
        progressBar = findViewById(R.id.progress_bar);
        resultsLayout = findViewById(R.id.results_layout);

        zhResult = findViewById(R.id.zh_result);
        enResult = findViewById(R.id.en_result);
        frResult = findViewById(R.id.fr_result);
        zhLabel  = findViewById(R.id.zh_label);
        enLabel  = findViewById(R.id.en_label);
        frLabel  = findViewById(R.id.fr_label);

        translateBtn.setOnClickListener(v -> {
            // 自动读取剪贴板
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String text = item.getText().toString().trim();
                if (!text.isEmpty()) {
                    inputText.setText(text);
                    startTranslation(text);
                } else {
                    Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "剪贴板没有内容", Toast.LENGTH_SHORT).show();
            }
        });

        // 点击结果直接复制
        zhResult.setOnClickListener(v -> copyText(zhResult.getText().toString(), "中文"));
        enResult.setOnClickListener(v -> copyText(enResult.getText().toString(), "English"));
        frResult.setOnClickListener(v -> copyText(frResult.getText().toString(), "Français"));
    }

    private void startTranslation(String text) {
        progressBar.setVisibility(View.VISIBLE);
        resultsLayout.setVisibility(View.GONE);
        translateBtn.setEnabled(false);

        executor.execute(() -> {
            try {
                String prompt = "请将以下文本翻译成中文、英文和法文三种语言。\n" +
                        "严格按照以下JSON格式返回，不要有任何其他内容：\n" +
                        "{\"zh\":\"中文翻译\",\"en\":\"English translation\",\"fr\":\"Traduction française\"}\n\n" +
                        "原文：" + text;

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL);
                requestBody.put("max_tokens", 1000);

                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                messages.put(message);
                requestBody.put("messages", messages);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                BufferedReader reader;
                if (responseCode == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                }

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                String content = response.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim();

                // 解析JSON结果
                if (content.startsWith("```")) {
                    content = content.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
                }
                JSONObject result = new JSONObject(content);
                String zh = result.getString("zh");
                String en = result.getString("en");
                String fr = result.getString("fr");

                mainHandler.post(() -> {
                    zhResult.setText(zh);
                    enResult.setText(en);
                    frResult.setText(fr);
                    progressBar.setVisibility(View.GONE);
                    resultsLayout.setVisibility(View.VISIBLE);
                    translateBtn.setEnabled(true);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "翻译失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                    translateBtn.setEnabled(true);
                });
            }
        });
    }

    private void copyText(String text, String lang) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("translation", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, lang + " 已复制！", Toast.LENGTH_SHORT).show();
    }
}
