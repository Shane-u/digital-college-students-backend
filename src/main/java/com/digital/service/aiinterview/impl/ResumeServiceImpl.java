package com.digital.service.aiinterview.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.mapper.CandidateProfileMapper;
import com.digital.mapper.CandidateResumeMapper;
import com.digital.model.entity.CandidateProfile;
import com.digital.model.entity.CandidateResume;
import com.digital.service.aiinterview.LlmClient;
import com.digital.service.aiinterview.ResumeService;
import com.digital.service.aiinterview.AiInterviewFileStorageService;
import com.digital.model.vo.aiinterview.ResumeUploadVO;
import com.digital.model.vo.aiinterview.ResumeVO;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeServiceImpl implements ResumeService {

    private final CandidateResumeMapper resumeMapper;
    private final CandidateProfileMapper profileMapper;
    private final AiInterviewFileStorageService fileStorageService;
    private final LlmClient llmClient;

    private final Tika tika = new Tika();

    @Override
    public ResumeUploadVO uploadResume(Long userId, MultipartFile file) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "简历文件为空");

        String fileUrl = fileStorageService.uploadResumeFile(userId, file);
        String rawText = extractText(file);
        ThrowUtils.throwIf(StringUtils.isBlank(rawText), ErrorCode.OPERATION_ERROR, "无法从简历中抽取文本（可尝试换成可复制文本的 PDF/DOCX）");

        CandidateResume resume = new CandidateResume();
        resume.setUserId(userId);
        resume.setFileUrl(fileUrl);
        resume.setOriginalFilename(file.getOriginalFilename());
        resume.setFileType(file.getContentType());
        resume.setRawText(rawText);
        resume.setCreateTime(new Date());
        resume.setUpdateTime(new Date());
        resumeMapper.insert(resume);

        // 结构化解析（JSON）
        String parsedJson = parseResumeToJson(rawText);
        resume.setParsedJson(parsedJson);
        resume.setUpdateTime(new Date());
        resumeMapper.updateById(resume);

        // 写入 CandidateProfile（简单把 JSON 存进 extraJson，结构字段后续再细化）
        CandidateProfile profile = new CandidateProfile();
        profile.setResumeId(resume.getId());
        profile.setUserId(userId);
        profile.setExtraJson(parsedJson);
        profile.setCreateTime(new Date());
        profile.setUpdateTime(new Date());
        profileMapper.insert(profile);

        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setResumeId(resume.getId());
        vo.setFileUrl(fileUrl);
        vo.setParsed(true);
        vo.setPreview(buildPreview(parsedJson, rawText));
        return vo;
    }

    @Override
    public ResumeVO getResume(Long userId, Long resumeId) {
        CandidateResume resume = getResumeEntity(userId, resumeId);
        ResumeVO vo = new ResumeVO();
        vo.setResumeId(resume.getId());
        vo.setFileUrl(resume.getFileUrl());
        vo.setOriginalFilename(resume.getOriginalFilename());
        vo.setRawText(resume.getRawText());
        vo.setParsedJson(resume.getParsedJson());
        return vo;
    }

    @Override
    public CandidateResume getResumeEntity(Long userId, Long resumeId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(resumeId == null, ErrorCode.PARAMS_ERROR, "resumeId 不能为空");

        QueryWrapper<CandidateResume> qw = new QueryWrapper<>();
        qw.eq("id", resumeId).eq("userId", userId).eq("isDelete", 0);
        CandidateResume resume = resumeMapper.selectOne(qw);
        ThrowUtils.throwIf(resume == null, ErrorCode.NOT_FOUND_ERROR, "简历不存在");
        return resume;
    }

    @Override
    public List<ResumeVO> listResumes(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        QueryWrapper<CandidateResume> qw = new QueryWrapper<>();
        qw.eq("userId", userId).eq("isDelete", 0).orderByDesc("createTime");
        List<CandidateResume> list = resumeMapper.selectList(qw);
        List<ResumeVO> result = new ArrayList<>();
        for (CandidateResume resume : list) {
            ResumeVO vo = new ResumeVO();
            vo.setResumeId(resume.getId());
            vo.setFileUrl(resume.getFileUrl());
            vo.setOriginalFilename(resume.getOriginalFilename());
            vo.setRawText(resume.getRawText());
            vo.setParsedJson(resume.getParsedJson());
            result.add(vo);
        }
        return result;
    }

    private String extractText(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            String text = tika.parseToString(in);
            if (text == null) {
                return "";
            }
            return text.replace("\u0000", "").trim();
        } catch (Exception e) {
            log.warn("简历抽取文本失败 filename={}", file.getOriginalFilename(), e);
            // txt 兜底
            try {
                return new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private String parseResumeToJson(String rawText) {
        String system = """
                你是一个简历解析器。请将用户简历解析为 JSON，必须是严格 JSON（不要 Markdown，不要多余解释）。
                输出字段建议：basicInfo{name,email,phone,location}, education[], workExperience[], projects[], skills[], certifications[]。
                如果缺失字段请输出空数组/空字符串，不要编造事实。
                """;
        String user = "简历文本如下：\n" + rawText;
        String content = llmClient.complete(system, user);
        return content.trim();
    }

    private String buildPreview(String parsedJson, String rawText) {
        if (StringUtils.isNotBlank(parsedJson)) {
            return parsedJson.length() > 500 ? parsedJson.substring(0, 500) + "..." : parsedJson;
        }
        return rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText;
    }
}

