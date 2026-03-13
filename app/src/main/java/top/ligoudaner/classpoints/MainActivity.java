package top.ligoudaner.classpoints;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.ligoudaner.classpoints.databinding.ActivityMainBinding;
import top.ligoudaner.classpoints.db.AppDatabase;
import top.ligoudaner.classpoints.model.PointRecord;
import top.ligoudaner.classpoints.model.Rule;
import top.ligoudaner.classpoints.model.Student;
import top.ligoudaner.classpoints.ui.StudentAdapter;
import top.ligoudaner.classpoints.ui.StudentDetailActivity;
import top.ligoudaner.classpoints.util.CSVExporter;
import top.ligoudaner.classpoints.util.DateUtils;
import top.ligoudaner.classpoints.util.RuleManager;
import top.ligoudaner.classpoints.util.SyncService;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private ActivityMainBinding binding;
    private AppDatabase db;
    private StudentAdapter adapter;
    private SharedPreferences prefs;
    private int sortMode = 0; // 0: By ID, 1: By Weekly, 2: By Cumulative

    private Model model;
    private SpeechService speechService;
    private Student voiceTargetStudent;

    private AlertDialog voiceInputDialog;
    private android.widget.TextView tvRealtimeText;

    private final BroadcastReceiver dataChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SyncService.ACTION_DATA_CHANGED.equals(intent.getAction())) {
                refreshList();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        db = AppDatabase.getDatabase(this);
        prefs = getSharedPreferences("cp_prefs", MODE_PRIVATE);
        sortMode = prefs.getInt("sort_mode", 0);

        checkWeeklyReset();
        setupRecyclerView();
        setupButtons();
        refreshList();

        // 检查通知权限 (Android 13+)
        checkNotificationPermission();

        // 启动后台同步服务
        startSyncService();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                dataChangeReceiver, new IntentFilter(SyncService.ACTION_DATA_CHANGED));

        initVosk();
    }

    private void initVosk() {
        LibVosk.setLogLevel(LogLevel.INFO);
        // 使用后台线程手动解压 assets，避免 UI 卡顿并绕过 uuid 校验
        new Thread(() -> {
            try {
                File modelDir = new File(getFilesDir(), "vosk-model-cn");
                // 如果关键文件不存在，则说明还没解压过，或者解压不完整
                if (!new File(modelDir, "conf/model.conf").exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "首次运行，正在准备语音模型(约30秒)...", Toast.LENGTH_LONG).show());
                    copyAssets("model-cn", modelDir);
                }

                // 直接从解压后的路径加载模型
                this.model = new Model(modelDir.getAbsolutePath());
                runOnUiThread(() -> Toast.makeText(this, "离线语音识别就绪", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "模型加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * 递归拷贝 assets 目录下的模型文件到 App 私有目录
     */
    private void copyAssets(String path, File outDir) throws IOException {
        String[] assets = getAssets().list(path);
        if (assets == null || assets.length == 0) {
            // 是文件，执行拷贝
            copyFile(path, outDir);
        } else {
            // 是目录，递归创建并拷贝
            if (!outDir.exists()) outDir.mkdirs();
            for (String asset : assets) {
                copyAssets(path + "/" + asset, new File(outDir, asset));
            }
        }
    }

    private void copyFile(String assetPath, File outFile) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024 * 8];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private void startSyncService() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        startForegroundService(serviceIntent);

        // 更新标题栏显示 IP
        String ip = getLocalIpAddress();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("班级积分通 (本机: " + android.os.Build.MODEL + ")");
            getSupportActionBar().setSubtitle("本机服务器IP: " + ip);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void checkWeeklyReset() {
        long lastReset = prefs.getLong("last_reset", 0);
        if (DateUtils.isNewWeek(lastReset)) {
            List<Student> students = db.studentDao().getAllStudents();
            for (Student s : students) {
                s.totalPoints += s.currentWeeklyPoints;
                s.currentWeeklyPoints = 100;
                db.studentDao().update(s);
            }
            prefs.edit().putLong("last_reset", System.currentTimeMillis()).apply();
            Toast.makeText(this, "新的一周，积分已重置", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceInput(Student student) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 102);
            return;
        }

        if (model == null) {
            Toast.makeText(this, "语音模型尚未就绪，请稍后", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechService != null) {
            stopVoiceService();
            return;
        }

        voiceTargetStudent = student;
        showVoiceInputDialog(student);
        try {
            // ！！重点优化：构建一个极其细碎且覆盖面广的“短语词库”！！
            java.util.Set<String> dynamicGrammar = new java.util.HashSet<>();
            List<String> categories = RuleManager.getCategories();

            for (String cat : categories) {
                for (Rule rule : RuleManager.getRulesByCategory(cat)) {
                    // 只保留中文字符和特殊的 A, +, - 进行识别匹配
                    String cleanDesc = rule.description.replaceAll("[^\\u4e00-\\u9fa5A\\+\\-]", "");
                    if (cleanDesc.length() < 1) continue;

                    // 1. 加入完整描述 (如 "作业优秀", "写字练习A+")
                    dynamicGrammar.add(cleanDesc);

                    // 2. 自动切词：注入所有 2 字和 3 字的子片段，极大提高片段识别率
                    // 比如 "上课不认真" 会被拆出 "上课"、"不认真"、"认真"
                    for (int i = 0; i < cleanDesc.length() - 1; i++) {
                        dynamicGrammar.add(cleanDesc.substring(i, i + 2)); // 2字词
                        if (i < cleanDesc.length() - 2) {
                            dynamicGrammar.add(cleanDesc.substring(i, i + 3)); // 3字词
                        }
                    }
                }
            }

            // 3. 补充常用的口语化指令词
            String[] helpers = {"迟到", "旷课", "打架", "没做", "没交", "发言", "垃圾", "吵架", "顶撞"};
            for (String h : helpers) dynamicGrammar.add(h);

            // 构建符合 Vosk 格式的 JSON 词表
            StringBuilder grammarJson = new StringBuilder("[");
            boolean first = true;
            for (String phrase : dynamicGrammar) {
                if (!first) grammarJson.append(",");
                grammarJson.append("\"").append(phrase).append("\"");
                first = false;
            }
            grammarJson.append("]"); // 移除 [unk]，强迫它在这些词里找最像的

            Recognizer recognizer = new Recognizer(model, 16000.0f, grammarJson.toString());
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);

            android.util.Log.d("VoskSpeech", "词法表已注入 " + dynamicGrammar.size() + " 个候选短词");
        } catch (IOException e) {
            if (voiceInputDialog != null) voiceInputDialog.dismiss();
            Toast.makeText(this, "识别器启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showVoiceInputDialog(Student student) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("正在为 " + student.name + " 评分...");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(android.graphics.Color.parseColor("#FF6200EE"));
        tvTitle.setPadding(0, 0, 0, 30);
        layout.addView(tvTitle);

        tvRealtimeText = new android.widget.TextView(this);
        tvRealtimeText.setText("倾听中...请说出加分项");
        tvRealtimeText.setHint("例如：作业优秀");
        tvRealtimeText.setTextSize(20);
        tvRealtimeText.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRealtimeText.setMinLines(2);
        layout.addView(tvRealtimeText);

        voiceInputDialog = builder.setView(layout)
                .setCancelable(false)
                .setNegativeButton("取消", (dialog, which) -> stopVoiceService())
                .create();

        voiceInputDialog.show();
    }

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.optString("text", "");
            android.util.Log.d("VoskSpeech", "Result: " + hypothesis);
            if (!text.isEmpty()) {
                runOnUiThread(() -> {
                    if (tvRealtimeText != null) tvRealtimeText.setText(text);
                });
                // 稍微延迟一点关闭，让用户看一眼结果
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (voiceInputDialog != null) voiceInputDialog.dismiss();
                    processVoiceInput(text);
                }, 500);
            } else {
                stopVoiceService();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            stopVoiceService();
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        // onResult 已经处理了逻辑
    }

    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String partial = json.optString("partial", "");
            android.util.Log.d("VoskSpeech", "Partial: " + hypothesis);
            runOnUiThread(() -> {
                if (tvRealtimeText != null) {
                    if (!partial.isEmpty()) {
                        tvRealtimeText.setText(partial);
                    } else {
                        tvRealtimeText.setText("正在倾听...");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(Exception exception) {
        runOnUiThread(() -> {
            Toast.makeText(this, "识别错误: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            stopVoiceService();
        });
    }

    @Override
    public void onTimeout() {
        runOnUiThread(this::stopVoiceService);
    }

    private void stopVoiceService() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
        if (voiceInputDialog != null && voiceInputDialog.isShowing()) {
            voiceInputDialog.dismiss();
        }
    }

    private void processVoiceInput(String text) {
        if (voiceTargetStudent == null) return;

        // 清洗识别结果 (Vosk 中文结果常带空格)
        String cleanText = text.replace(" ", "").trim();
        android.util.Log.d("VoskSpeech", "识别到文字: [" + cleanText + "]");

        if (cleanText.isEmpty() || cleanText.equals("[unk]")) {
            Toast.makeText(this, "未听清，请尝试简洁说话", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> categories = RuleManager.getCategories();
        Rule bestMatch = null;
        int maxSimilarity = 0;

        for (String cat : categories) {
            for (Rule rule : RuleManager.getRulesByCategory(cat)) {
                // 修改此处：支持识别 A, +, - 字符的匹配
                String cleanRuleDesc = rule.description.replaceAll("[^\\u4e00-\\u9fa5A\\+\\-]", "");

                // 核心算法：检查识别出的“短词”是否在“规则描述”中，或者反之
                if (cleanText.contains(cleanRuleDesc) || cleanRuleDesc.contains(cleanText)) {
                    // 取描述长度作为权重，防止“优秀”误杀“作业优秀”
                    if (cleanRuleDesc.length() > maxSimilarity) {
                        bestMatch = rule;
                        maxSimilarity = cleanRuleDesc.length();
                    }
                }
            }
        }

        if (bestMatch != null) {
            applyRule(voiceTargetStudent, bestMatch);
        } else {
            Toast.makeText(this, "识别为 '" + cleanText + "'，但未找到匹配细则", Toast.LENGTH_LONG).show();
        }
        voiceTargetStudent = null;
    }

    private void setupRecyclerView() {
        adapter = new StudentAdapter();
        adapter.setOnStudentClickListener(new StudentAdapter.OnStudentClickListener() {
            @Override
            public void onAddPointClick(Student student) {
                showCategoryDialog(student);
            }

            @Override
            public void onStudentLongClick(Student student) {
                Intent intent = new Intent(MainActivity.this, StudentDetailActivity.class);
                intent.putExtra("student_id", student.id);
                startActivity(intent);
            }

            @Override
            public void onStudentDoubleClick(Student student) {
                startVoiceInput(student);
            }
        });
        binding.rvStudents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStudents.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnSetup.setOnClickListener(v -> showSetupDialog());
        updateSortButtonText();
        binding.btnRank.setOnClickListener(v -> {
            sortMode = (sortMode + 1) % 3;
            prefs.edit().putInt("sort_mode", sortMode).apply();
            updateSortButtonText();
            refreshList();

            String toastText = "";
            switch (sortMode) {
                case 0:
                    toastText = "按学号排序";
                    break;
                case 1:
                    toastText = "按本周总分排序";
                    break;
                case 2:
                    toastText = "按累积总分排序";
                    break;
            }
            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
        });
        binding.btnExport.setOnClickListener(v -> exportCSV());
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "127.0.0.1";
    }

    private void updateSortButtonText() {
        switch (sortMode) {
            case 0:
                binding.btnRank.setText("按本周总分排序");
                break;
            case 1:
                binding.btnRank.setText("按累积总分排序");
                break;
            case 2:
                binding.btnRank.setText("按学号排序");
                break;
        }
    }

    private void refreshList() {
        switch (sortMode) {
            case 0:
                adapter.setStudents(db.studentDao().getAllStudents());
                break;
            case 1:
                adapter.setStudents(db.studentDao().getStudentsRankedByWeekly());
                break;
            case 2:
                adapter.setStudents(db.studentDao().getStudentsRankedByCumulative());
                break;
        }
    }

    private void showSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("初始化班级");
        final EditText input = new EditText(this);
        input.setHint("请输入学生人数");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                int count = Integer.parseInt(text);
                db.studentDao().deleteAll();
                List<Student> students = new ArrayList<>();
                for (int i = 1; i <= count; i++) {
                    students.add(new Student(i, "学生 " + i));
                }
                db.studentDao().insertAll(students);
                refreshList();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showCategoryDialog(Student student) {
        List<String> categories = RuleManager.getCategories();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择积分项分类");
        builder.setItems(categories.toArray(new String[0]), (dialog, which) -> {
            showRuleDialog(student, categories.get(which));
        });
        builder.show();
    }

    private void showRuleDialog(Student student, String category) {
        List<Rule> rules = RuleManager.getRulesByCategory(category);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择具体细则");
        builder.setItems(rules.stream().map(Rule::toString).toArray(String[]::new), (dialog, which) -> {
            Rule selectedRule = rules.get(which);
            applyRule(student, selectedRule);
        });
        builder.show();
    }

    private void applyRule(Student student, Rule rule) {
        student.currentWeeklyPoints += rule.score;
        db.studentDao().update(student);

        PointRecord record = new PointRecord(
                student.id,
                rule.category,
                rule.description,
                rule.score,
                System.currentTimeMillis(),
                DateUtils.getWeekIdentifier()
        );
        db.pointRecordDao().insert(record);

        refreshList();
        Toast.makeText(this, "由于 " + rule.description + "，" + student.name + " " + (rule.score >= 0 ? "+" : "") + rule.score + "分", Toast.LENGTH_SHORT).show();

        // Android 侧数据变化，Web 侧通常通过下一次请求同步，这里 refreshList 已更新 UI
    }

    private void exportCSV() {
        try {
            File exportDir = new File(getExternalFilesDir(null), "exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            File file = new File(exportDir, "ClassPoints_" + DateUtils.getWeekIdentifier() + ".csv");
            CSVExporter.exportToCSV(db, file.getAbsolutePath());
            Toast.makeText(this, "文件导出至: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (item.getItemId() == R.id.action_battery_optimization) {
            showBatteryOptimizationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showBatteryOptimizationDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
                new AlertDialog.Builder(this)
                        .setTitle("设置提示")
                        .setMessage("已关闭系统基础电池优化。但在部分手机（如小米、OV、华为）上，仍需手动在【后台耗电管理】中设置为【允许后台高耗电】或【无限制】，应用才能在后台稳定运行。\n\n是否前往手动配置？")
                        .setPositiveButton("前往设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("省电设置优化")
                        .setMessage("为了确保 App 在后台或锁屏时能够持续提供同步服务和语音识别，建议您关闭对本应用的电池优化。")
                        .setPositiveButton("一键优化", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                                startActivity(intent);
                            } catch (Exception e) {
                                // 某些国产 ROM 可能禁用了该 Intent
                                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        } else {
            Toast.makeText(this, "当前系统版本无需配置电池优化", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于 班级积分通")
                .setMessage("作者: 李狗蛋儿\n" +
                        "版本: 1.0\n\n" +
                        "使用指南:\n" +
                        "1. 初始化：点击'初始化班级'设定学生人数。\n" +
                        "2. 评分：点击学生条目右侧'+'号选择加/扣分项。网页版也可实时评分。\n" +
                        "3. 详情：长按学生条目（或网页版点击详情）查看分数分布与趋势图。\n" +
                        "4. 排序：点击排序按钮切换学号、本周、累积总分排序。双端操作均会自动触发重新排序。\n" +
                        "5. 同步：App 启动后自动开启同步服务，IP 显示在标题栏下方。网页端接入 IP 即可管理。\n" +
                        "6. 实时性：取消轮询，改为数据变动触发同步，双端实时响应并自动刷新排序。")
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataChangeReceiver);
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
        // 通常不在这里停止服务，除非你想退出 App 时也停止同步
        // Stop the service only if we are specifically closing the app
        // For teacher's convenience, let's keep it running unless manually stopped or app explicitly destroyed
    }

    private void showSpeechServiceUnavailableDialog() {
        // 由于使用了 Vosk 离线识别，不再需要此对话框，已通过 initVosk 的回调处理
    }
}
