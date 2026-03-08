package com.zeuslu.es.basic.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.util.JSONWrappedObject;
import com.zeuslu.es.basic.entity.Employee;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;

import java.io.IOException;
import java.util.*;

@Slf4j
@SpringBootTest
class EmployeeRepositoryTest {
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;


    @Test
    public void testDocument() {

        Employee employee = new Employee(10L, "fox666", 1, 32, "长沙麓谷", "java architect");
        //插入文档
        employeeRepository.save(employee);

        //根据id查询
        Optional<Employee> result = employeeRepository.findById(10L);
        result.ifPresent(value -> log.info(String.valueOf(value)));

        //根据name查询
        List<Employee> list = employeeRepository.findByName("fox666");
        if(!list.isEmpty()){
            log.info(String.valueOf(list.get(0)));
        }
    }
    @Test
    public void testCreateIndex(){
        //索引是否存在
        boolean exist = elasticsearchTemplate.indexOps(Employee.class).exists();
        if(exist){
            //删除索引
            elasticsearchTemplate.indexOps(Employee.class).delete();
        }
        //创建索引
        //1）配置settings
        Map<String, Object> settings = new HashMap<>();
        //"number_of_shards": 1,
        //"number_of_replicas": 1
        settings.put("number_of_shards",1);
        settings.put("number_of_replicas",1);
        //2) 配置mapping
        String json = "{\n" +
                "      \"properties\": {\n" +
                "        \"_class\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"fields\": {\n" +
                "            \"keyword\": {\n" +
                "              \"type\": \"keyword\",\n" +
                "              \"ignore_above\": 256\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"address\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"fields\": {\n" +
                "            \"keyword\": {\n" +
                "              \"type\": \"keyword\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"analyzer\": \"ik_max_word\"\n" +
                "        },\n" +
                "        \"age\": {\n" +
                "          \"type\": \"integer\"\n" +
                "        },\n" +
                "        \"id\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"name\": {\n" +
                "          \"type\": \"keyword\"\n" +
                "        },\n" +
                "        \"remark\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"fields\": {\n" +
                "            \"keyword\": {\n" +
                "              \"type\": \"keyword\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"analyzer\": \"ik_smart\"\n" +
                "        },\n" +
                "        \"sex\": {\n" +
                "          \"type\": \"integer\"\n" +
                "        }\n" +
                "      }\n" +
                "    }";
        Document mapping = Document.parse(json);
        //3)创建索引
        elasticsearchTemplate.indexOps(Employee.class)
                .create(settings,mapping);

        //查看索引mappings信息
        Map<String, Object> mappings = elasticsearchTemplate.indexOps(Employee.class).getMapping();
        log.info(mappings.toString());
    }


    @Test
    public void testDocumentOpe(){

        //根据id删除文档
        //对应： DELETE /employee/_doc/12
        elasticsearchTemplate.delete(String.valueOf(12L),Employee.class);

        Employee employee = new Employee(12L,"张三三",1,25,"广州天河公园","java developer");
        //插入文档
        elasticsearchTemplate.save(employee);

        //根据id查询文档
        //对应：GET /employee/_doc/12
        Employee emp = elasticsearchTemplate.get(String.valueOf(12L),Employee.class);
        log.info(String.valueOf(emp));

    }



    @Test
    public void testQueryDocument(){
        //条件查询
    /* 查询姓名为张三的员工信息
    GET /employee/_search
    {
        "query": {
        "term": {
            "name": {
                "value": "张三"
            }
        }
    }
    }*/

        //第一步：构建查询语句
        //方式1：StringQuery
//        Query query = new StringQuery("{\n" +
//                "            \"term\": {\n" +
//                "                \"name\": {\n" +
//                "                    \"value\": \"张三\"\n" +
//                "                }\n" +
//                "            }\n" +
//                "        }");
        //方式2：NativeQuery
        Query query = NativeQuery.builder()
                .withQuery(q -> q.term(
                        t -> t.field("name").value("张三三")))
                .build();


        //第二步：调用search查询
        SearchHits<Employee> search = elasticsearchTemplate.search(query, Employee.class);
        //第三步：解析返回结果
        List<SearchHit<Employee>> searchHits = search.getSearchHits();
        for (SearchHit hit: searchHits){
            log.info("返回结果："+hit.toString());
        }


    }


