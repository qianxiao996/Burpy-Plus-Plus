package burp.models;


public class DataEntry {
    public int id;
    public String item;
    public String match;
    public String replace;
    public String type;
    public String comment;
    public boolean enable;

    public DataEntry( int id,String item, String match, String replace, String type, String comment,boolean enable) {
        this.id = id;
        this.item = item;
        this.match = match;
        this.replace = replace;
        this.type = type;
        this.comment = comment;
        this.enable = enable;

    }

    public DataEntry() {

    }
}