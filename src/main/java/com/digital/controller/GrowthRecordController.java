package com.digital.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digital.common.BaseResponse;
import com.digital.common.BatchDeleteRequest;
import com.digital.common.DeleteRequest;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.MinioManager;
import com.digital.mapper.GrowthFileMapper;
import com.digital.mapper.GrowthImageMapper;
import com.digital.model.dto.growthrecord.GrowthRecordAddRequest;
import com.digital.model.dto.growthrecord.GrowthRecordQueryRequest;
import com.digital.model.dto.growthrecord.GrowthRecordUpdateRequest;
import com.digital.model.entity.GrowthFile;
import com.digital.model.entity.GrowthImage;
import com.digital.model.entity.GrowthRecord;
import com.digital.model.entity.User;
import com.digital.model.vo.GrowthFileVO;
import com.digital.model.vo.GrowthImageVO;
import com.digital.model.vo.GrowthRecordEventVO;
import com.digital.model.vo.GrowthRecordStatisticsVO;
import com.digital.model.vo.GrowthRecordVO;
import com.digital.model.vo.MilestoneStatisticsVO;
import com.digital.model.vo.PhotoWallStatisticsVO;
import com.digital.service.GrowthRecordService;
import com.digital.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成长记录接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/growth-record")
@Slf4j
public class GrowthRecordController {

    @Resource
    private GrowthRecordService growthRecordService;

    @Resource
    private UserService userService;

    @Resource
    private MinioManager minioManager;

    @Resource
    private GrowthImageMapper growthImageMapper;

    @Resource
    private GrowthFileMapper growthFileMapper;

