package com.heartsync.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.heartsync.app.model.ExerciseRecord;
import com.heartsync.app.model.MoodRecord;
import com.heartsync.app.model.SleepRecord;
import com.heartsync.app.model.User;
import com.heartsync.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地数据持久化：SQLite 数据库帮助类。
 *
 * <p>本类同时承担 DAO 职责，集中封装全部建表、增删改查语句，
 * 让 Activity 层只关心业务而无需书写 SQL。</p>
 *
 * <p>技术点对应：本地数据持久化方案（SQLite + SQLiteOpenHelper）。</p>
 */
public class HeartSyncDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "heartsync.db";
    private static final int DB_VERSION = 1;

    private static final String T_USER = "t_user";
    private static final String T_SLEEP = "t_sleep_record";
    private static final String T_EXERCISE = "t_exercise_record";
    private static final String T_MOOD = "t_mood_record";

    private static HeartSyncDb instance;

    public static synchronized HeartSyncDb getInstance(Context context) {
        if (instance == null) {
            instance = new HeartSyncDb(context.getApplicationContext());
        }
        return instance;
    }

    private HeartSyncDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_USER + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account TEXT NOT NULL UNIQUE," +
                "nickname TEXT," +
                "class_name TEXT," +
                "password_hash TEXT," +
                "avatar_color INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_SLEEP + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL," +
                "sleep_start TEXT," +
                "wake_time TEXT," +
                "duration_minutes INTEGER DEFAULT 0," +
                "deep_minutes INTEGER DEFAULT 0," +
                "quality INTEGER DEFAULT 3," +
                "note TEXT," +
                "create_time INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_EXERCISE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL," +
                "type TEXT," +
                "duration_minutes INTEGER DEFAULT 0," +
                "intensity INTEGER DEFAULT 2," +
                "calories INTEGER DEFAULT 0," +
                "note TEXT," +
                "create_time INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_MOOD + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL," +
                "score INTEGER DEFAULT 3," +
                "tags TEXT," +
                "diary TEXT," +
                "ai_analysis TEXT," +
                "create_time INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 当前为 1.0 版本，暂无升级需求；后续如需增加字段在此处理。
    }

    /* ==================== 用户 ==================== */

    /**
     * 查询用户是否存在。
     */
    public User findUserByAccount(String account) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_USER, null, "account=?", new String[]{account}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return readUser(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    /**
     * 注册新用户，返回是否成功。
     */
    public boolean registerUser(User user) {
        if (findUserByAccount(user.getAccount()) != null) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", user.getAccount());
        values.put("nickname", user.getNickname());
        values.put("class_name", user.getClassName());
        values.put("password_hash", user.getPasswordHash());
        values.put("avatar_color", user.getAvatarColor());
        long id = db.insert(T_USER, null, values);
        if (id > 0) {
            user.setId(id);
            return true;
        }
        return false;
    }

    /**
     * 更新用户昵称与班级。
     */
    public boolean updateUserInfo(User user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("nickname", user.getNickname());
        values.put("class_name", user.getClassName());
        values.put("avatar_color", user.getAvatarColor());
        return db.update(T_USER, values, "id=?", new String[]{String.valueOf(user.getId())}) > 0;
    }

    private User readUser(Cursor c) {
        User u = new User();
        u.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        u.setAccount(c.getString(c.getColumnIndexOrThrow("account")));
        u.setNickname(c.getString(c.getColumnIndexOrThrow("nickname")));
        u.setClassName(c.getString(c.getColumnIndexOrThrow("class_name")));
        u.setPasswordHash(c.getString(c.getColumnIndexOrThrow("password_hash")));
        u.setAvatarColor(c.getInt(c.getColumnIndexOrThrow("avatar_color")));
        return u;
    }

    /* ==================== 睡眠 ==================== */

    /**
     * 插入睡眠记录。
     */
    public long insertSleep(SleepRecord r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date", r.getDate());
        v.put("sleep_start", r.getSleepStart());
        v.put("wake_time", r.getWakeTime());
        v.put("duration_minutes", r.getDurationMinutes());
        v.put("deep_minutes", r.getDeepMinutes());
        v.put("quality", r.getQuality());
        v.put("note", r.getNote());
        v.put("create_time", r.getCreateTime());
        long id = db.insert(T_SLEEP, null, v);
        if (id > 0) {
            r.setId(id);
        }
        return id;
    }

    public int updateSleep(SleepRecord r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date", r.getDate());
        v.put("sleep_start", r.getSleepStart());
        v.put("wake_time", r.getWakeTime());
        v.put("duration_minutes", r.getDurationMinutes());
        v.put("deep_minutes", r.getDeepMinutes());
        v.put("quality", r.getQuality());
        v.put("note", r.getNote());
        return db.update(T_SLEEP, v, "id=?", new String[]{String.valueOf(r.getId())});
    }

    public int deleteSleep(long id) {
        return getWritableDatabase().delete(T_SLEEP, "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * 查询睡眠记录列表，按日期倒序。
     */
    public List<SleepRecord> querySleepList() {
        List<SleepRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_SLEEP, null, null, null, null, null, "date DESC, id DESC");
        try {
            while (cursor.moveToNext()) {
                list.add(readSleep(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public SleepRecord querySleepById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_SLEEP, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return readSleep(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    /**
     * 查询指定日期的睡眠记录（一天最多一条主记录）。
     */
    public SleepRecord querySleepByDate(String date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_SLEEP, null, "date=?", new String[]{date}, null, null, "id DESC", "1");
        try {
            if (cursor.moveToFirst()) {
                return readSleep(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private SleepRecord readSleep(Cursor c) {
        SleepRecord r = new SleepRecord();
        r.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        r.setDate(c.getString(c.getColumnIndexOrThrow("date")));
        r.setSleepStart(c.getString(c.getColumnIndexOrThrow("sleep_start")));
        r.setWakeTime(c.getString(c.getColumnIndexOrThrow("wake_time")));
        r.setDurationMinutes(c.getInt(c.getColumnIndexOrThrow("duration_minutes")));
        r.setDeepMinutes(c.getInt(c.getColumnIndexOrThrow("deep_minutes")));
        r.setQuality(c.getInt(c.getColumnIndexOrThrow("quality")));
        r.setNote(c.getString(c.getColumnIndexOrThrow("note")));
        r.setCreateTime(c.getLong(c.getColumnIndexOrThrow("create_time")));
        return r;
    }

    /* ==================== 运动 ==================== */

    public long insertExercise(ExerciseRecord r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date", r.getDate());
        v.put("type", r.getType());
        v.put("duration_minutes", r.getDurationMinutes());
        v.put("intensity", r.getIntensity());
        v.put("calories", r.getCalories());
        v.put("note", r.getNote());
        v.put("create_time", r.getCreateTime());
        long id = db.insert(T_EXERCISE, null, v);
        if (id > 0) {
            r.setId(id);
        }
        return id;
    }

    public int deleteExercise(long id) {
        return getWritableDatabase().delete(T_EXERCISE, "id=?", new String[]{String.valueOf(id)});
    }

    public List<ExerciseRecord> queryExerciseList() {
        List<ExerciseRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_EXERCISE, null, null, null, null, null, "date DESC, id DESC");
        try {
            while (cursor.moveToNext()) {
                list.add(readExercise(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public ExerciseRecord queryExerciseById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_EXERCISE, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return readExercise(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private ExerciseRecord readExercise(Cursor c) {
        ExerciseRecord r = new ExerciseRecord();
        r.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        r.setDate(c.getString(c.getColumnIndexOrThrow("date")));
        r.setType(c.getString(c.getColumnIndexOrThrow("type")));
        r.setDurationMinutes(c.getInt(c.getColumnIndexOrThrow("duration_minutes")));
        r.setIntensity(c.getInt(c.getColumnIndexOrThrow("intensity")));
        r.setCalories(c.getInt(c.getColumnIndexOrThrow("calories")));
        r.setNote(c.getString(c.getColumnIndexOrThrow("note")));
        r.setCreateTime(c.getLong(c.getColumnIndexOrThrow("create_time")));
        return r;
    }

    /* ==================== 情绪 ==================== */

    public long insertMood(MoodRecord r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date", r.getDate());
        v.put("score", r.getScore());
        v.put("tags", r.getTags());
        v.put("diary", r.getDiary());
        v.put("ai_analysis", r.getAiAnalysis());
        v.put("create_time", r.getCreateTime());
        long id = db.insert(T_MOOD, null, v);
        if (id > 0) {
            r.setId(id);
        }
        return id;
    }

    public int updateMood(MoodRecord r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date", r.getDate());
        v.put("score", r.getScore());
        v.put("tags", r.getTags());
        v.put("diary", r.getDiary());
        v.put("ai_analysis", r.getAiAnalysis());
        return db.update(T_MOOD, v, "id=?", new String[]{String.valueOf(r.getId())});
    }

    public int deleteMood(long id) {
        return getWritableDatabase().delete(T_MOOD, "id=?", new String[]{String.valueOf(id)});
    }

    public List<MoodRecord> queryMoodList() {
        List<MoodRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_MOOD, null, null, null, null, null, "date DESC, id DESC");
        try {
            while (cursor.moveToNext()) {
                list.add(readMood(cursor));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public MoodRecord queryMoodById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(T_MOOD, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return readMood(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private MoodRecord readMood(Cursor c) {
        MoodRecord r = new MoodRecord();
        r.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        r.setDate(c.getString(c.getColumnIndexOrThrow("date")));
        r.setScore(c.getInt(c.getColumnIndexOrThrow("score")));
        r.setTags(c.getString(c.getColumnIndexOrThrow("tags")));
        r.setDiary(c.getString(c.getColumnIndexOrThrow("diary")));
        r.setAiAnalysis(c.getString(c.getColumnIndexOrThrow("ai_analysis")));
        r.setCreateTime(c.getLong(c.getColumnIndexOrThrow("create_time")));
        return r;
    }

    /* ==================== 统计 ==================== */

    /**
     * 统计近 n 天的睡眠平均时长（分钟）。无记录返回 0。
     *
     * <p>按记录日期过滤，只统计最近 n 天内新增的记录，
     * 避免早期历史数据拉低近期的平均值。</p>
     */
    public int averageSleepMinutes(int days) {
        SQLiteDatabase db = getReadableDatabase();
        String since = DateUtils.dateDaysAgo(days);
        Cursor cursor = db.rawQuery("SELECT AVG(duration_minutes) FROM " + T_SLEEP
                + " WHERE date >= ?", new String[]{since});
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return (int) Math.round(cursor.getDouble(0));
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    /**
     * 统计近 n 天平均睡眠质量。
     */
    public float averageSleepQuality() {
        SQLiteDatabase db = getReadableDatabase();
        String since = DateUtils.dateDaysAgo(7);
        Cursor cursor = db.rawQuery("SELECT AVG(quality) FROM " + T_SLEEP
                + " WHERE date >= ?", new String[]{since});
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getFloat(0);
            }
        } finally {
            cursor.close();
        }
        return 0f;
    }

    /**
     * 统计近 n 天的运动总时长、总热量与运动天数。
     *
     * @param days 统计窗口天数
     * @return {总时长(分钟), 总热量(千卡), 运动天数}
     */
    public int[] exerciseTotals(int days) {
        SQLiteDatabase db = getReadableDatabase();
        String since = DateUtils.dateDaysAgo(days);
        Cursor cursor = db.rawQuery("SELECT IFNULL(SUM(duration_minutes),0)," +
                " IFNULL(SUM(calories),0), COUNT(DISTINCT date) FROM " + T_EXERCISE
                + " WHERE date >= ?", new String[]{since});
        try {
            if (cursor.moveToFirst()) {
                return new int[]{cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)};
            }
        } finally {
            cursor.close();
        }
        return new int[]{0, 0, 0};
    }

    /**
     * 统计全部运动记录的总时长、总热量与运动天数（累计值，不限时间窗口）。
     *
     * @return {总时长(分钟), 总热量(千卡), 运动天数}
     */
    public int[] exerciseTotalsAll() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT IFNULL(SUM(duration_minutes),0)," +
                " IFNULL(SUM(calories),0), COUNT(DISTINCT date) FROM " + T_EXERCISE, null);
        try {
            if (cursor.moveToFirst()) {
                return new int[]{cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)};
            }
        } finally {
            cursor.close();
        }
        return new int[]{0, 0, 0};
    }

    /**
     * 统计近 n 天的平均心情分值。
     */
    public float averageMoodScore(int days) {
        SQLiteDatabase db = getReadableDatabase();
        String since = DateUtils.dateDaysAgo(days);
        Cursor cursor = db.rawQuery("SELECT AVG(score) FROM " + T_MOOD
                + " WHERE date >= ?", new String[]{since});
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getFloat(0);
            }
        } finally {
            cursor.close();
        }
        return 0f;
    }

    /**
     * 清空所有业务数据（保留用户账号），用于设置页「清除缓存」。
     */
    public void clearAllRecords() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_SLEEP, null, null);
        db.delete(T_EXERCISE, null, null);
        db.delete(T_MOOD, null, null);
    }
}
