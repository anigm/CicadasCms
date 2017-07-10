package com.zhiliao.module.web.cms.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhiliao.common.exception.CmsException;
import com.zhiliao.common.lucene.util.IndexObject;
import com.zhiliao.common.utils.*;
import com.zhiliao.module.web.cms.service.CategoryService;
import com.zhiliao.module.web.cms.service.ContentService;
import com.zhiliao.module.web.cms.service.TagService;
import com.zhiliao.module.web.cms.vo.TCmsContentVo;
import com.zhiliao.mybatis.mapper.master.TCmsContentMapper;
import com.zhiliao.mybatis.model.master.TCmsCategory;
import com.zhiliao.mybatis.model.master.TCmsContent;
import com.zhiliao.mybatis.model.master.TCmsModelFiled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;

;

/**
 * Description:内容服务
 *
 * @author Jin
 * @create 2017-04-18
 **/
@Service
@CacheConfig(cacheNames = "cms-content-cache")
public class ContentServiceImpl implements ContentService{

    @Value("${system.http.protocol}")
    private String httpProtocol;

    @Value("${system.site.page.size}")
    private String pageSize;

    @Autowired
    private TCmsContentMapper contentMapper;

    @Autowired
    private TagService tagService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private LuceneServiceImpl luceneService;

    @Override
    public String save(TCmsContent pojo) {
        return null;
    }

    @Override
    public String update(TCmsContent pojo) {
        return null;
    }

    @Override
    public String delete(Long[] ids) {
        boolean flag_=false;
        if(ids!=null&&ids.length>0)
            for (Long id : ids){
                flag_ =  contentMapper.deleteByPrimaryKey(id)>0;
            }
        if(flag_)
            return JsonUtil.toSUCCESS("操作成功","content-tab",false);
         return JsonUtil.toERROR("操作失败");
    }

    @Override
    public PageInfo<TCmsContent> page(Integer pageNumber, Integer pageSize, TCmsContent pojo) {
        PageHelper.startPage(pageNumber,pageSize);
        return new PageInfo(contentMapper.selectByCondition(pojo));
    }

    @Override
    public PageInfo<TCmsContent> page(Integer pageNumber, Integer pageSize) {
        PageHelper.startPage(pageNumber,pageSize);
        return new PageInfo(contentMapper.selectAll());
    }

