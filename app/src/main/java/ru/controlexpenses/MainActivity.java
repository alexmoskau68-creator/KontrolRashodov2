package ru.controlexpenses;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA=10, REQ_GALLERY=11, REQ_RECEIPT=12;
    private FrameLayout content;
    private DB db;
    private Uri cameraUri;

    public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db=new DB(this);
        content=findViewById(R.id.content);
        findViewById(R.id.add).setOnClickListener(v->showAdd());
        findViewById(R.id.menu).setOnClickListener(v->showRightMenu());
        showExpenses();
    }

    protected void onResume(){super.onResume(); if(db!=null) showExpenses();}

    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(30,45,55));t.setPadding(14,9,14,9);if(bold)t.setTypeface(null,1);return t;
    }

    private String symbol(String c){
        if("RUB".equals(c))return "₽"; if("BYN".equals(c))return "Br"; if("KZT".equals(c))return "₸";
        if("UAH".equals(c))return "₴"; if("EUR".equals(c))return "€"; if("USD".equals(c))return "$"; if("GBP".equals(c))return "£"; return c;
    }

    private String money(double v){return String.format(Locale.getDefault(),"%.2f",v).replace('.',',');}

    private void showExpenses(){
        content.removeAllViews();
        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(14,16,14,20);
        box.addView(text("Расходы",25,true));
        ArrayList<String[]> totals=db.totalsByCurrency();
        if(totals.isEmpty()) box.addView(text("Пока нет расходов",17,false));
        else {
            box.addView(text("Итоги по валютам",14,false));
            for(String[] x:totals) box.addView(text(x[0]+"   "+money(Double.parseDouble(x[1]))+" "+symbol(x[0]),22,true));
        }
        for(Expense e:db.all()){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(10,10,10,10);card.setBackgroundColor(Color.WHITE);
            card.addView(text(e.merchant==null||e.merchant.isEmpty()?"Покупка":e.merchant,18,true));
            card.addView(text(e.date+"  •  "+e.category,14,false));
            card.addView(text(money(e.amount)+" "+symbol(e.currency),21,true));
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit=new Button(this);edit.setText("Изменить");edit.setAllCaps(false);edit.setOnClickListener(v->editExpense(e));
            Button del=new Button(this);del.setText("Удалить");del.setAllCaps(false);del.setOnClickListener(v->confirmDelete(e));
            actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));actions.addView(del,new LinearLayout.LayoutParams(0,-2,1));card.addView(actions);
            box.addView(card); box.addView(text("",4,false));
        }
        scroll.addView(box);content.addView(scroll);
    }

    private void showAdd(){
        new AlertDialog.Builder(this).setTitle("Добавить расход").setItems(new String[]{"📷 Сканировать чек","🖼 Выбрать из галереи","✍ Ввести вручную"},(d,w)->{
            if(w==0) openCamera(); else if(w==1) openGallery(); else manualExpense(null);
        }).show();
    }

    private void openCamera(){
        try{
            File dir=new File(getCacheDir(),"receipts");dir.mkdirs();
            File photo=new File(dir,"receipt_"+System.currentTimeMillis()+".jpg");
            cameraUri=FileProvider.getUriForFile(this,"ru.controlexpenses.fileprovider",photo);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,REQ_CAMERA);
        }catch(Exception e){Toast.makeText(this,"Не удалось открыть камеру: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void openGallery(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_GALLERY);}

    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK)return;
        if(requestCode==REQ_CAMERA && cameraUri!=null) openReceipt(cameraUri);
        if(requestCode==REQ_GALLERY && data!=null && data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){} openReceipt(u);}
        if(requestCode==REQ_RECEIPT) showExpenses();
    }

    private void openReceipt(Uri uri){Intent i=new Intent(this,ReceiptActivity.class);i.putExtra("image_uri",uri.toString());i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,REQ_RECEIPT);}

    private void manualExpense(Expense existing){
        boolean editing=existing!=null;
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,6,16,6);
        EditText date=new EditText(this);date.setHint("Дата покупки");date.setText(editing?existing.date:new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()));
        EditText shop=new EditText(this);shop.setHint("Магазин / получатель");shop.setText(editing?existing.merchant:"");
        EditText amount=new EditText(this);amount.setHint("Фактически уплачено");amount.setInputType(2|8192);amount.setText(editing?money(existing.amount):"");
        Spinner currency=new Spinner(this);String[] cs={"RUB ₽","BYN Br","KZT ₸","UAH ₴","EUR €","USD $","GBP £"};currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cs));
        if(editing){for(int i=0;i<cs.length;i++)if(cs[i].startsWith(existing.currency))currency.setSelection(i);}
        l.addView(date);l.addView(shop);l.addView(amount);l.addView(currency);
        new AlertDialog.Builder(this).setTitle(editing?"Изменить расход":"Новый расход").setView(l).setPositiveButton("Сохранить",(d,w)->{
            double a=0;try{a=Double.parseDouble(amount.getText().toString().replace(" ","").replace(',','.'));}catch(Exception ignored){}
            String cur=String.valueOf(currency.getSelectedItem()).substring(0,3);
            if(editing){existing.date=date.getText().toString();existing.merchant=shop.getText().toString();existing.amount=a;existing.currency=cur;db.update(existing);}else db.add(new Expense(0,date.getText().toString(),shop.getText().toString(),"Другое",cur,"",a));
            showExpenses();
        }).setNegativeButton("Отмена",null).show();
    }

    private void editExpense(Expense e){manualExpense(e);}

    private void confirmDelete(Expense e){
        new AlertDialog.Builder(this).setTitle("Удалить расход?").setMessage("Запись будет удалена из истории расходов.").setPositiveButton("Удалить",(d,w)->{db.delete(e.id);showExpenses();}).setNegativeButton("Отмена",null).show();
    }

    private void showRightMenu(){
        final String[] x={"Расходы","Категории","Проекты","Отчёты","Валюты"};
        PopupMenu p=new PopupMenu(this,findViewById(R.id.menu),Gravity.END);
        for(int i=0;i<x.length;i++)p.getMenu().add(0,i,i,x[i]);
        p.setOnMenuItemClickListener(item->{if(item.getItemId()==0)showExpenses();else if(item.getItemId()==3)showReports();else Toast.makeText(this,item.getTitle()+" — раздел готовится",Toast.LENGTH_SHORT).show();return true;});p.show();
    }

    private void showReports(){
        content.removeAllViews();LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,16,16,16);l.addView(text("Отчёты",25,true));
        for(String[] x:db.totalsByCurrency())l.addView(text(x[0]+" — "+money(Double.parseDouble(x[1]))+" "+symbol(x[0]),20,true));content.addView(l);
    }
}
