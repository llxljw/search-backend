## 聚合搜索项目总结回顾

### 1.项目的基本介绍

搜索聚合平台（搜索中台）

用户可以在一个页面搜索出不同来源的数据，能够提升我们的检索效率和搜索体验。

企业角度：当企业有多个项目的数据需要被搜索时，无需针对每个项目单独开发搜索功能，可以直接将数据接入搜索中台，提升开发效率。

### 2.项目用到的技术

**前端：** vue3+ant-design-vue

**后端：** springboot+es(重点)+数据抓取(Jsoup,Hutools)+数据同步（es和数据库间同步）

### 3.项目架构图

![](https://lxbucket-1315016093.cos.ap-beijing.myqcloud.com/images/1.png)

### 4.前端

动态路由，router和route,url和页面状态同步，组件间传值。

### 5.后端

#### 一.数据抓取

主要使用到了Jsoup。

[参考文档](http://www.dtmao.cc/Html5/69518.html)

帖子会持久化到数据库中！通过一个定时任务，同步到ES中。在数据抓取之前会先查询es中是否有足够数量的帖子。

图片，视频不会存入数据库

#### 二.设计模式

思考：怎么样能让前端又能一次搜出所有数据、又能够分别获取某一类数据（比如分页场景）
解决方案：
新增 type 字段：前端传 type 调用后端同一个接口，后端根据 type 调用不同的 service 查询
比如前端传递 type =  user，后端执行 userService.query 



问题：type 增多后，要把查询逻辑堆积在 controller 代码里么？
思考：怎么能让搜索系统 更轻松地 接入更多的数据源？

##### 1.门面模式

帮助我们用户（客户端）去更轻松的实现功能，不需要关心门面背后的细节。

聚合搜索类业务基本就是门面模式：即前端不用关心后端从哪里、怎么去取不同来源、怎么去聚合不同来源的数据，更方便的获取到内容。

补充：当调用你系统（接口）的客户端觉得麻烦的时候，你就应该思考，是不是可以抽象一个门面了。



![门面模式图解](https://lxbucket-1315016093.cos.ap-beijing.myqcloud.com/images/image-20230325120636868.png)

门面代码：

```java
@Component
@Slf4j
public class SearchFacade {
    @Resource
    private UserService userService;

    @Resource
    private PostService postService;

    @Resource
    private PictureService pictureService;

    public SearchVO searchAll(@RequestBody SearchRequest searchRequest, HttpServletRequest request) {
        String type = searchRequest.getType();
        SearchTypeEnum searchTypeEnum = SearchTypeEnum.getEnumByValue(type);
        ThrowUtils.throwIf(StringUtils.isBlank(type), ErrorCode.PARAMS_ERROR);
        String searchText = searchRequest.getSearchText();

        // 搜索出所有数据
        if (searchTypeEnum == null) {
            CompletableFuture<Page<UserVO>> userTask = CompletableFuture.supplyAsync(() -> {
                UserQueryRequest userQueryRequest = new UserQueryRequest();
                userQueryRequest.setUserName(searchText);
                Page<UserVO> userVOPage = userService.listUserVOByPage(userQueryRequest);
                return userVOPage;
            });

            CompletableFuture<Page<PostVO>> postTask = CompletableFuture.supplyAsync(() -> {
                PostQueryRequest postQueryRequest = new PostQueryRequest();
                postQueryRequest.setSearchText(searchText);
                Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest, request);
                return postVOPage;
            });

            CompletableFuture<Page<Picture>> pictureTask = CompletableFuture.supplyAsync(() -> {
                Page<Picture> picturePage = pictureService.searchPicture(searchText, 1, 10);
                return picturePage;
            });

            CompletableFuture.allOf(userTask, postTask, pictureTask).join();
            try {
                Page<UserVO> userVOPage = userTask.get();
                Page<PostVO> postVOPage = postTask.get();
                Page<Picture> picturePage = pictureTask.get();

                SearchVO searchVO = new SearchVO();
                searchVO.setUserList(userVOPage.getRecords());
                searchVO.setPostList(postVOPage.getRecords());
                searchVO.setPictureList(picturePage.getRecords());
                return searchVO;
            } catch (Exception e) {
                log.error("查询异常", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询异常");
            }
        } else {
            SearchVO searchVO = new SearchVO();
            switch (searchTypeEnum) {
                case POST:
                    PostQueryRequest postQueryRequest = new PostQueryRequest();
                    postQueryRequest.setSearchText(searchText);
                    Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest, request);
                    searchVO.setPostList(postVOPage.getRecords());
                    break;
                case USER:
                    UserQueryRequest userQueryRequest = new UserQueryRequest();
                    userQueryRequest.setUserName(searchText);
                    Page<UserVO> userVOPage = userService.listUserVOByPage(userQueryRequest);
                    searchVO.setUserList(userVOPage.getRecords());
                    break;
                case PICTURE:
                    Page<Picture> picturePage = pictureService.searchPicture(searchText, 1, 10);
                    searchVO.setPictureList(picturePage.getRecords());
                    break;
                default:
            }
            return searchVO;
        }
    }
    
}
```

##### 2.适配器模式

定制一个数据源的接入规范：保证不是任何数据源都能随意接入（必须满足规范！）。同时还要保证接入是的便捷。

**规范：任何接入我们系统的数据，它必须要能够根据关键词搜索、并且支持分页搜索**

假如我们的数据源已经支持了搜索，但是原有的方法参数和我们的规范不一致，怎么办？

适配器模式的作用：通过转换，让两个系统能够完成对接



声明接口来定义规范：

```java
/**
 * 数据源接口（新接入的数据源必须实现）
 *
 * @param <T>
 */
public interface DataSource<T> {

    /**
     * 搜索
     *
     * @param searchText
     * @param pageNum
     * @param pageSize
     * @return
     */
    Page<T> doSearch(String searchText, long pageNum, long pageSize);
}

```

##### 3.注册器模式

可以发现，之前实现的门面包含了switch语句，当我们的功能增多时，代码就会堆积在门面中。我们可以通过注册器模式来简化代码！

**提前通过一个map或者其它的数据结构存储需要调用的对象**

实现

```java
@Component
public class DataSourceRegistry {

    @Resource
    private PostDataSource postDataSource;

    @Resource
    private UserDataSource userDataSource;

    @Resource
    private PictureDataSource pictureDataSource;

    @Resource
    private VideoDataSource videoDataSource;

    private Map<String, DataSource<T>> typeDataSourceMap;

    @PostConstruct
    public void doInit() {
        System.out.println(1);
        typeDataSourceMap = new HashMap() {{
            put(SearchTypeEnum.POST.getValue(), postDataSource);
            put(SearchTypeEnum.USER.getValue(), userDataSource);
            put(SearchTypeEnum.PICTURE.getValue(), pictureDataSource);
            put(SearchTypeEnum.VIDEO.getValue(),videoDataSource);
        }};
    }

    public DataSource getDataSourceByType(String type) {
        if (typeDataSourceMap == null) {
            return null;
        }
        return typeDataSourceMap.get(type);
    }
}

```

优化门面

```java
SearchVO searchVO = new SearchVO();
            DataSource<?> dataSource = dataSourceRegistry.getDataSourceByType(type);
            Page<?> page = dataSource.doSearch(searchText, current, pageSize);
            searchVO.setDataList(page.getRecords());
            return searchVO;
```

##### 4.演示增加一个新的数据源---**视频**

`1.注册一个Video对象`

```java
//添加枚举
VIDEO("视频", "video");
// 注册
put(SearchTypeEnum.VIDEO.getValue(),videoDataSource);
```

`2.实现适配器接口，进行数据抓取`

```java
package com.yupi.yuso.datasource;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yuso.common.ErrorCode;
import com.yupi.yuso.exception.BusinessException;
import com.yupi.yuso.model.entity.Video;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class VideoDataSource implements DataSource<Video> {

    @Override
    public Page<Video> doSearch(String searchText, long pageNum, long pageSize) {
        long current=(pageNum-1) * pageSize;
        String url=String.format("https://cn.bing.com/videos/search?q=%s&first=%s", searchText, current);
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"数据获取异常！");
        }
        Elements newsHeadlines = doc.select(".mc_vtvc.b_canvas.isv ");
        List<Video> videos =new ArrayList<>();
        for (Element element : newsHeadlines) {
            // 取视频地址
            String vUrl=element.select(".mc_vtvc.b_canvas.isv").select("a").attr("href");
            String title=element.select(".rms_iac").attr("data-alt");
            String imageUrl= element.select(".rms_iac").attr("data-src");
            Video video=new Video();
            video.setTitle(title);
            video.setImageUrl(imageUrl);
            video.setVUrl(vUrl);
            videos.add(video);

        }

        Page<Video> videoPage=new Page<>(pageNum,pageSize);
        videoPage.setRecords(videos);
        return videoPage;
    }
    }

```

可以注意到，有了适配器和注册器，我们不需要去改动门面，只需要满足规范（实现适配器接口）同时注册一下就可以快速实现了。

#### 三.elsticsearch

##### 1.为什么要使用es

为了让搜索更加的灵活

##### 2.简介

[es官方文档](https://www.elastic.co/cn/)

es是一套技术栈，包含了数据的整合 => 提取 => 存储 => 使用

beats：从各种不同类型的文件 / 应用来 采集数据  a,b,c,d,e,aa,bb,cc

Logstash：从多个采集器或数据源抽取 / 转换数据，向 es 输送 aa,bb,cc

elasticsearch：存储、查询数据

kibana：可视化 es 的数据

##### 3.理解

把 Elasticsearch 当成 MySQL 一样的数据库

Index 索引 => MySQL 里的表（table）

ES 相比于 MySQL，能够自动帮我们做分词，能够非常高效、灵活的查询内容。

##### 4.es调用的几种方式

`1.restful api调用`

这里我们使用postman来测试

![image-20230509215251538](https://lxbucket-1315016093.cos.ap-beijing.myqcloud.com/images/image-20230509215251538.png)

添加查询条件

![image-20230509215435141](https://lxbucket-1315016093.cos.ap-beijing.myqcloud.com/images/image-20230509215435141.png)

`2.kibana看板`

[kibana访问](http://localhost:5601/app/integrations/browse)

使用**kibana devtools** 自由的对es进行操作（本质上也是调用了resful  api）

`3.java客户端操作es`

使用**Spring Data Elasticsearch**

官方文档：https://docs.spring.io/spring-data/elasticsearch/docs/4.4.10/reference/html/



##### 5.es实现搜索接口

###### 1.建表

```java
PUT post/_mapping
{
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "contentSuggestion": {
        "type": "completion",
        "analyzer": "ik_max_word"
      },
      "titleSuggestion": {
        "type": "completion",
        "analyzer": "ik_max_word"
      },
      "tags": {
        "type": "keyword"
      },
      "userId": {
        "type": "keyword"
      },
      "createTime": {
        "type": "date"
      },
      "updateTime": {
        "type": "date"
      },
      "isDelete": {
        "type": "keyword"
      }
    }
}
```

###### 2.ElasticsearchRepository<PostEsDTO, Long>

默认提供了简单的增删改查，多用于可预期的、相对没那么复杂的查询、自定义查询，返回结果相对简单直接。

[官方文档](https://docs.spring.io/spring-data/elasticsearch/docs/4.4.10/reference/html/#repositories.core-concepts)

这种方式支持根据es中的字段自定义查询，比如要根据name来进行查询,我们可以定义查询方法

```java
List<PostEsDTO> findByUserId(Long userId);
```

###### 3.ElasticsearchRestTemplate

Spring 默认给我们提供的操作 es 的客户端对象：ElasticsearchRestTemplate，也提供了简单的增删改查，它的增删改查更灵活，适用于更复杂的操作，返回结果更完整，但需要自己解析。



三个步骤：

1. 取参数
2. 把参数组合为 ES 支持的搜索条件
3. 从返回值中取结果



简单来说，就是把查询条件翻译成java代码，再把所有的查询条件组合在一起从es中进行查询，最后把数据解析出来就可以了。

```java
// 构造查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withHighlightBuilder(highlightBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder).build();
        SearchHits<PostEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery, PostEsDTO.class);
```

