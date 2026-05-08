package com.yupi.template.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.mapper.CourseDocumentChunkMapper;
import com.yupi.template.model.entity.CourseDocumentChunk;
import com.yupi.template.service.CourseDocumentChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档切片服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class CourseDocumentChunkServiceImpl extends ServiceImpl<CourseDocumentChunkMapper, CourseDocumentChunk>
        implements CourseDocumentChunkService {

}