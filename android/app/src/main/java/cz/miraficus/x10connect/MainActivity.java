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

public final class MainActivity extends LocalizedActivity {
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
    private boolean debugMode,modeChanging;
    private SimplePreview simplePreview;
    private Spinner simpleModes;
    private ConnectivityManager connectivity;
    private boolean connected,legacy,destroyed,receiverRegistered;
    private String base="http://192.168.0.1",device="",mode="NormalPhoto",tab="Galerie",filter="Vše";
    private TextView status,transfer,selectionText,batteryText,cameraNameText;
    private String cameraName="",batteryLevel="—";
    private Button downloadButton,clearSelectionButton;
    private FrameLayout content;
    private LinearLayout navigation;
    private GridView grid;
    private GalleryAdapter adapter;
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){ui.post(()->{wifi=CameraClient.wifi(MainActivity.this);updateStatus();});}
        @Override public void onLost(Network network){ui.post(()->{if(network.equals(wifi)){wifi=null;connected=false;stopSimplePreview();}updateStatus();});}
    };
    private final BroadcastReceiver progressReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            transfer.setText(intent.getStringExtra("text"));transfer.setVisibility(View.VISIBLE);
            if(!debugMode && intent.getBooleanExtra("finished",false))ui.postDelayed(()->{if(!debugMode && !DownloadService.running && !WebExportService.running)transfer.setVisibility(View.GONE);},6000);
            if(intent.getBooleanExtra("finished",false)){toast(intent.getStringExtra("text"));if(intent.getBooleanExtra("webexport",false) && tab.equals("Nastavení"))showTab();}
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        base=getPreferences(MODE_PRIVATE).getString("base",base);
        debugMode=getPreferences(MODE_PRIVATE).getBoolean("debug_mode",false);
        legacy=debugMode && getPreferences(MODE_PRIVATE).getBoolean("legacy",false);
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
        if(simplePreview!=null)startSimplePreview();
        transfer.setText(getSharedPreferences("transfer",MODE_PRIVATE).getString("status",getString(R.string.ui_photos_pictures_x10connect_videos_movies_x10conn)));
        if(!debugMode)transfer.setVisibility(DownloadService.running || WebExportService.running?View.VISIBLE:View.GONE);
    }
    // API 29–32 only: the signature permission restricts senders to this app.
    // API 33+ uses RECEIVER_NOT_EXPORTED in onStart. Lint does not infer this guard.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyProgressReceiver(IntentFilter filter){
        registerReceiver(progressReceiver,filter,getPackageName()+".permission.DOWNLOAD",null);
    }
    @Override public void onStop(){
        stopSimplePreview();super.onStop();thumbnailsPaused=true;connectivity.unregisterNetworkCallback(networkCallback);
        if(receiverRegistered){unregisterReceiver(progressReceiver);receiverRegistered=false;}
    }
    @Override public void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);commands.shutdownNow();thumbnails.shutdownNow();super.onDestroy();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(debugMode)showTab();else build();}
    private boolean wideLayout(){return getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(LocaleHelper.label(this,value));v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(LocaleHelper.label(this,label));b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(14);b.setMinHeight(dp(48));b.setBackgroundTintList(ColorStateList.valueOf(CARD));b.setOnClickListener(v->action.run());if(!debugMode){b.setBackgroundTintList(null);b.setBackground(UiStyle.background(this,CARD,22,true));b.setPadding(dp(16),dp(12),dp(16),dp(12));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.bottomMargin=dp(8);b.setLayoutParams(bp);}return b;}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void error(Exception e){if(destroyed)return;String message=e.getMessage()==null?e.toString():e.getMessage();AppLog.add(this,"Error: "+message);toast(message);status.setText(getString(R.string.ui_error)+message);}
    private interface Work<T>{T run() throws Exception;}
    private interface Done<T>{void accept(T value);}
    private <T> void async(Work<T> work,Done<T> done){if(destroyed)return;commands.execute(()->{try{T result=work.run();ui.post(()->{if(!destroyed)done.accept(result);});}catch(Exception e){ui.post(()->error(e));}});}
    private CameraClient client(){wifi=CameraClient.wifi(this);return new CameraClient(this,base,wifi);}
    private void build(){
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(debugMode?16:20),0,dp(debugMode?16:20),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom,left,right;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}
            v.setPadding(dp(debugMode?16:20)+left,top,dp(debugMode?16:20)+right,bottom);return insets;
        });
        batteryText=null;cameraNameText=null;
        if(debugMode){
        LinearLayout header=row();TextView title=text("X10 CONNECT",23,TEXT);title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(64),1));header.addView(button("Wi-Fi",this::openWifi));root.addView(header);
        status=text(getString(R.string.ui_connect_to_the_camera_wi_fi),13,MUTED);root.addView(status);
        }else{
            TextView brand=text("X10 Connect",17,MUTED);brand.setTypeface(null,Typeface.BOLD);
            if(wideLayout()){LinearLayout compact=row();compact.addView(brand,new LinearLayout.LayoutParams(0,-2,1));compact.addView(UiStyle.iconButton(this,getString(R.string.ui_choose_wi_fi_network),"wifi",this::openWifi),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(compact);}else root.addView(brand);
            LinearLayout connection=row();connection.setPadding(dp(14),dp(10),dp(10),dp(10));connection.setBackground(UiStyle.shape(this,CARD,22,true));
            LinearLayout identity=column();identity.setPadding(0,0,dp(4),0);cameraNameText=text(getString(R.string.simple_camera_model),15,TEXT);cameraNameText.setSingleLine(true);cameraNameText.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(cameraNameText);
            status=text("",12,MUTED);status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(status);connection.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
            batteryText=text(batteryLevel,12,MUTED);batteryText.setCompoundDrawablesRelative(UiStyle.icon(this,"battery",MUTED,20),null,null,null);batteryText.setCompoundDrawablePadding(dp(4));connection.addView(batteryText);
            Button wifiButton=UiStyle.iconButton(this,getString(R.string.ui_choose_wi_fi_network),"wifi",this::openWifi);
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(48),dp(48));wp.setMarginStart(dp(10));connection.addView(wifiButton,wp);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(4);cp.bottomMargin=dp(8);root.addView(connection,cp);if(wideLayout())connection.setVisibility(View.GONE);
        }
        transfer=text("",12,ACCENT);transfer.setMaxLines(2);if(!debugMode)transfer.setVisibility(View.GONE);root.addView(transfer);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        navigation=row();root.addView(navigation);setContentView(root);root.requestApplyInsets();updateStatus();showTab();
    }
    private void updateStatus(){
        if(status==null)return;
        status.setText(debugMode?(connected?device:"Wi-Fi "+(wifi==null?getString(R.string.ui_disconnected):getString(R.string.ui_connected))+" · "+base)
            :(connected?getString(R.string.simple_connected):getString(R.string.simple_not_connected)));
        if(batteryText!=null)batteryText.setText(connected?batteryLevel:"—");
        if(cameraNameText!=null)cameraNameText.setText(connected && !cameraName.trim().isEmpty()?cameraName:getString(R.string.simple_camera_model));
        status.setTextColor(connected?ACCENT:MUTED);
    }
    private void openWifi(){try{startActivity(new Intent(Settings.Panel.ACTION_WIFI));}catch(Exception e){startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}}
    private void showTab(){
        if(content==null)return;
        stopSimplePreview();simplePreview=null;simpleModes=null;adapter=null;grid=null;selectionText=null;downloadButton=null;clearSelectionButton=null;
        content.removeAllViews();navigation.removeAllViews();
        for(String name:(debugMode?new String[]{"Galerie","Kamera","Nastavení","Diagnostika"}:new String[]{"Galerie","Kamera","Nastavení"})){
            Button b=button(name,()->{tab=name;showTab();});if(name.equals(tab))b.setTextColor(ACCENT);
            if(debugMode){b.setTextSize(11);b.setPadding(0,0,0,0);navigation.addView(b,new LinearLayout.LayoutParams(0,dp(56),1));}
            else{
                String icon=name.equals("Galerie")?"gallery":name.equals("Kamera")?"camera":"settings";
                boolean active=name.equals(tab);b.setSelected(active);b.setTextSize(12);b.setMinHeight(dp(68));b.setTextColor(active?ACCENT:MUTED);
                b.setPadding(dp(4),dp(8),dp(4),dp(8));b.setCompoundDrawables(null,UiStyle.icon(this,icon,active?ACCENT:MUTED,24),null,null);b.setCompoundDrawablePadding(dp(5));
                b.setBackground(UiStyle.background(this,active?0xff183b3e:Color.TRANSPARENT,24,false));
                LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.setMargins(dp(3),dp(8),dp(3),dp(4));navigation.addView(b,np);
            }
        }
        switch(tab){case "Galerie":showGallery();break;case "Kamera":showCamera();break;case "Nastavení":showSettings();break;default:showLog();}
    }
    private LinearLayout scrollContent(){ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(0,dp(8),0,dp(16));scroll.addView(body);content.addView(scroll);return body;}
    private void connect(){
        status.setText(getString(R.string.ui_connecting));
        async(()->{
            CameraClient c=client();Map<String,String> attr=Protocol.vars(c.get("getdeviceattr.cgi"));
            if(attr.isEmpty())throw new IllegalStateException(getString(R.string.ui_the_camera_did_not_return_its_identity));
            Map<String,String> battery=Protocol.vars(c.get("getbatterycapacity.cgi?"));
            return new String[]{attr.getOrDefault("name",getString(R.string.simple_camera_model)),attr.getOrDefault("softversion",""),battery.getOrDefault("capacity","?")};
        },result->{connected=true;cameraName=result[0];batteryLevel=result[2]+" %";device=result[0]+" · "+result[1]+" · "+result[2]+" %";updateStatus();refresh();loadModes();});
    }
    private void refresh(){
        if(!connected){connect();return;}
        status.setText(getString(R.string.ui_loading_files));
        async(()->client().list(),result->{media.clear();media.addAll(result);Set<String> paths=new HashSet<>();for(Protocol.Media m:media)paths.add(m.path);selected.retainAll(paths);updateStatus();if(tab.equals("Galerie"))showTab();});
    }
    private void showGallery(){
        if(!debugMode){showSimpleGallery();return;}
        LinearLayout body=column();content.addView(body);
        LinearLayout actions=row();actions.addView(button(connected?getString(R.string.ui_refresh):getString(R.string.ui_connect),connected?this::refresh:this::connect),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button(getString(R.string.ui_download_selected),this::downloadSelected),new LinearLayout.LayoutParams(0,-2,1));body.addView(actions);
        LinearLayout filters=row();for(String name:new String[]{"Vše","Fotky","Videa"}){Button b=button(name,()->{filter=name;showTab();});if(filter.equals(name))b.setTextColor(ACCENT);filters.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}body.addView(filters);
        visible.clear();for(Protocol.Media m:media)if(filter.equals("Vše") || filter.equals("Videa")==m.video())visible.add(m);
        LinearLayout selectRow=row();selectionText=text(visible.size()+getString(R.string.ui_files_selected)+selected.size(),12,MUTED);selectRow.addView(selectionText,new LinearLayout.LayoutParams(0,-2,1));
        selectRow.addView(button(getString(R.string.ui_select_all),()->{for(Protocol.Media m:visible)selected.add(m.path);if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();}));
        selectRow.addView(button(getString(R.string.ui_cancel),()->{selected.clear();if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();}));body.addView(selectRow);
        if(media.isEmpty()){
            TextView empty=text(connected?getString(R.string.ui_no_media_on_the_card_try_refreshing_the_list):getString(R.string.ui_your_camera_without_the_cloud_1_enable_wi_fi_on_),18,MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new LinearLayout.LayoutParams(-1,0,1));return;
        }
        grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(155));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));grid.setClipToPadding(false);
        adapter=new GalleryAdapter();grid.setAdapter(adapter);body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
    }
    private LinearLayout simplePageHeading(LinearLayout body,String title,String subtitle){
        LinearLayout heading=row();LinearLayout captions=column();TextView name=text(title,28,TEXT);name.setTypeface(null,Typeface.BOLD);captions.addView(name);
        if(subtitle!=null)captions.addView(text(subtitle,12,MUTED));heading.addView(captions,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=UiStyle.button(this,connected?getString(R.string.ui_refresh):getString(R.string.ui_connect),connected?"refresh":"connect",false,false,connected?this::refresh:this::connect);
        heading.addView(refresh,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(14);body.addView(heading,hp);return heading;
    }
    private void showSimpleGallery(){
        LinearLayout body=column(),results=body;
        if(wideLayout()){
            LinearLayout split=row();split.setGravity(Gravity.TOP);content.addView(split);
            ScrollView toolbar=new ScrollView(this);toolbar.setFillViewport(false);toolbar.addView(body);
            int width=Math.min(dp(270),getResources().getDisplayMetrics().widthPixels*2/5);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(width,-1);tp.setMarginEnd(dp(16));split.addView(toolbar,tp);
            results=column();split.addView(results,new LinearLayout.LayoutParams(0,-1,1));
        }else content.addView(body);
        visible.clear();for(Protocol.Media m:media)if(filter.equals("Vše") || filter.equals("Videa")==m.video())visible.add(m);
        simplePageHeading(body,LocaleHelper.label(this,"Galerie"),getResources().getQuantityString(R.plurals.simple_files,visible.size(),visible.size()));
        LinearLayout filters=row();filters.setPadding(dp(4),dp(4),dp(4),dp(4));filters.setBackground(UiStyle.shape(this,CARD,28,true));filters.setContentDescription(getString(R.string.simple_media_filter));
        for(String name:new String[]{"Vše","Fotky","Videa"}){
            boolean active=filter.equals(name);Button b=UiStyle.button(this,LocaleHelper.label(this,name),null,active,false,()->{filter=name;showTab();});
            b.setTextSize(14);b.setSelected(active);b.setTextColor(active?BG:MUTED);b.setBackground(UiStyle.background(this,active?ACCENT:Color.TRANSPARENT,24,false));
            filters.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        }
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.bottomMargin=dp(12);body.addView(filters,fp);
        selectionText=text("",14,MUTED);body.addView(selectionText);
        LinearLayout selection=row();
        Button all=UiStyle.button(this,getString(R.string.simple_select_all),null,false,false,()->{for(Protocol.Media m:visible)selected.add(m.path);if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();});
        clearSelectionButton=UiStyle.button(this,getString(R.string.simple_clear_selection),null,false,false,()->{selected.clear();if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();});
        for(Button b:new Button[]{all,clearSelectionButton}){b.setTextSize(13);b.setBackground(UiStyle.background(this,Color.TRANSPARENT,18,true));}
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMarginEnd(dp(6));selection.addView(all,ap);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(6));selection.addView(clearSelectionButton,cp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.bottomMargin=dp(12);body.addView(selection,sp);
        if(visible.isEmpty()){
            TextView empty=text(connected?getString(R.string.simple_no_matches):getString(R.string.ui_your_camera_without_the_cloud_1_enable_wi_fi_on_),16,MUTED);empty.setGravity(Gravity.CENTER);results.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(144));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(14));grid.setClipToPadding(false);grid.setPadding(0,0,0,dp(8));
            adapter=new GalleryAdapter();grid.setAdapter(adapter);results.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        }
        downloadButton=UiStyle.button(this,"","download",true,false,this::downloadSelected);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2);dp.topMargin=dp(10);dp.bottomMargin=dp(4);results.addView(downloadButton,dp);updateSelection();
    }
    private View simpleTile(Protocol.Media item){
        LinearLayout card=column();
        FrameLayout thumbnail=new FrameLayout(this);thumbnail.setBackground(UiStyle.shape(this,selected.contains(item.path)?ACCENT:CARD,18,false));thumbnail.setPadding(dp(1),dp(1),dp(1),dp(1));thumbnail.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(item.name());image.setImageResource(android.R.drawable.ic_menu_gallery);
        thumbnail.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Runnable toggle=()->{if(!selected.add(item.path))selected.remove(item.path);if(adapter!=null)adapter.notifyDataSetChanged();updateSelection();};
        Button check=UiStyle.iconButton(this,getString(selected.contains(item.path)?R.string.simple_deselect_file:R.string.simple_select_file,item.name()),"check",toggle);
        check.setPadding(dp(10),dp(10),dp(10),dp(10));check.setBackground(UiStyle.background(this,Color.TRANSPARENT,24,false));
        check.setCompoundDrawables(UiStyle.icon(this,selected.contains(item.path)?"selected":"unselected",ACCENT,28),null,null,null);check.setSelected(selected.contains(item.path));
        FrameLayout.LayoutParams tick=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);thumbnail.addView(check,tick);
        if(item.video()){
            TextView duration=text(item.duration+" s",11,TEXT);duration.setPadding(dp(8),dp(4),dp(8),dp(4));duration.setBackground(UiStyle.shape(this,0xdd0d141c,12,false));duration.setCompoundDrawablesRelative(UiStyle.icon(this,"video",TEXT,14),null,null,null);duration.setCompoundDrawablePadding(dp(4));
            FrameLayout.LayoutParams time=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);time.setMargins(dp(8),0,dp(8),dp(8));thumbnail.addView(duration,time);
        }
        card.addView(thumbnail,new LinearLayout.LayoutParams(-1,dp(120)));
        TextView filename=text(item.name(),12,TEXT);filename.setSingleLine(true);filename.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);card.addView(filename);
        TextView size=text(item.sizeText(),11,MUTED);size.setPadding(dp(4),0,dp(4),0);card.addView(size);
        image.setOnClickListener(v->mediaActions(item));card.setOnLongClickListener(v->{toggle.run();return true;});loadThumbnail(item,image);
        return card;
    }
    private void loadThumbnail(Protocol.Media item,ImageView image){
        String key=base+"|"+item.path;image.setTag(key);Bitmap cached=cache.get(key);
        if(cached!=null){image.setImageBitmap(cached);return;}
        if(thumbnailsPaused || DownloadService.running || WebExportService.running || !fetching.add(key))return;
        CameraClient camera=client();
        thumbnails.execute(()->{Bitmap bitmap=null;try{
            if(thumbnailsPaused || DownloadService.running || WebExportService.running)return;
            byte[] data;try{data=camera.image(item.thumbnail());}catch(Exception e){if(item.video())throw e;data=camera.image(item.path);}
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
            while(bounds.outWidth/opts.inSampleSize>600 || bounds.outHeight/opts.inSampleSize>600)opts.inSampleSize*=2;
            bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(bitmap!=null)cache.put(key,bitmap);
        }catch(Exception ignored){}finally{fetching.remove(key);}
            final Bitmap done=bitmap;ui.post(()->{if(!destroyed && done!=null){if(image.isAttachedToWindow() && key.equals(image.getTag()))image.setImageBitmap(done);else if(tab.equals("Galerie") && adapter!=null && !thumbnailsPaused)adapter.notifyDataSetChanged();}});
        });
    }
    private void updateSelection(){
        if(selectionText!=null)selectionText.setText(debugMode?visible.size()+getString(R.string.ui_files_selected)+selected.size():getResources().getQuantityString(R.plurals.simple_selected,selected.size(),selected.size()));
        if(downloadButton!=null){downloadButton.setText(getString(R.string.simple_download_selected,selected.size()));downloadButton.setEnabled(!selected.isEmpty());downloadButton.setAlpha(selected.isEmpty()?0.45f:1f);}
        if(clearSelectionButton!=null){clearSelectionButton.setEnabled(!selected.isEmpty());clearSelectionButton.setAlpha(selected.isEmpty()?0.45f:1f);}
    }
    private final class GalleryAdapter extends BaseAdapter{
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int p){return visible.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            Protocol.Media item=visible.get(position);if(!debugMode)return simpleTile(item);LinearLayout card=column();card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(shape(CARD,14));
            ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(BG,8));image.setContentDescription(item.name());image.setImageResource(android.R.drawable.ic_menu_gallery);card.addView(image,new LinearLayout.LayoutParams(-1,dp(110)));
            CheckBox choose=new CheckBox(MainActivity.this);choose.setText(item.name());choose.setTextSize(12);choose.setTextColor(TEXT);choose.setButtonTintList(ColorStateList.valueOf(ACCENT));choose.setChecked(selected.contains(item.path));choose.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(item.path);else selected.remove(item.path);updateSelection();});card.addView(choose);
            TextView info=text((item.video()?"VIDEO · ":getString(R.string.ui_photo))+item.sizeText(),11,MUTED);card.addView(info);image.setOnClickListener(v->mediaActions(item));card.setOnLongClickListener(v->{choose.setChecked(!choose.isChecked());return true;});
            loadThumbnail(item,image);
            return card;
        }
    }
    private void mediaActions(Protocol.Media item){
        String key=base+"|"+item.path+"|"+item.size;String saved=getSharedPreferences("saved",MODE_PRIVATE).getString(key,null);
        new AlertDialog.Builder(this).setTitle(item.name()).setMessage(Protocol.date(item.created)+"\n"+item.sizeText()+(item.video()?" · "+item.duration+" s":""))
                .setPositiveButton(getString(R.string.ui_download),(d,w)->startDownload(Collections.singletonList(item)))
                .setNeutralButton(saved==null?getString(R.string.ui_close):getString(R.string.ui_open_saved_file),(d,w)->{if(saved!=null)try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(saved),item.mime()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){error(e);}}).show();
    }
    private void downloadSelected(){List<Protocol.Media> items=new ArrayList<>();for(Protocol.Media m:media)if(selected.contains(m.path))items.add(m);if(items.isEmpty()){toast(getString(R.string.ui_select_files_using_the_checkboxes));return;}startDownload(items);}
    private void startDownload(List<Protocol.Media> items){
        if(WebExportService.running){toast(getString(R.string.export_busy));return;}
        if(DownloadService.running){toast(getString(R.string.ui_a_download_is_already_running_see_the_notificati));return;}
        try{
            Network network=CameraClient.wifi(this);if(network==null)throw new IllegalStateException(getString(R.string.ui_connect_to_the_camera_wi_fi_22));
            JSONArray files=new JSONArray();for(Protocol.Media m:items)files.put(m.json());
            if(files.toString().length()>300000)throw new IllegalStateException(getString(R.string.ui_select_fewer_files_at_a_time));
            startForegroundService(new Intent(this,DownloadService.class).putExtra("base",base).putExtra("network",network).putExtra("files",files.toString()));
            transfer.setVisibility(View.VISIBLE);transfer.setText(getString(R.string.ui_download_started));
        }catch(Exception e){error(e);}
    }
    private void command(String label,String endpoint,boolean photo){
        if(!debugMode && modeChanging){toast(getString(R.string.mode_changing));return;}
        async(()->{
            CameraClient c=client(); int before=-1;
            if(photo)try{before=Integer.parseInt(Protocol.vars(c.get("getfilecount.cgi?")).get("count"));}catch(Exception e){AppLog.add(this,getString(R.string.ui_initial_file_count_unavailable)+e);}
            String raw=c.get(endpoint);
            if(!raw.contains("Success") && !raw.contains("SUCCESS") && !raw.contains("SvrFuncResult=\"0\""))throw new IllegalStateException(getString(R.string.ui_unexpected_response)+raw);
            return new String[]{raw,Integer.toString(before)};
        },result->{toast(debugMode?label+": "+result[0].trim():getString(R.string.simple_request_sent));if(photo)watchPhotos(Integer.parseInt(result[1]));});
    }
    private void watchPhotos(int before){
        final int generation=++photoWatch;
        final String origin=base;
        final int[] lastCount={before};
        status.setText(getString(R.string.ui_request_accepted_waiting_for_files));
        // Read-only checks, never resend the shutter. Continue after the first change for burst shots.
        for(int delay:new int[]{1500,4500,9000,16000,30000})ui.postDelayed(()->{
            if(destroyed || generation!=photoWatch || !connected || !base.equals(origin) || DownloadService.running)return;
            async(()->Integer.parseInt(Protocol.vars(client().get("getfilecount.cgi?")).get("count")),count->{
                if(generation!=photoWatch || !base.equals(origin))return;
                if(count!=lastCount[0]){lastCount[0]=count;refresh();}
                if(delay==30000){
                    AppLog.add(this,getString(R.string.ui_after_shutter_initial_count)+before+getString(R.string.ui_after_30_s)+count+getString(R.string.ui_a_count_change_alone_cannot_identify_the_origina));
                    if(before>=0 && count<=before){status.setText(getString(R.string.ui_new_file_not_confirmed_yet_try_refresh));toast(getString(R.string.ui_the_camera_accepted_the_shutter_request_but_its_));}
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
        },result->{@SuppressWarnings("unchecked") List<String> values=(List<String>)result[0];if(!values.isEmpty()){modes.clear();modes.addAll(new LinkedHashSet<>(values));}mode=(String)result[1];if(tab.equals("Kamera")){if(debugMode)showTab();else updateSimpleModes();}});
    }
    private void showCamera(){
        if(!debugMode){showSimpleCamera();return;}
        LinearLayout body=scrollContent();body.addView(text(getString(R.string.ui_camera_controls),25,TEXT));body.addView(text(getString(R.string.ui_photo_capture_and_the_small_preview_were_tested_),14,MUTED));
        body.addView(button(getString(R.string.ui_load_modes_and_current_status),this::loadModes));
        Spinner choose=new Spinner(this);List<String> options=legacy?Arrays.asList(getString(R.string.ui_photo_0),getString(R.string.ui_timer_1),getString(R.string.ui_burst_10),getString(R.string.ui_photo_lapse_11),"Video · 20",getString(R.string.ui_loop_21),getString(R.string.ui_video_lapse_22),getString(R.string.ui_slow_motion_24)):modes;
        ArrayAdapter<String> modeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);choose.setAdapter(modeAdapter);
        if(!legacy && modes.contains(mode))choose.setSelection(modes.indexOf(mode));body.addView(choose,new LinearLayout.LayoutParams(-1,dp(56)));
        body.addView(button(getString(R.string.ui_apply_selected_mode),()->{
            String value=choose.getSelectedItem().toString();if(legacy)value=value.substring(value.lastIndexOf(' ')+1);
            String selectedMode=value;String endpoint=legacy?"setworkmode.cgi?&-workmode="+value:"setcurworkmode.cgi?-workmode="+Protocol.encode(value);
            async(()->{String raw=client().get(endpoint);if(!raw.contains("Success") && !raw.contains("SUCCESS"))throw new IllegalStateException(raw);return selectedMode;},m->{mode=m;toast(getString(R.string.ui_mode)+m);});
        }));
        Button photo=button(getString(R.string.ui_take_photo),()->command(getString(R.string.ui_photo_47),"photo.cgi?&-type=photo",true));photo.setTextColor(BG);photo.setBackgroundTintList(ColorStateList.valueOf(ACCENT));body.addView(photo);
        body.addView(text(getString(R.string.ui_select_a_photo_mode_before_shooting_let_the_time),12,MUTED));
        LinearLayout rec=row();rec.addView(button(getString(R.string.ui_record),()->command(getString(R.string.ui_start_recording),"record.cgi?&-cmd=start",false)),new LinearLayout.LayoutParams(0,-2,1));rec.addView(button(getString(R.string.ui_stop),()->command(getString(R.string.ui_stop_recording),"record.cgi?&-cmd=stop",false)),new LinearLayout.LayoutParams(0,-2,1));body.addView(rec);
        body.addView(button(getString(R.string.ui_live_preview_small_tested),()->preview(false)));body.addView(button(getString(R.string.ui_live_preview_large_experimental),()->preview(true)));
        body.addView(text(getString(R.string.ui_camera_settings),20,TEXT));
        body.addView(button(getString(R.string.ui_current_mode_settings),()->menu(false)));body.addView(button(getString(R.string.ui_camera_system_settings),()->menu(true)));
        body.addView(button(getString(R.string.ui_sync_date_and_time_with_phone),()->command(getString(R.string.ui_time),"setsystime.cgi?-time="+new java.text.SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).format(new Date()),false)));
        body.addView(text(getString(R.string.ui_additional_recording_commands_experimental),20,TEXT));
        Spinner variants=new Spinner(this);String[] types={"common","loop","timelapse","slow","recsnap"};variants.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));body.addView(variants);
        LinearLayout extra=row();extra.addView(button("Start record2",()->command("record2 start","record2.cgi?&-type="+variants.getSelectedItem()+"&-cmd=start",false)),new LinearLayout.LayoutParams(0,-2,1));extra.addView(button("Stop record2",()->command("record2 stop","record2.cgi?&-type="+variants.getSelectedItem()+"&-cmd=stop",false)),new LinearLayout.LayoutParams(0,-2,1));body.addView(extra);
    }
    private void showSimpleCamera(){
        LinearLayout body=scrollContent();simplePageHeading(body,LocaleHelper.label(this,"Kamera"),null);
        simplePreview=new SimplePreview(this);
        simplePreview.controls(this::startSimplePreview,()->{stopSimplePreview();startActivity(new Intent(this,FullPreviewActivity.class).putExtra("base",base));});
        FrameLayout aspect=new FrameLayout(this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(w)*9/16,View.MeasureSpec.EXACTLY));}};
        aspect.addView(simplePreview.view(),new FrameLayout.LayoutParams(-1,-1));body.addView(aspect,new LinearLayout.LayoutParams(-1,-2));
        TextView label=text(getString(R.string.simple_camera_mode),14,MUTED);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(20);lp.bottomMargin=dp(6);body.addView(label,lp);
        simpleModes=new Spinner(this);simpleModes.setBackground(UiStyle.background(this,CARD,22,true));simpleModes.setPopupBackgroundDrawable(UiStyle.shape(this,CARD,20,true));simpleModes.setPadding(dp(12),dp(4),dp(16),dp(4));
        simpleModes.setContentDescription(getString(R.string.simple_camera_mode));updateSimpleModes();
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(64));mp.bottomMargin=dp(18);body.addView(simpleModes,mp);
        simpleModes.setEnabled(!modeChanging);
        simpleModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                String value=(String)parent.getItemAtPosition(position);if(value.equals(mode) || modeChanging)return;
                modeChanging=true;simpleModes.setEnabled(false);
                commands.execute(()->{
                    try{String raw=client().get("setcurworkmode.cgi?-workmode="+Protocol.encode(value));
                        if(!raw.contains("Success"))throw new IllegalStateException(raw);
                        ui.post(()->{mode=value;finishSimpleModeChange();});
                    }catch(Exception e){ui.post(()->{finishSimpleModeChange();error(e);});}
                });
            }
        });
        LinearLayout actions=row();
        Button photo=UiStyle.button(this,getString(R.string.simple_photo),"camera",true,true,()->command(getString(R.string.ui_photo_47),"photo.cgi?&-type=photo",true));
        Button start=UiStyle.button(this,getString(R.string.simple_start),"record",false,true,()->{
            boolean lapse=mode.equals("VideoLapse");command(getString(R.string.ui_start_recording),lapse?"photo.cgi?&-type=photo":"record.cgi?&-cmd=start",lapse);
        });
        Button stop=UiStyle.button(this,getString(R.string.ui_stop),"stop",false,true,()->command(getString(R.string.ui_stop_recording),"record.cgi?&-cmd=stop",false));
        for(Button action:new Button[]{photo,start,stop}){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.setMargins(action==photo?0:dp(5),0,action==stop?0:dp(5),0);action.setMinHeight(dp(88));actions.addView(action,ap);}body.addView(actions);
        TextView hint=text(getString(R.string.simple_controls_hint),12,MUTED);hint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(14);body.addView(hint,hp);
        startSimplePreview();
    }
    private void finishSimpleModeChange(){
        modeChanging=false;if(destroyed)return;
        updateSimpleModes();if(simpleModes!=null)simpleModes.setEnabled(true);
    }
    private void updateSimpleModes(){
        if(simpleModes==null)return;
        Spinner widget=simpleModes;AdapterView.OnItemSelectedListener listener=widget.getOnItemSelectedListener();widget.setOnItemSelectedListener(null);
        widget.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(modes)){
            private View format(View v,int position){TextView t=(TextView)v;t.setText(modeLabel(getItem(position)));t.setTextColor(TEXT);t.setTextSize(16);t.setPadding(dp(12),dp(14),dp(12),dp(14));return t;}
            @Override public View getView(int position,View recycled,ViewGroup parent){View v=format(super.getView(position,recycled,parent),position);((TextView)v).setCompoundDrawablesRelative(null,null,UiStyle.icon(MainActivity.this,"chevron",MUTED,20),null);return v;}
            @Override public View getDropDownView(int position,View recycled,ViewGroup parent){return format(super.getDropDownView(position,recycled,parent),position);}
        });
        if(modes.contains(mode))widget.setSelection(modes.indexOf(mode),false);
        if(listener!=null)widget.post(()->{if(simpleModes==widget && !destroyed)widget.setOnItemSelectedListener(listener);});
    }
    private String modeLabel(String value){
        switch(value){
            case "NormalPhoto":return getString(R.string.mode_normal_photo);
            case "NormalVideo":return getString(R.string.mode_normal_video);
            case "TimerPhoto":return getString(R.string.mode_timer_photo);
            case "Burst":return getString(R.string.mode_burst);
            case "PhotoLapse":return getString(R.string.mode_photo_lapse);
            case "VideoLapse":return getString(R.string.mode_video_lapse);
            case "CarMode":return getString(R.string.mode_car);
            case "SlowRec":return getString(R.string.mode_slow);
            default:return value;
        }
    }
    private void startSimplePreview(){
        if(simplePreview==null)return;
        if(WebExportService.running){toast(getString(R.string.export_busy));return;}
        Network network=CameraClient.wifi(this);
        if(network==null){simplePreview.message(getString(R.string.ui_connect_to_the_camera_wi_fi));return;}
        simplePreview.start(base,network);
    }
    private void stopSimplePreview(){if(simplePreview!=null)simplePreview.stop();}
    private void preview(boolean high){if(WebExportService.running){toast(getString(R.string.export_busy));return;}if(CameraClient.wifi(this)==null){toast(getString(R.string.ui_connect_to_the_camera_wi_fi_22));return;}startActivity(new Intent(this,PreviewActivity.class).putExtra("base",base).putExtra("high",high));}
    private void menu(boolean system){
        String context=system?"System":mode;
        if(legacy){legacyResolution();return;}
        async(()->Protocol.vars(client().get("getprimarymenuitem.cgi?-workmode="+Protocol.encode(context))),values->{
            String[] items=values.getOrDefault("item","").split(","),current=values.getOrDefault("cur","").split(",",-1);
            if(items.length==0 || items[0].isEmpty()){toast(getString(R.string.ui_the_camera_did_not_return_a_settings_list));return;}
            List<String> names=new ArrayList<>(),labels=new ArrayList<>();
            for(int i=0;i<items.length;i++){
                String lower=items[i].toLowerCase(Locale.ROOT);
                if(lower.contains("format") || lower.contains("reset") || lower.contains("default") || lower.contains("upgrade") || lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("delete"))continue;
                names.add(items[i]);labels.add(items[i]+(i<current.length?" · "+current[i]:""));
            }
            new AlertDialog.Builder(this).setTitle(system?getString(R.string.ui_camera_system_experimental):getString(R.string.ui_mode_62)+context).setItems(labels.toArray(new String[0]),(d,w)->setting(context,names.get(w))).setNegativeButton(getString(R.string.ui_close),null).show();
        });
    }
    private void setting(String context,String name){
        async(()->Protocol.vars(client().get("getsecondmenuitem.cgi?-workmode="+Protocol.encode(context)+"&-name="+Protocol.encode(name))),values->{
            String items=values.getOrDefault("item","");if(items.isEmpty()){toast(getString(R.string.ui_the_camera_did_not_return_options)+values);return;}
            String[] choices=items.split(",");new AlertDialog.Builder(this).setTitle(name+getString(R.string.ui_experimental)).setItems(choices,(d,w)->command(name,"setcurparameter.cgi?-workmode="+Protocol.encode(context)+"&-name="+Protocol.encode(name)+"&-value="+Protocol.encode(choices[w]),false)).setNegativeButton(getString(R.string.ui_cancel),null).show();
        });
    }
    private void legacyResolution(){
        EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText("20");
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_numeric_mode_0_photo_20_video)).setView(input).setPositiveButton(getString(R.string.ui_load_resolutions),(d,w)->{
            String numeric=input.getText().toString();if(!numeric.matches("\\d{1,2}")){toast(getString(R.string.ui_invalid_mode));return;}
            async(()->Protocol.vars(client().get("getcapability.cgi?&-workmode="+numeric+"&-type=0")),values->{
                String value=values.getOrDefault("capability","");if(value.isEmpty()){toast(getString(R.string.ui_the_camera_did_not_return_any_options));return;}
                String[] options=value.split(",");new AlertDialog.Builder(this).setTitle(getString(R.string.ui_resolution_experimental)).setItems(options,(dialog,index)->command(getString(R.string.ui_resolution),"setparameter.cgi?&-workmode="+numeric+"&-type=0&-value="+Protocol.encode(options[index]),false)).show();
            });
        }).setNegativeButton(getString(R.string.ui_cancel),null).show();
    }
    private void showSettings(){
        LinearLayout body=scrollContent();
        Switch debug=new Switch(this);debug.setText(getString(R.string.debug_mode));debug.setTextColor(TEXT);debug.setChecked(debugMode);
        debug.setOnCheckedChangeListener((v,on)->{debugMode=on;legacy=on && getPreferences(MODE_PRIVATE).getBoolean("legacy",false);getPreferences(MODE_PRIVATE).edit().putBoolean("debug_mode",on).apply();build();});body.addView(debug);
        body.addView(text(getString(R.string.debug_mode_help),13,MUTED));
        if(!debugMode){showSimpleSettings(body);return;}
        body.addView(text(getString(R.string.ui_connection),25,TEXT));body.addView(text(getString(R.string.ui_camera_address),14,MUTED));
        EditText ip=new EditText(this);ip.setText(base);ip.setSingleLine(true);ip.setTextColor(TEXT);ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);body.addView(ip);
        body.addView(button(getString(R.string.ui_save_address),()->{try{base=Protocol.base(ip.getText().toString());getPreferences(MODE_PRIVATE).edit().putString("base",base).apply();connected=false;media.clear();selected.clear();cache.evictAll();updateStatus();toast(getString(R.string.ui_address_saved));}catch(Exception e){error(e);}}));
        body.addView(button(getString(R.string.ui_choose_wi_fi_network),this::openWifi));body.addView(button(getString(R.string.ui_connect_and_load_card),this::connect));
        Switch protocol=new Switch(this);protocol.setText(getString(R.string.ui_use_legacy_numeric_api_instead_of_newapp));protocol.setTextColor(TEXT);protocol.setChecked(legacy);protocol.setPadding(dp(4),dp(16),dp(4),dp(16));protocol.setOnCheckedChangeListener((v,on)->{legacy=on;getPreferences(MODE_PRIVATE).edit().putBoolean("legacy",on).apply();});body.addView(protocol);
        body.addView(text(getString(R.string.ui_leave_this_off_for_v1_0_0_4_newapp_loads_mode_an),13,MUTED));
        addLanguageSetting(body);
        body.addView(text(getString(R.string.ui_storage),20,TEXT));body.addView(text(getString(R.string.ui_photos_pictures_x10connect_videos_movies_x10conn_80),14,MUTED));
        body.addView(button(getString(R.string.ui_cancel_current_download),()->{if(DownloadService.running)startService(new Intent(this,DownloadService.class).setAction(DownloadService.CANCEL));else toast(getString(R.string.ui_no_download_is_running));}));
        body.addView(text(getString(R.string.export_title),20,TEXT));
        body.addView(text(getString(R.string.export_description),14,MUTED));
        body.addView(button(getString(R.string.export_start),this::startWebExport));
        body.addView(button(getString(R.string.export_cancel),()->{if(WebExportService.running)startService(new Intent(this,WebExportService.class).setAction(WebExportService.CANCEL));else toast(getString(R.string.ui_no_download_is_running));}));
        String latest=getSharedPreferences("webexport",MODE_PRIVATE).getString("latest_name",null);
        String exportStatus=getSharedPreferences("webexport",MODE_PRIVATE).getString("status","");
        if(!exportStatus.isEmpty())body.addView(text(exportStatus,13,MUTED));
        if(latest!=null){body.addView(text(latest+"\nDownloads/X10Connect",13,MUTED));body.addView(button(getString(R.string.export_share),this::shareWebExport));}
        body.addView(text("X10 Connect "+BuildConfig.VERSION_NAME,18,TEXT));body.addView(text(getString(R.string.ui_unofficial_lamax_x10_1_client_android_10_no_acco),13,MUTED));
    }
    private void showSimpleSettings(LinearLayout body){
        body.addView(button(getString(R.string.ui_choose_wi_fi_network),this::openWifi));
        body.addView(button(getString(R.string.ui_connect_and_load_card),this::connect));
        addLanguageSetting(body);
        body.addView(button(getString(R.string.ui_current_mode_settings),()->menu(false)));
        body.addView(button(getString(R.string.ui_sync_date_and_time_with_phone),()->command(getString(R.string.ui_time),"setsystime.cgi?-time="+new java.text.SimpleDateFormat("yyyyMMddHHmmss",Locale.ROOT).format(new Date()),false)));
        body.addView(text(getString(R.string.ui_photos_pictures_x10connect_videos_movies_x10conn_80),14,MUTED));
        body.addView(button(getString(R.string.ui_cancel_current_download),()->{if(DownloadService.running)startService(new Intent(this,DownloadService.class).setAction(DownloadService.CANCEL));}));
        body.addView(text("X10 Connect "+BuildConfig.VERSION_NAME,18,TEXT));
    }
    private void addLanguageSetting(LinearLayout body){
        body.addView(button("Jazyk / Language",()->{
            String[] codes={"system","cs","en"};
            String current=getSharedPreferences("language",MODE_PRIVATE).getString("code","system");
            int selectedLanguage=Arrays.asList(codes).indexOf(current);
            new AlertDialog.Builder(this).setTitle("Jazyk / Language").setSingleChoiceItems(new String[]{getString(R.string.system_language),"Čeština","English"},selectedLanguage,(dialog,index)->{
                getSharedPreferences("language",MODE_PRIVATE).edit().putString("code",codes[index]).apply();dialog.dismiss();recreate();
            }).setNegativeButton(android.R.string.cancel,null).show();
        }));
    }
    private void startWebExport(){
        if(DownloadService.running || WebExportService.running){toast(getString(R.string.export_busy));return;}
        Network network=CameraClient.wifi(this);if(network==null){toast(getString(R.string.ui_connect_to_the_camera_wi_fi_22));return;}
        try{startForegroundService(new Intent(this,WebExportService.class).putExtra("base",base).putExtra("network",network));transfer.setText(getString(R.string.export_preparing));}
        catch(Exception e){error(e);}
    }
    private void shareWebExport(){
        String value=getSharedPreferences("webexport",MODE_PRIVATE).getString("latest_uri",null);if(value==null)return;
        Uri uri=Uri.parse(value);
        try{
            Intent share=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("ZIP",uri));startActivity(Intent.createChooser(share,getString(R.string.export_share)));
        }catch(Exception e){error(e);}
    }
    private void showLog(){
        LinearLayout body=scrollContent();body.addView(text("Diagnostika",25,TEXT));
        body.addView(button(getString(R.string.ui_refresh_log),this::showTab));body.addView(button(getString(R.string.ui_share_log),()->{
            Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,getString(R.string.ui_x10_connect_diagnostics)).putExtra(Intent.EXTRA_TEXT,"X10 Connect "+BuildConfig.VERSION_NAME+" · Android "+Build.VERSION.RELEASE+"\n"+AppLog.read(this));startActivity(Intent.createChooser(share,getString(R.string.ui_share_diagnostics)));
        }));
        body.addView(button(getString(R.string.ui_clear_log),()->{
            try{AppLog.clear(this);showTab();toast(getString(R.string.ui_log_cleared_new_events_will_still_be_recorded));}
            catch(java.io.IOException e){error(e);}
        }));
        body.addView(text(getString(R.string.ui_clearing_removes_existing_history_new_events_wil),12,MUTED));
        TextView log=text(AppLog.read(this),12,MUTED);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);body.addView(log);
    }
}
