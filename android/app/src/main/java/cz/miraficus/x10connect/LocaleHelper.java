package cz.miraficus.x10connect;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {}
    public static Context wrap(Context context) {
        String code=context.getSharedPreferences("language",Context.MODE_PRIVATE).getString("code","system");
        if(code.equals("system"))code=Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        Locale locale=Locale.forLanguageTag(code.equals("cs") || code.equals("sk")?"cs":"en");
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }
    public static String label(Context context,String value) {
        switch(value){
            case "Galerie":return context.getString(R.string.gallery);
            case "Kamera":return context.getString(R.string.camera);
            case "Nastavení":return context.getString(R.string.settings);
            case "Diagnostika":return context.getString(R.string.diagnostics);
            case "Vše":return context.getString(R.string.all);
            case "Fotky":return context.getString(R.string.photos);
            case "Videa":return context.getString(R.string.videos);
            default:return value;
        }
    }
}