    /**
     * 上传文件（支持 MD5 秒传）
     *
     * @param file    文件
     * @param request HTTP 请求
     * @return 文件信息
     */
    @PostMapping("/upload/file")
    public BaseResponse<GrowthFileVO> uploadFile(@RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        User loginUser = userService.getLoginUser(request);
        try {
            // 读取文件内容
            byte[] fileBytes = file.getBytes();
            InputStream inputStream = new ByteArrayInputStream(fileBytes);

            // 并行计算默克尔树 MD5（使用 2MB 分块，多线程并行计算）
            String md5 = minioManager.calculateMerkleTreeMd5(new ByteArrayInputStream(fileBytes), 2 * 1024 * 1024);

            // 检查文件是否已存在（秒传）- 从数据库查询
            QueryWrapper<GrowthFile> fileQueryWrapper = new QueryWrapper<>();
            fileQueryWrapper.eq("fileMd5", md5);
            fileQueryWrapper.last("limit 1");
            GrowthFile existingFile = growthFileMapper.selectOne(fileQueryWrapper);
            String fileUrl;
            String objectName;

            if (existingFile != null) {
                // 秒传：文件已存在，复用文件 URL，但创建新记录
                fileUrl = existingFile.getFileUrl();
            } else {
                // 上传新文件
                objectName = "files/" + md5 + "/" + file.getOriginalFilename();
                fileUrl = minioManager.putObject(objectName, inputStream, file.getContentType(), file.getSize());
            }

            // 保存文件记录到数据库（即使秒传也创建新记录）
            GrowthFile growthFile = new GrowthFile();
            growthFile.setUserId(loginUser.getId());
            // fileUrl中9002全部替换为9003
            fileUrl = fileUrl.replace(":9002/", ":9003/");
            growthFile.setFileUrl(fileUrl);
            growthFile.setFileName(file.getOriginalFilename());
            growthFile.setFileSize(file.getSize());
            growthFile.setFileMd5(md5);
            growthFile.setCreateTime(new Date());
            growthFileMapper.insert(growthFile);

            // 返回文件信息
            GrowthFileVO fileVO = new GrowthFileVO();
            BeanUtils.copyProperties(growthFile, fileVO);
            return ResultUtils.success(fileVO);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
    }

    /**
     * 上传图片（支持 MD5 秒传）
     *
     * @param file      图片文件
     * @param type      类型：1-单纯作为照片存储，2-成长记录中的照片
     * @param uploadTime 目标上传时间（用户选择的上传时间，格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）
     * @param request   HTTP 请求
     * @return 图片信息
     */
    @PostMapping("/upload/image")
    public BaseResponse<GrowthImageVO> uploadImage(@RequestPart("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "1") Integer type,
            @RequestParam(value = "uploadTime", required = false) String uploadTime,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不能为空");
        }
        if (type != 1 && type != 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "类型必须是1或2");
        }
        User loginUser = userService.getLoginUser(request);
        try {
            // 读取文件内容
            byte[] fileBytes = file.getBytes();
            InputStream inputStream = new ByteArrayInputStream(fileBytes);

            // 并行计算默克尔树 MD5（使用 2MB 分块，多线程并行计算）
            String md5 = minioManager.calculateMerkleTreeMd5(new ByteArrayInputStream(fileBytes), 2 * 1024 * 1024);

            // 检查文件是否已存在（秒传）- 从数据库查询
            QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
            imageQueryWrapper.eq("imageMd5", md5);
            imageQueryWrapper.last("limit 1");
            GrowthImage existingImage = growthImageMapper.selectOne(imageQueryWrapper);
            String imageUrl;
            String objectName;

            if (existingImage != null) {
                // 秒传：文件已存在，复用文件 URL，但创建新记录
                imageUrl = existingImage.getImageUrl();
            } else {
                // 上传新文件
                objectName = "images/" + md5 + "/" + file.getOriginalFilename();
                imageUrl = minioManager.putObject(objectName, inputStream, file.getContentType(), file.getSize());
            }

            // 保存图片记录到数据库（即使秒传也创建新记录）
            GrowthImage growthImage = new GrowthImage();
            growthImage.setUserId(loginUser.getId());
            imageUrl = imageUrl.replace(":9002/", ":9003/");
            growthImage.setImageUrl(imageUrl);
            growthImage.setImageName(file.getOriginalFilename());
            growthImage.setImageSize(file.getSize());
            growthImage.setImageMd5(md5);
            growthImage.setType(type);
            growthImage.setCreateTime(new Date());
            
            // 解析并设置目标上传时间
            if (uploadTime != null && !uploadTime.trim().isEmpty()) {
                try {
                    Date parsedUploadTime = parseUploadTime(uploadTime);
                    growthImage.setUploadTime(parsedUploadTime);
                } catch (Exception e) {
                    log.warn("解析上传时间失败，使用当前时间: {}", uploadTime, e);
                    growthImage.setUploadTime(new Date());
                }
            } else {
                // 如果没有提供上传时间，使用当前时间
                growthImage.setUploadTime(new Date());
            }
            
            growthImageMapper.insert(growthImage);

            // 返回图片信息
            GrowthImageVO imageVO = new GrowthImageVO();
            BeanUtils.copyProperties(growthImage, imageVO);
            return ResultUtils.success(imageVO);
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        }
    }

