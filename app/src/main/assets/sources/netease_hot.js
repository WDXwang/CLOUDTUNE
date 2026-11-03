/**
 * @name 网易云热歌榜
 * @author CloudTune
 * @version 1.0.0
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

// 网易云公开榜单
const BOARDS = [
    { id: '3778678', name: '热歌榜' },
    { id: '3779629', name: '新歌榜' },
    { id: '21845217', name: '原创榜' },
    { id: '60198',   name: '飙升榜' },
    { id: '180106',  name: '全球华语歌曲榜' },
    { id: '60131',   name: '抖音榜' }
];

const UA = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

// 把 lx.request 包成 Promise
function httpGet(url) {
    return new Promise(function (resolve, reject) {
        lx.request(
            { url: url, headers: { 'User-Agent': UA, 'Referer': 'https://music.163.com/' } },
            function (err, res) {
                if (err) { reject(err); return; }
                resolve(res.body || res);
            }
        );
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
    // 无 boardId → 返回榜单目录
    if (!info.boardId && !info.id && !info.board) {
        return { list: BOARDS.map(function (b) { return { id: b.id, name: b.name }; }) };
    }
    var boardId = info.boardId || info.id || info.board;
    var url = 'https://music.163.com/api/playlist/detail?id=' + boardId;
    return httpGet(url).then(function (body) {
        var json;
        try { json = typeof body === 'string' ? JSON.parse(body) : body; }
        catch (e) { return { list: [] }; }
        var tracks = json && json.playlist && json.playlist.tracks ? json.playlist.tracks : [];
        var songs = tracks.map(function (t) {
            var artists = t.ar || t.artists || [];
            return {
                id: String(t.id),
                name: t.name,
                singer: artists.map(function (a) { return a.name; }).join('/'),
                album: t.al ? t.al.name : ''
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
        try { json = typeof body === 'string' ? JSON.parse(body) : body; }
        catch (e) { return { list: [] }; }
        var result = json && json.result ? json.result : {};
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
    var songmid = musicInfo.id || musicInfo.songmid || '';
    return { url: 'https://music.163.com/song/media/outer/url?id=' + songmid + '.mp3', type: '128k' };
}
