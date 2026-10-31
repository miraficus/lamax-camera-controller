package cz.miraficus.x10connect;

import android.app.Activity;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Native RTSP preview; stream 12 is device-tested, stream 11 remains experimental. */
public final class PreviewActivity extends Activity {
    private VideoView video;
    private TextView info;
    private ConnectivityManager connectivity;
    private Network previous;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempt;
    private boolean high;
    private String host;
    private Runnable timeout;
    private Network cameraNetwork;
    private final java.util.concurrent.ExecutorService probes=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile java.net.Socket probeSocket;
    private volatile boolean probing, stopped;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        connectivity = getSystemService(ConnectivityManager.class);
        previous = connectivity.getBoundNetworkForProcess();
        Network wifi = CameraClient.wifi(this);
        if (wifi == null || !connectivity.bindProcessToNetwork(wifi)) {
            AppLog.add(this, "RTSP: nelze připojit přehrávač k Wi-Fi");
            Toast.makeText(this, "Připoj telefon k Wi-Fi kamery.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        cameraNetwork=wifi;
        try { host = new java.net.URI(Protocol.base(getIntent().getStringExtra("base"))).getHost(); }
        catch (Exception e) { AppLog.add(this, "RTSP adresa: " + e); finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff0d141c);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        Button back = new Button(this); back.setText("Zpět do aplikace"); back.setOnClickListener(v -> finish()); root.addView(back);
        LinearLayout choices = new LinearLayout(this);
        Button small = new Button(this); small.setText("Menší · ověřený"); small.setOnClickListener(v -> open(false));
        Button large = new Button(this); large.setText("Větší · test"); large.setOnClickListener(v -> open(true));
        choices.addView(small, new LinearLayout.LayoutParams(0, -2, 1));
        choices.addView(large, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(choices);
        Button describe=new Button(this); describe.setText("Zjistit parametry obou streamů"); describe.setOnClickListener(v -> describeStreams()); root.addView(describe);
        info = new TextView(this); info.setTextColor(0xffeef4fa); info.setPadding(24, 16, 24, 16); root.addView(info);
        video = new VideoView(this);
        root.addView(video, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root); root.requestApplyInsets();
        open(getIntent().getBooleanExtra("high", false));
    }

    private void open(boolean larger) {
        if(probing){Toast.makeText(this,"Počkej na dokončení diagnostiky streamů.",Toast.LENGTH_SHORT).show();return;}
        final int current = ++attempt;
        high = larger;
        if (timeout != null) handler.removeCallbacks(timeout);
        video.stopPlayback();
        final String stream = high ? "11" : "12";
        final String url = "rtsp://" + host + ":554/livestream/" + stream;
        final long started = SystemClock.elapsedRealtime();
        info.setText("Připojuji stream " + stream + (high ? " · neověřený větší náhled" : " · menší náhled"));
        AppLog.add(this, "RTSP start " + url + " · " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        timeout = () -> {
            if (current != attempt) return;
            info.setText("Stream " + stream + ": přehrávač se nepřipravil do 25 s. Můžeš zkusit menší náhled.");
            AppLog.add(this, "RTSP " + stream + ": bez onPrepared po 25 s; příčina není známá");
        };
        handler.postDelayed(timeout, 25000);
        video.setOnPreparedListener(player -> {
            if (current != attempt) return;
            handler.removeCallbacks(timeout);
            int width = player.getVideoWidth(), height = player.getVideoHeight();
            info.setText("Stream " + stream + (width>0 && height>0 ? " · " + width + " × " + height : " · přehrávač zatím nehlásí rozměry"));
            AppLog.add(this, "RTSP " + stream + " prepared " + width + "x" + height + " za " + (SystemClock.elapsedRealtime() - started) + " ms");
            try {
                for (android.media.MediaPlayer.TrackInfo track : player.getTrackInfo())
                    AppLog.add(this, "RTSP " + stream + " track " + track.getTrackType() + " · " + track.getFormat());
            } catch (Exception e) { AppLog.add(this, "RTSP " + stream + " metadata: " + e); }
            video.start();
        });
        video.setOnInfoListener((player, what, extra) -> {
            if(current==attempt && what==3){
                int width=player.getVideoWidth(), height=player.getVideoHeight();
                if(width>0 && height>0)info.setText("Stream "+stream+" · "+width+" × "+height);
                AppLog.add(this,"RTSP "+stream+" rozměry při prvním snímku "+width+"x"+height);
            }
            if (current == attempt && (what == 3 || what == 701 || what == 702))
                AppLog.add(this, "RTSP " + stream + " info " + what + "/" + extra + (what == 3 ? " · první video snímek" : " · buffering"));
            return false;
        });
        video.setOnErrorListener((player, what, extra) -> {
            if (current != attempt) return true;
            handler.removeCallbacks(timeout);
            info.setText("Stream " + stream + " nelze přehrát (" + what + "/" + extra + "). Zkus menší náhled; podrobnosti jsou v diagnostice.");
            AppLog.add(this, "RTSP " + stream + " error what=" + what + " extra=" + extra + " za " + (SystemClock.elapsedRealtime() - started) + " ms · " + url);
            return true;
        });
        try { video.setVideoURI(Uri.parse(url)); }
        catch (Exception e) {
            handler.removeCallbacks(timeout); info.setText("RTSP " + stream + ": " + e.getMessage());
            AppLog.add(this, "RTSP " + stream + " výjimka: " + e);
        }
    }

    private void describeStreams() {
        if(probing)return;
        probing=true; ++attempt;
        if(timeout!=null)handler.removeCallbacks(timeout);
        video.stopPlayback();
        info.setText("Náhled zastaven. Zjišťuji popis streamů 12 a 11…");
        probes.execute(() -> {
            for(String stream:new String[]{"12","11"}) {
                if(stopped)break;
                String url="rtsp://"+host+":554/livestream/"+stream;
                try(java.net.Socket socket=cameraNetwork.getSocketFactory().createSocket()) {
                    probeSocket=socket;
                    if(stopped)break;
                    socket.connect(new java.net.InetSocketAddress(host,554),4000);
                    socket.setSoTimeout(4000);
                    // DESCRIBE only: no SETUP, PLAY, recording or settings changes.
                    String request="DESCRIBE "+url+" RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: X10Connect/"+BuildConfig.VERSION_NAME+"\r\n\r\n";
                    socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    RtspResponse response=RtspResponse.read(socket.getInputStream());
                    AppLog.add(this,"RTSP DESCRIBE "+stream+"\n"+response.diagnostic());
                } catch(Exception e) { AppLog.add(this,"RTSP DESCRIBE "+stream+": "+e); }
                finally { probeSocket=null; }
            }
            probing=false;
            handler.post(() -> { if(!stopped)info.setText("Parametry jsou v Diagnostice. Pro přehrávání znovu zvol menší nebo větší stream."); });
        });
    }

    @Override public void onStop() {
        stopped=true;
        java.net.Socket socket=probeSocket;
        if(socket!=null)try{socket.close();}catch(java.io.IOException ignored){}
        probes.shutdownNow();
        ++attempt; handler.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback();
        if (connectivity != null) connectivity.bindProcessToNetwork(previous);
        super.onStop(); finish();
    }
}