    /**
     * 创建成长记录
     *
     * @param growthRecordAddRequest 添加请求
     * @param request               HTTP 请求
     * @return 成长记录ID
     */
    @PostMapping("/add")
    public BaseResponse<Long> addGrowthRecord(@RequestBody GrowthRecordAddRequest growthRecordAddRequest,
            HttpServletRequest request) {
        if (growthRecordAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        GrowthRecord growthRecord = new GrowthRecord();
        BeanUtils.copyProperties(growthRecordAddRequest, growthRecord);
        growthRecord.setUserId(loginUser.getId());
        growthRecordService.validGrowthRecord(growthRecord, true);
        boolean result = growthRecordService.save(growthRecord);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 关联图片和文件
        Long growthRecordId = growthRecord.getId();
        if (growthRecordAddRequest.getImageIds() != null && !growthRecordAddRequest.getImageIds().isEmpty()) {
            for (Long imageId : growthRecordAddRequest.getImageIds()) {
                GrowthImage image = growthImageMapper.selectById(imageId);
                if (image != null && image.getUserId().equals(loginUser.getId())) {
                    image.setGrowthRecordId(growthRecordId);
                    image.setType(2); // 设置为成长记录中的照片
                    growthImageMapper.updateById(image);
                }
            }
        }
        if (growthRecordAddRequest.getFileIds() != null && !growthRecordAddRequest.getFileIds().isEmpty()) {
            for (Long fileId : growthRecordAddRequest.getFileIds()) {
                GrowthFile file = growthFileMapper.selectById(fileId);
                if (file != null && file.getUserId().equals(loginUser.getId())) {
                    file.setGrowthRecordId(growthRecordId);
                    growthFileMapper.updateById(file);
                }
            }
        }

        return ResultUtils.success(growthRecordId);
    }

    /**
     * 照片墙 - 获取全部图片信息（含 type=1 与 type=2，按 uploadTime 倒序，不分页）
     *
     * @param request HTTP 请求
     * @return 图片列表
     */
    @GetMapping("/photo-wall/list")
    public BaseResponse<List<GrowthImageVO>> listPhotoWallImages(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        QueryWrapper<GrowthImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        // 包含 type=1 和 type=2（无需额外条件，全部图片皆返回）
        // 以用户选择的日历日期为准，对应 uploadTime 字段；按 uploadTime 倒序排列
        queryWrapper.orderByDesc("uploadTime", "createTime");
        List<GrowthImage> images = growthImageMapper.selectList(queryWrapper);
        List<GrowthImageVO> imageVOList = images.stream().map(image -> {
            GrowthImageVO imageVO = new GrowthImageVO();
            BeanUtils.copyProperties(image, imageVO);
            return imageVO;
        }).collect(Collectors.toList());
        return ResultUtils.success(imageVOList);
    }

    /**
     * 删除成长记录
     * 删除成长记录时，同时删除关联的图片和文件（Minio 中物理删除，数据库中逻辑删除）
     *
     * @param deleteRequest 删除请求
     * @param request       HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteGrowthRecord(@RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        GrowthRecord oldRecord = growthRecordService.getById(id);
        ThrowUtils.throwIf(oldRecord == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldRecord.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 查询关联的图片
        QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("growthRecordId", id);
        List<GrowthImage> images = growthImageMapper.selectList(imageQueryWrapper);

        // 查询关联的文件
        QueryWrapper<GrowthFile> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("growthRecordId", id);
        List<GrowthFile> files = growthFileMapper.selectList(fileQueryWrapper);

        // 从 Minio 中删除图片（物理删除）
        // 注意：即使多个记录引用了同一个文件，删除记录时也会删除 Minio 中的文件
        // 因为每个记录都有自己独立的文件记录
        for (GrowthImage image : images) {
            try {
                if (image.getImageUrl() != null && !image.getImageUrl().isEmpty()) {
                    // 检查是否还有其他记录使用这个图片（通过 imageUrl 或 imageMd5）
                    QueryWrapper<GrowthImage> checkImageWrapper = new QueryWrapper<>();
                    checkImageWrapper.eq("imageUrl", image.getImageUrl());
                    checkImageWrapper.ne("id", image.getId());
                    Long otherImageCount = growthImageMapper.selectCount(checkImageWrapper);
                    
                    // 如果没有其他记录使用这个图片，才从 Minio 中删除
                    if (otherImageCount == 0) {
                        minioManager.removeObjectByUrl(image.getImageUrl());
                        log.info("已从 Minio 删除图片: {}", image.getImageUrl());
                    } else {
                        log.info("图片被其他记录使用，跳过 Minio 删除: {}", image.getImageUrl());
                    }
                }
            } catch (Exception e) {
                log.error("删除 Minio 图片失败: {}", image.getImageUrl(), e);
                // 继续删除其他文件，不中断流程
            }
        }

        // 从 Minio 中删除文件（物理删除）
        for (GrowthFile file : files) {
            try {
                if (file.getFileUrl() != null && !file.getFileUrl().isEmpty()) {
                    // 检查是否还有其他记录使用这个文件（通过 fileUrl 或 fileMd5）
                    QueryWrapper<GrowthFile> checkFileWrapper = new QueryWrapper<>();
                    checkFileWrapper.eq("fileUrl", file.getFileUrl());
                    checkFileWrapper.ne("id", file.getId());
                    Long otherFileCount = growthFileMapper.selectCount(checkFileWrapper);
                    
                    // 如果没有其他记录使用这个文件，才从 Minio 中删除
                    if (otherFileCount == 0) {
                        minioManager.removeObjectByUrl(file.getFileUrl());
                        log.info("已从 Minio 删除文件: {}", file.getFileUrl());
                    } else {
                        log.info("文件被其他记录使用，跳过 Minio 删除: {}", file.getFileUrl());
                    }
                }
            } catch (Exception e) {
                log.error("删除 Minio 文件失败: {}", file.getFileUrl(), e);
                // 继续删除其他文件，不中断流程
            }
        }

        // 对数据库记录进行逻辑删除（图片和文件）
        for (GrowthImage image : images) {
            growthImageMapper.deleteById(image.getId());
        }
        for (GrowthFile file : files) {
            growthFileMapper.deleteById(file.getId());
        }

        // 删除成长记录（逻辑删除）
        boolean b = growthRecordService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新成长记录
     *
     * @param growthRecordUpdateRequest 更新请求
     * @param request                  HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateGrowthRecord(@RequestBody GrowthRecordUpdateRequest growthRecordUpdateRequest,
            HttpServletRequest request) {
        if (growthRecordUpdateRequest == null || growthRecordUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = growthRecordUpdateRequest.getId();
        GrowthRecord oldRecord = growthRecordService.getById(id);
        ThrowUtils.throwIf(oldRecord == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可更新
        if (!oldRecord.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        GrowthRecord growthRecord = new GrowthRecord();
        BeanUtils.copyProperties(growthRecordUpdateRequest, growthRecord);
        growthRecordService.validGrowthRecord(growthRecord, false);
        boolean result = growthRecordService.updateById(growthRecord);

        // 更新关联的图片和文件
        if (growthRecordUpdateRequest.getImageIds() != null) {
            // 先清除旧的关联
            QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
            imageQueryWrapper.eq("growthRecordId", id);
            List<GrowthImage> oldImages = growthImageMapper.selectList(imageQueryWrapper);
            for (GrowthImage oldImage : oldImages) {
                oldImage.setGrowthRecordId(null);
                oldImage.setType(1);
                growthImageMapper.updateById(oldImage);
            }
            // 设置新的关联
            if (!growthRecordUpdateRequest.getImageIds().isEmpty()) {
                for (Long imageId : growthRecordUpdateRequest.getImageIds()) {
                    GrowthImage image = growthImageMapper.selectById(imageId);
                    if (image != null && image.getUserId().equals(loginUser.getId())) {
                        image.setGrowthRecordId(id);
                        image.setType(2);
                        growthImageMapper.updateById(image);
                    }
                }
            }
        }
        if (growthRecordUpdateRequest.getFileIds() != null) {
            // 先清除旧的关联
            QueryWrapper<GrowthFile> fileQueryWrapper = new QueryWrapper<>();
            fileQueryWrapper.eq("growthRecordId", id);
            List<GrowthFile> oldFiles = growthFileMapper.selectList(fileQueryWrapper);
            for (GrowthFile oldFile : oldFiles) {
                oldFile.setGrowthRecordId(null);
                growthFileMapper.updateById(oldFile);
            }
            // 设置新的关联
            if (!growthRecordUpdateRequest.getFileIds().isEmpty()) {
                for (Long fileId : growthRecordUpdateRequest.getFileIds()) {
                    GrowthFile file = growthFileMapper.selectById(fileId);
                    if (file != null && file.getUserId().equals(loginUser.getId())) {
                        file.setGrowthRecordId(id);
                        growthFileMapper.updateById(file);
                    }
                }
            }
        }

        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取成长记录（单个查询）
     *
     * @param id      成长记录ID
     * @param request HTTP 请求
     * @return 成长记录VO
     */
    @GetMapping("/get")
    public BaseResponse<GrowthRecordVO> getGrowthRecordById(@RequestParam Long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        GrowthRecord growthRecord = growthRecordService.getById(id);
        if (growthRecord == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(growthRecordService.getGrowthRecordVO(growthRecord));
    }

    /**
     * 分页获取成长记录列表
     *
     * @param growthRecordQueryRequest 查询请求
     * @param request                 HTTP 请求
     * @return 分页结果
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<GrowthRecordVO>> listGrowthRecordByPage(
            @RequestBody GrowthRecordQueryRequest growthRecordQueryRequest, HttpServletRequest request) {
        if (growthRecordQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        growthRecordQueryRequest.setUserId(loginUser.getId());
        long current = growthRecordQueryRequest.getCurrent();
        long size = growthRecordQueryRequest.getPageSize();
        Page<GrowthRecord> recordPage = growthRecordService.page(new Page<>(current, size),
                growthRecordService.getQueryWrapper(growthRecordQueryRequest));
        Page<GrowthRecordVO> recordVOPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(),
                recordPage.getTotal());
        List<GrowthRecordVO> recordVOList = recordPage.getRecords().stream()
                .map(growthRecordService::getGrowthRecordVO).collect(Collectors.toList());
        recordVOPage.setRecords(recordVOList);
        return ResultUtils.success(recordVOPage);
    }

    /**
     * 获取统计信息（记录总数、照片总数、文件总数）
     *
     * @param request HTTP 请求
     * @return 统计信息
     */
    @GetMapping("/statistics")
    public BaseResponse<GrowthRecordStatisticsVO> getStatistics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(growthRecordService.getStatistics(loginUser.getId()));
    }

    /**
     * 获取事件列表（id 和事件描述）
     *
     * @param request HTTP 请求
     * @return 事件列表
     */
    @GetMapping("/event/list")
    public BaseResponse<List<GrowthRecordEventVO>> getEventList(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(growthRecordService.getEventList(loginUser.getId()));
    }

    /**
     * 模糊查询成长记录（根据事件描述）
     *
     * @param eventDesc 事件描述
     * @param request   HTTP 请求
     * @return 成长记录列表
     */
    @GetMapping("/search")
    public BaseResponse<List<GrowthRecordVO>> searchByEventDesc(@RequestParam String eventDesc,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(growthRecordService.searchByEventDesc(eventDesc, loginUser.getId()));
    }

    /**
     * 获取里程碑统计信息（4星及以上）
     *
     * @param request HTTP 请求
     * @return 里程碑统计信息
     */
    @GetMapping("/milestone/statistics")
    public BaseResponse<MilestoneStatisticsVO> getMilestoneStatistics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(growthRecordService.getMilestoneStatistics(loginUser.getId()));
    }

