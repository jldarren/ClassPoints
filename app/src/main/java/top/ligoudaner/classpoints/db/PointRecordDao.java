package top.ligoudaner.classpoints.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import top.ligoudaner.classpoints.model.PointRecord;

@Dao
public interface PointRecordDao {
    @Insert
    void insert(PointRecord record);

    @Query("SELECT * FROM point_records WHERE studentId = :studentId ORDER BY timestamp DESC")
    List<PointRecord> getRecordsByStudent(int studentId);

    @Query("SELECT * FROM point_records WHERE studentId = :studentId AND weekIdentifier = :weekId")
    List<PointRecord> getRecordsByStudentAndWeek(int studentId, String weekId);

    @Query("SELECT SUM(points) FROM point_records WHERE studentId = :studentId AND category = :category")
    Double getTotalPointsByCategory(int studentId, String category);

    @Query("SELECT SUM(points) FROM point_records WHERE studentId = :studentId AND category = :category AND weekIdentifier = :weekId")
    Double getWeeklyPointsByCategory(int studentId, String category, String weekId);
}
