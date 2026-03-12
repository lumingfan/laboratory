package com.zeuslu.esearch.util;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;

import java.util.List;

public class QueryUtil {
    public static Query addMatchIfPresent(String field, Object value) {
        if (value != null) {
            return Query.of(builder -> builder.match(m -> m.field(field).query(FieldValue.of(value))));
        }
        return null;
    }

    public static Query addTermIfPresent(String field, Object value) {
        if (value != null) {
            return Query.of(builder -> builder.term(t -> t.field(field).value(FieldValue.of(value))));
        }
        return null;
    }

    public static Query addRangeIfPresent(String field, Object minValue, Object maxValue) {
        if (minValue != null || maxValue != null) {
            return Query.of(builder -> builder.range(r -> {
                r.field(field);
                if (minValue != null) {
                    r.gte(JsonData.of(minValue));
                }
                if (maxValue != null) {
                    r.lte(JsonData.of(maxValue));
                }
                return r;
            }));
        }
        return null;
    }

    public static void addQueryIfPresent(List<Query> queryList, Query query) {
        if (query != null && queryList != null) {
            queryList.add(query);
        }
    }
}