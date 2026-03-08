package com.zeuslu.es.basic.constant;

import java.util.ArrayList;
import java.util.List;

public class SearchConstant {
    public static final Integer PAGE_SIZE = 10;
    public static final List<String> INDEX_NAME = new ArrayList<>(1);
    static {
        INDEX_NAME.add("product_db");
    }
}
