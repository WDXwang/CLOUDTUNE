/**
 * @name 网易云热歌榜
 * @author CloudTune
 * @version 1.1.0
 * @description 内置官方源，提供网易云音乐排行榜、搜索与播放解析
 */

// 声明音源能力
lx.send('inited', {
    status: 'ok',
    openDevTools: false,
    sources: {
        netease: {
            name: '网易云热歌榜',
            type: 'music',
            actions: ['leaderboard', 'musicSearch', 'musicUrl']
        }
    }
});

const UA = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
const COMMON_HEADERS = { 'User-Agent': UA, 'Referer': 'https://music.163.com/' };

// 把 lx.request 包成 Promise
function httpGet(url) {
    return new Promise(function (resolve, reject) {
        lx.request({ url: url, headers: COMMON_HEADERS }, function (err, res) {
            if (err) { reject(err); return; }
            // res.body 是返回体字符串；如果没有 body 可能 Android bridge 直接给了 body 内容
            var body = res.body !== undefined ? res.body : res;
            resolve(typeof body === 'string' ? body : JSON.stringify(body));
        });
    });
}

// 统一 request handler
lx.on('request', function (params) {
    var action = params.action;
    var info = params.info || {};

    if (action === 'leaderboard') {
        return handleLeaderboard(info);
    } else if (action === 'musicSearch') {
        return handleSearch(info);
    } else if (action === 'musicUrl') {
        return handleMusicUrl(info);
    } else {
        return { __error: '未实现的 action: ' + action };
    }
});

// ---------- leaderboard ----------
function handleLeaderboard(info) {
    // 无 boardId → 返回榜单目录（api/toplist 直接返回 { list: [...] }）
    if (!info.boardId && !info.id && !info.board) {
        return httpGet('https://music.163.com/api/toplist').then(function (body) {
            var json;
            try { json = JSON.parse(body); } catch (e) { return { list: [] }; }
            // 榜单列表在顶层 list 字段
            var list = json.list || [];
            var boards = list.map(function (item) {
                return { id: String(item.id), name: item.name || ('榜单 ' + item.id) };
            });
            return { list: boards };
        }).catch(function () { return { list: [] }; });
    }

    // 有 boardId → 拉榜单歌曲
    var boardId = info.boardId || info.id || info.board;
    return httpGet('https://music.163.com/api/playlist/detail?id=' + boardId).then(function (body) {
        var json;
        try { json = JSON.parse(body); } catch (e) { return { list: [] }; }
        // 网易云返回两种结构：
        // 热门榜单（toplist）：{ result: { tracks: [...] } }
        // 普通歌单：         { result: { playlist: { tracks: [...] } } }
        var result = json.result || {};
        var tracks = result.tracks;
        if (!tracks && result.playlist) tracks = result.playlist.tracks;
        tracks = tracks || [];
        var songs = tracks.map(function (t) {
            var artists = t.ar || t.artists || [];
            var singer = artists.map(function (a) { return a.name; }).join('/');
            var album = '';
            if (t.al) album = t.al.name;
            return {
                id: String(t.id),
                name: t.name,
                singer: singer,
                album: album
            };
        });
        return { list: songs };
    }).catch(function () { return { list: [] }; });
}

// ---------- musicSearch ----------
function handleSearch(info) {
    var keyword = info.keyword || info.text || '';
    if (!keyword) return { list: [] };
    var url = 'https://music.163.com/api/search/get?s=' + encodeURIComponent(keyword) + '&type=1&limit=30';
    return httpGet(url).then(function (body) {
        var json;
        try { json = JSON.parse(body); } catch (e) { return { list: [] }; }
        var result = json.result || {};
        var songs = (result.songs || []).map(function (s) {
            var artists = s.artists || [];
            return {
                id: String(s.id),
                name: s.name,
                singer: artists.map(function (a) { return a.name; }).join('/'),
                album: s.album ? s.album.name : ''
            };
        });
        return { list: songs };
    }).catch(function () { return { list: [] }; });
}

// ---------- musicUrl ----------
function handleMusicUrl(info) {
    var musicInfo = info.musicInfo || info;
    var songmid = musicInfo.id || musicInfo.songmid || musicInfo.songId || '';
    if (!songmid) return { url: '' };
    return { url: 'https://music.163.com/song/media/outer/url?id=' + songmid + '.mp3', type: '128k' };
}
