package cz.miraficus.x10connect;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class AppLog {
    private AppLog() {}
    public static synchronized void add(Context context, String message) {
        File file = new File(context.getFilesDir(), "diagnostics.txt");
        try {
            String line = new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+"  "+message+"\n";
            if (file.length()>120000) {
                String old = read(context);
                try (FileWriter out = new FileWriter(file)) { out.write(old.substring(Math.max(0,old.length()-60000))); }
            }
            try (FileWriter out = new FileWriter(file,true)) { out.write(line); }
        } catch (IOException ignored) { }
    }
    public static synchronized void clear(Context context) throws IOException {
        File file = new File(context.getFilesDir(), "diagnostics.txt");
        if (file.exists() && !file.delete()) throw new IOException(context.getString(R.string.ui_could_not_clear_the_log));
    }
    public static synchronized String read(Context context) {
        try (FileInputStream in = new FileInputStream(new File(context.getFilesDir(),"diagnostics.txt"))) {
            return new String(CameraClient.readBounded(in,200000), java.nio.charset.StandardCharsets.UTF_8);
        } catch(IOException e) { return context.getString(R.string.ui_no_requests_yet); }
    }
}
