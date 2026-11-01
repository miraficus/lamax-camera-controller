package cz.miraficus.x10connect;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.MediaStore;
import android.database.Cursor;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground export to Downloads. Only an explicit user action starts a snapshot. */
public final class WebExportService extends LocalizedService {
    public static volatile boolean running;
    public static final String CANCEL="cancel";
    private static final int ID=20;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile HttpURLConnection active;
    private NotificationManager manager;
    private long lastUpdate;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){
        super.onCreate();manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("webexport",getString(R.string.export_title),NotificationManager.IMPORTANCE_LOW));
        String pending=getSharedPreferences("webexport",MODE_PRIVATE).getString("pending",null);
        if(pending!=null){
            try(Cursor row=getContentResolver().query(Uri.parse(pending),new String[]{MediaStore.MediaColumns.IS_PENDING},null,null,null)){
                if(row!=null && row.moveToFirst() && row.getInt(0)==1)getContentResolver().delete(Uri.parse(pending),null,null);
            }catch(Exception ignored){}
            getSharedPreferences("webexport",MODE_PRIVATE).edit().remove("pending").apply();
        }
        File directory=new File(getCacheDir(),"web-export");File[] leftovers=directory.listFiles();
        if(leftovers!=null)for(File file:leftovers)if(file.isFile())file.delete();
    }
    private Notification notification(String text,int percent){
        PendingIntent open=PendingIntent.getActivity(this,20,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,21,new Intent(this,WebExportService.class).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"webexport").setSmallIcon(R.drawable.ic_camera).setContentTitle(getString(R.string.export_title)).setContentText(text).setContentIntent(open)
                .setOnlyAlertOnce(true).setOngoing(true).setProgress(100,Math.max(0,percent),percent<0)
                .addAction(new Notification.Action.Builder(null,getString(R.string.ui_cancel),stop).build()).build();
    }
    private void update(String text,int percent,boolean finished){
        getSharedPreferences("webexport",MODE_PRIVATE).edit().putString("status",text).apply();
        sendBroadcast(new Intent(DownloadService.EVENT).setPackage(getPackageName()).putExtra("text",text).putExtra("percent",percent).putExtra("finished",finished).putExtra("webexport",true));
        if(!finished && (Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED))manager.notify(ID,notification(text,percent));
    }
    private void cancel(){cancelled.set(true);HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(CANCEL.equals(intent.getAction())){cancel();if(!running)stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        if(DownloadService.running){update(getString(R.string.export_busy),-1,true);stopSelf();return START_NOT_STICKY;}
        running=true;cancelled.set(false);startForeground(ID,notification(getString(R.string.export_preparing),-1));
        String base=intent.getStringExtra("base");Network network=intent.getParcelableExtra("network");
        new Thread(()->export(base,network),"camera-web-export").start();return START_NOT_STICKY;
    }
    private void export(String base,Network network){
        Uri target=null;
        try{
            CameraClient camera=new CameraClient(this,base,network);
            String filename="LAMAX-web-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".zip";
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/X10Connect");values.put(MediaStore.MediaColumns.IS_PENDING,1);
            target=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(target==null)throw new IOException("Cannot create ZIP in Downloads");
            getSharedPreferences("webexport",MODE_PRIVATE).edit().putString("pending",target.toString()).apply();
            WebExporter.Transport transport=path->{
                HttpURLConnection connection=camera.open(path);active=connection;
                try{
                    if(cancelled.get())throw new InterruptedIOException("Export cancelled");
                    int status=connection.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);
                    return new WebExporter.Response(connection.getInputStream(),connection.getContentLengthLong(),connection.getContentType(),connection.getLastModified(),()->{connection.disconnect();if(active==connection)active=null;});
                }catch(IOException e){connection.disconnect();if(active==connection)active=null;throw e;}
            };
            WebExporter.Result result;
            try(OutputStream out=getContentResolver().openOutputStream(target,"w")){
                if(out==null)throw new IOException("Cannot open ZIP output");
                result=new WebExporter(transport,new File(getCacheDir(),"web-export"),cancelled,(done,total,path,bytes)->{
                    long now=SystemClock.elapsedRealtime();if(now-lastUpdate<400)return;lastUpdate=now;
                    String text=total==0?getString(R.string.export_listing)+path:getString(R.string.export_progress,done,total,path);
                    update(text,total==0?-1:(int)(done*100L/total),false);
                },WebExporter.Limits.defaults()).export(base,out);
            }
            if(cancelled.get())throw new InterruptedIOException("Export cancelled");
            if(result.errors>0)filename=filename.replace(".zip","-partial.zip");
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);getContentResolver().update(target,values,null,null);
            getSharedPreferences("webexport",MODE_PRIVATE).edit().putString("latest_uri",target.toString()).putString("latest_name",filename).remove("pending").apply();target=null;
            String text=getString(result.errors==0?R.string.export_complete:R.string.export_partial,result.saved,result.errors);
            AppLog.add(this,"Web export: "+result.saved+" files, "+result.bytes+" bytes, "+result.errors+" errors");update(text,100,true);
        }catch(Exception e){
            AppLog.add(this,"Web export: "+e);update(cancelled.get()?getString(R.string.export_cancelled):getString(R.string.export_failed)+e.getMessage(),-1,true);
        }finally{
            if(target!=null)try{getContentResolver().delete(target,null,null);}catch(Exception ignored){}
            getSharedPreferences("webexport",MODE_PRIVATE).edit().remove("pending").apply();
            active=null;running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    @Override public void onTimeout(int startId,int type){cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
}
