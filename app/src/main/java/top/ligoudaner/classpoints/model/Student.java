package top.ligoudaner.classpoints.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "students")
public class Student {
    @PrimaryKey
    public int id; // Student ID (学号)
    public String name;
    public int weeklyBasePoints = 100;
    public double currentWeeklyPoints = 100;
    public double totalPoints = 0;

    public Student(int id, String name) {
        this.id = id;
        this.name = name;
        this.currentWeeklyPoints = 100;
        this.totalPoints = 0;
    }
}