    @Test
    public void testMatchQueryDocument(){
        //条件查询
    /*最少匹配广州，公园两个词
    GET /employee/_search
    {
        "query": {
        "match": {
            "address": {
                "query": "广州公园",
                 "minimum_should_match": 2
            }
        }
    }
    }*/

        //第一步：构建查询语句
        //方式1：StringQuery
//        Query query = new StringQuery("{\n" +
//                "            \"match\": {\n" +
//                "                \"address\": {\n" +
//                "                    \"query\": \"广州公园\",\n" +
//                "                     \"minimum_should_match\": 2\n" +
//                "                }\n" +
//                "            }\n" +
//                "        }");
        //方式2：NativeQuery
        Query query = NativeQuery.builder()
                .withQuery(q -> q.match(
                        m -> m.field("address").query("广州公园")
                                .minimumShouldMatch("2")))
                .build();


        //第二步：调用search查询
        SearchHits<Employee> search = elasticsearchTemplate.search(query, Employee.class);
        //第三步：解析返回结果
        List<SearchHit<Employee>> searchHits = search.getSearchHits();
        for (SearchHit hit: searchHits){
            log.info("返回结果："+hit.toString());
        }

    }

    @Test
    public void testQueryDocument3(){
        // 分页排序高亮
    /*
    GET /employee/_search
    {
      "from": 0,
      "size": 3,
      "query": {
        "match": {
          "remark": {
            "query": "JAVA"
          }
        }
      },
      "highlight": {
        "pre_tags": ["<font color='red'>"],
        "post_tags": ["<font/>"],
        "require_field_match": "false",
        "fields": {
          "*":{}
        }
      },
      "sort": [
        {
          "age": {
            "order": "desc"
          }
        }
      ]
    }*/
        //第一步：构建查询语句
        Query query = new StringQuery("{\n" +
                "        \"match\": {\n" +
                "          \"remark\": {\n" +
                "            \"query\": \"JAVA\"\n" +
                "          }\n" +
                "        }\n" +
                "      }");
        //分页  注意：from = pageNumber（页码，从0开始，） * pageSize（每页的记录数）
        query.setPageable(PageRequest.of(0, 3));
        //排序
        query.addSort(Sort.by(Order.desc("age")));
        //高亮
        HighlightField highlightField = new HighlightField("*");
        HighlightParameters highlightParameters = new HighlightParameters.HighlightParametersBuilder()
                .withPreTags("<font color='red'>")
                .withPostTags("<font/>")
                .withRequireFieldMatch(false)
                .build();
        Highlight highlight = new Highlight(highlightParameters,Arrays.asList(highlightField));
        HighlightQuery highlightQuery = new HighlightQuery(highlight,Employee.class);

        query.setHighlightQuery(highlightQuery);


        //第二步：调用search查询
        SearchHits<Employee> search = elasticsearchTemplate.search(query, Employee.class);
        //第三步：解析返回结果
        List<SearchHit<Employee>> searchHits = search.getSearchHits();
        for (SearchHit hit: searchHits){
            log.info("返回结果："+hit.toString());
        }
    }


