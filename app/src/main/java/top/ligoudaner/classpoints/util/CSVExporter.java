package top.ligoudaner.classpoints.util;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import top.ligoudaner.classpoints.model.PointRecord;
import top.ligoudaner.classpoints.model.Student;
import top.ligoudaner.classpoints.db.AppDatabase;

public class CSVExporter {
    public static void exportToCSV(AppDatabase db, String filePath) throws IOException {
        List<Student> students = db.studentDao().getAllStudents();
        String currentWeek = DateUtils.getWeekIdentifier();

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            // Row 1: Title
            writer.writeNext(new String[]{"我为人人，人人为我 —— 班级管理考核量化表 (" + currentWeek + ")"});

            // Row 2: Empty
            writer.writeNext(new String[]{""});

            // Row 3: Categories
            String[] header2 = {"姓名", "基础分", "纪律", "", "", "", "", "学习", "", "", "", "", "安全", "", "", "", "", "劳动", "", "", "", "", "文明礼仪", "", "", "", "", "两操", "", "", "", "", "每星期总分", "", "", "", "", "大总分"};
            writer.writeNext(header2);

            // Row 4: Responsibles (could be filled if needed)
            String[] header3 = new String[header2.length];
            header3[0] = "责任人";
            writer.writeNext(header3);

            // Row 5: Days
            String[] header4 = new String[header2.length];
            header4[0] = "日期";
            int idx = 2;
            String[] days = {"一", "二", "三", "四", "五"};
            for (int i = 0; i < 7; i++) { // 6 Categories + 1 weekly total
                for (String day : days) {
                    header4[idx++] = day;
                }
            }
            writer.writeNext(header4);

            // Student Data
            for (Student s : students) {
                List<PointRecord> records = db.pointRecordDao().getRecordsByStudentAndWeek(s.id, currentWeek);

                String[] row = new String[header2.length];
                row[0] = s.name + " (" + s.id + ")";
                row[1] = String.valueOf(s.weeklyBasePoints);

                Map<String, Map<String, Double>> pointsMap = new HashMap<>();
                for (PointRecord r : records) {
                    String day = DateUtils.getDayOfWeekName(r.timestamp);
                    pointsMap.computeIfAbsent(r.category, k -> new HashMap<>())
                             .merge(day, r.points, Double::sum);
                }

                String[] categories = {"纪律", "学习", "安全", "劳动", "文明礼仪", "两操"};
                String[] dayNames = {"一", "二", "三", "四", "五"};

                int currentIdx = 2;
                for (String cat : categories) {
                    Map<String, Double> categoryDayPoints = pointsMap.getOrDefault(cat, new HashMap<>());
                    for (String day : dayNames) {
                        Double p = categoryDayPoints.get(day);
                        row[currentIdx++] = p != null ? String.valueOf(p) : "";
                    }
                }

                // 每星期总分: Cumulative total for each day starting from 100
                double dailyRunningTotal = 100;
                for (String day : dayNames) {
                    double dayChange = 0;
                    for (String cat : categories) {
                        dayChange += pointsMap.getOrDefault(cat, new HashMap<>()).getOrDefault(day, 0.0);
                    }
                    dailyRunningTotal += dayChange;
                    row[currentIdx++] = String.valueOf(dailyRunningTotal);
                }

                // 大总分: Final score of the week
                row[currentIdx] = String.valueOf(dailyRunningTotal);
                writer.writeNext(row);
            }
        }
    }
}
