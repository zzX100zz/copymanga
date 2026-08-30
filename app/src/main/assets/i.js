javascript:
if (typeof (loaded) == "undefined") {
    var loaded = true;
    var invoke = {
        preUrl: "",
        hideRanobeTab: function () {
            var tabs = document.getElementsByClassName("van-tabbar-item");
            for (var i = 0; i < tabs.length; i++) {
                if (tabs[i].innerText == "輕小說") tabs[i].style.display = "none";
            }
        },
        hideRanobeRack: function () {
            var tabs = document.getElementsByClassName("van-tabs van-tabs--line");
            if (tabs.length) tabs[0].hidden = true;
        },
        pinTitle: function () {
            var game = document.getElementsByName("exchange");
            if (game.length) game[0].hidden = true;
        },
        notCallGM: function (url) {
            if (this.preUrl == url) return false;
            else {
                this.preUrl = url;
                return true;
            }
        },
        clickClass: function (name, index) { document.getElementsByClassName(name)[index].click(); },
        clickClassCenter: function (name, index) {
            var ev = document.createEvent('HTMLEvents');
            ev.clientX = innerWidth / 2;
            ev.clientY = innerHeight / 2;
            ev.initEvent('click', false, true);
            document.getElementsByClassName(name)[index].dispatchEvent(ev);
        },
        resetPreUrl: function () { this.preUrl = ""; },
        fullChapter: null,
        loadChapter: function () { GM.loadComic(location.href); },
        findReaderViewModel: function (vm, visited) {
            if (!vm || visited.indexOf(vm) >= 0) return null;
            visited.push(vm);
            if (vm.content && vm.content.chapter && vm.content.chapter.contents) return vm;
            var children = vm.$children || [];
            for (var i = 0; i < children.length; i++) {
                var found = this.findReaderViewModel(children[i], visited);
                if (found) return found;
            }
            return null;
        },
        readerViewModel: function () {
            var root = document.querySelector(".comicContentPopup");
            var candidates = [];
            while (root) {
                if (root.__vue__) candidates.push(root.__vue__);
                root = root.parentElement;
            }
            var app = document.querySelector("#app");
            if (app && app.__vue__) candidates.push(app.__vue__);
            for (var i = 0; i < candidates.length; i++) {
                var found = this.findReaderViewModel(candidates[i], []);
                if (found) return found;
            }
            return null;
        },
        sameRoute: function (left, right) {
            function clean(url) {
                return (url || "").split("?")[0].split("#")[0].replace(/\/$/, "");
            }
            return clean(left) == clean(right);
        },
        applyDomChapter: function (chapter) {
            var list = document.querySelector(".comicContentPopupImageList");
            if (!list || !chapter.imageUrls || !chapter.imageUrls.length) return false;
            var oldApiItems = list.querySelectorAll(".copyMangaApiImageItem");
            var renderedItems = list.querySelectorAll(".comicContentPopupImageItem");
            if (!oldApiItems.length && renderedItems.length == chapter.imageUrls.length) return true;
            var complete = oldApiItems.length == chapter.imageUrls.length;
            if (complete && oldApiItems.length) {
                complete = oldApiItems[0].getAttribute("data-url") == chapter.imageUrls[0] &&
                    oldApiItems[oldApiItems.length - 1].getAttribute("data-url") ==
                    chapter.imageUrls[chapter.imageUrls.length - 1];
            }
            if (complete) return true;
            for (var i = oldApiItems.length - 1; i >= 0; i--) {
                oldApiItems[i].parentNode.removeChild(oldApiItems[i]);
            }
            var previewItems = list.querySelectorAll(".comicContentPopupImageItem");
            for (var j = 0; j < previewItems.length; j++) previewItems[j].style.display = "none";
            var anchor = list.firstChild;
            for (var k = 0; k < chapter.imageUrls.length; k++) {
                var item = document.createElement("li");
                item.className = "comicContentPopupImageItem copyMangaApiImageItem";
                item.setAttribute("data-url", chapter.imageUrls[k]);
                item.style.cssText = "display:block;width:100%;margin:0;padding:0;list-style:none;";
                var image = document.createElement("img");
                image.src = chapter.imageUrls[k];
                image.style.cssText = "display:block;width:100%;height:auto;margin:0;padding:0;";
                item.appendChild(image);
                list.insertBefore(item, anchor);
            }
            return true;
        },
        applyFullChapter: function () {
            var chapter = this.fullChapter;
            if (!chapter || !chapter.imageUrls ||
                (chapter.sourceUrl && !this.sameRoute(chapter.sourceUrl, location.href))) return false;
            var vm = this.readerViewModel();
            if (!vm || !vm.content || !vm.content.chapter) return this.applyDomChapter(chapter);
            var current = vm.content.chapter.contents || [];
            var different = current.length != chapter.imageUrls.length;
            if (!different && current.length) {
                different = !current[0] || current[0].url != chapter.imageUrls[0] ||
                    !current[current.length - 1] ||
                    current[current.length - 1].url != chapter.imageUrls[chapter.imageUrls.length - 1];
            }
            if (!different) return this.applyDomChapter(chapter) || true;
            var contents = [];
            for (var i = 0; i < chapter.imageUrls.length; i++) contents.push({url: chapter.imageUrls[i]});
            if (vm.$set) {
                vm.$set(vm.content.chapter, "contents", contents);
                vm.$set(vm.content.chapter, "size", contents.length);
            } else {
                vm.content.chapter.contents = contents;
                vm.content.chapter.size = contents.length;
            }
            vm.total = contents.length;
            vm.isNotContent = false;
            vm.previewImages = [];
            if (vm.$forceUpdate) vm.$forceUpdate();
            this.applyDomChapter(chapter);
            return true;
        },
        urlChangeListener: function (todo) {
            setInterval(function () { if (invoke.notCallGM(location.href)) { todo(); } }, 1000);
        }
    };
    function modify() {
        var url = location.href;
        GM.hideFab();
        GM.setReaderFullscreen(url.indexOf("/comicContent/") > 0);
        if (url.endsWith("/index")) {
            invoke.pinTitle();
            invoke.hideRanobeTab();
        }
        else if (url.endsWith("/bookrack")) {
            invoke.hideRanobeTab();
            invoke.hideRanobeRack();
        }
        else if (url.indexOf("/searchContent") > 0) invoke.hideRanobeRack();
        else if (url.indexOf("/comicContent/") > 0) setTimeout(function () { invoke.loadChapter() }, 1000);
        else if (url.indexOf("/details/comic/") > 0) GM.loadComic(url);
    }
    window.copyMangaApplyChapter = function (payload) {
        try {
            invoke.fullChapter = typeof payload == "string" ? JSON.parse(payload) : payload;
            invoke.applyFullChapter();
        } catch (error) {
            console.error("Unable to apply full App API chapter", error);
        }
    };
    modify();
    invoke.urlChangeListener(modify);
    setInterval(function () { invoke.applyFullChapter(); }, 750);
} else {
    setTimeout(modify, 1280);
}
