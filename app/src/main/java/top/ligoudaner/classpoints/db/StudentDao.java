package top.ligoudaner.classpoints.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import top.ligoudaner.classpoints.model.Student;

@Dao
public interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id ASC")
    List<Student> getAllStudents();

    @Query("SELECT * FROM students ORDER BY currentWeeklyPoints DESC")
    List<Student> getStudentsRankedByWeekly();

    @Query("SELECT * FROM students ORDER BY (totalPoints + currentWeeklyPoints) DESC")
    List<Student> getStudentsRankedByCumulative();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Student> students);

    @Update
    void update(Student student);

    @Query("DELETE FROM students")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM students")
    int getStudentCount();

    @Query("SELECT * FROM students WHERE id = :id")
    Student getStudentById(int id);
}
