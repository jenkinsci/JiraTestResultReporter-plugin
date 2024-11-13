function hideshow(which) {
    if (which.style.display == "block") {
        which.style.display = "none";
    } else {
        which.style.display = "block";
    }
}


function validateFieldConfigs(event) {
    event.preventDefault();
    const button = event.target;
    const { descriptorProxyVarName } = button.dataset;
    /*
    Ugly hack part 1. See JiraTestDataPublisherDescriptor.validateFieldConfigs for part2.
    Since Jenkins does not offer a way to send the hetero-list for validation, I'm constructing
    the whole form and sending it.
    */
    const errorDiv = document.getElementById("JiraIssueConfigErrors");
    const spinner = document.getElementById("jiraSpinner");
    spinner.style.display = "inline";
    const form = document.getElementsByName("config")[0];
    buildFormTree(form);
    let jsonElement = null;
    for (let i = 0; i != form.elements.length; i++) {
        let e = form.elements[i];
        if (e.name == "json") {
            jsonElement = e;
            break;
        }
    }

    const descriptor = window[descriptorProxyVarName];
    const socketTimeout = setTimeout(function() {
        spinner.style.display = "none";
        errorDiv.innerHTML = "Validation Failed: Socket Timeout. The issue was probably created, but the server did not respond in a timely manner. Please try again.";
    }, 30000);
    descriptor.validateFieldConfigs(jsonElement.value, function(rsp) {
        clearTimeout(socketTimeout);
        spinner.style.display = "none";
        updateValidationArea(errorDiv, rsp.responseText);
    });
}

window.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".validateJiraIssueConfig").forEach((button) => {
        button.addEventListener("click", validateFieldConfigs);
    });

    document.querySelectorAll(".show-hide-jira-issue-config").forEach((icon) => {
        icon.addEventListener("click", (event) => {
            const container = event.target.closest(".jira-validate-issue-config-container");
            hideshow(container.querySelector(".helpValidateJiraIssueConfig"));
        });
    });
});
