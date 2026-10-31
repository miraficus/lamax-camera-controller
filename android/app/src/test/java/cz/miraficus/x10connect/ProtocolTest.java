package cz.miraficus.x10connect;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void parsesCameraVariablesWithoutExecutingCode(){
        Map<String,String> vars=Protocol.vars("var name=\"LAMAX X10.1\";\r\nvar count=\"78\";\r\nalert('ignore');");
        assertEquals("78",vars.get("count"));assertEquals(2,vars.size());
    }
    @Test public void normalizesCameraAddress(){assertEquals("http://192.168.0.1",Protocol.base("192.168.0.1/"));}
    @Test public void permitsLocalTestingPort(){assertEquals("http://127.0.0.1:8765",Protocol.base("http://127.0.0.1:8765"));}
    @Test public void rejectsRemoteOrAmbiguousTargets(){
        for(String input:new String[]{"https://192.168.0.1","http://example.org","http://192.168.0.1@evil.test","192.168.0.1/cgi-bin/","192.168.0.999","http://8.8.8.8","http://192.168.0.1?x=1"}){
            try{Protocol.base(input);fail(input);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void rejectsTraversalAndQueryInjection(){
        for(String path:new String[]{"../main_app","/sd/DCIM/a.JPG","sd/DCIM/../main_app.JPG","sd/DCIM/%2e%2e/a.JPG","sd/DCIM/a.JPG?x=1","sd/DCIM/a.JPG#frag","sd/DCIM/a\n.JPG"}){
            try{Protocol.encodedPath(path);fail(path);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void preservesCaseAndEncodesSpaces(){assertEquals("/sd/DCIM/100GKUDV/Foto%201.JPG",Protocol.encodedPath("sd/DCIM/100GKUDV/Foto 1.JPG"));}
    @Test public void decodesFirmwareV1004FileShape()throws Exception{
        List<Protocol.Media> files=Protocol.parseFiles("[{\"path\":\"sd/DCIM/100GKUDV/SING0076.JPG\",\"create\":\"2026/05/22 13:56:58\",\"time\":\"0\",\"size\":\"2966788\"}]");
        assertEquals(2966788L,files.get(0).size);assertFalse(files.get(0).video());
        assertEquals("sd/DCIM/100GKUDV/SING0076.THM",files.get(0).thumbnail());
    }
    @Test public void retainsLargeVideoSizes()throws Exception{
        Protocol.Media m=Protocol.parseFiles("[{\"path\":\"sd/DCIM/100GKUDV/NORM0001.MP4\",\"size\":5368709120,\"time\":\"99\"}]").get(0);
        assertEquals(5368709120L,m.size);assertTrue(m.video());
    }
    @Test public void acceptsBothKnownDateFormats(){assertEquals(Protocol.date("20260902021006"),Protocol.date("2026/09/02 02:10:06"));}
    @Test public void preservesUnknownDates(){assertEquals("unknown",Protocol.date("unknown"));}
    @Test public void rejectsCameraErrorEvenWithHttp200(){try{Protocol.checkResult("SvrFuncResult=\"sd is not ready\"");fail();}catch(IllegalStateException expected){}}
    @Test public void acceptsCameraSuccess(){Protocol.checkResult("Success\r\n");Protocol.checkResult("SvrFuncResult=\"0\"");}
}
