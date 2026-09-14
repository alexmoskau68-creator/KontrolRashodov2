package ru.controlexpenses;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

public class DB extends SQLiteOpenHelper {
    public DB(Context c) { super(c, "control_expenses.db", null, 1); }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,merchant TEXT,category TEXT,currency TEXT,note TEXT,amount REAL)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void add(Expense e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date); v.put("merchant", e.merchant); v.put("category", e.category);
        v.put("currency", e.currency); v.put("note", e.note); v.put("amount", e.amount);
        getWritableDatabase().insert("expenses", null, v);
    }

    public ArrayList<Expense> all() {
        ArrayList<Expense> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,date,merchant,category,currency,note,amount FROM expenses ORDER BY id DESC", null);
        while (c.moveToNext()) list.add(new Expense(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getDouble(6)));
        c.close();
        return list;
    }

    public ArrayList<String[]> totalsByCurrency() {
        ArrayList<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT currency, SUM(amount) FROM expenses GROUP BY currency ORDER BY currency", null);
        while (c.moveToNext()) list.add(new String[]{c.getString(0), String.valueOf(c.getDouble(1))});
        c.close();
        return list;
    }
}
