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
import android.widget.ScrollView;
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
    private Button clipboardBtn, inputBtn, clearBtn;
    private ProgressBar progressBar;
    private ScrollView mainScroll;
    private LinearLayout resultsLayout;
    private TextView zhResult, enResult, frResult;

    private final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private final String MODEL = "glm-4.7-flash";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText    = findViewById(R.id.input_text);
        clipboardBtn = findViewById(R.id.clipboard_btn);
        inputBtn     = findViewById(R.id.input_btn);
        clearBtn     = findViewById(R.id.clear_btn);
        progressBar  = findViewById(R.id.progress_bar);
        mainScroll   = findViewById(R.id.main_scroll);
        resultsLayout = findViewById(R.id.results_layout);
        zhResult     = findViewById(R.id.zh_result);
        enResult     = findViewById(R.id.en_result);
        frResult     = findViewById(R.id.fr_result);

        clipboardBtn.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence seq = cb.getPrimaryClip().getItemAt(0).getText();
                if (seq != null && seq.toString().trim().length() > 0) {
                    String text = seq.toString().trim();
                    inputText.setText(text);
                    startTranslation(text);
                } else {
                    Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "剪贴板没有内容", Toast.LENGTH_SHORT).show();
            }
        });

        inputBtn.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "请先输入要翻译的内容", Toast.LENGTH_SHORT).show();
            } else {
                startTranslation(text);
            }
        });

        clearBtn.setOnClickListener(v -> {
            inputText.setText("");
            zhResult.setText("");
            enResult.setText("");
            frResult.setText("");
            resultsLayout.setVisibility(View.GONE);
            // 滚回顶部
            mainScroll.post(() -> mainScroll.smoothScrollTo(0, 0));
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
        });

        zhResult.setOnClickListener(v -> copyText(zhResult.getText().toString(), "中文"));
        enResult.setOnClickListener(v -> copyText(enResult.getText().toString(), "English"));
        frResult.setOnClickListener(v -> copyText(frResult.getText().toString(), "Français"));
    }

    private void startTranslation(String text) {
        final String apiKey = getPreferences(MODE_PRIVATE).getString("api_key", "");
        if (apiKey.trim().isEmpty()) {
            showApiKeyDialog(text);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        resultsLayout.setVisibility(View.GONE);
        setButtonsEnabled(false);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String prompt = "将以下文本完整翻译成中文、英文和法文，不得省略任何内容。" +
                        "严格只返回JSON，格式：{\"zh\":\"中文完整译文\",\"en\":\"English full translation\",\"fr\":\"Traduction française complète\"}\n原文：" + text;

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL);
                requestBody.put("max_tokens", 8192);
                requestBody.put("thinking", new JSONObject().put("type", "disabled"));
                requestBody.put("response_format", new JSONObject().put("type", "json_object"));
                JSONArray messages = new JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", prompt);
                messages.put(msg);
                requestBody.put("messages", messages);

                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int status = conn.getResponseCode();
                java.io.InputStream stream = status >= 200 && status < 300
                        ? conn.getInputStream() : conn.getErrorStream();
                if (stream == null) throw new java.io.IOException("HTTP " + status);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (status < 200 || status >= 300) {
                    String detail = "HTTP " + status;
                    try {
                        JSONObject error = new JSONObject(sb.toString()).optJSONObject("error");
                        if (error != null) detail += "：" + error.optString("message", "接口请求失败");
                    } catch (Exception ignored) {}
                    throw new java.io.IOException(detail);
                }
                JSONObject response = new JSONObject(sb.toString());
                if ("length".equals(response.getJSONArray("choices").getJSONObject(0)
                        .optString("finish_reason"))) {
                    throw new java.io.IOException("译文超过输出上限，请分段翻译");
                }
                String content = response.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .getString("content").trim()
                        .replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();

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
                    setButtonsEnabled(true);
                    // 翻译完成后自动滚动到结果区域
                    mainScroll.post(() -> mainScroll.smoothScrollTo(0, resultsLayout.getTop()));
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("翻译失败")
                            .setMessage(e.getMessage())
                            .setPositiveButton("确定", null)
                            .setNeutralButton("更换密钥", (dialog, which) -> showApiKeyDialog(null))
                            .show();
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void showApiKeyDialog(String pendingText) {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint("智谱 API Key");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("设置智谱 API 密钥")
                .setMessage("请输入你在智谱开放平台生成的密钥，仅保存在本机。")
                .setView(keyInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (key.isEmpty()) {
                        Toast.makeText(this, "密钥不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    getPreferences(MODE_PRIVATE).edit().putString("api_key", key).apply();
                    if (pendingText != null) startTranslation(pendingText);
                }).show();
    }

    private void setButtonsEnabled(boolean enabled) {
        clipboardBtn.setEnabled(enabled);
        inputBtn.setEnabled(enabled);
        clearBtn.setEnabled(enabled);
    }

    private void copyText(String text, String lang) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("translation", text));
        Toast.makeText(this, lang + " 已复制！", Toast.LENGTH_SHORT).show();
    }
}
