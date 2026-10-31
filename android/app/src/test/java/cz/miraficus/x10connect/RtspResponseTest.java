package cz.miraficus.x10connect;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class RtspResponseTest {
    private RtspResponse read(String raw) throws IOException {
        return RtspResponse.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void readsSdpWithoutWaitingForConnectionClose() throws Exception {
        String body="v=0\r\na=rtpmap:96 H264/90000\r\n";
        ByteArrayInputStream input=new ByteArrayInputStream(("RTSP/1.0 200 OK\r\nCSeq: 1\r\ncOnTeNt-LeNgTh: "+body.length()+"\r\n\r\n"+body+"NEXT").getBytes(StandardCharsets.US_ASCII));
        RtspResponse r=RtspResponse.read(input);
        assertEquals(body,r.body);assertEquals('N',input.read());assertEquals("1",r.headers.get("cseq"));
    }
    @Test public void preservesCameraRejection() throws Exception {
        RtspResponse r=read("RTSP/1.0 404 Not Found\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n");
        assertEquals("RTSP/1.0 404 Not Found",r.status);assertEquals("",r.body);
    }
    @Test public void reportsMissingLength() throws Exception {
        assertTrue(read("RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n").diagnostic().contains("No Content-Length"));
    }
    @Test public void rejectsTruncatedBody() throws Exception {
        try { read("RTSP/1.0 200 OK\r\nContent-Length: 5\r\n\r\na");fail(); }catch(EOFException expected){}
    }
    @Test public void rejectsConflictingLengths() throws Exception {
        try { read("RTSP/1.0 200 OK\r\nContent-Length: 0\r\nContent-Length: 1\r\n\r\n");fail(); }catch(IOException expected){}
    }
    @Test public void rejectsUnboundedAndMalformedResponses() throws Exception {
        for(String length:new String[]{"-1","65537","99999999999","bad"}) {
            try { read("RTSP/1.0 200 OK\r\nContent-Length: "+length+"\r\n\r\n");fail(length); }catch(IOException expected){}
        }
        try { read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");fail(); }catch(IOException expected){}
    }
    @Test public void capsHeaders() throws Exception {
        try { read("RTSP/1.0 200 OK\r\nX: "+new String(new char[17000]).replace('\0','a')+"\r\n\r\n");fail(); }catch(IOException expected){}
    }
}
