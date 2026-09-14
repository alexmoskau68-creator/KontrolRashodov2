package ru.controlexpenses;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.InputStream;
import java.io.File;
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
        if(imageUri==null)return;
        status.setText("Распознаю русский текст чека…");
        new Thread(() -> {
            try{
                Bitmap bm;
                try(InputStream in=getContentResolver().openInputStream(imageUri)){bm=BitmapFactory.decodeStream(in);}
                if(bm==null)throw new Exception("Не удалось открыть изображение");
                String raw=recognizeWithTesseract(bm);
                runOnUiThread(() -> parseRaw(raw));
            }catch(Exception e){
                runOnUiThread(() -> status.setText("Ошибка распознавания: "+e.getMessage()));
            }
        }).start();
    }

    private String recognizeWithTesseract(Bitmap bm) throws Exception{
        File dataDir=new File(getFilesDir(),"tesseract");
        File tessdata=new File(dataDir,"tessdata");
        if(!tessdata.exists() && !tessdata.mkdirs())throw new Exception("Не удалось создать папку OCR");
        copyAssetIfNeeded("tessdata/rus.traineddata",new File(tessdata,"rus.traineddata"));
        copyAssetIfNeeded("tessdata/eng.traineddata",new File(tessdata,"eng.traineddata"));
        TessBaseAPI api=new TessBaseAPI();
        if(!api.init(dataDir.getAbsolutePath()+File.separator,"rus+eng")){
            api.recycle();throw new Exception("Не удалось загрузить русский OCR");
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        api.setVariable("preserve_interword_spaces","1");
        api.setImage(bm);
        String text=api.getUTF8Text();
        api.recycle();
        bm.recycle();
        return text==null?"":text;
    }

    private void copyAssetIfNeeded(String assetPath,File target) throws Exception{
        if(target.exists() && target.length()>0)return;
        File parent=target.getParentFile();if(parent!=null)parent.mkdirs();
        try(InputStream in=getAssets().open(assetPath);java.io.FileOutputStream out=new java.io.FileOutputStream(target)){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
    }

    private void parseRaw(String raw){
        parseText(raw);
    }

    private void parseText(String raw){
        raw=raw==null?"":raw;
        String foundDate=findDate(raw);if(!foundDate.isEmpty())date.setText(foundDate);
        String foundShop=findShop(raw);if(!foundShop.isEmpty())shop.setText(foundShop);

        ArrayList<String> productLines=new ArrayList<>();
        double itemSum=0;
        Double receiptTotal=findReceiptTotal(raw);
        String pendingName=null;
        boolean pendingTotal=false;

        for(String line:raw.split("\r?\n")){
            String s=normalize(line);
            if(s.length()<2)continue;

            if(isTotalLabel(s)){
                Double same=findRightAmount(s);
                if(same!=null){receiptTotal=same;pendingTotal=false;}
                else pendingTotal=true;
                continue;
            }

            if(pendingTotal){
                Double only=onlyAmount(s);
                if(only!=null){receiptTotal=only;pendingTotal=false;continue;}
                pendingTotal=false;
            }

            if(skip(s))continue;

            ParsedItem p=parseItemLine(s);
            if(p!=null && p.paid>0){
                productLines.add(p.name+" — "+money(p.paid));
                itemSum+=p.paid;
                pendingName=null;
                continue;
            }

            if(pendingName!=null){
                Double only=onlyAmount(s);
                if(only!=null){
                    productLines.add(cleanName(pendingName)+" — "+money(only));
                    itemSum+=only;
                    pendingName=null;
                    continue;
                }
            }

            if(looksLikeProductName(s)){
                pendingName=s;
            }
        }

        items.setText(join(productLines));
        if(receiptTotal!=null && receiptTotal>0) total.setText(money(receiptTotal));
        else if(itemSum>0) total.setText(money(itemSum));
        status.setText("Распознавание завершено. Найдено товаров: "+productLines.size()+". Проверьте данные перед сохранением.");
    }

    private ParsedItem parseItemLine(String s){
        // Для чеков формата Дикси: НАЗВАНИЕ ... КОЛ-ВО * ЦЕНА = СУММА.
        // Берём именно последнюю сумму строки: это фактически уплачено за позицию.
        String x=s.replace('×','x').replace('х','x').replace('Х','x');
        x=x.replaceAll("\\s*=\\s*"," = ").replaceAll("\\s+"," ").trim();

        Matcher calc=Pattern.compile("^(.+?)\\s+(\\d+(?:[,.]\\d+)?)\\s*[x*]\\s*(\\d+(?:[,.]\\d{2})?)\\s*=\\s*(\\d+[,.]\\d{2})$").matcher(x);
        if(calc.find() && hasLetters(calc.group(1))){
            return new ParsedItem(cleanName(calc.group(1)),num(calc.group(4)));
        }

        // Иногда OCR теряет знак '='.
        Matcher noEq=Pattern.compile("^(.+?)\\s+(\\d+(?:[,.]\\d+)?)\\s*[x*]\\s*(\\d+(?:[,.]\\d{2})?)\\s+(\\d+[,.]\\d{2})$").matcher(x);
        if(noEq.find() && hasLetters(noEq.group(1))){
            return new ParsedItem(cleanName(noEq.group(1)),num(noEq.group(4)));
        }

        // Универсальный вариант: строка товара содержит несколько чисел,
        // последняя денежная сумма с двумя знаками после запятой — сумма позиции.
        Matcher tail=Pattern.compile("^(.+?)\\s+(\\d+[,.]\\d{2})\\s*$").matcher(x);
        if(tail.find() && hasLetters(tail.group(1))){
            String name=cleanName(tail.group(1));
            if(!name.matches(".*\\b(?:итого|итог|к оплате|всего)\\b.*")){
                return new ParsedItem(name,num(tail.group(2)));
            }
        }
        return null;
    }

    private Double findReceiptTotal(String raw){
        Pattern p=Pattern.compile("(?i)(?:итого|к оплате|сумма к оплате|всего|итог)\\s*[:=]?\\s*(\\d+[,.]\\d{2})");
        Double found=null;
        for(String line:raw.split("\r?\n")){
            Matcher m=p.matcher(normalize(line));
            if(m.find()) found=num(m.group(1));
        }
        return found;
    }

    private boolean isTotalLabel(String s){
        String q=s.toLowerCase(Locale.ROOT);
        return q.matches(".*\\b(итого|итог|к оплате|всего|сумма к оплате)\\b.*");
    }

    private Double findRightAmount(String s){
        Matcher m=Pattern.compile("(\\d+[,.]\\d{2})\\s*$").matcher(s);
        return m.find()?num(m.group(1)):null;
    }

    private Double onlyAmount(String s){
        Matcher m=Pattern.compile("^(?:=\\s*)?(\\d+[,.]\\d{2})$").matcher(s);
        return m.find()?num(m.group(1)):null;
    }

    private boolean looksLikeProductName(String s){
        if(!hasLetters(s)||s.length()<3||s.length()>70)return false;
        if(s.matches(".*\\d+[,.]\\d{2}.*"))return false;
        String q=s.toLowerCase(Locale.ROOT);
        return !q.contains("магазин")&&!q.contains("чек")&&!q.contains("адрес")&&!q.contains("телефон")
                &&!q.contains("кассир")&&!q.contains("дата")&&!q.contains("время")&&!q.contains("инн")
                &&!q.contains("номер")&&!q.contains("скид")&&!q.contains("налог");
    }

    private void save(){
        double amount=num(total.getText().toString());
        if(amount<=0){new AlertDialog.Builder(this).setMessage("Укажите фактически уплаченную сумму за чек.").setPositiveButton("Понятно",null).show();return;}
        String cur=String.valueOf(currency.getSelectedItem()).substring(0,3);
        db.add(new Expense(0,date.getText().toString(),shop.getText().toString(),"Покупки",cur,items.getText().toString(),amount));
        setResult(RESULT_OK);finish();
    }

    private String findDate(String raw){Matcher m=Pattern.compile("(\\d{2}[./-]\\d{2}[./-](?:\\d{2}|\\d{4}))").matcher(raw);return m.find()?m.group(1).replace('/','.').replace('-','.'): "";}

    private String findShop(String raw){
        for(String l:raw.split("\r?\n")){
            String s=normalize(l);
            if(s.length()>=3&&s.length()<=45&&hasLetters(s)&&!skip(s)&&!s.matches(".*\\d+[,.]\\d{2}$"))return s;
        }
        return "";
    }

    private String normalize(String s){return s==null?"":s.trim().replaceAll("\\s{2,}"," ");}

    private String cleanName(String s){
        return s.replaceAll("(?i)\\b(цена|стоимость|сумма|кол-?во|количество)\\b"," ")
                .replaceAll("\s{2,}"," ").trim();
    }

    private boolean skip(String s){
        String q=s.toLowerCase(Locale.ROOT);
        return q.contains("кассир")||q.contains("касса")||q.contains("ндс")||q.contains("фн ")||q.contains("фд ")
                ||q.contains("фп ")||q.contains("адрес")||q.contains("цена за")||q.contains("оплата картой")
                ||q.contains("наличными")||q.contains("телефон")||q.contains("инн");
    }

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
