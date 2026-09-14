package ru.controlexpenses;

public class Expense {
    public long id;
    public String date;
    public String merchant;
    public String category;
    public String currency;
    public String project;
    public String note;
    public double amount;

    public Expense(long id, String date, String merchant, String category, String currency, String note, double amount) {
        this(id,date,merchant,category,currency,"",note,amount);
    }

    public Expense(long id, String date, String merchant, String category, String currency, String project, String note, double amount) {
        this.id = id;
        this.date = date;
        this.merchant = merchant;
        this.category = category;
        this.currency = currency;
        this.project = project == null ? "" : project;
        this.note = note;
        this.amount = amount;
    }
}
