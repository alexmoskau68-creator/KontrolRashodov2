package ru.controlexpenses;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

public class DB extends SQLiteOpenHelper {
    public DB(Context c) { super(c, "control_expenses.db", null, 2); }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,merchant TEXT,category TEXT,currency TEXT,project TEXT DEFAULT '',note TEXT,amount REAL)");
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN project TEXT DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)");
        }
    }

    private ContentValues values(Expense e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date); v.put("merchant", e.merchant); v.put("category", e.category);
        v.put("currency", e.currency); v.put("project", e.project); v.put("note", e.note); v.put("amount", e.amount);
        return v;
    }

    public void add(Expense e) { getWritableDatabase().insert("expenses", null, values(e)); }
    public void update(Expense e) { getWritableDatabase().update("expenses", values(e), "id=?", new String[]{String.valueOf(e.id)}); }
    public void delete(long id) { getWritableDatabase().delete("expenses", "id=?", new String[]{String.valueOf(id)}); }

    public ArrayList<Expense> all() {
        ArrayList<Expense> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,date,merchant,category,currency,project,note,amount FROM expenses ORDER BY id DESC", null);
        while (c.moveToNext()) list.add(new Expense(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getDouble(7)));
        c.close();
        return list;
    }

    public void addProject(String name) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("name", name.trim());
        getWritableDatabase().insertWithOnConflict("projects", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void deleteProject(String name) { getWritableDatabase().delete("projects", "name=?", new String[]{name}); }

    public ArrayList<String> projects() {
        ArrayList<String> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT name FROM projects ORDER BY name", null);
        while (c.moveToNext()) list.add(c.getString(0)); c.close(); return list;
    }

    public ArrayList<String[]> totalsByCurrency() {
        ArrayList<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT currency, SUM(amount) FROM expenses GROUP BY currency ORDER BY currency", null);
        while (c.moveToNext()) list.add(new String[]{c.getString(0), String.valueOf(c.getDouble(1))});
        c.close();
        return list;
    }
}
