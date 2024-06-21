package org.avni.server.domain.metabase;

public class Group {
    private String name;
    private Integer id;

    public Group(){

    }
    public Group(String name){
        this(name,null);

    }
    public Group(String name ,Integer id){
        this.name=name;
        this.id=id;
    }
    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(int id) {
        this.id = id;
    }
}
