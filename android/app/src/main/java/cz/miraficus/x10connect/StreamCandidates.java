package cz.miraficus.x10connect;
public final class StreamCandidates {
    private StreamCandidates(){}
    public static String number(String value){
        if(value==null || !value.matches("[0-9]{1,3}"))throw new IllegalArgumentException("Stream number must be between 0 and 999");
        return Integer.toString(Integer.parseInt(value));
    }
    public static String[] nearby(){String[] values=new String[10];for(int i=0;i<values.length;i++)values[i]=Integer.toString(10+i);return values;}
}
