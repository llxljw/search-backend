package com.yupi.yuso.datasource;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yuso.model.dto.post.PostQueryRequest;
import com.yupi.yuso.model.entity.Post;
import com.yupi.yuso.model.vo.PostVO;
import com.yupi.yuso.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 帖子服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Service
@Slf4j
public class PostDataSource implements DataSource<PostVO> {

    @Resource
    private PostService postService;

    @Override
    public Page<PostVO> doSearch(String searchText, long pageNum, long pageSize) {
        PostQueryRequest postQueryRequest = new PostQueryRequest();
        postQueryRequest.setSearchText(searchText);
        postQueryRequest.setPageSize(pageSize);
        postQueryRequest.setCurrent(pageNum);
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();
//        Page<PostVO> postVOPage = postService.listPostVOByPage(postQueryRequest, request);
        // 从es中搜索
        Page<PostVO> postVOPage =null;
        Page<Post> postPage = postService.searchFromEs(postQueryRequest);
        // 数量太少时重新抓取
        if (postPage.getRecords().size() == 0 || postPage.getRecords().size() <=10){
            log.info("es中数据为空，开始爬取数据");
            postVOPage = postService.searchPost(searchText, pageNum, pageSize);
        }
        else {
            postVOPage = postService.getPostVOPage(postPage, request);
        }
        return postVOPage;
    }
}




