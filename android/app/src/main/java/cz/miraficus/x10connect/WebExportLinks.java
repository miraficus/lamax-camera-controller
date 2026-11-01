package cz.miraficus.x10connect;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Accept only direct, static children of a thttpd-style directory listing. */
public final class WebExportLinks {
    private WebExportLinks(){}
    private static final Pattern LINK=Pattern.compile("(?is)<a\\b[^>]*\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Set<String> EXCLUDED=new HashSet<>(Arrays.asList("sd","sdcard","mnt","media","storage","proc","sys","dev","cgi-bin"));
    public static final class Entry {
        public final String path; public final boolean directory;
        Entry(String path,boolean directory){this.path=path;this.directory=directory;}
    }
    public static boolean listing(String html){return Pattern.compile("(?is)<(?:h[1-6]|title)\\b[^>]*>\\s*Index\\s+of\\b").matcher(html).find();}
    public static String path(String value){
        if(!value.startsWith("/") || value.startsWith("//") || value.contains("\\") || value.contains("%") || value.contains("?") || value.contains("#") || value.indexOf(':')>=0 || value.chars().anyMatch(c->c<32 || c==127))throw new IllegalArgumentException("Invalid export path");
        String[] parts=value.substring(1).split("/",-1);
        for(int i=0;i<parts.length;i++)if(parts[i].equals(".") || parts[i].equals("..") || (parts[i].isEmpty() && i<parts.length-1))throw new IllegalArgumentException("Invalid export path segment");
        if(parts.length>0 && EXCLUDED.contains(parts[0].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Excluded storage/system path");
        for(String part:parts)if(part.equalsIgnoreCase("cgi-bin") || part.toLowerCase(Locale.ROOT).endsWith(".cgi"))throw new IllegalArgumentException("CGI is not a static file");
        return value;
    }
    public static String requestPath(String decoded){
        path(decoded);
        try{return new URI(null,null,decoded,null).toASCIIString();}
        catch(java.net.URISyntaxException e){throw new IllegalArgumentException(e);}
    }
    public static List<Entry> parse(String base,String directory,String html){
        path(directory);if(!directory.endsWith("/"))throw new IllegalArgumentException("Not a directory");
        URI origin=URI.create(Protocol.base(base)),parent=origin.resolve(requestPath(directory));
        LinkedHashMap<String,Entry> result=new LinkedHashMap<>();
        Matcher matcher=LINK.matcher(html);
        while(matcher.find()){
            // thttpd marks symbolic links with an l-mode; never traverse them.
            int line=html.lastIndexOf('\n',matcher.start())+1;
            String prefix=html.substring(line,matcher.start()).replaceAll("<[^>]*>"," ").trim();
            if(Pattern.compile("(?:^|\\s)l[-rwxstST?]{2,12}\\s+\\d+\\s").matcher(prefix).find())continue;
            String href=matcher.group(1)!=null?matcher.group(1):matcher.group(2)!=null?matcher.group(2):matcher.group(3);
            href=entities(href);
            try{
                URI relative=URI.create(href.replace(" ","%20"));
                if(relative.getRawQuery()!=null || relative.getRawFragment()!=null || relative.getUserInfo()!=null)continue;
                // Check before resolve() normalizes dot segments.
                String rawPath=relative.getPath();if(rawPath==null)continue;
                if(Arrays.asList(rawPath.split("/",-1)).contains("..") || Arrays.asList(rawPath.split("/",-1)).contains("."))continue;
                URI resolved=parent.resolve(relative);
                if(!"http".equals(resolved.getScheme()) || !origin.getHost().equalsIgnoreCase(resolved.getHost()) || port(origin)!=port(resolved) || resolved.getUserInfo()!=null)continue;
                String candidate=path(resolved.getPath());
                if(!candidate.startsWith(directory) || candidate.equals(directory))continue;
                String child=candidate.substring(directory.length());
                if(child.endsWith("/"))child=child.substring(0,child.length()-1);
                if(child.isEmpty() || child.contains("/"))continue;
                result.put(candidate,new Entry(candidate,candidate.endsWith("/")));
            }catch(IllegalArgumentException ignored){}
        }
        return new ArrayList<>(result.values());
    }
    private static int port(URI uri){return uri.getPort()==-1?80:uri.getPort();}
    private static String entities(String value){
        Matcher m=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(value);StringBuffer out=new StringBuffer();
        while(m.find()){
            try{String number=m.group(1);int cp=Integer.parseInt(number.startsWith("x")?number.substring(1):number,number.startsWith("x")?16:10);m.appendReplacement(out,Matcher.quoteReplacement(new String(Character.toChars(cp))));}
            catch(IllegalArgumentException e){m.appendReplacement(out,Matcher.quoteReplacement(m.group()));}
        }
        m.appendTail(out);
        return out.toString().replace("&quot;","\"").replace("&apos;","'").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");
    }
}
