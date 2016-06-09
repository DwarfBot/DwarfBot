function submitFailureReport() {
    document.getElementById('failure-report-status').innerHTML = "Submitting...";
    var httpRequest = new XMLHttpRequest();
    httpRequest.onreadystatechange = function () {
        if (httpRequest.readyState === XMLHttpRequest.DONE && httpRequest.status === 200) {
            document.getElementById('failure-report-status').innerHTML = httpRequest.responseText;
        }
    }
    httpRequest.open('POST', '/' + session + '/submitfailurereport', true);
    httpRequest.send();
}
