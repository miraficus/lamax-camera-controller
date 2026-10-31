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
        info = new TextView(this); info.setTextColor(0xffeef4fa); info.setPadding(24, 16, 24, 16); root.addView(info);
        video = new VideoView(this);
        root.addView(video, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root); root.requestApplyInsets();
        open(getIntent().getBooleanExtra("high", false));
    }

    private void open(boolean larger) {
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
            info.setText("Stream " + stream + " · " + width + " × " + height);
            AppLog.add(this, "RTSP " + stream + " prepared " + width + "x" + height + " za " + (SystemClock.elapsedRealtime() - started) + " ms");
            try {
                for (android.media.MediaPlayer.TrackInfo track : player.getTrackInfo())
                    AppLog.add(this, "RTSP " + stream + " track " + track.getTrackType() + " · " + track.getFormat());
            } catch (Exception e) { AppLog.add(this, "RTSP " + stream + " metadata: " + e); }
            video.start();
        });
        video.setOnInfoListener((player, what, extra) -> {
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

    @Override public void onStop() {
        ++attempt; handler.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback();
        if (connectivity != null) connectivity.bindProcessToNetwork(previous);
        super.onStop(); finish();
    }
}
