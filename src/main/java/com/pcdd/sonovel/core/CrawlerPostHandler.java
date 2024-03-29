package com.pcdd.sonovel.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileAppender;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.setting.dialect.Props;
import com.pcdd.sonovel.model.Book;
import com.pcdd.sonovel.util.Settings;
import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author pcdd
 */
@UtilityClass
public class CrawlerPostHandler {

    private static final String SAVE_PATH;

    static {
        Props usr = Settings.usr();
        SAVE_PATH = usr.getStr("savePath");
    }

    public void handle(String extName, Book book, File saveDir) {
        StringBuilder s = new StringBuilder(StrUtil.format("\n<== 《{}》（{}）下载完毕", book.getBookName(), book.getAuthor()));
        if ("txt".equals(extName) || "epub".equals(extName)) {
            s.append("，开始合并为 ").append(extName);
        }
        Console.log(s);

        switch (extName) {
            case "txt":
                mergeTxt(saveDir, book.getBookName(), book.getAuthor());
                break;
            case "epub":
                convertToEpub(saveDir, book);
                break;
            default:
        }
    }

    @SneakyThrows
    private void convertToEpub(File dir, Book b) {
        io.documentnode.epub4j.domain.Book book = new io.documentnode.epub4j.domain.Book();
        book.getMetadata().addTitle(b.getBookName());
        book.getMetadata().addAuthor(new Author(b.getAuthor()));
        book.getMetadata().addDescription(b.getDescription());
        byte[] bytes = HttpUtil.downloadBytes(b.getCoverUrl());
        book.setCoverImage(new Resource(bytes, ".jpg"));

        // TODO 创建 guide
        // Guide guide = book.getGuide();
        // guide.addReference(new GuideReference(new Resource(bytes, ".jpg"), "cover.jpg", GuideReference.COVER));

        int i = 1;
        // 遍历下载后的目录，添加章节
        for (File file : files(dir)) {
            // 截取第一个 _ 后的字符串，即章节名
            String title = StrUtil.subAfter(FileUtil.mainName(file), "_", false);

            Resource resource = new Resource(FileUtil.readBytes(file), i + ".html");
            resource.setId(String.valueOf(i));
            resource.setTitle(title);
            book.addSection(title, resource);

            // guide.addReference(new GuideReference(resource, i + ".html", GuideReference.TEXT));
            i++;
        }

        EpubWriter epubWriter = new EpubWriter();
        String savePath = StrUtil.format("{}/{}.epub", dir.getParent(), b.getBookName());
        epubWriter.write(book, new FileOutputStream(savePath));
    }

    private void mergeTxt(File dir, String... args) {
        String path = StrUtil.format("{}{}{}（{}）.txt",
                System.getProperty("user.dir") + File.separator, SAVE_PATH + File.separator, args[0], args[1]);
        File file = FileUtil.touch(path);
        FileAppender appender = new FileAppender(file, 16, true);

        for (File item : files(dir)) {
            String s = FileUtil.readString(item, StandardCharsets.UTF_8);
            appender.append(s);
        }
        appender.flush();
    }

    // 文件排序，按文件名升序
    private List<File> files(File dir) {
        return Arrays.stream(dir.listFiles())
                .sorted((o1, o2) -> {
                    String s1 = o1.getName();
                    String s2 = o2.getName();
                    int no1 = Integer.parseInt(s1.substring(0, s1.indexOf("_")));
                    int no2 = Integer.parseInt(s2.substring(0, s2.indexOf("_")));
                    return no1 - no2;
                }).toList();
    }

}
