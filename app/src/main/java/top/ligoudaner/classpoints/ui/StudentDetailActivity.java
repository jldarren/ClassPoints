package top.ligoudaner.classpoints.ui;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.ligoudaner.classpoints.databinding.ActivityStudentDetailBinding;
import top.ligoudaner.classpoints.db.AppDatabase;
import top.ligoudaner.classpoints.model.PointRecord;
import top.ligoudaner.classpoints.model.Student;
import top.ligoudaner.classpoints.util.DateUtils;

public class StudentDetailActivity extends AppCompatActivity {

    private ActivityStudentDetailBinding binding;
    private AppDatabase db;
    private int studentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getDatabase(this);
        studentId = getIntent().getIntExtra("student_id", -1);
        Student student = db.studentDao().getStudentById(studentId);

        if (student != null) {
            binding.tvStudentInfo.setText("学号: " + student.id + "  姓名: " + student.name);
            setupPieChart();
            setupLineChart();
        }
    }

    private void setupPieChart() {
        PieChart pieChart = binding.pieChart;
        List<PieEntry> entries = new ArrayList<>();
        String[] categories = {"纪律", "学习", "安全", "劳动", "文明礼仪", "两操"};
        String currentWeek = DateUtils.getWeekIdentifier();

        List<PointRecord> records = db.pointRecordDao().getRecordsByStudentAndWeek(studentId, currentWeek);
        Map<String, Double> categoryPoints = new HashMap<>();
        for (PointRecord r : records) {
            categoryPoints.put(r.category, categoryPoints.getOrDefault(r.category, 0.0) + r.points);
        }

        for (String cat : categories) {
            Double total = categoryPoints.get(cat);
            if (total != null && total != 0) {
                String label = cat + " (" + (total > 0 ? "+" : "") + total + ")";
                entries.add(new PieEntry(Math.abs(total.floatValue()), label));
            }
        }

        if (entries.isEmpty()) {
            pieChart.clear();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "本周积分变动分布");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false); // Labels are on entries
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.invalidate();
    }

    private void setupLineChart() {
        LineChart lineChart = binding.lineChart;
        String currentWeek = DateUtils.getWeekIdentifier();
        List<PointRecord> records = db.pointRecordDao().getRecordsByStudentAndWeek(studentId, currentWeek);

        String[] days = {"一", "二", "三", "四", "五"};
        Map<String, Double> dailyChanges = new HashMap<>();
        for (PointRecord r : records) {
            String day = DateUtils.getDayOfWeekName(r.timestamp);
            dailyChanges.put(day, dailyChanges.getOrDefault(day, 0.0) + r.points);
        }

        List<Entry> entries = new ArrayList<>();
        double runningTotal = 100; // Starting base

        int todayIndex = DateUtils.getTodayDayIndex();

        // Show only up to today (max Friday)
        int maxLoop = Math.min(todayIndex, 4);

        for (int i = 0; i <= maxLoop; i++) {
            runningTotal += dailyChanges.getOrDefault(days[i], 0.0);
            entries.add(new Entry(i, (float) runningTotal));
        }

        if (entries.isEmpty()) {
            lineChart.clear();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "本周积分趋势");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(50);
        dataSet.setFillColor(Color.BLUE);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(days));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.invalidate();
    }
}
