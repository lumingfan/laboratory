package com.zeuslu.es.basic.controller;


import com.zeuslu.es.basic.entity.dto.ESRequestParam;
import com.zeuslu.es.basic.entity.dto.ESResponseResult;
import com.zeuslu.es.basic.service.TulingMallSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {
    @Autowired
    private TulingMallSearchService tulingMallSearchService;

    @GetMapping
    public ESResponseResult search(ESRequestParam esRequestParam) {
        return tulingMallSearchService.search(esRequestParam);
    }
}
