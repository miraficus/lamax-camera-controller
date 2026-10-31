package cz.miraficus.x10connect;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamCandidatesTest {
    @Test public void acceptsOnlyStreamNumbers(){
        assertEquals("13",StreamCandidates.number("013"));
        for(String value:new String[]{"-1","1000","12?x=1","12/13","http://host",""," 12"}){
            try{StreamCandidates.number(value);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void scanStaysWithinRequestedNearbyRange(){assertArrayEquals(new String[]{"10","11","12","13","14","15","16","17","18","19"},StreamCandidates.nearby());}
}
