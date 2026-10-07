package rip.moth.cocoonshell.froglog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Widget and pod figures from GET /stats plus a month of GET /sessions/*: week, month and
 * year hours, the current play streak, and the most played game this month.
 */
public final class FroglogStatsSummary {
    public double weekHours;
    public double monthHours;
    public double yearHours;
    public double totalHours;
    public int monthCompleted;
    public int yearCompleted;
    public int totalGames;
    public int completed;
    /** Percent, or -1 when Froglog did not send one. */
    public int completionRate = -1;
    public int streak;
    public int weekSessions;

    public String topTitle;
    public String topCover;
    public double topHours;
    public long topId;
    public boolean topLive;

    private final String today;
    private final String monthPrefix;
    private final Map<String, Top> tops = new HashMap<String, Top>();

    private static final class Top {
        String title;
        String cover;
        double hours;
        long id;
        boolean live;
    }

    public FroglogStatsSummary(String today) {
        this.today = today;
        this.monthPrefix = today.length() >= 7 ? today.substring(0, 7) : today;
    }

    public static String today() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        return format.format(Calendar.getInstance().getTime());
    }

    /** Accepts both the documented camelCase fields and the older snake_case ones. */
    public void readStats(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        JSONObject month = object(json, "thisMonth", "this_month");
        JSONObject year = object(json, "thisYear", "this_year");
        JSONObject overall = json.optJSONObject("overall");
        if (month != null) {
            monthHours = month.optDouble("hours", 0);
            monthCompleted = month.optInt("completed", 0);
        }
        if (year != null) {
            yearHours = year.optDouble("hours", 0);
            yearCompleted = year.optInt("completed", 0);
        }
        totalHours = number(json, overall, "totalHours", "total_hours");
        totalGames = (int) number(json, overall, "totalGames", "total_games");
        completed = (int) number(json, overall, "completed", "completed");
        double rate = number(json, overall, "completionRate", "completion_rate");
        if (!Double.isNaN(rate)) {
            completionRate = (int) Math.round(rate <= 1.0 && rate > 0 ? rate * 100.0 : rate);
        }
        if (Double.isNaN(totalHours)) {
            totalHours = 0;
        }
        readHeatmap(json.optJSONArray("sessionHeatmap"));
        JSONObject ls = json.optJSONObject("lsStats");
        if (ls != null) {
            readHeatmap(ls.optJSONArray("sessionHeatmap"));
        }
        computeStreak();
    }

    private final Map<String, Double> dayHours = new HashMap<String, Double>();
    private final Map<String, Integer> daySessions = new HashMap<String, Integer>();

    private void readHeatmap(JSONArray days) {
        if (days == null) {
            return;
        }
        for (int i = 0; i < days.length(); i++) {
            JSONObject row = days.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String day = row.optString("day", "");
            if (day.length() < 10) {
                continue;
            }
            day = day.substring(0, 10);
            double hours = row.optDouble("hours", 0);
            int sessions = row.optInt("sessions", 0);
            Double before = dayHours.get(day);
            dayHours.put(day, (before == null ? 0 : before) + (Double.isNaN(hours) ? 0 : hours));
            Integer count = daySessions.get(day);
            daySessions.put(day, (count == null ? 0 : count) + sessions);
        }
    }

    private void computeStreak() {
        Set<String> played = new HashSet<String>();
        for (Map.Entry<String, Double> entry : dayHours.entrySet()) {
            Integer sessions = daySessions.get(entry.getKey());
            if (entry.getValue() > 0 || (sessions != null && sessions > 0)) {
                played.add(entry.getKey());
            }
        }
        weekHours = 0;
        weekSessions = 0;
        for (int back = 0; back < 7; back++) {
            String day = shift(today, -back);
            Double hours = dayHours.get(day);
            Integer sessions = daySessions.get(day);
            weekHours += hours == null ? 0 : hours;
            weekSessions += sessions == null ? 0 : sessions;
        }
        // A streak survives until the end of today, so start from yesterday when today is empty.
        int back = played.contains(today) ? 0 : 1;
        int run = 0;
        while (played.contains(shift(today, -back - run)) && run < 3660) {
            run++;
        }
        streak = run;
    }

    /**
     * Folds one page of /sessions/games or /sessions/live-service into the monthly top game.
     * Returns true when the page ended still inside this month, so the next page may matter.
     */
    public boolean addSessions(String body, boolean live) throws Exception {
        JSONObject json = new JSONObject(body);
        JSONArray rows = json.optJSONArray("sessions");
        if (rows == null) {
            return false;
        }
        boolean stillInMonth = rows.length() > 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String date = row.optString("date", "");
            if (!date.startsWith(monthPrefix)) {
                if (date.compareTo(monthPrefix) < 0) {
                    stillInMonth = false;
                }
                continue;
            }
            double hours = row.optDouble("hours", 0);
            if (Double.isNaN(hours) || hours <= 0) {
                continue;
            }
            long id = row.optLong("game_id", row.optLong("live_service_id", row.optLong("live_service_game_id", 0)));
            String title = row.optString("title", "").trim();
            String key = (live ? "live:" : "game:") + (id > 0 ? String.valueOf(id) : title.toLowerCase(Locale.ROOT));
            Top top = tops.get(key);
            if (top == null) {
                top = new Top();
                top.id = id;
                top.live = live;
                top.title = title;
                tops.put(key, top);
            }
            top.hours += hours;
            String cover = text(row, "cover_image");
            if (cover == null) {
                cover = text(row, "img");
            }
            if (top.cover == null && cover != null) {
                top.cover = cover;
            }
        }
        int page = json.optInt("page", 1);
        int pages = json.optInt("totalPages", 1);
        return stillInMonth && page < pages;
    }

    public void finishTop() {
        Top best = null;
        for (Top top : tops.values()) {
            if (best == null || top.hours > best.hours) {
                best = top;
            }
        }
        if (best == null || best.title == null || best.title.isEmpty()) {
            return;
        }
        topTitle = best.title;
        topCover = best.cover;
        topHours = best.hours;
        topId = best.id;
        topLive = best.live;
    }

    /** Hours per day for the last {@code count} days, oldest first, ending today. */
    public double[] lastDays(int count) {
        double[] out = new double[Math.max(0, count)];
        for (int i = 0; i < out.length; i++) {
            Double hours = dayHours.get(shift(today, i - out.length + 1));
            out[i] = hours == null || hours < 0 ? 0 : hours;
        }
        return out;
    }

    /** Single-letter weekday labels (M T W T F S S) matching {@link #lastDays(int)}. */
    public String[] lastDayLetters(int count) {
        String[] letters = {"S", "M", "T", "W", "T", "F", "S"};
        String[] out = new String[Math.max(0, count)];
        for (int i = 0; i < out.length; i++) {
            out[i] = letters[weekday(shift(today, i - out.length + 1))];
        }
        return out;
    }

    /** 0 for Sunday through 6 for Saturday. */
    static int weekday(String day) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            calendar.setTime(format.parse(day));
            return calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        } catch (Exception e) {
            return 0;
        }
    }

    static String shift(String day, int days) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            calendar.setTime(format.parse(day));
            calendar.add(Calendar.DAY_OF_MONTH, days);
            return format.format(calendar.getTime());
        } catch (Exception e) {
            return day;
        }
    }

    private static JSONObject object(JSONObject json, String a, String b) {
        JSONObject value = json.optJSONObject(a);
        return value != null ? value : json.optJSONObject(b);
    }

    private static double number(JSONObject json, JSONObject overall, String camel, String snake) {
        double value = json.optDouble(camel, Double.NaN);
        if (Double.isNaN(value) && overall != null) {
            value = overall.optDouble(snake, Double.NaN);
        }
        if (Double.isNaN(value)) {
            value = json.optDouble(snake, Double.NaN);
        }
        return value;
    }

    private static String text(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) {
            return null;
        }
        String value = row.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }
}
