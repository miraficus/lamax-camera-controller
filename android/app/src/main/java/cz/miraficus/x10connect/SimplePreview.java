package cz.miraficus.x10connect;

import android.content.Context;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

/** Small, lifecycle-owned TCP preview. No process-wide network binding. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class SimplePreview {
    private final Context context;
    private final FrameLayout root;
    private final PlayerView video;
    private final TextView status;
    private TextView live;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private Runnable timeout;
    SimplePreview(Context context){
        this.context=context;
        root=new FrameLayout(context);root.setBackground(UiStyle.shape(context,0xff000000,22,false));root.setClipToOutline(true);
        video=(PlayerView)android.view.LayoutInflater.from(context).inflate(R.layout.simple_preview_player,root,false);video.setUseController(false);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(context);status.setGravity(Gravity.CENTER);status.setTextSize(13);status.setPadding(UiStyle.dp(context,20),UiStyle.dp(context,48),UiStyle.dp(context,20),UiStyle.dp(context,12));status.setTextColor(0xffedf3fa);status.setBackgroundColor(0xb0000000);
        root.addView(status,new FrameLayout.LayoutParams(-1,-1));
    }
    void controls(Runnable retry,Runnable expand){
        live=new TextView(context);live.setText(R.string.simple_live);live.setTextColor(UiStyle.TEXT);live.setTextSize(11);live.setCompoundDrawablesRelative(UiStyle.icon(context,"dot",UiStyle.ACCENT,10),null,null,null);live.setCompoundDrawablePadding(UiStyle.dp(context,5));
        live.setPadding(UiStyle.dp(context,10),UiStyle.dp(context,7),UiStyle.dp(context,10),UiStyle.dp(context,7));live.setBackground(UiStyle.shape(context,0xdd0d141c,18,false));live.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);lp.setMargins(UiStyle.dp(context,12),UiStyle.dp(context,12),0,0);root.addView(live,lp);
        android.widget.Button refresh=UiStyle.iconButton(context,context.getString(R.string.preview_retry),"refresh",retry);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(UiStyle.dp(context,48),UiStyle.dp(context,48),Gravity.TOP|Gravity.END);rp.setMargins(0,UiStyle.dp(context,8),UiStyle.dp(context,8),0);root.addView(refresh,rp);
        if(expand!=null){android.widget.Button full=UiStyle.iconButton(context,context.getString(R.string.simple_expand),"expand",expand);
            FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(UiStyle.dp(context,48),UiStyle.dp(context,48),Gravity.BOTTOM|Gravity.END);fp.setMargins(0,0,UiStyle.dp(context,8),UiStyle.dp(context,8));root.addView(full,fp);}
    }
    View view(){return root;}
    void message(String value){stop();status.setText(value);status.setVisibility(View.VISIBLE);}
    void start(String base,Network network){
        stop();status.setText(R.string.preview_loading);status.setVisibility(View.VISIBLE);
        try{
            String host=java.net.URI.create(Protocol.base(base)).getHost();
            DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(3000,10000,500,1500).build();
            ExoPlayer active=new ExoPlayer.Builder(context).setLoadControl(load).build();player=active;video.setPlayer(active);
            active.addListener(new Player.Listener(){
                @Override public void onIsPlayingChanged(boolean playing){if(player==active && live!=null)live.setVisibility(playing?View.VISIBLE:View.GONE);}
                @Override public void onRenderedFirstFrame(){if(player!=active)return;clearTimeout();status.setVisibility(View.GONE);AppLog.add(context,"Simple preview 12 first frame (Media3 TCP)");}
                @Override public void onPlayerError(PlaybackException error){if(player!=active)return;AppLog.add(context,"Simple preview: "+error.getErrorCodeName()+" "+error);message(context.getString(R.string.preview_error));}
            });
            RtspMediaSource source=new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8000)
                .setSocketFactory(network.getSocketFactory()).setUserAgent("X10Connect/"+BuildConfig.VERSION_NAME)
                .createMediaSource(MediaItem.fromUri("rtsp://"+host+":554/livestream/12"));
            active.setMediaSource(source);active.prepare();active.play();
            timeout=()->{if(player==active)message(context.getString(R.string.preview_error));};handler.postDelayed(timeout,25000);
        }catch(Exception error){AppLog.add(context,"Simple preview: "+error);message(context.getString(R.string.preview_error));}
    }
    private void clearTimeout(){if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}}
    void stop(){clearTimeout();if(live!=null)live.setVisibility(View.GONE);ExoPlayer old=player;player=null;video.setPlayer(null);if(old!=null)old.release();}
}
