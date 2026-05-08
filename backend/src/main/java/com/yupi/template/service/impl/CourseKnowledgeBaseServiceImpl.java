package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.mapper.CourseKnowledgeBaseMapper;
import com.yupi.template.model.dto.course.CreateKnowledgeBaseRequest;
import com.yupi.template.model.entity.CourseKnowledgeBase;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.KnowledgeBaseStatusEnum;
import com.yupi.template.model.vo.KnowledgeBaseVO;
import com.yupi.template.service.CourseKnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程知识库服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class CourseKnowledgeBaseServiceImpl extends ServiceImpl<CourseKnowledgeBaseMapper, CourseKnowledgeBase>
        implements CourseKnowledgeBaseService {

    @Override
    public String createKnowledgeBase(CreateKnowledgeBaseRequest request, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request.getName() == null || request.getName().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "知识库名称不能为空");

        // 构建知识库实体
        CourseKnowledgeBase kb = new CourseKnowledgeBase();
        kb.setKbId(IdUtil.simpleUUID());
        kb.setUserId(loginUser.getId());
        kb.setName(request.getName().trim());
        kb.setCourseName(request.getCourseName());
        kb.setDescription(request.getDescription());
        kb.setStatus(KnowledgeBaseStatusEnum.NORMAL.getValue());
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setCreateTime(LocalDateTime.now());

        this.save(kb);

        log.info("课程知识库创建成功, kbId={}, userId={}, name={}", kb.getKbId(), loginUser.getId(), kb.getName());
        return kb.getKbId();
    }

    @Override
    public List<KnowledgeBaseVO> listMyKnowledgeBase(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 查询当前用户的知识库，按创建时间倒序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", loginUser.getId())
                .orderBy("createTime", false);

        List<CourseKnowledgeBase> list = this.list(queryWrapper);

        return list.stream()
                .map(this::toKnowledgeBaseVO)
                .collect(Collectors.toList());
    }

    /**
     * Entity 转 VO
     */
    private KnowledgeBaseVO toKnowledgeBaseVO(CourseKnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setKbId(kb.getKbId());
        vo.setName(kb.getName());
        vo.setCourseName(kb.getCourseName());
        vo.setDescription(kb.getDescription());
        vo.setStatus(kb.getStatus());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setCreateTime(kb.getCreateTime());
        vo.setUpdateTime(kb.getUpdateTime());
        return vo;
    }

}