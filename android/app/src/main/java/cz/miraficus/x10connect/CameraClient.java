package cz.miraficus.x10connect;

import android.content.Context;
import android.net.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CameraClient {
    private final Context context;
    public final String base;
    public final Network network;
    public CameraClient(Context context, String base, Network network) {
        this.context=context.getApplicationContext(); this.base=Protocol.base(base); this.network=network;
    }
    public static Network wifi(Context context) {
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        for (Network n:manager.getAllNetworks()) {
            NetworkCapabilities cap=manager.getNetworkCapabilities(n);
            if(cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return n;
        }
        return null;
    }
    public HttpURLConnection open(String relative) throws IOException {
        if(network==null) throw new IOException("Připoj telefon k Wi-Fi kamery a klepni na Připojit.");
        URL url=new URL(base+relative);
        HttpURLConnection c=(HttpURLConnection)network.openConnection(url, java.net.Proxy.NO_PROXY);
        c.setConnectTimeout(6000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept-Encoding","identity"); c.setRequestProperty("User-Agent","X10Connect/0.1");
        return c;
    }
    public String get(String endpoint) throws Exception {
        HttpURLConnection c=open(Protocol.CGI+endpoint);
        try {
            int status=c.getResponseCode();
            String raw;
            try(InputStream in=status>=400?c.getErrorStream():c.getInputStream()) { raw=new String(readBounded(in,4*1024*1024),StandardCharsets.UTF_8); }
            AppLog.add(context,"GET "+endpoint+" → HTTP "+status+"\n"+raw.substring(0,Math.min(raw.length(),5000)));
            if(status!=200) throw new IOException("HTTP "+status+": "+raw.substring(0,Math.min(raw.length(),180)));
            Protocol.checkResult(raw);
            return raw;
        } catch(Exception e) { AppLog.add(context,endpoint+" → "+e); throw e; }
        finally { c.disconnect(); }
    }
    static byte[] readBounded(InputStream in,int limit) throws IOException {
        if(in==null) return new byte[0];
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n;
        while((n=in.read(b))!=-1) { if(out.size()+n>limit)throw new IOException("Odpověď je příliš velká"); out.write(b,0,n); }
        return out.toByteArray();
    }
    public byte[] image(String path) throws Exception {
        HttpURLConnection c=open(Protocol.encodedPath(path));
        try {
            if(c.getResponseCode()!=200) throw new IOException("Náhled není dostupný");
            try(InputStream in=c.getInputStream()) { return readBounded(in,25*1024*1024); }
        } finally { c.disconnect(); }
    }
    public List<Protocol.Media> list() throws Exception {
        String raw=get("getfilecount.cgi?");
        int count=Integer.parseInt(Protocol.vars(raw).getOrDefault("count","-1"));
        if(count<0 || count>100000) throw new IOException("Neplatný počet souborů: "+raw);
        LinkedHashMap<String,Protocol.Media> all=new LinkedHashMap<>();
        for(int start=1;start<=count;start+=100) {
            List<Protocol.Media> page=Protocol.parseFiles(get("getfilelistinfoios.cgi?&-start="+start+"&-end="+Math.min(count,start+99)));
            if(page.isEmpty())throw new IOException("Kamera vrátila prázdnou stránku. Obnov seznam.");
            for(Protocol.Media m:page) {
                if(!m.path.endsWith(".THM") && !m.path.endsWith(".LRV")) all.put(m.path,m);
            }
        }
        AppLog.add(context,"Seznam: kamera hlásí "+count+", hlavních médií "+all.size());
        return new ArrayList<>(all.values());
    }
    public interface Progress { void update(long bytes,long total); }
    public void transfer(Protocol.Media media,OutputStream out,AtomicBoolean cancel,Progress progress) throws Exception {
        HttpURLConnection c=open(Protocol.encodedPath(media.path));
        long done=0;
        try {
            if(c.getResponseCode()!=200)throw new IOException("Stažení: HTTP "+c.getResponseCode());
            long length=c.getContentLengthLong();
            try(InputStream in=c.getInputStream()) {
                byte[] buffer=new byte[131072]; int n;
                while((n=in.read(buffer))!=-1) {
                    if(cancel.get())throw new IOException("Stahování zrušeno");
                    out.write(buffer,0,n); done+=n; progress.update(done,media.size);
                }
            }
            if(cancel.get())throw new IOException("Stahování zrušeno");
            if(done!=media.size || (length>=0 && done!=length))throw new IOException("Nesouhlasí velikost: "+done+" / "+media.size);
            out.flush(); AppLog.add(context,"Staženo "+media.path+" ("+done+" B)");
        } finally { c.disconnect(); }
    }
}
