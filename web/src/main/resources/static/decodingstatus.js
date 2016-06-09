/**
 * Created by jacob on 2016-05-28.
 */

function createProgressBar(percent) {
    var outer = document.createElement('div');
    outer.className = 'progressbar-outer';
    var inner = document.createElement('div');
    inner.className = 'progressbar-inner';
    inner.style.width = percent + '%';
    outer.appendChild(inner);
    return outer;
}

function createParagraph(text) {
    var p = document.createElement('p');
    p.appendChild(document.createTextNode(text));
    return p;
}

function updatePage(j) {
    var container = document.getElementById('decodingstatus-container');
    container.innerHTML = '';
    container.appendChild(createParagraph('Load image'));
    container.appendChild(createProgressBar(j['loadImageForConverting']));
    container.appendChild(createParagraph('Detect tileset'));
    container.appendChild(createProgressBar(j['extractTileset']));
    container.appendChild(createParagraph('Read tiles'));
    container.appendChild(createProgressBar(j['readTiles']));
    
    var done = j['loadImageForConverting'] === 100 && j['extractTileset'] === 100 && j['readTiles'] === 100;
    if (done) {
        window.location.href = '/' + session + '/encodeimage';
    }
    
    // Do we need another update?
    return !done;
}

function startStatusUpdates() {
    sendUpdateRequest();
}

function sendUpdateRequest() {
    var httpRequest = new XMLHttpRequest();
    httpRequest.onreadystatechange = function () {
        if (httpRequest.readyState === XMLHttpRequest.DONE && httpRequest.status === 200) {
            if (updatePage(JSON.parse(httpRequest.responseText))) {
                setTimeout(sendUpdateRequest, 1000);
            }
        }
    }
    httpRequest.open('GET', '/' + session + '/decodingstatus.json', true);
    httpRequest.send();
}
