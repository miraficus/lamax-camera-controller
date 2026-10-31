package cz.miraficus.x10connect;

import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;
import java.util.*;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public final class PreviewActivity extends LocalizedActivity {
    private VideoView nativeVideo;
    private PlayerView playerView;
    private ExoPlayer player;
    private FrameLayout surface;
    private TextView info;
    private Spinner engine;
    private EditText streamNumber;
    private ConnectivityManager connectivity;
    private Network previous, cameraNetwork;
    private String host;
    private int attempt;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable timeout;
    private final java.util.concurrent.ExecutorService probes=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile java.net.Socket probeSocket;
    private volatile boolean probing, stopped, cancelled;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        connectivity=getSystemService(ConnectivityManager.class);
        previous=connectivity.getBoundNetworkForProcess();
        cameraNetwork=CameraClient.wifi(this);
        if(cameraNetwork==null || !connectivity.bindProcessToNetwork(cameraNetwork)) {
            Toast.makeText(this,getString(R.string.ui_connect_your_phone_to_the_camera_wi_fi),Toast.LENGTH_LONG).show();finish();return;
        }
        try {host=new java.net.URI(Protocol.base(getIntent().getStringExtra("base"))).getHost();}
        catch(Exception e){AppLog.add(this,"RTSP address: "+e);finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0d141c);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        // Controls scroll independently on small and landscape displays; keep video space below.
        ScrollView controls=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);controls.addView(panel);
        root.addView(controls,new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(button(getString(R.string.ui_back_to_app),this::finish));
        engine=new Spinner(this);
        engine.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{getString(R.string.ui_original_player_tested),getString(R.string.ui_media3_tcp_experimental),getString(R.string.ui_media3_udp_automatic_experimental)}));
        engine.setSelection(getPreferences(MODE_PRIVATE).getInt("engine",0));panel.addView(engine);
        engine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){getPreferences(MODE_PRIVATE).edit().putInt("engine",position).apply();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        LinearLayout choices=new LinearLayout(this);
        choices.addView(button(getString(R.string.ui_small_12),()->open("12")),new LinearLayout.LayoutParams(0,-2,1));
        choices.addView(button(getString(R.string.ui_large_11),()->open("11")),new LinearLayout.LayoutParams(0,-2,1));panel.addView(choices);
        LinearLayout custom=new LinearLayout(this);streamNumber=new EditText(this);streamNumber.setText("12");streamNumber.setSingleLine();streamNumber.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);streamNumber.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        custom.addView(streamNumber,new LinearLayout.LayoutParams(0,-2,1));custom.addView(button(getString(R.string.ui_play_number),()->{try{open(StreamCandidates.number(streamNumber.getText().toString()));}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}));panel.addView(custom);
        panel.addView(button(getString(R.string.ui_describe_streams_12_and_11),()->describe(new String[]{"12","11"})));
        panel.addView(button(getString(R.string.ui_scan_streams_1019),()->describe(StreamCandidates.nearby())));
        panel.addView(button(getString(R.string.ui_stop_scan),()->{cancelled=true;closeProbe();}));
        TextView hint=new TextView(this);hint.setTextColor(0xffb8c8d8);hint.setText(getString(R.string.ui_to_try_reducing_stalls_select_media3_tcp_and_tap));panel.addView(hint);
        info=new TextView(this);info.setTextColor(0xffeef4fa);info.setPadding(16,12,16,12);panel.addView(info);
        surface=new FrameLayout(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);root.requestApplyInsets();open(getIntent().getBooleanExtra("high",false)?"11":"12");
    }
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private String url(String stream){return "rtsp://"+host+":554/livestream/"+StreamCandidates.number(stream);}
    private void stopPlayer(){
        if(timeout!=null)handler.removeCallbacks(timeout);
        if(nativeVideo!=null){nativeVideo.stopPlayback();nativeVideo=null;}
        if(playerView!=null){playerView.setPlayer(null);playerView=null;}
        if(player!=null){player.release();player=null;}
        if(surface!=null)surface.removeAllViews();
    }
    private void open(String stream){
        if(probing){Toast.makeText(this,getString(R.string.ui_wait_for_stream_diagnostics_to_finish),Toast.LENGTH_SHORT).show();return;}
        final int generation=++attempt;stopPlayer();streamNumber.setText(stream);
        final long started=SystemClock.elapsedRealtime();final int selectedEngine=engine.getSelectedItemPosition();
        info.setText(getString(R.string.ui_connecting_to_stream)+stream);
        AppLog.add(this,"RTSP start "+url(stream)+" engine="+selectedEngine+" · "+Build.MANUFACTURER+" "+Build.MODEL+" Android "+Build.VERSION.RELEASE);
        timeout=()->{if(generation==attempt){info.setText(getString(R.string.ui_preview_was_not_ready_within_25_seconds_try_anot));AppLog.add(this,"RTSP "+stream+" no ready event after 25 s");}};
        handler.postDelayed(timeout,25000);
        if(selectedEngine==0)openNative(stream,generation,started);else openMedia3(stream,generation,started,selectedEngine==1);
    }
    private void openNative(String stream,int generation,long started){
        VideoView video=new VideoView(this);nativeVideo=video;surface.addView(video,new FrameLayout.LayoutParams(-1,-1));
        video.setOnPreparedListener(mp->{if(generation!=attempt)return;handler.removeCallbacks(timeout);AppLog.add(this,"RTSP "+stream+" native prepared after "+(SystemClock.elapsedRealtime()-started)+" ms");video.start();});
        video.setOnInfoListener((mp,what,extra)->{
            if(generation!=attempt)return false;
            if(what==3){int w=mp.getVideoWidth(),h=mp.getVideoHeight();info.setText("Stream "+stream+" · "+w+" × "+h);AppLog.add(this,"RTSP "+stream+" first frame "+w+"x"+h);}
            if(what==3 || what==701 || what==702)AppLog.add(this,"RTSP "+stream+" native info "+what+"/"+extra);
            return false;
        });
        video.setOnErrorListener((mp,what,extra)->{if(generation==attempt){handler.removeCallbacks(timeout);info.setText(getString(R.string.ui_cannot_play_preview)+what+"/"+extra);AppLog.add(this,"RTSP "+stream+" native error "+what+"/"+extra+" after "+(SystemClock.elapsedRealtime()-started)+" ms");}return true;});
        try{video.setVideoURI(Uri.parse(url(stream)));}catch(Exception e){info.setText(e.getMessage());AppLog.add(this,"RTSP native: "+e);}
    }
    private void openMedia3(String stream,int generation,long started,boolean tcp){
        playerView=new PlayerView(this);playerView.setUseController(false);surface.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        DefaultLoadControl loadControl=new DefaultLoadControl.Builder().setBufferDurationsMs(3000,10000,500,1500).build();
        player=new ExoPlayer.Builder(this).setLoadControl(loadControl).build();playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(generation!=attempt)return;
                AppLog.add(PreviewActivity.this,"RTSP "+stream+" Media3 state="+state+" tcp="+tcp+" after "+(SystemClock.elapsedRealtime()-started)+" ms");
                if(state==Player.STATE_READY){handler.removeCallbacks(timeout);info.setText("Stream "+stream+" · Media3 "+(tcp?"TCP":"UDP / auto"));}
            }
            @Override public void onVideoSizeChanged(VideoSize size){if(generation==attempt){info.setText("Stream "+stream+" · "+size.width+" × "+size.height);AppLog.add(PreviewActivity.this,"RTSP "+stream+" Media3 video "+size.width+"x"+size.height);}}
            @Override public void onRenderedFirstFrame(){if(generation==attempt)AppLog.add(PreviewActivity.this,"RTSP "+stream+" Media3 first frame after "+(SystemClock.elapsedRealtime()-started)+" ms");}
            @Override public void onPlayerError(PlaybackException e){if(generation==attempt){handler.removeCallbacks(timeout);info.setText(getString(R.string.ui_cannot_play_preview)+e.getErrorCodeName());AppLog.add(PreviewActivity.this,"RTSP "+stream+" Media3 error "+e.getErrorCodeName()+" "+e+" cause="+e.getCause());}}
        });
        try{
            RtspMediaSource source=new RtspMediaSource.Factory().setForceUseRtpTcp(tcp).setTimeoutMs(8000).setSocketFactory(cameraNetwork.getSocketFactory()).setUserAgent("X10Connect/"+BuildConfig.VERSION_NAME).createMediaSource(MediaItem.fromUri(url(stream)));
            player.setMediaSource(source);player.prepare();player.play();
        }catch(Exception e){handler.removeCallbacks(timeout);info.setText(e.getMessage());AppLog.add(this,"RTSP Media3: "+e);}
    }
    private void closeProbe(){java.net.Socket socket=probeSocket;if(socket!=null)try{socket.close();}catch(java.io.IOException ignored){}}
    private void describe(String[] streams){
        if(probing)return;
        probing=true;cancelled=false;final int generation=++attempt;stopPlayer();
        info.setText(getString(R.string.ui_preview_stopped_scanning_available_streams));
        probes.execute(()->{
            List<String> found=new ArrayList<>();
            try{
                for(String stream:streams){
                    if(stopped || cancelled)break;
                    handler.post(()->{if(!stopped && generation==attempt)info.setText(getString(R.string.ui_describing_stream)+stream);});
                    try(java.net.Socket socket=cameraNetwork.getSocketFactory().createSocket()){
                        probeSocket=socket;if(stopped || cancelled)break;
                        socket.connect(new java.net.InetSocketAddress(host,554),2000);socket.setSoTimeout(2000);
                        String request="DESCRIBE "+url(stream)+" RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: X10Connect/"+BuildConfig.VERSION_NAME+"\r\n\r\n";
                        socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();
                        RtspResponse response=RtspResponse.read(socket.getInputStream());
                        AppLog.add(this,"RTSP DESCRIBE "+stream+"\n"+response.diagnostic());
                        if(response.status.matches("RTSP/1\\.[01] 200( .*)?") && response.body.contains("m=video"))found.add(stream);
                    }catch(Exception e){if(!cancelled && !stopped)AppLog.add(this,"RTSP DESCRIBE "+stream+": "+e);}
                    finally{probeSocket=null;}
                }
            }finally{
                probing=false;
                String summary=found.isEmpty()?getString(R.string.ui_no_video_stream_confirmed):getString(R.string.ui_available_streams)+android.text.TextUtils.join(", ",found);
                AppLog.add(this,"RTSP scan complete: "+found+" cancelled="+cancelled);
                handler.post(()->{if(!stopped && generation==attempt)info.setText(summary+"\n"+getString(R.string.ui_details_are_in_diagnostics_enter_a_stream_number));});
            }
        });
    }
    @Override public void onStop(){
        stopped=true;cancelled=true;++attempt;closeProbe();probes.shutdownNow();handler.removeCallbacksAndMessages(null);stopPlayer();
        if(connectivity!=null)connectivity.bindProcessToNetwork(previous);
        super.onStop();finish();
    }
}
