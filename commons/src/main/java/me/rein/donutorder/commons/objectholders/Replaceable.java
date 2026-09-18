package me.rein.donutorder.commons.objectholders;

import me.rein.donutorder.commons.util.message.ChatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Replaceable {
    private Map<String, String> stringStringMap = new HashMap<>();
    public static Replaceable inst(String... strings){
        return new Replaceable(strings);
    }
    private Replaceable(Map<String, String> stringStringMap) {
        this.stringStringMap = stringStringMap;
    }

    private Replaceable(){}

    private Replaceable(String... strings){
        if(strings.length == 0)
            return;

        if(strings.length%2 !=0)
            return;

        for (int i = 0; i < strings.length; i+=2) {
            if(strings.length > i+1){

                String key =  strings[i];
                String value = strings[i+1];

                stringStringMap.put(key,value);
            }
        }
    }

    public boolean isEmpty(){
        return stringStringMap.isEmpty();
    }

    public static Replaceable empty() {
        return new Replaceable();
    }

    public String replace(String str){
        String finalString = ChatUtil.c(str);

        for (Map.Entry<String, String> entry : stringStringMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            finalString = finalString.replace(key,value);
        }
        return finalString;
    }

    public List<String> replaceList(List<String> stringList){
        List<String> strings = new ArrayList<>();

        for (String s : stringList) {
            strings.add(replace(s));
        }

        return strings;
    }
}