    @Test
    public void testBoolQueryDocumentClient(){
        //条件查询
    /*
    GET /employee/_search
    {
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "address": "广州"
              }
            },{
              "match": {
                "remark": "java"
              }
            }
          ]
        }
      }
    }
     */

        //第一步：构建查询语句
        //方式1：StringQuery
//        Query query = new StringQuery("{\n" +
//                "            \"bool\": {\n" +
//                "              \"must\": [\n" +
//                "                {\n" +
//                "                  \"match\": {\n" +
//                "                    \"address\": \"广州\"\n" +
//                "                  }\n" +
//                "                },{\n" +
//                "                  \"match\": {\n" +
//                "                    \"remark\": \"java\"\n" +
//                "                  }\n" +
//                "                }\n" +
//                "              ]\n" +
//                "            }\n" +
//                "          }");
        //方式2：NativeQuery
        Query query = NativeQuery.builder()
                .withQuery(q -> q.bool(
                        m -> m.must(
                                QueryBuilders.match(q1 -> q1.field("address").query("广州")),
                                QueryBuilders.match( q2 -> q2.field("remark").query("java"))
                        )))
                .build();

        //第二步：调用search查询
        SearchHits<Employee> search = elasticsearchTemplate.search(query, Employee.class);
        //第三步：解析返回结果
        List<SearchHit<Employee>> searchHits = search.getSearchHits();
        for (SearchHit hit: searchHits){
            log.info("返回结果："+hit.toString());
        }
    }

    @Autowired
    ElasticsearchClient elasticsearchClient;

    String indexName = "employee_demo";

    @Test
    public void testCreateIndexClient() throws IOException {

        //索引是否存在
        BooleanResponse exist = elasticsearchClient.indices()
                .exists(e->e.index(indexName));
        if(exist.value()){
            //删除索引
            elasticsearchClient.indices().delete(d->d.index(indexName));
        }
        //创建索引
        elasticsearchClient.indices().create(c->c.index(indexName)
                .settings(s->s.numberOfShards("1").numberOfReplicas("1"))
                .mappings(m-> m.properties("name",p->p.keyword(k->k))
                        .properties("sex",p->p.long_(l->l))
                        .properties("address",p->p.text(t->t.analyzer("ik_max_word")))
                )
        );

        //查询索引
        GetIndexResponse getIndexResponse = elasticsearchClient.indices().get(g -> g.index(indexName));
        log.info(getIndexResponse.result().toString());

    }

    @Test
    public void testDocumentClient() throws IOException {
        Employee employee = new Employee(12L,"张三三",1,25,"广州天河公园","java developer");

        IndexRequest<Employee> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(employee.getId().toString())
                .document(employee)
        );

        IndexResponse response = elasticsearchClient.index(request);

        log.info("response:"+response);
    }


    @Test
    public void testQuery() throws IOException {
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q.match(m -> m.field("name").query("张三三"))
                ));

        log.info("构建的DSL语句:"+ searchRequest.toString());

        SearchResponse<Employee> searchResponse = elasticsearchClient.search(searchRequest, Employee.class);

        List<Hit<Employee>> hits = searchResponse.hits().hits();
        hits.stream().map(Hit::source).forEach(employee -> {
            log.info("员工信息:"+employee);
        });

    }

    @Test
    public void testBoolQueryDocument() throws IOException {
        //条件查询
    /*
    GET /employee/_search
    {
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "address": "广州"
              }
            },{
              "match": {
                "remark": "java"
              }
            }
          ]
        }
      }
    }
     */

        //第一步：构建查询语句
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        boolQueryBuilder.must(m->m.match(q->q.field("address").query("广州")))
                .must(m->m.match(q->q.field("remark").query("java")));

        SearchRequest searchRequest = new SearchRequest.Builder()
                .index("employee")
                .query(q->q.bool(boolQueryBuilder.build()))
                .build();

        //第二步：调用search查询
        SearchResponse<Employee> searchResponse = elasticsearchClient.search(searchRequest, Employee.class);
        //第三步：解析返回结果
        List<Hit<Employee>> list = searchResponse.hits().hits();
        for(Hit<Employee> hit: list){
            //返回source
            log.info(String.valueOf(hit.source()));
        }

    }
}