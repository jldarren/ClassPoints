package top.ligoudaner.classpoints;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
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
import top.ligoudaner.classpoints.util.SyncServer;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppDatabase db;
    private StudentAdapter adapter;
    private SharedPreferences prefs;
    private int sortMode = 0; // 0: By ID, 1: By Weekly, 2: By Cumulative
    private SyncServer syncServer;

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

        // 默认开启同步服务器
        startSyncServer();
    }

    private void startSyncServer() {
        try {
            if (syncServer == null) {
                syncServer = new SyncServer(8080, db);
                // 注册回调，当 Web 端有数据变更时通知 UI 刷新
                syncServer.setOnDataChangeListener(() -> {
                    runOnUiThread(this::refreshList);
                });
                syncServer.start();
                String ip = getLocalIpAddress();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("本机服务器IP: " + ip);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "启动同步服务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                case 0: toastText = "按学号排序"; break;
                case 1: toastText = "按本周总分排序"; break;
                case 2: toastText = "按累积总分排序"; break;
            }
            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
        });
        binding.btnExport.setOnClickListener(v -> exportCSV());
        binding.btnSync.setText("同步");
        binding.btnSync.setOnClickListener(v -> {
            refreshList();
            Toast.makeText(this, "列表已刷新并同步", Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleSyncServer() {
        // 此方法已弃用，功能已移入 startSyncServer
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
            case 0: binding.btnRank.setText("按本周总分排序"); break;
            case 1: binding.btnRank.setText("按累积总分排序"); break;
            case 2: binding.btnRank.setText("按学号排序"); break;
        }
    }

    private void refreshList() {
        switch (sortMode) {
            case 0: adapter.setStudents(db.studentDao().getAllStudents()); break;
            case 1: adapter.setStudents(db.studentDao().getStudentsRankedByWeekly()); break;
            case 2: adapter.setStudents(db.studentDao().getStudentsRankedByCumulative()); break;
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
        }
        return super.onOptionsItemSelected(item);
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
        if (syncServer != null) {
            syncServer.stop();
        }
    }
}

