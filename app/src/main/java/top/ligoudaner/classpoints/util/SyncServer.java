package top.ligoudaner.classpoints.util;

import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import top.ligoudaner.classpoints.db.AppDatabase;
import top.ligoudaner.classpoints.model.Student;

public class SyncServer extends NanoHTTPD {
    private final AppDatabase db;
    private final Gson gson = new Gson();
    private OnDataChangeListener listener;

    public interface OnDataChangeListener {
        void onDataChanged();
    }

    public void setOnDataChangeListener(OnDataChangeListener listener) {
        this.listener = listener;
    }

    public SyncServer(int port, AppDatabase db) {
        super(port);
        this.db = db;
    }

    @Override
    public Response serve(IHTTPSession session) {
        // 处理 OPTIONS 请求解决跨域预检
        if (session.getMethod() == Method.OPTIONS) {
            Response response = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.addHeader("Access-Control-Allow-Headers", "Content-Type");
            return response;
        }

        String uri = session.getUri();

        // 允许跨域 (CORS)
        Response response;
        if (uri.equals("/api/sync")) {
            List<Student> students = db.studentDao().getAllStudents();
            Map<String, Object> data = new HashMap<>();
            data.put("students", students);
            String json = gson.toJson(data);
            response = newFixedLengthResponse(Response.Status.OK, "application/json", json);
        } else if (uri.equals("/api/rules")) {
            List<String> categories = RuleManager.getCategories();
            Map<String, List<top.ligoudaner.classpoints.model.Rule>> rules = new HashMap<>();
            for (String cat : categories) {
                rules.put(cat, RuleManager.getRulesByCategory(cat));
            }
            String json = gson.toJson(rules);
            response = newFixedLengthResponse(Response.Status.OK, "application/json", json);
        } else if (uri.equals("/api/apply_rule") && (session.getMethod() == Method.POST || session.getMethod() == Method.PUT)) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String postData = files.get("postData");
                if (postData == null) {
                    // Try to get from parameters if not in body (some clients might send as query params)
                    postData = session.getParms().get("postData");
                }

                if (postData == null) {
                    throw new Exception("Missing postData");
                }

                Map<String, Object> params = gson.fromJson(postData, Map.class);

                Object sIdObj = params.get("studentId");
                int studentId = (sIdObj instanceof Number) ? ((Number) sIdObj).intValue() : Integer.parseInt(sIdObj.toString());

                String category = (String) params.get("category");
                String description = (String) params.get("description");

                Object scoreObj = params.get("score");
                double score = (scoreObj instanceof Number) ? ((Number) scoreObj).doubleValue() : Double.parseDouble(scoreObj.toString());

                Student student = db.studentDao().getStudentById(studentId);
                if (student != null) {
                    student.currentWeeklyPoints += score;
                    db.studentDao().update(student);

                    top.ligoudaner.classpoints.model.PointRecord record = new top.ligoudaner.classpoints.model.PointRecord(
                            student.id, category, description, score,
                            System.currentTimeMillis(), DateUtils.getWeekIdentifier()
                    );
                    db.pointRecordDao().insert(record);

                    // 通知 UI 刷新
                    if (listener != null) {
                        listener.onDataChanged();
                    }

                    response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"success\"}");
                } else {
                    response = newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Student not found\"}");
                }
            } catch (Exception e) {
                response = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        } else if (uri.equals("/api/student_details")) {
            String idStr = session.getParms().get("id");
            if (idStr != null) {
                int studentId = Integer.parseInt(idStr);
                String currentWeek = DateUtils.getWeekIdentifier();
                List<top.ligoudaner.classpoints.model.PointRecord> records = db.pointRecordDao().getRecordsByStudentAndWeek(studentId, currentWeek);
                String json = gson.toJson(records);
                response = newFixedLengthResponse(Response.Status.OK, "application/json", json);
            } else {
                response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing id");
            }
        } else {
            response = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
        }

        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");

        return response;
    }
}