    /**
     * 获取照片墙统计信息
     *
     * @param request HTTP 请求
     * @return 照片墙统计信息
     */
    @GetMapping("/photo-wall/statistics")
    public BaseResponse<PhotoWallStatisticsVO> getPhotoWallStatistics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PhotoWallStatisticsVO statistics = new PhotoWallStatisticsVO();

        // 统计照片总数（包括同步到成长记录的和没有同步的）
        QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("userId", loginUser.getId());
        Long imageCount = growthImageMapper.selectCount(imageQueryWrapper);
        statistics.setImageCount(imageCount);

        // 获取最新记录时间（使用目标上传时间 uploadTime）
        imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("userId", loginUser.getId());
        imageQueryWrapper.isNotNull("uploadTime");
        imageQueryWrapper.orderByDesc("uploadTime");
        imageQueryWrapper.last("limit 1");
        GrowthImage latestImage = growthImageMapper.selectOne(imageQueryWrapper);
        if (latestImage != null && latestImage.getUploadTime() != null) {
            statistics.setLatestRecordTime(latestImage.getUploadTime());
        }

        // 计算总记录时长（最早上传时间 - 最晚上传时间）
        imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("userId", loginUser.getId());
        imageQueryWrapper.isNotNull("uploadTime");
        imageQueryWrapper.orderByAsc("uploadTime");
        imageQueryWrapper.last("limit 1");
        GrowthImage earliestImage = growthImageMapper.selectOne(imageQueryWrapper);
        if (earliestImage != null && earliestImage.getUploadTime() != null 
                && latestImage != null && latestImage.getUploadTime() != null) {
            long diff = latestImage.getUploadTime().getTime() - earliestImage.getUploadTime().getTime();
            long days = diff / (1000 * 60 * 60 * 24) + 1; // 确保有1天
            statistics.setTotalDays(days);
        } else {
            statistics.setTotalDays(0L);
        }

