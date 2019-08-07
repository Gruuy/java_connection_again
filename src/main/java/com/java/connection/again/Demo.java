package com.java.connection.again;

/**
 * @author: Gruuy
 * @remark:
 * @date: Create in 16:24 2019/8/5
 */

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 断点续传工具
 * @author Gruuy
 * 时间：2019-8-5
 */
@Controller
public class Demo {

    private static Logger log = LoggerFactory.getLogger(Demo.class);

    @RequestMapping("/down")
    public void down(HttpServletRequest request,HttpServletResponse response){
        try {
            download(request,response,"E:\\Documents\\Music\\MV\\test.mp4");
        } catch (IOException e) {
            e.printStackTrace( );
        }
    }


    /** 下载操作，支持断点续传
     * 主要是获取请求头里面的range参数，从某个地方开始传
     * 利用RandomAccessFile  把文件的指针指到range的参数的位置  再继续传输
     * */
    public static void download(HttpServletRequest req, HttpServletResponse resp, String filepath) throws NumberFormatException, IOException {
        File file = new File(filepath);
        //开始下载位置
        long startByte = 0;
        //结束下载位置
        long endByte = file.length( ) - 1;

        String range = req.getHeader("Range");
        //验证下range的合法性
        if (range != null && range.contains("bytes=") && range.contains("-")) {
            //+1是因为要把等号也干掉
            range = range.substring(range.lastIndexOf("=") + 1).trim( );
            String[] ranges = range.split("-");
            try {
                //判断range的类型
                if (ranges.length == 1) {
                    //类型一：bytes=-2343
                    if (range.startsWith("-")) {
                        endByte = Long.parseLong(ranges[0]);
                    }
                    //类型二：bytes=2343-
                    else if (range.endsWith("-")) {
                        startByte = Long.parseLong(ranges[0]);
                    }
                }
                //类型三：bytes=22-2343
                else if (ranges.length == 2) {
                    startByte = Long.parseLong(ranges[0]);
                    endByte = Long.parseLong(ranges[1]);
                }
            } catch (Exception ex) {
                startByte = 0;
                endByte = file.length( ) - 1;
            }
        }else
        {
            startByte = 0;
            endByte = file.length( ) - 1;
        }
            //下载长度
            long contentLength=endByte-startByte+1;
            //filename
            String filename=file.getName();
            //file type
            String contentType=req.getServletContext().getMimeType(filename);

            //各种响应头设置
            //设置服务器可以接受的类型请求
            resp.setHeader("Accept-Ranges", "bytes");
            //http状态码要为206
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            //文件类型
            resp.setContentType(contentType);
/**           resp.setHeader("Content-Type", contentType);*/
            //设置文件名
            resp.setHeader("Content-Disposition", "inline;filename="+filename);
            //设置下载长度(结束-开始+1  开始包括0 加回去)
            resp.setHeader("Content-Length", String.valueOf(contentLength));
            //这是Content-Range  表示起始-结束 /总大小
            resp.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + file.length());

            //缓冲流  每个循环传输多少数据
            BufferedOutputStream outputStream = null;
            //使用RandomAccessFile是因为可以把文件指针拖到对应的起始位置
            RandomAccessFile randomAccessFile = null;
            //已传送数据大小
            long transmitted = 0;
            try {
                //获取文件
                randomAccessFile = new RandomAccessFile(file, "r");
                //获取流
                outputStream = new BufferedOutputStream(resp.getOutputStream());
                //缓冲区
                byte[] buff = new byte[8];
                int len = 0;
                //文件指针移动到开始的字节位
                randomAccessFile.seek(startByte);
                //先传输所有可以被整除的部分
                while ((transmitted + len) <= contentLength && (len = randomAccessFile.read(buff)) != -1) {
                    outputStream.write(buff, 0, len);
                    transmitted += len;
                }
                //处理不足buff.length部分
                if (transmitted < contentLength) {
                    len = randomAccessFile.read(buff, 0, (int) (contentLength - transmitted));
                    outputStream.write(buff, 0, len);
                    transmitted += len;
                }
                //发送一波
                outputStream.flush();
                resp.flushBuffer();
                //关闭流
                randomAccessFile.close();
                System.out.println("下载完毕：" + startByte + "-" + endByte + "：" + transmitted);

            } catch (ClientAbortException e) {
                System.out.println("用户停止下载：" + startByte + "-" + endByte + "：" + transmitted);
                //捕获此异常表示拥护停止下载
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (randomAccessFile != null) {
                        randomAccessFile.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

}
