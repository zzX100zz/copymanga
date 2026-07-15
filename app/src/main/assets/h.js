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
    var detailScanVersion = 0;
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
        var version = ++detailScanVersion;
        var sourceUrl = location.href;
        var lastChapterCount = -1;
        var stableChecks = 0;
        var startedAt = Date.now();
        function scan() {
            if (version !== detailScanVersion || location.href !== sourceUrl) return;
            var groups = collectChapterGroups();
            if (groups) {
                var chapterCount = groups.reduce(function (count, group) {
                    return count + group.chapters.length;
                }, 0);
                stableChecks = chapterCount === lastChapterCount ? stableChecks + 1 : 0;
                lastChapterCount = chapterCount;
                if (stableChecks >= 4) {
                    var titleElement = document.getElementsByTagName("h6")[0];
                    var comicTitle = titleElement && titleElement.title
                        ? titleElement.title
                        : document.title;
                    GM.setFab(JSON.stringify(groups), sourceUrl, comicTitle);
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
    function smoothLoadChapter(speed, interval) {
        let lastTime = 0;
        let ticking = false;
        let lastHeight = 0;
        let lastImageCount = 0;
        let stableCount = 0;
        let finished = false;
        let declaredCount = 0;
        let lastProgressNotify = 0;
        let lastPushNotify = 0;
        const pushed = Object.create(null);
        const startTime = Date.now();

        function getTextByClass(name) {
            const item = document.getElementsByClassName(name)[0];
            return item ? item.innerText : "0";
        }

        function getImages() {
            const content = document.getElementsByClassName("container-fluid comicContent")[0];
            return content ? content.getElementsByTagName("li") : [];
        }

        function safeHref(className, index) {
            try {
                const items = document.getElementsByClassName(className);
                const links = items[index].getElementsByTagName("a");
                return links[0].href;
            } catch(e) {
                return "null";
            }
        }

        function getChapterHeader() {
            var nextChapter = safeHref("comicContent-next", 0);
            var prevChapter = safeHref("comicContent-prev", 1);
            if(nextChapter == location.href) nextChapter = "null";
            if(prevChapter == location.href) prevChapter = "null";
            return document.title.split(" - ")[1] + " " + location.href.substring(location.href.lastIndexOf("/")+1) + "\n" + nextChapter + "\n" + prevChapter;
        }

        function notifyMeta() {
            try { GM.setChapterMeta(getChapterHeader()); } catch(e) {}
        }

        function imageSrcOf(li) {
            const img = li.getElementsByTagName("img")[0];
            if (!img) return "";
            return img.dataset.src || img.getAttribute("data-src") || img.src || "";
        }

        function notifyCount(count) {
            if (count > 0 && count !== declaredCount) {
                declaredCount = count;
                try { GM.setChapterCount(count.toString()); } catch(e) {}
            }
        }

        function pushNewImages(force) {
            const images = getImages();
            const batch = [];
            for(var i = 0; i < images.length; i++) {
                const src = imageSrcOf(images[i]);
                if (src && !pushed[src]) {
                    pushed[src] = true;
                    batch.push(src);
                }
            }
            if (batch.length > 0) {
                try { GM.appendChapterImages(batch.join("\n")); } catch(e) {}
            } else if (force) {
                try { GM.appendChapterImages(""); } catch(e) {}
            }
        }

        function finish() {
            if (finished) return;
            finished = true;
            notifyMeta();
            pushNewImages(true);
            var images = getImages();
            var result = getChapterHeader();
            for(var i = 0; i < images.length; i++) {
                var src = imageSrcOf(images[i]);
                if (src) result += "\n" + src;
            }
            try { GM.finishStreamingChapter(); } catch(e) {}
            GM.setLoadingDialog(false);
            GM.loadChapter(result);
        }

        function requestTick() {
            if (!ticking && !finished) {
                ticking = true;
                requestAnimationFrame(step);
            }
        }

        function step(timestamp) {
            if (!lastTime) lastTime = timestamp;
            const elapsed = timestamp - lastTime;
            if (elapsed >= interval) {
                const index = parseInt(getTextByClass("comicIndex")) || 0;
                const count = parseInt(getTextByClass("comicCount")) || 0;
                const images = getImages();
                notifyCount(count);
                if (timestamp - lastProgressNotify >= 200) {
                    lastProgressNotify = timestamp;
                    try { GM.setLoadingDialogProgress(index.toString(), count.toString()); } catch(e) {}
                }
                if (timestamp - lastPushNotify >= 80) {
                    lastPushNotify = timestamp;
                    pushNewImages(false);
                }
                window.scrollBy(0, speed);
                lastTime = timestamp;

                const height = document.body.scrollHeight;
                const imageCount = images.length;
                const atBottom = Math.round(window.innerHeight + window.scrollY + 1) >= height;
                if (height === lastHeight && imageCount === lastImageCount) stableCount++;
                else stableCount = 0;
                lastHeight = height;
                lastImageCount = imageCount;

                if ((count > 0 && imageCount >= count && (index >= count || atBottom) && stableCount >= 2) ||
                    (atBottom && stableCount >= 10) ||
                    (Date.now() - startTime > 180000)) {
                    finish();
                    return;
                }
            }
            ticking = false;
            requestTick();
        }
        notifyMeta();
        pushNewImages(false);
        requestTick();
    }
    function modify() {
        var url = location.href;
        if(url.indexOf("/chapter/") > 0){
            GM.setLoadingDialog(true);
            smoothLoadChapter(320, 16);
        } else {
            waitForChapterGroups();
        }
    }
    modify();
} else modify();