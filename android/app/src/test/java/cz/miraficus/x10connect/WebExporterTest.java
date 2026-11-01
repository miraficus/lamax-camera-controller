package cz.miraficus.x10connect;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

public class WebExporterTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String BASE="http://192.168.0.1";
    private static String index(String links){return "<html><h1>Index of /</h1><pre>\n"+links+"</pre></html>";}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private WebExporter exporter(Map<String,byte[]> data,List<String> requested,AtomicBoolean cancel,WebExporter.Limits limits)throws Exception{
        return new WebExporter(path->{requested.add(path);byte[] b=data.get(path);if(b==null)throw new IOException("HTTP 404");return new WebExporter.Response(new ByteArrayInputStream(b),b.length,"application/octet-stream",0,()->{});},temporary.newFolder(),cancel,(a,b,c,d)->{},limits);
    }
    private static Map<String,byte[]> unzip(byte[] data)throws IOException{
        Map<String,byte[]> files=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;byte[] buffer=new byte[4096];while((e=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);files.put(e.getName(),out.toByteArray());}}
        return files;
    }
    @Test public void filtersDangerousLinksAndKeepsOnlyStaticChildren(){
        String html=index("<a href='main_app'>main_app</a>\n<a href='res/'>res</a>\n<a href='sd/'>sd</a>\n<a href='/sdcard/'>sdcard</a>\n<a href='cgi-bin/'>cgi</a>\n<a href='upgrade.cgi'>upgrade</a>\n<a href='file?cmd=erase'>query</a>\n<a href='http://evil.test/file'>external</a>\n<a href='//evil.test/file'>external</a>\n<a href='http://192.168.0.1:81/file'>other port</a>\n<a href='../file'>parent</a>\n<a href='%2e%2e/file'>encoded parent</a>\n<a href='%252e%252e/file'>double encoded</a>\n<a href='dev/'>device</a>\nlrwx 1 0 Jan 1 1970 <a href='alias/'>alias</a>\n");
        List<WebExportLinks.Entry> entries=WebExportLinks.parse(BASE,"/",html);
        assertEquals(2,entries.size());assertEquals("/main_app",entries.get(0).path);assertEquals("/res/",entries.get(1).path);
    }
    @Test public void decodesNamesWithoutEscapingArchiveRoot(){
        List<WebExportLinks.Entry> entries=WebExportLinks.parse(BASE,"/res/",index("<a href='Icon%20One.png'>one</a>\n<a href='a&amp;b.png'>two</a>\n<a href='http://192.168.0.1:80/res/file.png'>same origin</a>"));
        assertEquals(3,entries.size());assertEquals("/res/Icon One.png",entries.get(0).path);assertEquals("/res/a&b.png",entries.get(1).path);
        assertEquals("/res/Icon%20One.png",WebExportLinks.requestPath(entries.get(0).path));
    }
    @Test public void exportsNestedFilesAndManifestWithoutRequestingSdOrCgi()throws Exception{
        Map<String,byte[]> data=new HashMap<>();
        data.put("/",bytes(index("<a href='main_app'>main</a>\n<a href='res/'>res</a>\n<a href='sd/'>sd</a>\n<a href='cgi-bin/'>cgi</a>")));
        data.put("/res/",bytes(index("<a href='../'>parent</a>\n<a href='pic/'>pic</a>")));
        data.put("/res/pic/",bytes(index("<a href='icon.png'>icon</a>")));data.put("/main_app",bytes("abc"));data.put("/res/pic/icon.png",new byte[]{1,2,3});
        List<String> requested=new ArrayList<>();ByteArrayOutputStream output=new ByteArrayOutputStream();
        WebExporter.Result result=exporter(data,requested,new AtomicBoolean(),WebExporter.Limits.defaults()).export(BASE,output);
        assertEquals(2,result.saved);assertEquals(0,result.errors);assertEquals(6,result.bytes);
        Map<String,byte[]> zip=unzip(output.toByteArray());assertArrayEquals(bytes("abc"),zip.get("camera/main_app"));assertTrue(zip.containsKey("camera/res/pic/icon.png"));
        JSONObject manifest=new JSONObject(new String(zip.get("export-manifest.json"),StandardCharsets.UTF_8));assertEquals("complete",manifest.getString("status"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",manifest.getJSONArray("files").getJSONObject(0).getString("sha256"));
        assertEquals(Arrays.asList("/","/res/","/res/pic/","/main_app","/res/pic/icon.png"),requested);
    }
    @Test public void marksMissingFilesAsPartial()throws Exception{
        Map<String,byte[]> data=new HashMap<>();data.put("/",bytes(index("<a href='missing'>missing</a>\n<a href='good'>good</a>")));data.put("/good",bytes("good"));
        ByteArrayOutputStream out=new ByteArrayOutputStream();WebExporter.Result result=exporter(data,new ArrayList<>(),new AtomicBoolean(),WebExporter.Limits.defaults()).export(BASE,out);
        assertEquals(1,result.saved);assertEquals(1,result.errors);Map<String,byte[]> zip=unzip(out.toByteArray());assertFalse(zip.containsKey("camera/missing"));assertEquals("partial",new JSONObject(new String(zip.get("export-manifest.json"),StandardCharsets.UTF_8)).getString("status"));
    }
    @Test public void cancelledExportStopsBeforeNetworkRequests()throws Exception{
        List<String> requested=new ArrayList<>();
        try{exporter(new HashMap<>(),requested,new AtomicBoolean(true),WebExporter.Limits.defaults()).export(BASE,new ByteArrayOutputStream());fail();}catch(InterruptedIOException expected){}
        assertTrue(requested.isEmpty());
    }
    @Test public void refusesNonDirectoryRoot()throws Exception{
        Map<String,byte[]> data=new HashMap<>();data.put("/",bytes("<html>Login</html>"));
        try{exporter(data,new ArrayList<>(),new AtomicBoolean(),WebExporter.Limits.defaults()).export(BASE,new ByteArrayOutputStream());fail();}catch(IOException expected){}
    }
    @Test public void perFileLimitDoesNotArchivePartialData()throws Exception{
        Map<String,byte[]> data=new HashMap<>();data.put("/",bytes(index("<a href='huge'>huge</a>")));data.put("/huge",bytes("123456"));ByteArrayOutputStream out=new ByteArrayOutputStream();
        WebExporter.Result r=exporter(data,new ArrayList<>(),new AtomicBoolean(),new WebExporter.Limits(10,10,10,3,10000)).export(BASE,out);
        assertEquals(0,r.saved);assertEquals(1,r.errors);assertFalse(unzip(out.toByteArray()).containsKey("camera/huge"));
    }
    @Test public void outputFailureIsFatal()throws Exception{
        Map<String,byte[]> data=new HashMap<>();data.put("/",bytes(index("<a href='file'>file</a>")));data.put("/file",bytes("abc"));
        try{exporter(data,new ArrayList<>(),new AtomicBoolean(),WebExporter.Limits.defaults()).export(BASE,new OutputStream(){public void write(int b)throws IOException{throw new IOException("Disk full");}});fail();}catch(IOException expected){}
    }
}
