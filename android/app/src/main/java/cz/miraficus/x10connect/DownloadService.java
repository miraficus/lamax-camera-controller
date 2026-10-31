package cz.miraficus.x10connect;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.provider.MediaStore;
import org.json.JSONArray;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground queue; pending MediaStore items are removed on cancellation or failure. */
public final class DownloadService extends Service {
    public static final String EVENT="cz.miraficus.x10connect.DOWNLOAD";
    public static final String CANCEL="cancel";
    public static volatile boolean running;
    private static final int ID=10;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private NotificationManager manager;
    private long lastUpdate;
    @Override public void onCreate() {
        super.onCreate(); manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("downloads","Stahování z kamery",NotificationManager.IMPORTANCE_LOW));
        String orphan=getSharedPreferences("transfer",MODE_PRIVATE).getString("pending",null);
        if(orphan!=null) {
            try(Cursor row=getContentResolver().query(Uri.parse(orphan),new String[]{MediaStore.MediaColumns.IS_PENDING},null,null,null)){
                if(row!=null && row.moveToFirst() && row.getInt(0)==1)getContentResolver().delete(Uri.parse(orphan),null,null);
            }catch(Exception ignored){}
            getSharedPreferences("transfer",MODE_PRIVATE).edit().remove("pending").apply();
        }
    }
    @Override public IBinder onBind(Intent i) { return null; }
    private Notification notification(String text,int percent) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("X10 Connect").setContentText(text).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(100,Math.max(0,percent),percent<0)
                .addAction(new Notification.Action.Builder(null,"Zrušit",stop).build()).build();
    }
    private void update(String text,int percent,boolean finished) {
        sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("text",text).putExtra("percent",percent).putExtra("finished",finished));
        if(!finished && (Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED))
            manager.notify(ID,notification(text,percent));
        getSharedPreferences("transfer",MODE_PRIVATE).edit().putString("status",text).apply();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(CANCEL.equals(intent.getAction())) { cancelled.set(true); if(!running)stopSelf(); return START_NOT_STICKY; }
        if(running)return START_NOT_STICKY;
        running=true;cancelled.set(false);
        startForeground(ID,notification("Připravuji stahování…",-1));
        String base=intent.getStringExtra("base"), json=intent.getStringExtra("files");
        Network network=intent.getParcelableExtra("network");
        new Thread(()->runQueue(base,json,network),"camera-download").start();
        return START_NOT_STICKY;
    }
    private void runQueue(String base,String json,Network network) {
        int saved=0,skipped=0,failed=0;
        try {
            List<Protocol.Media> list=Protocol.parseFiles(json);
            CameraClient camera=new CameraClient(this,base,network);
            for(int i=0;i<list.size() && !cancelled.get();i++) {
                Protocol.Media media=list.get(i); final int index=i+1;
                String key=base+"|"+media.path+"|"+media.size;
                String existing=getSharedPreferences("saved",MODE_PRIVATE).getString(key,null);
                if(existing!=null && readable(Uri.parse(existing),media.size)) { skipped++; continue; }
                Uri target=null;
                try {
                    ContentValues values=new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME,media.name());
                    values.put(MediaStore.MediaColumns.MIME_TYPE,media.mime());
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,media.video()?"Movies/X10Connect":"Pictures/X10Connect");
                    values.put(MediaStore.MediaColumns.IS_PENDING,1);
                    Uri collection=media.video()?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    target=getContentResolver().insert(collection,values);
                    if(target==null)throw new IOException("Nelze vytvořit soubor v telefonu");
                    getSharedPreferences("transfer",MODE_PRIVATE).edit().putString("pending",target.toString()).apply();
                    update(index+"/"+list.size()+"  "+media.name(),0,false);
                    try(OutputStream out=getContentResolver().openOutputStream(target,"w")) {
                        if(out==null)throw new IOException("Nelze otevřít cílový soubor");
                        camera.transfer(media,out,cancelled,(bytes,total)->{
                            long now=SystemClock.elapsedRealtime();
                            if(now-lastUpdate>400) {
                                lastUpdate=now;
                                int percent=total==0?100:(int)Math.min(100,bytes*100.0/total);
                                update(index+"/"+list.size()+"  "+media.name()+" · "+percent+" %",percent,false);
                            }
                        });
                    }
                    values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
                    getContentResolver().update(target,values,null,null);
                    getSharedPreferences("saved",MODE_PRIVATE).edit().putString(key,target.toString()).apply();
                    target=null;saved++;
                } catch(Exception e) {
                    failed++;AppLog.add(this,"Stažení "+media.name()+": "+e);
                    update("Chyba: "+media.name()+" · "+e.getMessage(),-1,false);
                } finally {
                    if(target!=null)try{getContentResolver().delete(target,null,null);}catch(Exception ignored){}
                    getSharedPreferences("transfer",MODE_PRIVATE).edit().remove("pending").apply();
                }
            }
            String result=(cancelled.get()?"Zrušeno. ":"Hotovo. ")+"Uloženo "+saved+", přeskočeno "+skipped+", chyby "+failed+".";
            AppLog.add(this,result);update(result,100,true);
        } catch(Exception e) {AppLog.add(this,"Fronta: "+e);update("Stahování selhalo: "+e.getMessage(),-1,true);}
        finally {running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }
    private boolean readable(Uri uri,long size) {
        try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")) {return fd!=null && fd.getStatSize()==size;}
        catch(Exception ignored){return false;}
    }
    @Override public void onTimeout(int startId,int fgsType) {
        cancelled.set(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
}
