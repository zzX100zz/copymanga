package top.fumiama.copymanga.data;

import java.util.Map;

public class CopyMangaApiModels {
    public static class BaseResponse {
        public int code;
        public String message;
    }

    public static class NetworkResponse extends BaseResponse {
        public NetworkResults results;
    }

    public static class NetworkResults {
        public String[][] api;
        public String[] share;
    }

    public static class BookResponse extends BaseResponse {
        public BookResults results;
    }

    public static class BookResults {
        public Comic comic;
        public Map<String, Group> groups;
    }

    public static class Comic {
        public String name;
        public String path_word;
    }

    public static class Group {
        public String name;
        public String path_word;
        public int count;
    }

    public static class VolumeResponse extends BaseResponse {
        public VolumeResults results;
    }

    public static class VolumeResults {
        public int total;
        public int limit;
        public int offset;
        public Chapter[] list;
    }

    public static class ChapterResponse extends BaseResponse {
        public ChapterResults results;
    }

    public static class ChapterResults {
        public Comic comic;
        public Chapter chapter;
    }

    public static class Chapter {
        public String uuid;
        public String name;
        public String comic_path_word;
        public String prev;
        public String next;
        public int size;
        public Content[] contents;
        public int[] words;
    }

    public static class Content {
        public String url;
    }
}
