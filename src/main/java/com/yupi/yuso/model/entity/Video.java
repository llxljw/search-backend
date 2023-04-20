package com.yupi.yuso.model.entity;

import lombok.Data;

/**
 * Created with IntelliJ IDEA.
 *
 * @Auther: ljw
 * @Date: 2023/05/05/21:00
 * @Description: 视频实体
 */
@Data
public class Video {
    /**
     * 视频链接
     */
    private String vUrl;

    /**
     *标题
     */
    private String title;

    /**
     * 描述图片链接
     */
    private String imageUrl;
}
