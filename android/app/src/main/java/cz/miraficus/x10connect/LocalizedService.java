package cz.miraficus.x10connect;
public abstract class LocalizedService extends android.app.Service {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(LocaleHelper.wrap(base));}
}
