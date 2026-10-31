package cz.miraficus.x10connect;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0d141c,CARD=0xff18232f,TEXT=0xffedf3fa,MUTED=0xff96a9bb,ACCENT=0xff61e1d0;
    private final ExecutorService commands=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=Executors.newFixedThreadPool(2);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<Protocol.Media> media=new ArrayList<>(), visible=new ArrayList<>();
    private final Set<String> selected=new LinkedHashSet<>(), fetching=ConcurrentHashMap.newKeySet();
    private final List<String> modes=new ArrayList<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private volatile Network wifi;
    private volatile boolean thumbnailsPaused;
    private int photoWatch;
    private ConnectivityManager connectivity;
    private boolean connected,legacy,destroyed,receiverRegistered;
    private String base="http://192.168.0.1",device="",mode="NormalPhoto",tab="Galerie",filter="Vše";
    private TextView status,transfer,selectionText;
    private FrameLayout content;
    private LinearLayout navigation;
    private GridView grid;
    private GalleryAdapter adapter;
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){ui.post(()->{wifi=CameraClient.wifi(MainActivity.this);updateStatus();});}
        @Override public void onLost(Network network){ui.post(()->{if(network.equals(wifi)){wifi=null;connected=false;}updateStatus();});}
    };
    private final BroadcastReceiver progressReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            transfer.setText(intent.getStringExtra("text"));
            if(intent.getBooleanExtra("finished",false))toast(intent.getStringExtra("text"));
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        base=getPreferences(MODE_PRIVATE).getString("base",base);
        legacy=getPreferences(MODE_PRIVATE).getBoolean("legacy",false);
        connectivity=getSystemService(ConnectivityManager.class);
        modes.addAll(Arrays.asList("NormalPhoto","NormalVideo","TimerPhoto","Burst","PhotoLapse","VideoLapse","CarMode","SlowRec"));
        build();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
    }
    @Override public void onStart(){
        super.onStart();thumbnailsPaused=false;wifi=CameraClient.wifi(this);updateStatus();
        NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        connectivity.registerNetworkCallback(request,networkCallback);
        IntentFilter f=new IntentFilter(DownloadService.EVENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(progressReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerLegacyProgressReceiver(f);
        receiverRegistered=true;
        if(adapter!=null)adapter.notifyDataSetChanged();
        transfer.setText(getSharedPreferences("transfer",MODE_PRIVATE).getString("status","Fotky → Pictures/X10Connect · videa → Movies/X10Connect"));
    }
    // API 29–32 only: the signature permission restricts senders to this app.
    // API 33+ uses RECEIVER_NOT_EXPORTED in onStart. Lint does not infer this guard.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyProgressReceiver(IntentFilter filter){
        registerReceiver(progressReceiver,filter,getPackageName()+".permission.DOWNLOAD",null);
    }
    @Override public void onStop(){
        super.onStop();thumbnailsPaused=true;connectivity.unregisterNetworkCallback(networkCallback);
        if(receiverRegistered){unregisterReceiver(progressReceiver);receiverRegistered=false;}
    }
    @Override public void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);commands.shutdownNow();thumbnails.shutdownNow();super.onDestroy();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);showTab();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(14);b.setMinHeight(dp(48));b.setBackgroundTintList(ColorStateList.valueOf(CARD));b.setOnClickListener(v->action.run());return b;}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void error(Exception e){if(destroyed)return;String message=e.getMessage()==null?e.toString():e.getMessage();AppLog.add(this,"Chyba: "+message);toast(message);status.setText("Chyba · "+message);}
    private interface Work<T>{T run() throws Exception;}
    private interface Done<T>{void accept(T value);}
    private <T> void async(Work<T> work,Done<T> done){if(destroyed)return;commands.execute(()->{try{T result=work.run();ui.post(()->{if(!destroyed)done.accept(result);});}catch(Exception e){ui.post(()->error(e));}});}
    private CameraClient client(){wifi=CameraClient.wifi(this);return new CameraClient(this,base,wifi);}
    private void build(){
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(16),0,dp(16),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom,left,right;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}
            v.setPadding(dp(16)+left,top,dp(16)+right,bottom);return insets;
        });
        LinearLayout header=row();TextView title=text("X10 CONNECT",23,TEXT);title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(64),1));header.addView(button("Wi-Fi",this::openWifi));root.addView(header);
        status=text("Připoj se k Wi-Fi kamery",13,MUTED);root.addView(status);
        transfer=text("",12,ACCENT);transfer.setMaxLines(2);root.addView(transfer);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        navigation=row();root.addView(navigation);setContentView(root);root.requestApplyInsets();showTab();
    }
    private void updateStatus(){
        if(status==null)return;
        status.setText(connected?device:"Wi-Fi "+(wifi==null?"není připojená":"připojená")+" · "+base);
        status.setTextColor(connected?ACCENT:MUTED);
    }
    private void openWifi(){try{startActivity(new Intent(Settings.Panel.ACTION_WIFI));}catch(Exception e){startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}}
    private void showTab(){
        if(content==null)return;
        content.removeAllViews();navigation.removeAllViews();
        for(String name:new String[]{"Galerie","Kamera","Nastavení","Diagnostika"}){
            Button b=button(name,()->{tab=name;showTab();});if(name.equals(tab))b.setTextColor(ACCENT);
            b.setTextSize(11);b.setPadding(0,0,0,0);navigation.addView(b,new LinearLayout.LayoutParams(0,dp(56),1));
        }
        switch(tab){case "Galerie":showGallery();break;case "Kamera":showCamera();break;case "Nastavení":showSettings();break;default:showLog();}
    }
    private LinearLayout scrollContent(){ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(0,dp(8),0,dp(16));scroll.addView(body);content.addView(scroll);return body;}
    private void connect(){
        status.setText("Připojuji…");
        async(()->{
            CameraClient c=client();Map<String,String> attr=Protocol.vars(c.get("getdeviceattr.cgi"));
            if(attr.isEmpty())throw new IllegalStateException("Kamera nevrátila identifikaci");
            Map<String,String> battery=Protocol.vars(c.get("getbatterycapacity.cgi?"));
            return new String[]{attr.getOrDefault("name","Kamera"),attr.getOrDefault("softversion",""),battery.getOrDefault("capacity","?")};
        },result->{connected=true;device=result[0]+" · "+result[1]+" · "+result[2]+" %";updateStatus();refresh();loadModes();});
    }
    private void refresh(){
        if(!connected){connect();return;}
        status.setText("Načítám seznam…");
        async(()->client().list(),result->{media.clear();media.addAll(result);Set<String> paths=new HashSet<>();for(Protocol.Media m:media)paths.add(m.path);selected.retainAll(paths);updateStatus();if(tab.equals("Galerie"))showTab();});
    }
    private void showGallery(){
        LinearLayout body=column();content.addView(body);
        LinearLayout actions=row();actions.addView(button(connected?"Obnovit":"Připojit",connected?this::refresh:this::connect),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("Stáhnout výběr",this::downloadSelected),new LinearLayout.LayoutParams(0,-2,1));body.addView(actions);
        LinearLayout filters=row();for(String name:new String[]{"Vše","Fotky","Videa"}){Button b=button(name,()->{filter=name;showTab();});if(filter.equals(name))b.setTextColor(ACCENT);filters.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}body.addView(filters);
        visible.clear();for(Protocol.Media m:media)if(filter.equals("Vše") || filter.equals("Videa")==m.video())visible.add(m);
        LinearLayout selectRow=row();selectionText=text(visible.size()+" souborů · vybráno "+selected.size(),12,MUTED);selectRow.addView(selectionText,new LinearLayout.LayoutParams(0,-2,1));
        selectRow.addView(button("Vybrat vše",()->{for(Protocol.Media m:visible)selected.add(m.path);if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();}));
        selectRow.addView(button("Zrušit",()->{selected.clear();if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();}));body.addView(selectRow);
        if(media.isEmpty()){
            TextView empty=text(connected?"Karta neobsahuje média.\nZkus obnovit seznam.":"Tvoje kamera, bez cloudu.\n\n1. Zapni Wi-Fi na kameře.\n2. Tlačítkem Wi-Fi připoj telefon.\n3. Klepni na Připojit.\n\nPřipojení bez internetu je v pořádku.",18,MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new LinearLayout.LayoutParams(-1,0,1));return;
        }
        grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(155));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));grid.setClipToPadding(false);
        adapter=new GalleryAdapter();grid.setAdapter(adapter);body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void updateSelection(){if(selectionText!=null)selectionText.setText(visible.size()+" souborů · vybráno "+selected.size());}
    private final class GalleryAdapter extends BaseAdapter{
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int p){return visible.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            Protocol.Media item=visible.get(position);LinearLayout card=column();card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(shape(CARD,14));
            ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(BG,8));image.setContentDescription(item.name());image.setImageResource(android.R.drawable.ic_menu_gallery);card.addView(image,new LinearLayout.LayoutParams(-1,dp(110)));
            CheckBox choose=new CheckBox(MainActivity.this);choose.setText(item.name());choose.setTextSize(12);choose.setTextColor(TEXT);choose.setButtonTintList(ColorStateList.valueOf(ACCENT));choose.setChecked(selected.contains(item.path));choose.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(item.path);else selected.remove(item.path);updateSelection();});card.addView(choose);
            TextView info=text((item.video()?"VIDEO · ":"FOTO · ")+item.sizeText(),11,MUTED);card.addView(info);image.setOnClickListener(v->mediaActions(item));card.setOnLongClickListener(v->{choose.setChecked(!choose.isChecked());return true;});
            String key=base+"|"+item.path;image.setTag(key);Bitmap cached=cache.get(key);
            if(cached!=null)image.setImageBitmap(cached);
            else if(!thumbnailsPaused && !DownloadService.running && fetching.add(key)){
                CameraClient camera=client();
                thumbnails.execute(()->{Bitmap bitmap=null;try{
                    if(thumbnailsPaused || DownloadService.running)return;
                    byte[] data;try{data=camera.image(item.thumbnail());}catch(Exception e){if(item.video())throw e;data=camera.image(item.path);}
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
                    while(bounds.outWidth/opts.inSampleSize>600 || bounds.outHeight/opts.inSampleSize>600)opts.inSampleSize*=2;
                    bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(bitmap!=null)cache.put(key,bitmap);
                }catch(Exception ignored){}finally{fetching.remove(key);}
                    final Bitmap done=bitmap;ui.post(()->{if(!destroyed && done!=null && key.equals(image.getTag()))image.setImageBitmap(done);});
                });
            }
            return card;
        }
    }
    private void mediaActions(Protocol.Media item){
        String key=base+"|"+item.path+"|"+item.size;String saved=getSharedPreferences("saved",MODE_PRIVATE).getString(key,null);
        new AlertDialog.Builder(this).setTitle(item.name()).setMessage(Protocol.date(item.created)+"\n"+item.sizeText()+(item.video()?" · "+item.duration+" s":""))
                .setPositiveButton("Stáhnout",(d,w)->startDownload(Collections.singletonList(item)))
                .setNeutralButton(saved==null?"Zavřít":"Otevřít uložené",(d,w)->{if(saved!=null)try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(saved),item.mime()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){error(e);}}).show();
    }
    private void downloadSelected(){List<Protocol.Media> items=new ArrayList<>();for(Protocol.Media m:media)if(selected.contains(m.path))items.add(m);if(items.isEmpty()){toast("Vyber soubory zaškrtávátkem.");return;}startDownload(items);}
    private void startDownload(List<Protocol.Media> items){
        if(DownloadService.running){toast("Stahování už běží. Průběh je v oznámení.");return;}
        try{
            Network network=CameraClient.wifi(this);if(network==null)throw new IllegalStateException("Připoj se k Wi-Fi kamery.");
            JSONArray files=new JSONArray();for(Protocol.Media m:items)files.put(m.json());
            if(files.toString().length()>300000)throw new IllegalStateException("Vyber méně souborů najednou.");
            startForegroundService(new Intent(this,DownloadService.class).putExtra("base",base).putExtra("network",network).putExtra("files",files.toString()));
            transfer.setText("Stahování zahájeno…");
        }catch(Exception e){error(e);}
    }
    private void command(String label,String endpoint,boolean photo){
        async(()->{
            CameraClient c=client(); int before=-1;
            if(photo)try{before=Integer.parseInt(Protocol.vars(c.get("getfilecount.cgi?")).get("count"));}catch(Exception e){AppLog.add(this,"Výchozí počet pro spoušť není známý: "+e);}
            String raw=c.get(endpoint);
            if(!raw.contains("Success") && !raw.contains("SUCCESS") && !raw.contains("SvrFuncResult=\"0\""))throw new IllegalStateException("Neočekávaná odpověď: "+raw);
            return new String[]{raw,Integer.toString(before)};
        },result->{toast(label+": "+result[0].trim());if(photo)watchPhotos(Integer.parseInt(result[1]));});
    }
    private void watchPhotos(int before){
        final int generation=++photoWatch;
        final String origin=base;
        final int[] lastCount={before};
        status.setText("Požadavek přijat · čekám na soubory…");
        // Read-only checks, never resend the shutter. Continue after the first change for burst shots.
        for(int delay:new int[]{1500,4500,9000,16000,30000})ui.postDelayed(()->{
            if(destroyed || generation!=photoWatch || !connected || !base.equals(origin) || DownloadService.running)return;
            async(()->Integer.parseInt(Protocol.vars(client().get("getfilecount.cgi?")).get("count")),count->{
                if(generation!=photoWatch || !base.equals(origin))return;
                if(count!=lastCount[0]){lastCount[0]=count;refresh();}
                if(delay==30000){
                    AppLog.add(this,"Po spoušti: počet před="+before+", po 30 s="+count+"; změna počtu sama nerozlišuje jednotlivé požadavky.");
                    if(before>=0 && count<=before){status.setText("Nový soubor zatím nepotvrzen · zkus Obnovit");toast("Kamera přijala spoušť, ale počet souborů se nezvýšil.");}
                }
            });
        },delay);
    }
    private void loadModes(){
        if(legacy)return;
        async(()->{
            CameraClient c=client();Map<String,String> options=Protocol.vars(c.get("getallworkmode.cgi"));
            List<String> values=new ArrayList<>();for(String key:new String[]{"photo","video","multi"})for(String value:options.getOrDefault(key,"").split(","))if(!value.trim().isEmpty())values.add(value.trim());
            String current=Protocol.vars(c.get("getcurworkmode.cgi")).getOrDefault("workmode",mode);
            return new Object[]{values,current};
        },result->{@SuppressWarnings("unchecked") List<String> values=(List<String>)result[0];if(!values.isEmpty()){modes.clear();modes.addAll(new LinkedHashSet<>(values));}mode=(String)result[1];if(tab.equals("Kamera"))showTab();});
    }
    private void showCamera(){
        LinearLayout body=scrollContent();body.addView(text("Ovládání kamery",25,TEXT));body.addView(text("Na Androidu 16 ověřeno fotografování a menší náhled. Kamera přijala přepnutí NormalVideo / NormalPhoto. Video a parametry čekají na test.",14,MUTED));
        body.addView(button("Načíst režimy a aktuální stav",this::loadModes));
        Spinner choose=new Spinner(this);List<String> options=legacy?Arrays.asList("Foto · 0","Časovač · 1","Série · 10","Časosběr foto · 11","Video · 20","Smyčka · 21","Časosběr video · 22","Zpomalení · 24"):modes;
        ArrayAdapter<String> modeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);choose.setAdapter(modeAdapter);
        if(!legacy && modes.contains(mode))choose.setSelection(modes.indexOf(mode));body.addView(choose,new LinearLayout.LayoutParams(-1,dp(56)));
        body.addView(button("Použít vybraný režim",()->{
            String value=choose.getSelectedItem().toString();if(legacy)value=value.substring(value.lastIndexOf(' ')+1);
            String selectedMode=value;String endpoint=legacy?"setworkmode.cgi?&-workmode="+value:"setcurworkmode.cgi?-workmode="+Protocol.encode(value);
            async(()->{String raw=client().get(endpoint);if(!raw.contains("Success") && !raw.contains("SUCCESS"))throw new IllegalStateException(raw);return selectedMode;},m->{mode=m;toast("Režim: "+m);});
        }));
        Button photo=button("●  Pořídit fotografii",()->command("Fotografie","photo.cgi?&-type=photo",true));photo.setTextColor(BG);photo.setBackgroundTintList(ColorStateList.valueOf(ACCENT));body.addView(photo);
        body.addView(text("Před focením nastav fotografický režim. U samospouště počkej na dokončení před změnou režimu. Nové soubory kontrolujeme až 30 s.",12,MUTED));
        LinearLayout rec=row();rec.addView(button("● Nahrávat",()->command("Start videa","record.cgi?&-cmd=start",false)),new LinearLayout.LayoutParams(0,-2,1));rec.addView(button("■ Zastavit",()->command("Stop videa","record.cgi?&-cmd=stop",false)),new LinearLayout.LayoutParams(0,-2,1));body.addView(rec);
        body.addView(button("Živý náhled · menší (ověřený)",()->preview(false)));body.addView(button("Živý náhled · větší (test)",()->preview(true)));
        body.addView(text("Parametry z kamery",20,TEXT));
        body.addView(button("Nastavení aktuálního režimu",()->menu(false)));body.addView(button("Systémové nastavení kamery",()->menu(true)));
        body.addView(button("Srovnat datum a čas s telefonem",()->command("Čas","setsystime.cgi?-time="+new java.text.SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).format(new Date()),false)));
        body.addView(text("Další video příkazy · test",20,TEXT));
        Spinner variants=new Spinner(this);String[] types={"common","loop","timelapse","slow","recsnap"};variants.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));body.addView(variants);
        LinearLayout extra=row();extra.addView(button("Start record2",()->command("record2 start","record2.cgi?&-type="+variants.getSelectedItem()+"&-cmd=start",false)),new LinearLayout.LayoutParams(0,-2,1));extra.addView(button("Stop record2",()->command("record2 stop","record2.cgi?&-type="+variants.getSelectedItem()+"&-cmd=stop",false)),new LinearLayout.LayoutParams(0,-2,1));body.addView(extra);
    }
    private void preview(boolean high){if(CameraClient.wifi(this)==null){toast("Připoj se k Wi-Fi kamery.");return;}startActivity(new Intent(this,PreviewActivity.class).putExtra("base",base).putExtra("high",high));}
    private void menu(boolean system){
        String context=system?"System":mode;
        if(legacy){legacyResolution();return;}
        async(()->Protocol.vars(client().get("getprimarymenuitem.cgi?-workmode="+Protocol.encode(context))),values->{
            String[] items=values.getOrDefault("item","").split(","),current=values.getOrDefault("cur","").split(",",-1);
            if(items.length==0 || items[0].isEmpty()){toast("Kamera nevrátila seznam nastavení.");return;}
            List<String> names=new ArrayList<>(),labels=new ArrayList<>();
            for(int i=0;i<items.length;i++){
                String lower=items[i].toLowerCase(Locale.ROOT);
                if(lower.contains("format") || lower.contains("reset") || lower.contains("default") || lower.contains("upgrade") || lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("delete"))continue;
                names.add(items[i]);labels.add(items[i]+(i<current.length?" · "+current[i]:""));
            }
            new AlertDialog.Builder(this).setTitle(system?"Systém kamery · test":"Režim · "+context).setItems(labels.toArray(new String[0]),(d,w)->setting(context,names.get(w))).setNegativeButton("Zavřít",null).show();
        });
    }
    private void setting(String context,String name){
        async(()->Protocol.vars(client().get("getsecondmenuitem.cgi?-workmode="+Protocol.encode(context)+"&-name="+Protocol.encode(name))),values->{
            String items=values.getOrDefault("item","");if(items.isEmpty()){toast("Kamera nevrátila možnosti: "+values);return;}
            String[] choices=items.split(",");new AlertDialog.Builder(this).setTitle(name+" · test").setItems(choices,(d,w)->command(name,"setcurparameter.cgi?-workmode="+Protocol.encode(context)+"&-name="+Protocol.encode(name)+"&-value="+Protocol.encode(choices[w]),false)).setNegativeButton("Zrušit",null).show();
        });
    }
    private void legacyResolution(){
        EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText("20");
        new AlertDialog.Builder(this).setTitle("Číselný režim (0 foto, 20 video)").setView(input).setPositiveButton("Načíst rozlišení",(d,w)->{
            String numeric=input.getText().toString();if(!numeric.matches("\\d{1,2}")){toast("Neplatný režim");return;}
            async(()->Protocol.vars(client().get("getcapability.cgi?&-workmode="+numeric+"&-type=0")),values->{
                String value=values.getOrDefault("capability","");if(value.isEmpty()){toast("Kamera nevrátila možnosti.");return;}
                String[] options=value.split(",");new AlertDialog.Builder(this).setTitle("Rozlišení · test").setItems(options,(dialog,index)->command("Rozlišení","setparameter.cgi?&-workmode="+numeric+"&-type=0&-value="+Protocol.encode(options[index]),false)).show();
            });
        }).setNegativeButton("Zrušit",null).show();
    }
    private void showSettings(){
        LinearLayout body=scrollContent();body.addView(text("Připojení",25,TEXT));body.addView(text("Adresa kamery",14,MUTED));
        EditText ip=new EditText(this);ip.setText(base);ip.setSingleLine(true);ip.setTextColor(TEXT);ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);body.addView(ip);
        body.addView(button("Uložit adresu",()->{try{base=Protocol.base(ip.getText().toString());getPreferences(MODE_PRIVATE).edit().putString("base",base).apply();connected=false;media.clear();selected.clear();cache.evictAll();updateStatus();toast("Adresa uložena");}catch(Exception e){error(e);}}));
        body.addView(button("Vybrat Wi-Fi síť",this::openWifi));body.addView(button("Připojit a načíst kartu",this::connect));
        Switch protocol=new Switch(this);protocol.setText("Starší číselné API místo NewAPP");protocol.setTextColor(TEXT);protocol.setChecked(legacy);protocol.setPadding(dp(4),dp(16),dp(4),dp(16));protocol.setOnCheckedChangeListener((v,on)->{legacy=on;getPreferences(MODE_PRIVATE).edit().putBoolean("legacy",on).apply();});body.addView(protocol);
        body.addView(text("Pro V1.0.0.4 ponech vypnuté. Nové API načítá názvy režimů a nastavení přímo z kamery.",13,MUTED));
        body.addView(text("Ukládání",20,TEXT));body.addView(text("Fotografie: Pictures/X10Connect\nVidea: Movies/X10Connect\n\nStahování pokračuje na pozadí. Nedokončené soubory se nezveřejní v galerii. Již uložený soubor se stejnou cestou a velikostí se přeskočí.",14,MUTED));
        body.addView(button("Zrušit probíhající stahování",()->{if(DownloadService.running)startService(new Intent(this,DownloadService.class).setAction(DownloadService.CANCEL));else toast("Nic se nestahuje.");}));
        body.addView(text("X10 Connect "+BuildConfig.VERSION_NAME,18,TEXT));body.addView(text("Neoficiální klient pro LAMAX X10.1. Android 10+. Bez účtu a cloudu. Wi-Fi kamery nemá internet; to je v pořádku. Při zapnuté Wi-Fi kamera omezuje některá vlastní tlačítka.",13,MUTED));
    }
    private void showLog(){
        LinearLayout body=scrollContent();body.addView(text("Diagnostika",25,TEXT));
        body.addView(button("Obnovit záznam",this::showTab));body.addView(button("Sdílet záznam",()->{
            Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"X10 Connect diagnostika").putExtra(Intent.EXTRA_TEXT,"X10 Connect "+BuildConfig.VERSION_NAME+" · Android "+Build.VERSION.RELEASE+"\n"+AppLog.read(this));startActivity(Intent.createChooser(share,"Sdílet diagnostiku"));
        }));
        TextView log=text(AppLog.read(this),12,MUTED);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);body.addView(log);
    }
}
