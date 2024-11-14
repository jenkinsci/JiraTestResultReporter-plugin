function hideshow(which){
    if (which.style.display == "block") {
        which.style.display = "none";
    } else {
        which.style.display = "block";
    }
}


function doSubmit(issueLink, spinner, proxyKey, errorsDiv) {
    if (!issueLink.value) {
        return;
    }
    spinner.style.display = "inline";
    var proxy = window[proxyKey];
    proxy.setIssueKey(issueLink.value, function (t) {
        if (t.responseText != undefined) {
            spinner.style.display = "none";
            updateValidationArea(errorsDiv, t.responseText);
        } else {
            location.reload();
        }
    });
}


function doClear(proxyKey) {
    var proxy = window[proxyKey];
    proxy.clearIssueKey(function (t) {
        location.reload();
    });
}

function createNewIssue(spinner, proxyKey, errorsDiv) {
    spinner.style.display = "inline";
    var proxy = window[proxyKey];
    var socketTimeout = setTimeout(function () {
        spinner.style.display = "none";
        errorsDiv.innerHTML = "Error: Socket Timeout. The issue was probably created, but the server did not respond in a timely manner. Check JIRA to avoid creating duplicated issues.";
    }, 30000);
    proxy.createIssue(function (t) {
        clearTimeout(socketTimeout);
        if (t.responseText != undefined) {
            spinner.style.display = "none";
            updateValidationArea(errorsDiv, t.responseText);
        } else {
            location.reload();
        }
    });
}

window.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".jira-create-issue-icon").forEach((icon) => {
        icon.addEventListener("click", (event) => {
            const parent = event.target.closest(".jira-test-action-container");
            const createIssueDiv = parent.querySelector(".jira-create-new-issue-section");

            hideshow(createIssueDiv);
        });
    });

    document.querySelectorAll(".jira-create-new-issue-button").forEach((button) => {
        button.addEventListener("click", (event) => {
            event.preventDefault();

            const parent = event.target.closest(".jira-test-action-container");
            const { proxyKey } = parent.querySelector(".jira-test-action-data-holder").dataset;
            const spinner = parent.querySelector(".jira-create-issue-spinner");
            const validationArea = parent.querySelector(".jira-create-issue-validation");

            createNewIssue(spinner, proxyKey, validationArea);
        });
    });

    document.querySelectorAll(".jira-create-new-issue-submit").forEach((element) => {
        element.addEventListener("click", (event) => {
            const parent = event.target.closest(".jira-test-action-container");
            const { proxyKey } = parent.querySelector(".jira-test-action-data-holder").dataset;
            const issueKeyInput = parent.querySelector(".jira-issue-key-input");
            const spinner = parent.querySelector(".jira-create-issue-spinner");
            const validationArea = parent.querySelector(".jira-create-issue-validation");

            doSubmit(issueKeyInput, spinner, proxyKey, validationArea);
        });
    });

    document.querySelectorAll(".jira-unlink-issue").forEach((element) => {
        element.addEventListener("click", (event) => {
            const parent = event.target.closest(".jira-test-action-container");
            const { proxyKey } = parent.querySelector(".jira-test-action-data-holder").dataset;

            doClear(proxyKey);
        });
    });

    document.querySelectorAll(".jira-toggle-issue-key-help").forEach((icon) => {
        icon.addEventListener("click", (event) => {
            const parent = event.target.closest(".jira-test-action-container");
            const helpDiv = parent.querySelector(".help-issue-key-input");

            hideshow(helpDiv);
        });
    });
});
