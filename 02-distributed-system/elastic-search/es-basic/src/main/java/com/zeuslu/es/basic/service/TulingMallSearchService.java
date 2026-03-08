package com.zeuslu.es.basic.service;

import com.zeuslu.es.basic.entity.dto.ESRequestParam;
import com.zeuslu.es.basic.entity.dto.ESResponseResult;

public interface TulingMallSearchService {
   ESResponseResult search(ESRequestParam esRequestParam);
}
