### How to contribute

Contributions for this plugin should be in the form of pull requests.

Before you make a contribution, please make sure that there is a JIRA ticket for the bug that you are fixing, or for the feature that you are implementing as specified above. In some trivial cases, like clean up, refactoring, fixing typos there is no need for a JIRA issue.

One pull request should solve one JIRA ticket. 

For your convinience you should try to use the following workflow:

**Setup workflow**
* Fork on GitHub (click Fork button)
* Clone to computer, use SSH URL ($ git clone git@github.com:${your_git_username}/JiraTestResultReporter-plugin.git)
* cd into your repo: ($ cd JiraTestResultReporter-plugin/)
* Set up remote upstream ($ git remote add upstream git@github.com:jenkinsci/JiraTestResultReporter-plugin.git)

**Fixing a bug/Implementing a feature workflow**
* Create a branch for the JIRA issue that you are solving ($ git checkout -b issue/JENKINS-XXXXXX)
* Develop on issue branch. [Time passes, the main repository accumulates new commits]
* Commit changes to your local issue branch. ($ git add . ; git commit -m 'JENKINS-XXXXXX - commit message')
* Fetch upstream ($ git fetch upstream)
* Update local master ($ git checkout master; git merge upstream/master)
* Rebase issue branch ($ git checkout issue/JENKINS-XXXXXX; git rebase master)
* Repeat the above steps until dev is complete
* Push branch to GitHub ($ git push)
* Start your browser, go to your Github repo, switch to "JENKINS-XXXXXX" branch and press the [Pull Request] button.

Please keep the PRs as clean as possible by not mixing two or more issues into a single PR, or refactoring/clean up/typo fixing with solving issue. 
If you can have the feature implementation in a single git commit, that's even better.
If you have splitted your work into many commits you can squash them into a single one afterwards, if it makes sense. 

In any case, every commit should be fully functional. As specified in the workflow the branch name and each commit message shoud contain the issue key. We would love it if you could also provide tests for you issue in the PR (even if we don't have any, yet :) ).
