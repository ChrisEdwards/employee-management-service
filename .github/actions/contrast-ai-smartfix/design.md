# Contrast AI SmartFix System Design

## 1. Overview

The Contrast AI SmartFix system is an automated solution designed to identify vulnerabilities reported by Contrast Security, attempt to generate fixes using AI, validate these fixes through a QA process, and create GitHub Pull Requests for human review and merging. Its primary goal is to accelerate the vulnerability remediation lifecycle.

The system is composed of a GitHub Actions workflow and a collection of Python scripts.

## 2. Components

### 2.1. GitHub Workflow (`.github/workflows/contrast-ai-smartfix.yml`)

This workflow is responsible for orchestrating the execution of the Contrast AI SmartFix Python scripts. It typically handles:

* **Triggers**: Scheduled runs (e.g., nightly) or manual triggers.
* **Environment Setup**:
  * Checking out the repository.
  * Setting up a Python environment.
  * Installing dependencies (e.g., from a `requirements.txt` file).
* **Secrets and Configuration**: Passing necessary secrets (e.g., Contrast API keys, GitHub token) and configuration parameters (e.g., `BUILD_COMMAND`) as environment variables to the Python scripts.
* **Script Execution**: Running the main Python script (`.github/contrast-ai-smartfix/main.py`).

### 2.2. Python Scripts (`.github/contrast-ai-smartfix/`)

This directory contains the core logic of the Contrast AI SmartFix system.

* **`main.py`**: The main orchestrator script. It coordinates the different stages of the process, from fetching vulnerabilities to creating PRs.
* **`config.py`**: Manages configuration settings, likely loading them from environment variables and providing constants used throughout the scripts (e.g., API endpoints, repository details, script directory).
* **`utils.py`**: Contains helper functions used by other scripts, such as debug printing, command execution, and Git ignore logic.
* **`contrast_api.py`**: Handles all interactions with the Contrast Security API. This includes fetching vulnerability listings, vulnerability details (story, events, HTTP requests).
* **`agent_handler.py`**: Manages the interaction with an AI-powered agent responsible for generating code fixes for identified vulnerabilities. It prepares the input for the AI agent and processes its output.
* **`git_handler.py`**: Encapsulates all Git and GitHub API operations. This includes:
  * Configuring Git user.
  * Creating and managing branches.
  * Staging, committing, and pushing changes.
  * Checking for existing PRs.
  * Creating new PRs.
  * Managing labels for PRs.
  * Counting open PRs with specific labels.
* **`qa_handler.py`**: Manages the Quality Assurance (QA) loop. This involves:
  * Running a build command (if provided).
  * Potentially running an AI-driven QA agent to fix build issues introduced by the initial AI fix.
  * Attempting the build multiple times if it fails.
  * Summarizing the QA process results.

## 3. Workflow Details (Orchestrated by `main.py`)

The `main.py` script executes the following sequence of operations:

1. **Initialization and Configuration**:
   * Reads environment variables for `BUILD_COMMAND`, `MAX_BUILD_ATTEMPTS`, and `MAX_OPEN_PRS`. Default values are used if these are not set or invalid.
   * Ensures the script directory is ignored by Git.
   * Configures the Git user for commits.
2. **Open PR Limit Check**:
   * Counts the number of currently open PRs that have a label with a specific prefix (e.g., "contrast-vuln-id:").
   * If the count meets or exceeds the `MAX_OPEN_PRS` setting, the script exits to prevent creating too many automated PRs.
3. **Vulnerability Fetching**:
   * Calls the Contrast API (via `contrast_api.py`) to fetch a list of potential vulnerabilities to process, prioritizing them based on criteria defined in the API call (e.g., severity, status).
   * If no suitable vulnerabilities are found, the script exits.
