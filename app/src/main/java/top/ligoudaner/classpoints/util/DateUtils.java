package top.ligoudaner.classpoints.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static String getWeekIdentifier() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        int weekYear = calendar.get(Calendar.YEAR);
        int weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR);
        return weekYear + "-W" + weekOfYear;
    }

    public static String getDayOfWeekName(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.MONDAY: return "一";
            case Calendar.TUESDAY: return "二";
            case Calendar.WEDNESDAY: return "三";
            case Calendar.THURSDAY: return "四";
            case Calendar.FRIDAY: return "五";
            case Calendar.SATURDAY: return "六";
            case Calendar.SUNDAY: return "日";
            default: return "";
        }
    }

    public static boolean isNewWeek(long lastResetTimestamp) {
        if (lastResetTimestamp == 0) return true;
        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(lastResetTimestamp);

        Calendar now = Calendar.getInstance();

        // Return true if current week is different from last reset week
        return last.get(Calendar.WEEK_OF_YEAR) != now.get(Calendar.WEEK_OF_YEAR)
               || last.get(Calendar.YEAR) != now.get(Calendar.YEAR);
    }

    public static int getTodayDayIndex() {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        switch (day) {
            case Calendar.MONDAY: return 0;
            case Calendar.TUESDAY: return 1;
            case Calendar.WEDNESDAY: return 2;
            case Calendar.THURSDAY: return 3;
            case Calendar.FRIDAY: return 4;
            case Calendar.SATURDAY:
            case Calendar.SUNDAY: return 5; // Weekend or later
            default: return -1;
        }
    }
}
