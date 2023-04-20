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
