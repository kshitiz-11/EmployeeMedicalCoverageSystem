package com.prototype1.dao;

import java.io.IOException;

import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.tasks.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class ElasticSearchDao {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private RestHighLevelClient restHighLevelClient;

    public ElasticSearchDao(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
    }



    public void index(String id, String document) {

        IndexRequest request = new IndexRequest("plan", "_doc", id);
        request.source(document, XContentType.JSON);

        try {
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public void index(String id, String document, String parentid) {
        IndexRequest request = new IndexRequest("plan", "_doc", id);
        request.source(document, XContentType.JSON);

        //The routing value is mandatory because parent and child documents must be indexed on the same shard
        request.routing(parentid);

        try {
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public void delete(String id) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest("plan", "_doc", id);
            restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
