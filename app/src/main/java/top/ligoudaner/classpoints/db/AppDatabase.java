package top.ligoudaner.classpoints.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import top.ligoudaner.classpoints.model.PointRecord;
import top.ligoudaner.classpoints.model.Student;

@Database(entities = {Student.class, PointRecord.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract StudentDao studentDao();
    public abstract PointRecordDao pointRecordDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "class_points_db")
                            .allowMainThreadQueries() // Simplification for this project
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
