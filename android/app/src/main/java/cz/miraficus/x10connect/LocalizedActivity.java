package cz.miraficus.x10connect;
public abstract class LocalizedActivity extends android.app.Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(LocaleHelper.wrap(base));}
}
