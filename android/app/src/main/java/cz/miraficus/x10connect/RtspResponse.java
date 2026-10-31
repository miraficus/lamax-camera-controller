package cz.miraficus.x10connect;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded RTSP/1.0 response reader. Does not start a media session. */
public final class RtspResponse {
    public final String status, body;
    public final Map<String,String> headers;
    private RtspResponse(String status, Map<String,String> headers, String body) {
        this.status=status; this.headers=headers; this.body=body;
    }
    public static RtspResponse read(InputStream input) throws IOException {
        ByteArrayOutputStream header=new ByteArrayOutputStream();
        int state=0;
        while(state<4) {
            int b=input.read(); if(b<0)throw new EOFException("Neúplná RTSP hlavička");
            header.write(b); if(header.size()>16384)throw new IOException("RTSP hlavička přesahuje 16 KiB");
            switch(state) {
                case 0: state=b=='\r'?1:0; break;
                case 1: state=b=='\n'?2:b=='\r'?1:0; break;
                case 2: state=b=='\r'?3:0; break;
                case 3: state=b=='\n'?4:0; break;
            }
        }
        String[] lines=header.toString("US-ASCII").split("\r\n");
        if(!lines[0].matches("RTSP/1\\.[01] [0-9]{3}( .*)?"))throw new IOException("Neplatný RTSP status");
        Map<String,String> headers=new LinkedHashMap<>();
        for(int i=1;i<lines.length;i++) {
            int split=lines[i].indexOf(':'); if(split<1)throw new IOException("Neplatná RTSP hlavička");
            String name=lines[i].substring(0,split).trim().toLowerCase(Locale.ROOT),value=lines[i].substring(split+1).trim();
            if(name.equals("content-length") && headers.containsKey(name) && !headers.get(name).equals(value))
                throw new IOException("Rozporné Content-Length");
            headers.put(name,value);
        }
        int length=0;
        if(headers.containsKey("content-length")) {
            try { length=Integer.parseInt(headers.get("content-length")); }
            catch(NumberFormatException e) { throw new IOException("Neplatné RTSP Content-Length",e); }
        }
        if(length<0 || length>65536)throw new IOException("RTSP tělo přesahuje limit 64 KiB nebo má zápornou délku");
        byte[] body=new byte[length]; int offset=0;
        while(offset<length) {
            int n=input.read(body,offset,length-offset); if(n<0)throw new EOFException("Neúplné RTSP tělo");
            offset+=n;
        }
        return new RtspResponse(lines[0],Collections.unmodifiableMap(headers),new String(body,StandardCharsets.UTF_8));
    }
    public String diagnostic() {
        String result=status+"\n"+headers+"\n"+body.substring(0,Math.min(body.length(),8192));
        if(body.length()>8192)result+="\n[SDP v logu zkráceno]";
        if(!headers.containsKey("content-length"))result+="\n[Bez Content-Length: tělo nebylo čteno.]";
        return result;
    }
}
