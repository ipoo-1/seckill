package com.seckill.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public void syncAll() {
        // 1. 创建索引（如果不存在），name/description 用 IK 中文分词
        try {
            boolean exists = esClient.indices().exists(e -> e.index("product")).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index("product")
                        .mappings(m -> m
                                .properties("id", p -> p.long_(l -> l))
                                .properties("name", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                                .properties("price", p -> p.double_(d -> d))
                                .properties("stock", p -> p.integer(i -> i))
                                .properties("description", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                                .properties("createTime", p -> p.date(d -> d))
                        ));
            }
        } catch (Exception e) {
            throw new RuntimeException("创建索引失败", e);
        }

        // 2. 从 MySQL 读出所有商品，一条条写入 ES
        List<Product> products = productMapper.selectList(null);
        for (Product product : products) {
            try {
                esClient.index(i -> i
                        .index("product")
                        .id(String.valueOf(product.getId()))
                        .document(product));
            } catch (Exception e) {
                throw new RuntimeException("同步商品失败: " + product.getId(), e);
            }
        }
    }

    @Override
    public List<Product> search(String keyword) {
        try {
            SearchResponse<Product> response = esClient.search(s -> s
                            .index("product")
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            .fields("name", "description")
                                            .query(keyword))),
                    Product.class);
            List<Product> result = new ArrayList<>();
            for (Hit<Product> hit : response.hits().hits()) {
                result.add(hit.source());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("搜索失败", e);
        }
    }
}