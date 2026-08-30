javascript:
if (typeof (loaded) == "undefined") {
    var loaded = true;
    function scanChapters(chapter) {
        var activeTab = chapter.getElementsByClassName("tab-pane fade show active")[0];
        var list = activeTab && activeTab.getElementsByTagName("ul")[0];
        var chapterList = list ? list.getElementsByTagName("a") : [];
        var chapterArr = Array();
        for (var i = 0; i < chapterList.length; i++) {
            var name = chapterList[i].title || chapterList[i].innerText.trim();
            var url = chapterList[i].href;
            if (name && url) chapterArr.push({"name": name, "url": url});
        }
        return chapterArr;
    }
    function collectChapterGroups() {
        var container = document.getElementsByClassName("upLoop")[0];
        if (!container || container.children.length < 2 || container.children.length % 2) return null;
        var groups = Array();
        for (var i = 0; i < container.children.length; i += 2) {
            var name = container.children[i].innerText.trim();
            var chapters = scanChapters(container.children[i + 1]);
            if (!name || !chapters.length) return null;
            groups.push({"name": name, "chapters": chapters});
        }
        return groups.length ? groups : null;
    }
    function waitForChapterGroups() {
        var sourceUrl = location.href;
        var startedAt = Date.now();
        var lastChapterCount = -1;
        var stableChecks = 0;
        function scan() {
            if (location.href != sourceUrl) return;
            var groups = collectChapterGroups();
            if (groups) {
                var chapterCount = 0;
                for (var i = 0; i < groups.length; i++) chapterCount += groups[i].chapters.length;
                stableChecks = chapterCount == lastChapterCount ? stableChecks + 1 : 0;
                lastChapterCount = chapterCount;
                if (stableChecks >= 4) {
                    var titleElement = document.getElementsByTagName("h6")[0];
                    GM.setTitle(titleElement && titleElement.title ? titleElement.title : document.title);
                    GM.setFab(JSON.stringify(groups));
                    return;
                }
            } else {
                stableChecks = 0;
                lastChapterCount = -1;
            }
            if (Date.now() - startedAt < 120000) setTimeout(scan, 250);
        }
        scan();
    }
    function loadChapterWhenReady() {
        var sourceUrl = location.href;
        var startedAt = Date.now();
        var lastHeight = 0;
        var lastImageCount = 0;
        var stableChecks = 0;
        function hrefByClass(name, index) {
            var items = document.getElementsByClassName(name);
            var links = items.length > index ? items[index].getElementsByTagName("a") : [];
            return links.length && links[0].href ? links[0].href : "null";
        }
        function imageSource(item) {
            var images = item.getElementsByTagName("img");
            if (!images.length) return "";
            return images[0].getAttribute("data-src") || images[0].src || "";
        }
        function finish(items) {
            var nextChapter = hrefByClass("comicContent-next", 0);
            var prevChapter = hrefByClass("comicContent-prev", 1);
            if (nextChapter == location.href) nextChapter = "null";
            if (prevChapter == location.href) prevChapter = "null";
            var title = document.title.split(" - ")[1] || document.title;
            var liststr = title + " " + location.href.substring(location.href.lastIndexOf("/") + 1) + "\n" + nextChapter + "\n" + prevChapter;
            for (var i = 0; i < items.length; i++) {
                var src = imageSource(items[i]);
                if (src) liststr += "\n" + src;
            }
            GM.loadChapter(liststr);
        }
        function scan() {
            if (location.href != sourceUrl) return;
            var content = document.getElementsByClassName("container-fluid comicContent")[0];
            var items = content ? content.getElementsByTagName("li") : [];
            var countElement = document.getElementsByClassName("comicCount")[0];
            var expected = countElement ? parseInt(countElement.innerText) || 0 : 0;
            var height = document.body.scrollHeight;
            stableChecks = height == lastHeight && items.length == lastImageCount ? stableChecks + 1 : 0;
            lastHeight = height;
            lastImageCount = items.length;
            var atBottom = Math.round(window.innerHeight + window.pageYOffset + 1) >= height;
            if ((expected > 0 && items.length >= expected && stableChecks >= 2) ||
                (atBottom && items.length > 0 && stableChecks >= 12) ||
                Date.now() - startedAt >= 120000) {
                finish(items);
                return;
            }
            window.scrollTo(0, document.body.scrollHeight);
            setTimeout(scan, 250);
        }
        scan();
    }
    function modify() {
        if (location.href.indexOf("/chapter/") > 0) loadChapterWhenReady();
        else waitForChapterGroups();
    }
    modify();
} else modify();
