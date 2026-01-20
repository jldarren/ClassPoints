package top.ligoudaner.classpoints.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "point_records")
public class PointRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int studentId;
    public String category; // 纪律, 学习, 安全, 劳动, 文明礼仪, 两操
    public String subCategory; // 具体考核细则内容
    public double points;
    public long timestamp;
    public String weekIdentifier; // e.g., "2024-W15" to help with weekly tracking

    public PointRecord(int studentId, String category, String subCategory, double points, long timestamp, String weekIdentifier) {
        this.studentId = studentId;
        this.category = category;
        this.subCategory = subCategory;
        this.points = points;
        this.timestamp = timestamp;
        this.weekIdentifier = weekIdentifier;
    }
}
