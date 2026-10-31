package cz.miraficus.x10connect;

import android.app.Activity;
import android.net.*;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.*;

/** Native Android RTSP player. Camera/codec support is to be tested on device. */
public final class PreviewActivity extends Activity {
    private VideoView video;
    private ConnectivityManager connectivity;
    private Network previous;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        connectivity=getSystemService(ConnectivityManager.class);
        previous=connectivity.getBoundNetworkForProcess();
        Network wifi=CameraClient.wifi(this);
        if(wifi==null || !connectivity.bindProcessToNetwork(wifi)) {finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0d141c);
        Button back=new Button(this);back.setText("Zpět · živý náhled (test)");back.setOnClickListener(v->finish());root.addView(back);
        TextView info=new TextView(this);info.setTextColor(0xffeef4fa);info.setPadding(24,16,24,16);info.setText("Připojuji RTSP…");root.addView(info);
        video=new VideoView(this);root.addView(video,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);
        try {
            String host=new java.net.URI(Protocol.base(getIntent().getStringExtra("base"))).getHost();
            String stream=getIntent().getBooleanExtra("high",false)?"11":"12";
            String url="rtsp://"+host+":554/livestream/"+stream;
            AppLog.add(this,"RTSP "+url);
            video.setOnPreparedListener(mp->{info.setText("Živý náhled · "+stream);video.start();});
            video.setOnErrorListener((mp,what,extra)->{info.setText("Náhled nelze přehrát ("+what+"/"+extra+"). Zkus druhý stream.");AppLog.add(this,"RTSP error "+what+"/"+extra);return true;});
            video.setVideoURI(Uri.parse(url));
        } catch(Exception e){info.setText(e.getMessage());}
    }
    @Override public void onStop(){super.onStop();if(video!=null)video.stopPlayback();if(connectivity!=null)connectivity.bindProcessToNetwork(previous);finish();}
}