    @Override
    public TCmsContent findById(Long id) {
        return contentMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<TCmsContent> findList(TCmsContent pojo) {
        return contentMapper.select(pojo);
    }

    @Override
    public List<TCmsContent> findAll() {
        return contentMapper.selectAll();
    }


    @Override
    public PageInfo<TCmsContent> page(Integer pageNumber, Integer pageSize, TCmsContentVo pojo) {
        PageHelper.startPage(pageNumber,pageSize);
        return new PageInfo(contentMapper.selectByCondition(pojo));
    }

    @Cacheable(key = "'find-contentId-'+#p0+'-tableName-'+#p1")
    @Override
    public Map findContentByContentIdAndTableName(Long contentId, String tableName) {
        Map result =contentMapper.selectByContentIdAndTableName(contentId,tableName);
        if(CmsUtil.isNullOrEmpty(result))
            throw new CmsException("内容不存在或已删除！");
        return result;
    }

    @Override
    public String recovery(Long[] ids) {
        /*多此一举,哈哈*/
        boolean flag_=false;
        if(ids!=null&&ids.length>0)
            for (Long id : ids){
                TCmsContent cmsContent = contentMapper.selectByPrimaryKey(id);
                if(cmsContent.getStatus()<0)
                    cmsContent.setStatus(1);
                else
                    cmsContent.setStatus(-1);
                flag_ =  contentMapper.updateByPrimaryKeySelective(cmsContent)>0;
            }
        if(flag_)
            return JsonUtil.toSUCCESS("操作成功","content-tab",false);
        return JsonUtil.toERROR("操作失败");
    }

    @CacheEvict(cacheNames ="cms-content-cache",allEntries = true)
    @Transactional(transactionManager = "masterTransactionManager",rollbackFor = Exception.class)
    @Override
    public String save(TCmsContent content, String tableName, Map<String, Object> formParam, String[] tags) throws SQLException {
        /*初始化文章的推荐和顶置为false*/
        content.setRecommend(CmsUtil.isNullOrEmpty(content.getRecommend())?false:true);
        content.setTop(CmsUtil.isNullOrEmpty(content.getTop())?false:true);
        if(contentMapper.insert(content)>0) {
            /*创建lucene索引*/
            IndexObject indexObject = new IndexObject();
            indexObject.setId(content.getContentId());
            indexObject.setTitle(content.getTitle());
            indexObject.setKeywords(content.getKeywords());
            indexObject.setDescripton(content.getDescription());
            indexObject.setPostDate(DateUtil.formatDateTime(content.getInputdate()));
            indexObject.setUrl(this.httpProtocol+"://"+ ControllerUtil.getDomain()+"/front/"+content.getSiteId()+"/"+content.getCategoryId()+"/"+content.getContentId());
            luceneService.save(indexObject);
            /*保存和文章管理的Tag*/
            if (tags != null)
                for (String tag : tags) {
                    tagService.save(content.getContentId(), tag);
                }
            this.SaveModelFiledParam(formParam,content,tableName,null);
            return JsonUtil.toSUCCESS("操作成功", "content-tab", true);
        }
        return JsonUtil.toERROR("操作失败");
    }

    @CacheEvict(cacheNames ="cms-content-cache",allEntries = true)
    @Transactional(transactionManager = "masterTransactionManager",rollbackFor = Exception.class)
    @Override
    public String update(TCmsContent content, String tableName, List<TCmsModelFiled> cmsModelFileds, Map<String, Object> formParam, String[] tags) throws SQLException {
        /*初始化文章的推荐和顶置为false*/
        content.setRecommend(CmsUtil.isNullOrEmpty(content.getRecommend())?false:true);
        content.setTop(CmsUtil.isNullOrEmpty(content.getTop())?false:true);
        content.setUpdatedate(new Date());
        if(contentMapper.updateByPrimaryKeySelective(content)>0) {
             /*创建lucene索引*/
            IndexObject indexObject = new IndexObject();
            indexObject.setId(content.getContentId());
            indexObject.setTitle(content.getTitle());
            indexObject.setKeywords(content.getKeywords());
            indexObject.setDescripton(content.getDescription());
            indexObject.setPostDate(DateUtil.formatDateTime(content.getInputdate()));
            indexObject.setUrl(this.httpProtocol+"://"+ ControllerUtil.getDomain()+"/front/"+content.getSiteId()+"/"+content.getCategoryId()+"/"+content.getContentId());
            luceneService.update(indexObject);
            /*保存和文章管理的Tag*/
            if (tags != null)
                for (String tag : tags) {
                    tagService.save(content.getContentId(), tag);
                }
            this.SaveModelFiledParam(formParam,content,tableName,cmsModelFileds);
            return JsonUtil.toSUCCESS("操作成功", "content-tab", true);
        }
        return JsonUtil.toERROR("操作失败");
    }

    @Transactional(transactionManager = "masterTransactionManager",rollbackFor = Exception.class)
    public int SaveModelFiledParam(Map<String, Object> formParam,TCmsContent content,String tableName,List<TCmsModelFiled> cmsModelFileds) throws SQLException {
        if(!CmsUtil.isNullOrEmpty(formParam)) {
            String exeSql;
            Connection  connection = dataSource.getConnection();
            Statement statement = connection.createStatement();
            String selectSql = "select * from t_cms_content_"+tableName+" where content_id="+content.getContentId();
            ResultSet resultSet =statement.executeQuery(selectSql);
            /*判断内容扩展表是否存在数据*/
            if(!resultSet.next()) {
                exeSql = "insert into t_cms_content_" + tableName.trim() + " set ";
                exeSql += "`content_id`=" + content.getContentId() + ", ";
                for (Map.Entry<String, Object> entry : formParam.entrySet()) {
                    exeSql += "`" + entry.getKey() + "`";
                    if(CmsUtil.isNullOrEmpty(entry.getValue()))
                        return 0;
                    if (entry.getValue() instanceof Integer) {
                        exeSql += "=" + entry.getValue() + ", ";
                    } else if (entry.getValue().getClass().isArray()) {
                        /*遍历字符数组，数组来源为checkbox和多图上传*/
                        String[] result = (String[]) entry.getValue();
                        if (result != null) {
                            String tmp = "";
                            for (String value : result) {
                                if(StrUtil.isBlank(value))
                                    continue;
                                tmp += value + "#";
                            }
                            exeSql += "='" + tmp.substring(0, tmp.length() - 1) + "', ";
                        }
                    } else {
                        exeSql += "='" + entry.getValue() + "', ";
                    }
                }
                /*执勤Sql前截取最后面的空格和英文逗号，并加上‘;’*/
                exeSql = exeSql.substring(0, exeSql.length() - 2) + ";";
                int status= statement.executeUpdate(exeSql);
                statement.close();
                connection.close();
                return status;
            }else {
                exeSql = "UPDATE t_cms_content_" + tableName.trim() + " set ";
                      /*遍历Map保存扩展数据表,代码有些复杂*/
                for (TCmsModelFiled filed : cmsModelFileds) {
                    if(CmsUtil.isNullOrEmpty(formParam.get(filed.getFiledName()))) continue;
                    exeSql += "`" + filed.getFiledName() + "`";
                    if (formParam.get(filed.getFiledName()) instanceof Integer) {
                        exeSql += "=" + formParam.get(filed.getFiledName()) + ", ";
                    } else if (!CmsUtil.isNullOrEmpty(formParam.get(filed.getFiledName())) && formParam.get(filed.getFiledName()).getClass().isArray()) {
                    /*遍历字符数组，数组来源为checkbox和多图上传*/
                        String[] result = (String[]) formParam.get(filed.getFiledName());
                        if (result != null) {
                            String tmp = "";
                            for (String value : result) {
                                tmp += value + "#";
                            }
                            exeSql += "='" + tmp.substring(0, tmp.length() - 1) + "', ";
                        }
                    } else {
                        exeSql += "='" + formParam.get(filed.getFiledName()) + "', ";
                    }
                }
                /*截取最后面的空格和英文逗号，并加上‘;’*/
                exeSql = exeSql.substring(0, exeSql.length() - 2) + "where `content_id`=" + content.getContentId() + ";";
                int status =statement.executeUpdate(exeSql);
                statement.close();
                connection.close();
                return status;
            }
        }
        return 0;
    }

    @Override
    public PageInfo<TCmsContent> findContentListByModelFiledValue(int pageNumber,Long categoryId, String tableName, Map<String, Object> param) {
        PageHelper.startPage(pageNumber,Integer.parseInt(pageSize));
        return new PageInfo<>(contentMapper.selectByTableNameAndMap(tableName,categoryId,param));
    }

    @Cacheable(key = "'find-siteid-'+#p0+'-categoryid-'+#p1+'-orderby-'+#p2+'-size-'+#p3+'-hasChild-'+#p4+'-isHot-'+#p5")
    @Override
    public PageInfo<TCmsContent> findContentListBySiteIdAndCategoryId(Integer siteId,
                                                                      Long categoryId,
                                                                      Integer orderBy,
                                                                      Integer size,
                                                                      Integer hasChild,
                                                                      Integer isHot) {
        PageHelper.startPage(1, size);
        String inExpress =String.valueOf(categoryId);
        /*如果包含子类栏目*/
        if(hasChild==1) {
            String tmp ="";
            List<TCmsCategory> cats =categoryService.findCategoryListByPid(categoryId);
            if(CmsUtil.isNullOrEmpty(cats))
                throw new CmsException("标签调用出错,当前栏目下没有子栏目！");
            for(TCmsCategory cat :cats){
                tmp+=cat.getCategoryId()+",";
            }
            inExpress+=","+tmp.substring(0,tmp.length()-1);
        }
        return new PageInfo<>(contentMapper.selectByContentListBySiteIdAndCategoryId(siteId,categoryId,inExpress,orderBy,isHot));
    }

    @Cacheable(key = "'page-pageNumber-'+#p0+'-siteId-'+#p1+'-categoryId-'+#p2")
    @Override
    public PageInfo<TCmsContent> page(Integer pageNumber,Integer siteId, Long categoryId) {
        /* 判断当前分类下有没有内容,如果有内容就直接返回*/
        PageHelper.startPage(pageNumber,Integer.parseInt(this.pageSize));
        List<TCmsContent> list =contentMapper.selectByCategoyId(categoryId,siteId);
        if(!CmsUtil.isNullOrEmpty(list)&&list.size()>0)
            return new PageInfo<>(list);

        /* 查询当前分类的父类下的内容列表 */
        TCmsCategory category= categoryService.findById(categoryId);
        if(CmsUtil.isNullOrEmpty(category))
            throw new CmsException("栏目["+categoryId+"]不存在！");

        /*查询父类列表*/
        PageHelper.startPage(pageNumber,Integer.parseInt(this.pageSize));
        list = contentMapper.selectByCategoyId(category.getParentId(),siteId);
        if(!CmsUtil.isNullOrEmpty(list)&&list.size()>0)
            return new PageInfo<>(list);

        /*查询当前栏目的子类*/
        PageHelper.startPage(pageNumber,Integer.parseInt(this.pageSize));
        return new PageInfo<>(contentMapper.selectByCategoyParentId(category.getCategoryId(),siteId));
    }


    @Async
    @Override
    public int viewUpdate(Long contentId){
        TCmsContent content = this.findById(contentId);
        content.setContentId(contentId);
        content.setViewNum(content.getViewNum()+1);
        return contentMapper.updateByPrimaryKeySelective(content);
    }
}