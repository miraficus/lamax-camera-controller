package cz.miraficus.x10connect;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Camera wire format. No eval, JavaScript engine, or vendor binary dependencies. */
public final class Protocol {
    private Protocol() {}
    public static final String CGI = "/cgi-bin/hi3510/";
    private static final Pattern VAR = Pattern.compile("\\bvar\\s+(\\w+)\\s*=\\s*\"([^\"\\r\\n]*)\"\\s*;");
    public static Map<String,String> vars(String raw) {
        Map<String,String> result = new LinkedHashMap<>();
        Matcher matcher = VAR.matcher(raw);
        while (matcher.find()) result.put(matcher.group(1), matcher.group(2));
        return result;
    }
    public static String base(String input) {
        String value = input.trim();
        if (!value.contains("://")) value = "http://" + value;
        URI uri = URI.create(value);
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("Enter a camera HTTP address, e.g. 192.168.0.1");
        // Only local IPv4 targets. Avoid sending camera commands to remote websites.
        String host = uri.getHost();
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) throw new IllegalArgumentException("Use the camera local IPv4 address");
        String[] parts = host.split("\\.");
        int[] ip = Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        for (int n : ip) if (n > 255) throw new IllegalArgumentException("Invalid IP address");
        boolean local = ip[0] == 10 || ip[0] == 127 || (ip[0] == 192 && ip[1] == 168)
                || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) || (ip[0] == 169 && ip[1] == 254);
        if (!local || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("A local camera address is required");
        return "http://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
    public static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch(java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    public static String mediaPath(String path) {
        if (!path.startsWith("sd/DCIM/") || path.contains("\\") || path.contains("%")
                || path.contains("?") || path.contains("#") || path.contains(":")
                || Arrays.asList(path.split("/", -1)).contains("..") || path.chars().anyMatch(c -> c < 32))
            throw new IllegalArgumentException("Invalid media path");
        if (!path.toUpperCase(Locale.ROOT).matches(".*\\.(JPG|JPEG|MP4|LRV|THM|DNG)$"))
            throw new IllegalArgumentException("Unsupported media type");
        return path;
    }
    public static String encodedPath(String path) {
        mediaPath(path);
        StringJoiner join = new StringJoiner("/");
        for (String part : path.split("/")) join.add(encode(part));
        return "/" + join;
    }
    public static void checkResult(String raw) {
        Matcher m = Pattern.compile("SvrFuncResult\\s*=\\s*\"([^\"]*)\"").matcher(raw);
        if (m.find() && !m.group(1).equals("0") && !m.group(1).equalsIgnoreCase("Success"))
            throw new IllegalStateException("Camera: " + m.group(1));
        if (raw.trim().isEmpty()) throw new IllegalStateException("Empty camera response");
    }
    public static List<Media> parseFiles(String raw) throws Exception {
        JSONArray array = new JSONArray(raw);
        List<Media> result = new ArrayList<>();
        for (int i=0; i<array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String path = mediaPath(o.getString("path"));
            long size = Long.parseLong(o.get("size").toString());
            if (size < 0) throw new IllegalArgumentException("Invalid file size");
            result.add(new Media(path, o.optString("create", ""), size, o.optString("time", "0")));
        }
        return result;
    }
    public static String date(String value) {
        for (String pattern : new String[]{"yyyy/MM/dd HH:mm:ss", "yyyyMMddHHmmss"}) {
            if (pattern.startsWith("yyyy/") ? value.length()!=19 : value.length()!=14) continue;
            try {
                SimpleDateFormat input = new SimpleDateFormat(pattern, Locale.ROOT); input.setLenient(false);
                Date parsed = input.parse(value);
                if (parsed != null) return new SimpleDateFormat("dd. MM. yyyy  HH:mm", Locale.getDefault()).format(parsed);
            } catch (Exception ignored) { }
        }
        return value;
    }
    public static final class Media {
        public final String path, created, duration; public final long size;
        public Media(String path, String created, long size, String duration) {
            this.path=path; this.created=created; this.size=size; this.duration=duration;
        }
        public String name() { return path.substring(path.lastIndexOf('/')+1); }
        public boolean video() { return path.toUpperCase(Locale.ROOT).matches(".*\\.(MP4|LRV)$"); }
        public String thumbnail() { return path.substring(0, path.lastIndexOf('.'))+".THM"; }
        public String mime() { return video()?"video/mp4":path.endsWith(".DNG")?"image/x-adobe-dng":"image/jpeg"; }
        public String sizeText() { return String.format(Locale.getDefault(), "%.1f MB", size/1048576.0); }
        public JSONObject json() throws Exception { return new JSONObject().put("path",path).put("create",created).put("size",size).put("time",duration); }
    }
}
