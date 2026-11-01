package cz.miraficus.x10connect;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

/** Streams a static HTTP-file snapshot to ZIP, never visiting CGI or storage links. */
public final class WebExporter {
    public interface Transport{Response get(String encodedPath)throws IOException;}
    public interface Progress{void update(int completed,int total,String path,long bytes);}
    public static final class Response implements Closeable{
        public final InputStream body;public final long length,modified;public final String type;
        private final Runnable close;
        public Response(InputStream body,long length,String type,long modified,Runnable close){this.body=body;this.length=length;this.type=type;this.modified=modified;this.close=close;}
        public void close()throws IOException{try{body.close();}finally{close.run();}}
    }
    public static final class Limits{
        public final int files,directories,depth;public final long perFile,total;
        public Limits(int files,int directories,int depth,long perFile,long total){this.files=files;this.directories=directories;this.depth=depth;this.perFile=perFile;this.total=total;}
        public static Limits defaults(){return new Limits(10000,512,16,64L*1024*1024,256L*1024*1024);}
    }
    public static final class Result{
        public final int saved,errors;public final long bytes;
        Result(int saved,int errors,long bytes){this.saved=saved;this.errors=errors;this.bytes=bytes;}
    }
    private final Transport transport;private final File scratch;private final AtomicBoolean cancel;private final Progress progress;private final Limits limits;
    private long received;
    public WebExporter(Transport transport,File scratch,AtomicBoolean cancel,Progress progress,Limits limits){this.transport=transport;this.scratch=scratch;this.cancel=cancel;this.progress=progress;this.limits=limits;}
    private static String timestamp(){java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(new Date());}
    private void check()throws InterruptedIOException{if(cancel.get() || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Export cancelled");}
    private int read(InputStream input,byte[] buffer)throws IOException{
        check();int n=input.read(buffer);
        if(n>0){received+=n;if(received>limits.total)throw new IOException("Export reached total byte limit");}
        return n;
    }
    public Result export(String base,OutputStream output)throws Exception{
        Protocol.base(base);check();
        JSONArray errors=new JSONArray(),files=new JSONArray();
        LinkedHashSet<String> pending=new LinkedHashSet<>();ArrayDeque<String> directories=new ArrayDeque<>();directories.add("/");
        Set<String> visited=new HashSet<>();List<String> discoveredDirectories=new ArrayList<>();
        while(!directories.isEmpty()){
            check();String dir=directories.removeFirst();if(!visited.add(dir))continue;
            if(visited.size()>limits.directories){errors.put("Directory limit reached");break;}
            progress.update(0,0,dir,received);
            try(Response response=transport.get(WebExportLinks.requestPath(dir))){
                ByteArrayOutputStream raw=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;
                while((n=read(response.body,b))!=-1){if(raw.size()+n>2*1024*1024)throw new IOException("Directory listing exceeds 2 MiB");raw.write(b,0,n);}
                String html=raw.toString("UTF-8");
                if(!WebExportLinks.listing(html))throw new IOException("Not a recognized directory listing: "+dir);
                discoveredDirectories.add(dir);
                for(WebExportLinks.Entry entry:WebExportLinks.parse(base,dir,html)){
                    if(entry.directory){
                        if(entry.path.split("/").length>limits.depth){errors.put("Directory depth limit: "+entry.path);continue;}
                        if(!visited.contains(entry.path))directories.add(entry.path);
                    }else{
                        if(pending.size()>=limits.files){errors.put("File limit reached");break;}
                        pending.add(entry.path);
                    }
                }
            }catch(InterruptedIOException e){check();if(dir.equals("/"))throw e;errors.put(dir+": "+e.getMessage());}
            catch(IOException e){if(dir.equals("/"))throw e;errors.put(dir+": "+e.getMessage());}
            if(received>limits.total)break;
        }
        if(!scratch.isDirectory() && !scratch.mkdirs())throw new IOException("Cannot create temporary export directory");
        int saved=0,processed=0,consecutiveErrors=0;long savedBytes=0;
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
            for(String dir:discoveredDirectories){if(!dir.equals("/")){zip.putNextEntry(new ZipEntry("camera/"+dir.substring(1)));zip.closeEntry();}}
            for(String path:pending){
                check();
                if(received>=limits.total){errors.put("Total byte limit reached before all files were copied");break;}
                File temporary=File.createTempFile("web-", ".part",scratch);
                progress.update(processed,pending.size(),path,received);
                try{
                    long size=0,modified=0;String type=null;boolean ready=false;
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    try(Response response=transport.get(WebExportLinks.requestPath(path));OutputStream file=new FileOutputStream(temporary)){
                        if(response.length>limits.perFile)throw new IOException("File exceeds per-file limit");
                        modified=response.modified;type=response.type;
                        byte[] b=new byte[65536];int n;
                        while((n=read(response.body,b))!=-1){size+=n;if(size>limits.perFile)throw new IOException("File exceeds per-file limit");file.write(b,0,n);digest.update(b,0,n);progress.update(processed,pending.size(),path,received);}
                        if(response.length>=0 && size!=response.length)throw new IOException("Incomplete file: "+size+" / "+response.length);
                        ready=true;
                    }catch(InterruptedIOException e){check();ready=false;errors.put(path+": "+e.getMessage());consecutiveErrors++;}
                    catch(IOException e){ready=false;errors.put(path+": "+e.getMessage());consecutiveErrors++;}
                    if(ready){
                        // Output/storage errors are fatal. Never keep an archive with a partially written ZIP entry.
                        ZipEntry entry=new ZipEntry("camera/"+path.substring(1));if(modified>0)entry.setTime(modified);zip.putNextEntry(entry);
                        try(InputStream in=new FileInputStream(temporary)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){check();zip.write(b,0,n);}}
                        zip.closeEntry();
                        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
                        files.put(new JSONObject().put("path",path.substring(1)).put("bytes",size).put("sha256",hash.toString()).put("content_type",type==null?JSONObject.NULL:type).put("http_last_modified",modified));
                        saved++;savedBytes+=size;consecutiveErrors=0;
                    }
                }finally{temporary.delete();}
                processed++;
                if(received>limits.total || consecutiveErrors>=3){errors.put("Export stopped before processing all discovered files");break;}
            }
            check();
            JSONObject manifest=new JSONObject().put("format",1).put("camera_base",base).put("created_utc",timestamp())
                    .put("scope","HTTP directory listings only; storage, CGI and symbolic links excluded; not a full flash/firmware backup")
                    .put("excluded_top_level",new JSONArray(Arrays.asList("sd","sdcard","mnt","media","storage","proc","sys","dev","cgi-bin")))
                    .put("status",errors.length()==0?"complete":"partial").put("saved_files",saved).put("saved_bytes",savedBytes).put("downloaded_bytes",received)
                    .put("limits",new JSONObject().put("files",limits.files).put("directories",limits.directories).put("depth",limits.depth).put("per_file_bytes",limits.perFile).put("total_bytes",limits.total))
                    .put("files",files).put("errors",errors);
            zip.putNextEntry(new ZipEntry("export-manifest.json"));zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));zip.write(("X10 Connect static HTTP export\nFiles are under camera/. See export-manifest.json for SHA-256 and errors.\nThis is NOT a full flash or firmware image, and it is not an installable update package.\nOnly files advertised by directory listings were requested; SD/storage paths, CGI, redirects and recognizable symbolic links were excluded.\nIf status is partial, some advertised files could not be copied.\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return new Result(saved,errors.length(),savedBytes);
    }
}