        return ResultUtils.success(statistics);
    }

    /**
     * 删除图片
     *
     * @param id      图片ID
     * @param request HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/image/delete")
    public BaseResponse<Boolean> deleteImage(@RequestParam Long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        GrowthImage image = growthImageMapper.selectById(id);
        ThrowUtils.throwIf(image == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!image.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 从 Minio 删除文件
        // minioManager.removeObject(image.getImageUrl());
        boolean result = growthImageMapper.deleteById(id) > 0;
        return ResultUtils.success(result);
    }

    /**
     * 批量删除图片
     *
     * @param batchDeleteRequest 批量删除请求（包含图片ID列表）
     * @param request            HTTP 请求
     * @return 删除成功的数量
     */
    @PostMapping("/image/batch-delete")
    public BaseResponse<Integer> batchDeleteImages(@RequestBody BatchDeleteRequest batchDeleteRequest,
            HttpServletRequest request) {
        if (batchDeleteRequest == null || batchDeleteRequest.getIds() == null
                || batchDeleteRequest.getIds().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片ID列表不能为空");
        }
        User loginUser = userService.getLoginUser(request);
        List<Long> ids = batchDeleteRequest.getIds();

        // 验证所有图片是否存在且属于当前用户
        List<GrowthImage> images = growthImageMapper.selectBatchIds(ids);
        if (images.size() != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "部分图片不存在");
        }

        // 检查权限：仅本人或管理员可删除
        boolean isAdmin = userService.isAdmin(request);
        for (GrowthImage image : images) {
            if (!image.getUserId().equals(loginUser.getId()) && !isAdmin) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除其他用户的图片");
            }
        }

        // 批量删除（逻辑删除）
        int deletedCount = 0;
        for (Long id : ids) {
            try {
                // 查询图片信息，用于检查是否需要从 Minio 删除
                GrowthImage image = growthImageMapper.selectById(id);
                if (image != null) {
                    // 检查是否还有其他记录使用这个图片
                    QueryWrapper<GrowthImage> checkImageWrapper = new QueryWrapper<>();
                    checkImageWrapper.eq("imageUrl", image.getImageUrl());
                    checkImageWrapper.ne("id", image.getId());
                    Long otherImageCount = growthImageMapper.selectCount(checkImageWrapper);

                    // 如果没有其他记录使用这个图片，从 Minio 中删除
                    if (otherImageCount == 0 && image.getImageUrl() != null && !image.getImageUrl().isEmpty()) {
                        try {
                            minioManager.removeObjectByUrl(image.getImageUrl());
                            log.info("已从 Minio 删除图片: {}", image.getImageUrl());
                        } catch (Exception e) {
                            log.error("删除 Minio 图片失败: {}", image.getImageUrl(), e);
                            // 继续删除数据库记录，不中断流程
                        }
                    }
                }

                // 删除数据库记录（逻辑删除）
                int result = growthImageMapper.deleteById(id);
                if (result > 0) {
                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("删除图片失败: id={}", id, e);
                // 继续删除其他图片，不中断流程
            }
        }

        return ResultUtils.success(deletedCount);
    }

    /**
     * 解析上传时间字符串
     * 支持格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd
     *
     * @param uploadTimeStr 上传时间字符串
     * @return Date 对象
     * @throws ParseException 解析失败
     */
    private Date parseUploadTime(String uploadTimeStr) throws ParseException {
        if (uploadTimeStr == null || uploadTimeStr.trim().isEmpty()) {
            return new Date();
        }
        String trimmed = uploadTimeStr.trim();
        SimpleDateFormat sdf;
        // 判断是否包含时间部分
        if (trimmed.length() > 10) {
            // yyyy-MM-dd HH:mm:ss
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        } else {
            // yyyy-MM-dd，设置为当天的 00:00:00
            sdf = new SimpleDateFormat("yyyy-MM-dd");
        }
        return sdf.parse(trimmed);
    }
}