4. **Vulnerability Processing Loop**:
   The script iterates through the fetched vulnerabilities one by one, attempting to process and create a PR for the first one that meets all criteria.
   * **Existing PR Check**: For each vulnerability, it checks (via `git_handler.py`) if an OPEN or MERGED PR with a specific label (derived from the vulnerability UUID) already exists. If so, it skips to the next vulnerability.
   * **Fetch Full Details**: If no existing PR is found, it fetches comprehensive details for the vulnerability from the Contrast API, including its story, event data, and associated HTTP request information.
   * **AI Fix Agent**:
     * The vulnerability details (UUID, title, rule name, story, events, build command, HTTP request) are passed to the `agent_handler.py`.
     * The AI fix agent attempts to generate a code fix. The output is a summary that includes the proposed changes and a PR body.
   * **PR Body Extraction**: The script extracts the PR body content from the AI fix agent's summary.
   * **Git Branching and Staging**:
     * A new Git branch is created with a name derived from the vulnerability UUID.
     * The changes proposed by the AI fix agent are applied to the working directory.
     * `git_handler.py` stages these changes.
   * **Change Detection**: The script checks if the AI agent actually made any changes.
     * If no changes were detected, the newly created branch is deleted, and the script moves to the next potential vulnerability.
   * **Commit Changes**: If changes are detected, they are committed with a standardized commit message.
   * **QA Loop (Conditional)**:
     * This step can be skipped if the `SKIP_QA_REVIEW` configuration is true.
     * If enabled, `qa_handler.run_qa_loop` is invoked:
       * It uses the `BUILD_COMMAND` (if provided) to build the project.
       * If the build fails, a QA agent might be invoked to attempt to fix the build issues. This can iterate up to `max_qa_attempts_setting`.
       * The QA loop records whether the build was run, its final status (success/failure), and a log of QA agent summaries if any fixes were attempted.
     * A `qa_section` is generated for the PR body, summarizing the QA process. If QA is skipped, this section is empty.
   * **Pull Request Creation**:
     * The local branch with the committed fix (and any QA-driven amendments) is pushed to the remote repository.
     * A GitHub label corresponding to the vulnerability UUID is ensured to exist (created if necessary).
     * A PR title is generated.
     * The final PR body is constructed by combining the base PR body (from the AI fix agent) and the `qa_section`.
     * A pull request is created (via `git_handler.py`) targeting the configured base branch, with the generated title, body, and label.
   * **Loop Termination**: If a PR is successfully created, the `processed_one` flag is set to true, and the script breaks out of the vulnerability processing loop (i.e., it only creates one PR per run).
5. **Script Completion**:
   * A final message indicates whether a vulnerability was processed and a PR was created.

## 4. Configuration

The system relies on several environment variables for its operation:

* **Contrast API Credentials**:
  * `CONTRAST_HOST`: The URL of the Contrast API.
  * `CONTRAST_ORG_ID`: The Contrast organization ID.
  * `CONTRAST_APP_ID`: The Contrast application ID.
  * `CONTRAST_AUTHORIZATION_KEY`: The Contrast authorization key.
  * `CONTRAST_API_KEY`: The Contrast API key.
* **GitHub Configuration**:
  * `GITHUB_TOKEN`: A token with permissions to create branches, push code, create PRs, and manage labels.
  * `BASE_BRANCH`: The target branch for pull requests (e.g., `main`, `develop`).
* **Build and QA Configuration**:
  * `BUILD_COMMAND` (Optional): The command to run to build and test the application (e.g., `mvn clean install`). If not provided, the build step in the QA loop might be skipped.
  * `MAX_BUILD_ATTEMPTS`: Maximum number of times the QA loop will attempt to build/fix (default: 3, hard cap: 6).
  * `SKIP_QA_REVIEW` (Optional): If set to `true`, the QA loop is skipped entirely.
* **Operational Configuration**:
  * `MAX_OPEN_PRS`: Maximum number of open PRs with the "contrast-vuln-id:" label prefix before the script stops creating new ones (default: 5).
* **AI Agent Configuration**: (Specific variables depend on the AI agent provider)
  * Likely API keys, model names, or endpoint URLs for the AI services used by `agent_handler.py` and potentially `qa_handler.py`.

## 5. Error Handling and Resilience

* **Branch Cleanup**: If branch creation fails, or if an AI agent makes no changes, the script attempts to switch back to the base branch and delete the newly created (and now unnecessary) branch.
* **PR Status Check**: Avoids creating duplicate PRs by checking for existing open or merged PRs for the same vulnerability.
* **Environment Variable Defaults**: Provides default values for critical operational parameters like `MAX_BUILD_ATTEMPTS` and `MAX_OPEN_PRS`.
* **Build Failures**: The QA loop attempts to handle build failures. If a build command is provided and the build ultimately fails after QA attempts, a PR might still be created (depending on exact logic) but will include details of the failure. The script currently does not automatically skip PR creation on final build failure if a build command was provided.

## 6. Logging and Output

The script prints progress messages to standard output, indicating the current stage of operation, vulnerabilities being processed, outcomes of AI agent runs, QA status, and PR creation details. Debug messages can be enabled for more verbose logging.
