package ru.controlexpenses;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class ReceiptActivity extends Activity {
    private Uri imageUri;
    private ImageView image;
    private TextView status;
    private EditText shop,date,total,items;
    private Spinner currency;
    private DB db;
    private final String[] currencies={"RUB ₽","BYN Br","KZT ₸","UAH ₴","EUR €","USD $","GBP £"};

    public void onCreate(Bundle b){
        super.onCreate(b);db=new DB(this);buildUi();String u=getIntent().getStringExtra("image_uri");if(u!=null){imageUri=Uri.parse(u);loadImage();}
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff4f7f8);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(10,8,10,8);bar.setBackgroundColor(0xff17324d);
        Button back=new Button(this);back.setText("‹ Назад");back.setAllCaps(false);back.setOnClickListener(v->finish());bar.addView(back);
        TextView title=new TextView(this);title.setText("Чек");title.setTextColor(Color.WHITE);title.setTextSize(22);title.setTypeface(null,1);title.setPadding(14,0,0,0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(14,12,14,24);
        image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.WHITE);box.addView(image,new LinearLayout.LayoutParams(-1,-2));
        status=text("Чек открыт полностью. Нажмите «Распознать».",14,false);box.addView(status);
        shop=field("Магазин / получатель","");date=field("Дата покупки",today());total=field("Фактически уплачено за чек","");items=field("Товары и фактически уплаченные суммы","");items.setMinLines(6);items.setGravity(Gravity.TOP);
        currency=new Spinner(this);currency.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,currencies));
        box.addView(shop);box.addView(date);box.addView(text("Валюта покупки",14,true));box.addView(currency);box.addView(items);box.addView(total);
        Button rec=button("Распознать чек");rec.setOnClickListener(v->recognize());box.addView(rec);
        Button save=button("Сохранить чек");save.setOnClickListener(v->save());box.addView(save);
        sv.addView(box);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void loadImage(){
        try(InputStream in=getContentResolver().openInputStream(imageUri)){Bitmap bm=BitmapFactory.decodeStream(in);image.setImageBitmap(bm);}catch(Exception e){status.setText("Не удалось открыть изображение: "+e.getMessage());}
    }

    private void recognize(){
        if(imageUri==null)return;status.setText("Распознаю чек…");
        try{
            InputImage input=InputImage.fromFilePath(this,imageUri);TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(input).addOnSuccessListener(this::parse).addOnFailureListener(e->status.setText("Ошибка распознавания: "+e.getMessage()));
        }catch(Exception e){status.setText("Ошибка изображения: "+e.getMessage());}
    }

    private void parse(Text result){
        String raw=result.getText()==null?"":result.getText();
        String foundDate=findDate(raw);if(!foundDate.isEmpty())date.setText(foundDate);
        String foundShop=findShop(raw);if(!foundShop.isEmpty())shop.setText(foundShop);

        ArrayList<String> productLines=new ArrayList<>();
        double itemSum=0;
        Double receiptTotal=findReceiptTotal(raw);

        for(String line:raw.split("\\r?\\n")){
            String s=normalize(line);
            if(s.length()<3||skip(s))continue;
            ParsedItem p=parseItemLine(s);
            if(p!=null && p.paid>0){
                productLines.add(p.name+" — "+money(p.paid));
                itemSum+=p.paid;
            }
        }

        items.setText(join(productLines));
        if(receiptTotal!=null && receiptTotal>0) total.setText(money(receiptTotal));
        else if(itemSum>0) total.setText(money(itemSum));
        status.setText("Распознавание завершено. Проверьте магазин, дату, товары и фактически уплаченные суммы.");
    }

    private ParsedItem parseItemLine(String s){
        // Формат: Товар 1,250 x 249,90 = 312,38
        Matcher calc=Pattern.compile("^(.+?)\\s+(\\d+[,.]\\d+)\\s*[xх×*]\\s*(\\d+[,.]\\d+)\\s*(?:=)?\\s*(\\d+[,.]\\d{2})$").matcher(s);
        if(calc.find() && hasLetters(calc.group(1))) return new ParsedItem(cleanName(calc.group(1)),num(calc.group(4)));

        // Формат: Товар 249,90 312,38 — берём последнюю сумму как реально оплаченную за позицию
        Matcher twoAmounts=Pattern.compile("^(.+?)\\s+(\\d+[,.]\\d{2})\\s+(\\d+[,.]\\d{2})$").matcher(s);
        if(twoAmounts.find() && hasLetters(twoAmounts.group(1))) return new ParsedItem(cleanName(twoAmounts.group(1)),num(twoAmounts.group(3)));

        // Формат: Товар .... 312,38 — берём крайнюю правую денежную сумму
        Matcher last=Pattern.compile("^(.+?)\\s+(\\d+[,.]\\d{2})$").matcher(s);
        if(last.find() && hasLetters(last.group(1))) return new ParsedItem(cleanName(last.group(1)),num(last.group(2)));
        return null;
    }

    private Double findReceiptTotal(String raw){
        Pattern p=Pattern.compile("(?i)(?:итого|к оплате|сумма к оплате|всего)\\s*[:=]?\\s*(\\d+[,.]\\d{2})");
        Double found=null;
        for(String line:raw.split("\\r?\\n")){
            Matcher m=p.matcher(normalize(line));
            if(m.find()) found=num(m.group(1));
        }
        return found;
    }

    private void save(){
        double amount=num(total.getText().toString());
        if(amount<=0){new AlertDialog.Builder(this).setMessage("Укажите фактически уплаченную сумму за чек.").setPositiveButton("Понятно",null).show();return;}
        String cur=String.valueOf(currency.getSelectedItem()).substring(0,3);
        db.add(new Expense(0,date.getText().toString(),shop.getText().toString(),"Покупки",cur,items.getText().toString(),amount));
        setResult(RESULT_OK);finish();
    }

    private String findDate(String raw){Matcher m=Pattern.compile("(\\d{2}[./-]\\d{2}[./-](?:\\d{2}|\\d{4}))").matcher(raw);return m.find()?m.group(1).replace('/','.').replace('-','.'):"";}
    private String findShop(String raw){for(String l:raw.split("\\r?\\n")){String s=normalize(l);if(s.length()>=3&&s.length()<=45&&hasLetters(s)&&!skip(s)&&!s.matches(".*\\d+[,.]\\d{2}$"))return s;}return "";}
    private String normalize(String s){return s==null?"":s.trim().replaceAll("\\s{2,}"," ");}
    private String cleanName(String s){return s.replaceAll("(?i)\\b(цена|стоимость|сумма|кол-?во|количество)\\b"," ").replaceAll("\\s{2,}"," ").trim();}
    private boolean skip(String s){String q=s.toLowerCase(Locale.ROOT);return q.contains("кассир")||q.contains("касса")||q.contains("ндс")||q.contains("фн ")||q.contains("фд ")||q.contains("фп ")||q.contains("адрес")||q.contains("цена за")||q.contains("оплата картой")||q.contains("наличными");}
    private boolean hasLetters(String s){return s.matches(".*[A-Za-zА-Яа-яЁё].*");}
    private double num(String s){try{return Double.parseDouble(s.replace(" ","").replace(',','.'));}catch(Exception e){return 0;}}
    private String money(double v){return String.format(Locale.getDefault(),"%.2f",v).replace('.',',');}
    private String join(ArrayList<String> a){StringBuilder b=new StringBuilder();for(String s:a)b.append(s).append('\n');return b.toString();}
    private String today(){return new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date());}
    private EditText field(String h,String v){EditText e=new EditText(this);e.setHint(h);e.setText(v);e.setPadding(10,10,10,10);return e;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(10,8,10,8);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    private static class ParsedItem{
        final String name; final double paid;
        ParsedItem(String name,double paid){this.name=name;this.paid=paid;}
    }
}
