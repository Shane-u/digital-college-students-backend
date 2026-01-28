package com.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.mapper.*;
import com.digital.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * 数据导出服务 - 用于Python训练
 */
@Service
@Slf4j
public class DataExportService {

    @Resource
    private UserBehaviorMapper userBehaviorMapper;
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private ContestMapper contestMapper;
    
    @Resource
    private JobInfoMapper jobInfoMapper;

    /**
     * 导出竞赛推荐训练数据
     * 输出文件：training_data_contest.csv
     */
    public void exportContestTrainingData(String outputPath) throws IOException {
        log.info("开始导出竞赛推荐训练数据...");
        
        FileWriter writer = new FileWriter(outputPath);
        // CSV表头
        writer.append("user_id,item_id,behavior_type,behavior_value,")
              .append("user_major,user_grade,user_school,")
              .append("contest_class_first,contest_class_second,contest_level,")
              .append("timestamp\n");
        
        // 查询所有竞赛相关的用户行为
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
            new QueryWrapper<UserBehavior>()
                .eq("itemType", UserBehavior.ItemType.CONTEST.getCode())
                .orderByAsc("createTime")
        );
        
        int count = 0;
        for (UserBehavior behavior : behaviors) {
            User user = userMapper.selectById(behavior.getUserId());
            Contest contest = contestMapper.selectById(behavior.getItemId());
            
            if (user == null || contest == null) {
                continue;
            }
            
            // 写入CSV行
            writer.append(String.valueOf(behavior.getUserId())).append(",")
                  .append(String.valueOf(behavior.getItemId())).append(",")
                  .append(behavior.getBehaviorType()).append(",")
                  .append(behavior.getBehaviorValue().toString()).append(",")
                  .append(escapeCsv(user.getMajor())).append(",")
                  .append(escapeCsv(user.getGrade())).append(",")
                  .append(escapeCsv(user.getSchool())).append(",")
                  .append(escapeCsv(contest.getContestClassFirst())).append(",")
                  .append(escapeCsv(contest.getContestClassSecond())).append(",")
                  .append(escapeCsv(contest.getLevelName())).append(",")
                  .append(String.valueOf(behavior.getCreateTime().getTime()))
                  .append("\n");
            
            count++;
            if (count % 1000 == 0) {
                log.info("已导出 {} 条数据", count);
            }
        }
        
        writer.close();
        log.info("竞赛推荐训练数据导出完成，共 {} 条", count);
    }

    /**
     * 导出职业推荐训练数据
     */
    public void exportJobTrainingData(String outputPath) throws IOException {
        log.info("开始导出职业推荐训练数据...");
        
        FileWriter writer = new FileWriter(outputPath);
        writer.append("user_id,item_id,behavior_type,behavior_value,")
              .append("user_major,user_grade,user_school,")
              .append("job_name,job_content,job_company,job_salary,")
              .append("timestamp\n");
        
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(
            new QueryWrapper<UserBehavior>()
                .eq("itemType", UserBehavior.ItemType.JOB.getCode())
                .orderByAsc("createTime")
        );
        
        int count = 0;
        for (UserBehavior behavior : behaviors) {
            User user = userMapper.selectById(behavior.getUserId());
            JobInfo job = jobInfoMapper.selectById(behavior.getItemId());
            
            if (user == null || job == null) {
                continue;
            }
            
            writer.append(String.valueOf(behavior.getUserId())).append(",")
                  .append(String.valueOf(behavior.getItemId())).append(",")
                  .append(behavior.getBehaviorType()).append(",")
                  .append(behavior.getBehaviorValue().toString()).append(",")
                  .append(escapeCsv(user.getMajor())).append(",")
                  .append(escapeCsv(user.getGrade())).append(",")
                  .append(escapeCsv(user.getSchool())).append(",")
                  .append(escapeCsv(job.getWorkName())).append(",")
                  .append(escapeCsv(job.getWorkContent())).append(",")
                  .append(escapeCsv(job.getCompanyName())).append(",")
                  .append(escapeCsv(job.getWorkSalary())).append(",")
                  .append(String.valueOf(behavior.getCreateTime().getTime()))
                  .append("\n");
            
            count++;
        }
        
        writer.close();
        log.info("职业推荐训练数据导出完成，共 {} 条", count);
    }

    /**
     * 导出用户特征数据（用于生成用户Embedding）
     */
    public void exportUserFeatures(String outputPath) throws IOException {
        log.info("开始导出用户特征数据...");
        FileWriter writer = new FileWriter(outputPath);
        writer.append("user_id,major,grade,school,user_profile\n");
        
        List<User> users = userMapper.selectList(null);
        int count = 0;
        for (User user : users) {
            writer.append(String.valueOf(user.getId())).append(",")
                  .append(escapeCsv(user.getMajor())).append(",")
                  .append(escapeCsv(user.getGrade())).append(",")
                  .append(escapeCsv(user.getSchool())).append(",")
                  .append(escapeCsv(user.getUserProfile()))
                  .append("\n");
            count++;
        }
        
        writer.close();
        log.info("用户特征数据导出完成，共 {} 条", count);
    }

    /**
     * 导出物品特征数据（用于生成物品Embedding）
     */
    public void exportItemFeatures(String itemType, String outputPath) throws IOException {
        log.info("开始导出 {} 物品特征数据...", itemType);
        FileWriter writer = new FileWriter(outputPath);
        
        if (UserBehavior.ItemType.CONTEST.getCode().equals(itemType)) {
            writer.append("item_id,contest_name,class_first,class_second,level,organiser\n");
            List<Contest> contests = contestMapper.selectList(null);
            int count = 0;
            for (Contest contest : contests) {
                writer.append(String.valueOf(contest.getId())).append(",")
                      .append(escapeCsv(contest.getContestName())).append(",")
                      .append(escapeCsv(contest.getContestClassFirst())).append(",")
                      .append(escapeCsv(contest.getContestClassSecond())).append(",")
                      .append(escapeCsv(contest.getLevelName())).append(",")
                      .append(escapeCsv(contest.getOrganiserName()))
                      .append("\n");
                count++;
            }
            log.info("竞赛物品特征数据导出完成，共 {} 条", count);
        } else if (UserBehavior.ItemType.JOB.getCode().equals(itemType)){
            writer.append("item_id,job_name,job_content,company_name,salary,work_year\n");
            List<JobInfo> jobs = jobInfoMapper.selectList(null);
            int count = 0;
            for (JobInfo job : jobs) {
                writer.append(String.valueOf(job.getId())).append(",")
                      .append(escapeCsv(job.getWorkName())).append(",")
                      .append(escapeCsv(job.getWorkContent())).append(",")
                      .append(escapeCsv(job.getCompanyName())).append(",")
                      .append(escapeCsv(job.getWorkSalary())).append(",")
                      .append(escapeCsv(job.getWorkYear()))
                      .append("\n");
                count++;
            }
            log.info("职业物品特征数据导出完成，共 {} 条", count);
        }
        
        writer.close();
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // 替换内部引号为双引号
        String escapedValue = value.replace("\"", "\"\"");
        if (escapedValue.contains(",") || escapedValue.contains("\n") || escapedValue.contains("\r") || escapedValue.contains("\"")) {
            return "\"" + escapedValue + "\"";
        }
        return escapedValue;
    }
}
