# Contrast AI SmartFix Automation

This directory contains the GitHub Actions workflow and Python scripts designed to automatically fetch vulnerability data from Contrast Security, attempt an AI-driven code fix, and create a Pull Request with the proposed changes.

> **Note:** This project was formerly called "Contrast Resolve" and has been rebranded to "Contrast AI SmartFix". The directory structure, workflow name, and related references have been updated accordingly.

## Files

* **`.github/workflows/contrast-ai-smartfix.yml`**: This GitHub Actions workflow orchestrates the entire process.
  * **Triggers**: Runs daily at midnight UTC or can be triggered manually (`workflow_dispatch`).
  * **Permissions**: Requires `contents: write` and `pull-requests: write` to modify code and create PRs.
  * **Setup**: Checks out the repository, sets up Python 3.10, Node.js 22, Git, and the GitHub CLI (`gh`).
  * **Dependencies**: Installs Python packages from `requirements.txt`.
  * **Execution**: Runs the `main.py` script, passing necessary secrets (Contrast API keys, GitHub token, AWS credentials) as environment variables.
* **`.github/actions/contrast-ai-smartfix/main.py`**: The main Python script that orchestrates the steps:
  1. Fetches vulnerability details from the Contrast API using `contrast_api.py`.
  2. Invokes the AI agent via `agent_handler.py` to analyze the vulnerability and apply code fixes using file system tools.
  3. Uses `git_handler.py` to manage Git operations (create branch, stage, commit, push) and GitHub operations (ensure label, create PR).
* **`.github/actions/contrast-ai-smartfix/contrast_api.py`**: Handles all interactions with the Contrast Security API, including fetching vulnerability lists, stories, and event details. Includes logic for parsing and redacting sensitive information.
* **`.github/actions/contrast-ai-smartfix/agent_handler.py`**: Manages the AI agent (using Google ADK/LiteLLM, configured for AWS Bedrock). It sets up the agent with a system prompt, connects it to the filesystem via an MCP server, runs the agent, and processes its response.
* **`.github/actions/contrast-ai-smartfix/git_handler.py`**: Contains functions for Git commands (config, branch, add, commit, push) and GitHub CLI commands (label creation, PR creation).
* **`.github/actions/contrast-ai-smartfix/config.py`**: Loads configuration from environment variables (set by the workflow) and defines common paths.
* **`.github/actions/contrast-ai-smartfix/utils.py`**: Provides utility functions like running shell commands (`run_command`), debug logging, and managing `.gitignore`.
* **`.github/actions/contrast-ai-smartfix/requirements.txt`**: Lists the required Python dependencies (`requests`, `google-adk`, `google-generativeai`, `litellm`, `boto3`).
* **`.github/actions/contrast-ai-smartfix/system-prompt.md`**: Contains the detailed instructions provided to the AI agent for analyzing and fixing vulnerabilities.
* **`.github/actions/contrast-ai-smartfix/README.md`**: This file.

