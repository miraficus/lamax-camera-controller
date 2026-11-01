package cz.miraficus.x10connect;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Fullscreen version of the simple preview, with no developer controls. */
public final class FullPreviewActivity extends LocalizedActivity {
    private SimplePreview preview;
    private String base;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);base=getIntent().getStringExtra("base");
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(UiStyle.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        preview=new SimplePreview(this);preview.controls(this::play,null);root.addView(preview.view(),new FrameLayout.LayoutParams(-1,-1));
        Button back=UiStyle.iconButton(this,getString(R.string.ui_back_to_app),"back",this::finish);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(UiStyle.dp(this,48),UiStyle.dp(this,48),Gravity.BOTTOM|Gravity.END);bp.setMargins(12,12,12,12);root.addView(back,bp);
        setContentView(root);root.requestApplyInsets();
    }
    @Override public void onStart(){super.onStart();play();}
    private void play(){android.net.Network network=CameraClient.wifi(this);
        if(network==null){preview.message(getString(R.string.ui_connect_to_the_camera_wi_fi));return;}
        if(WebExportService.running){preview.message(getString(R.string.export_busy));return;}preview.start(base,network);
    }
    @Override public void onStop(){preview.stop();super.onStop();}
}
