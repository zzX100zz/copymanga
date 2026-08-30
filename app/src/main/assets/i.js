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
        readerViewModel: function () {
            var root = document.querySelector(".comicContentPopup");
            while (root && !root.__vue__) root = root.parentElement;
            return root && root.__vue__ ? root.__vue__ : null;
        },
        applyFullChapter: function () {
            var chapter = this.fullChapter;
            if (!chapter || !chapter.uuid || location.href.indexOf(chapter.uuid) < 0) return false;
            var vm = this.readerViewModel();
            if (!vm || !vm.content || !vm.content.chapter || !chapter.imageUrls) return false;
            var current = vm.content.chapter.contents || [];
            var different = current.length != chapter.imageUrls.length;
            if (!different && current.length) {
                different = !current[0] || current[0].url != chapter.imageUrls[0] ||
                    !current[current.length - 1] ||
                    current[current.length - 1].url != chapter.imageUrls[chapter.imageUrls.length - 1];
            }
            if (!different) return true;
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
