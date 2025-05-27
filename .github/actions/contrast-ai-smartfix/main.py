#-
# #%L
# Contrast AI SmartFix
# %%
# Copyright (C) 2025 Contrast Security, Inc.
# %%
# Contact: support@contrastsecurity.com
# License: Commercial
# NOTICE: This Software and the patented inventions embodied within may only be
# used as part of Contrast Security’s commercial offerings. Even though it is
# made available through public repositories, use of this Software is subject to
# the applicable End User Licensing Agreement found at
# https://www.contrastsecurity.com/enduser-terms-0317a or as otherwise agreed
# between Contrast Security and the End User. The Software may not be reverse
# engineered, modified, repackaged, sold, redistributed or otherwise used in a
# way not consistent with the End User License Agreement.
# #L%
#

# .github/contrast-ai-smartfix/main.py
import sys
import os # <<< ADDED
from pathlib import Path
# <<< REMOVED subprocess import >>>
from typing import Tuple, List # <<< ADDED List >>>
from datetime import datetime, timedelta # <<< ADDED for runtime limit

# Import configurations and utilities
import config
from utils import debug_print, ensure_gitignore_ignores_script_dir, run_command # <<< ADDED run_command

# Import domain-specific handlers
import contrast_api
import agent_handler
import git_handler
import qa_handler # <<< ADDED

# <<< REMOVED run_build_command function >>>

def main():
    """Main orchestration logic."""

    start_time = datetime.now()
    print("--- Starting Contrast AI SmartFix Script ---")
    debug_print(f"Start time: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")

    # --- Use Build Command and Max Attempts/PRs from Config ---
    build_command = config.BUILD_COMMAND
    if build_command:
        debug_print(f"Build command specified: {build_command}")
    else:
        debug_print("BUILD_COMMAND not set or empty.")
        build_command = None # Ensure it's None if not set

    formatting_command = config.FORMATTING_COMMAND
    if formatting_command:
        debug_print(f"Formatting command specified: {formatting_command}")
    else:
        debug_print("FORMATTING_COMMAND not set or empty.")
        formatting_command = None # Ensure it's None if not set

    # Process MAX_BUILD_ATTEMPTS
    default_max_attempts = 3
    hard_cap_attempts = 6
    try:
        max_attempts_from_env = int(config.MAX_BUILD_ATTEMPTS)
        # Apply the hard cap
        max_qa_attempts_setting = min(max_attempts_from_env, hard_cap_attempts)
        if max_attempts_from_env > hard_cap_attempts:
             debug_print(f"MAX_BUILD_ATTEMPTS ({max_attempts_from_env}) exceeded hard cap ({hard_cap_attempts}). Using {hard_cap_attempts}.")
        else:
             debug_print(f"Using MAX_BUILD_ATTEMPTS from config: {max_qa_attempts_setting}")
    except (ValueError, TypeError):
        debug_print(f"Invalid MAX_BUILD_ATTEMPTS value. Using default: {default_max_attempts}")
        max_qa_attempts_setting = default_max_attempts

    # Process MAX_OPEN_PRS
    default_max_open_prs = 5
    try:
        max_open_prs_setting = int(config.MAX_OPEN_PRS)
        if max_open_prs_setting < 0: # Ensure non-negative
             max_open_prs_setting = default_max_open_prs
             debug_print(f"MAX_OPEN_PRS was negative, using default: {default_max_open_prs}")
        else:
             debug_print(f"Using MAX_OPEN_PRS from environment: {max_open_prs_setting}")
    except (ValueError, TypeError):
        debug_print(f"Invalid or missing MAX_OPEN_PRS environment variable. Using default: {default_max_open_prs}")
        max_open_prs_setting = default_max_open_prs
    # <<< END Reading Max Open PRs >>>

    # --- Initial Setup ---
    script_dir_relative = config.SCRIPT_DIR.relative_to(config.REPO_ROOT)
    ensure_gitignore_ignores_script_dir(config.REPO_ROOT, str(script_dir_relative))
    git_handler.configure_git_user()

    # <<< ADDED: Check Open PR Limit >>>
    print("\n::group::--- Checking Open PR Limit ---")
    label_prefix_to_check = "contrast-vuln-id:"
    current_open_pr_count = git_handler.count_open_prs_with_prefix(label_prefix_to_check)
    if current_open_pr_count >= max_open_prs_setting:
        print(f"Found {current_open_pr_count} open PR(s) with label prefix '{label_prefix_to_check}'.")
        print(f"This meets or exceeds the configured limit of {max_open_prs_setting}.")
        print("Exiting script to avoid creating more PRs.")
        sys.exit(0)
    else:
        print(f"Found {current_open_pr_count} open PR(s) with label prefix '{label_prefix_to_check}' (Limit: {max_open_prs_setting}). Proceeding...")
    print("\n::endgroup::", flush=True)
    # <<< END Check Open PR Limit >>>

    # --- Fetch Potential Vulnerabilities ---
    print("\n::group::--- Fetching potential vulnerabilities from Contrast API ---")
    potential_vulns = contrast_api.get_vulnerabilities_to_process(
        config.CONTRAST_HOST, config.CONTRAST_ORG_ID, config.CONTRAST_APP_ID,
        config.CONTRAST_AUTHORIZATION_KEY, config.CONTRAST_API_KEY,
        limit=10, # Fetch up to 10 potential candidates
        severities=config.VULNERABILITY_SEVERITIES # Filter by severity levels
    )
    print("\n::endgroup::", flush=True)

    if not potential_vulns:
        print("No suitable vulnerabilities found to process. Exiting.")
        sys.exit(0)

    processed_one = False
    max_runtime = timedelta(hours=3)  # Set maximum runtime to 3 hours
    
    for vuln_summary in potential_vulns:
        # Check if we've exceeded the maximum runtime
        current_time = datetime.now()
        elapsed_time = current_time - start_time
        if elapsed_time > max_runtime:
            print(f"\n--- Maximum runtime of 3 hours exceeded (actual: {elapsed_time}). Stopping processing. ---")
            break
            
        ensure_gitignore_ignores_script_dir(config.REPO_ROOT, str(script_dir_relative))

        vuln_uuid = vuln_summary["uuid"]
        vuln_title = vuln_summary["title"]
        vuln_rule_name = vuln_summary["rule_name"]
        print(f"\n::group::--- Considering Vulnerability: {vuln_title} (UUID: {vuln_uuid}) ---")

        # --- Check for Existing PRs ---
        label_name, _, _ = git_handler.generate_label_details(vuln_uuid)
        pr_status = git_handler.check_pr_status_for_label(label_name)

        # Changed this logic to check only for OPEN PRs for dev purposes
        #if pr_status == "OPEN" or pr_status == "MERGED":
        if pr_status == "OPEN":
            print(f"Skipping vulnerability {vuln_uuid} as an OPEN or MERGED PR with label '{label_name}' already exists.")
            print("\n::endgroup::", flush=True)
            continue # Move to the next potential vulnerability
        else: # pr_status == "NONE"
            print(f"No existing OPEN or MERGED PR found for vulnerability {vuln_uuid}. Proceeding with fix attempt.")

        print("\n::endgroup::", flush=True)

        print(f"\n\033[0;33m Selected vuln to fix: {vuln_title} \033[0m")

        # --- Fetch Full Vulnerability Details ---
        print(f"\n::group::--- Fetching full details for {vuln_uuid} ---")
        # Update: get_vulnerability_story now only returns the story string
        vuln_story = contrast_api.get_vulnerability_story(
            vuln_uuid, config.CONTRAST_HOST, config.CONTRAST_ORG_ID,
            config.CONTRAST_AUTHORIZATION_KEY, config.CONTRAST_API_KEY
        )
        # Provide placeholder if fetching failed
        vuln_story = vuln_story if vuln_story else "(Could not retrieve vulnerability story)"
        # story_api_url is no longer returned or needed here, UI URL is constructed in agent_handler

        vuln_events_details = contrast_api.get_vulnerability_events(
            vuln_uuid, config.CONTRAST_HOST, config.CONTRAST_ORG_ID,
            config.CONTRAST_AUTHORIZATION_KEY, config.CONTRAST_API_KEY
        )
        vuln_events_details = vuln_events_details if vuln_events_details else "(Could not retrieve event details)"

        # <<< ADDED: Fetch HTTP Request Details >>>
        vuln_http_request = contrast_api.get_vulnerability_http_request(
            vuln_uuid, config.CONTRAST_HOST, config.CONTRAST_ORG_ID,
            config.CONTRAST_AUTHORIZATION_KEY, config.CONTRAST_API_KEY
        )
        vuln_http_request = vuln_http_request if vuln_http_request else "(Could not retrieve HTTP request details)"
        # <<< END Fetch HTTP Request Details >>>

        # --- Prepare Data for AI Agent ---
        ai_vuln_details = {
            "uuid": vuln_uuid,
            "title": vuln_title,
            "rule_name": vuln_rule_name,
            "story": vuln_story,
            "events": vuln_events_details,
            "build_command": build_command,
            "http_request": vuln_http_request # <<< ADDED
        }
        print("\n::endgroup::", flush=True)

        # --- Run AI Agent ---
        ai_fix_summary_full = agent_handler.run_ai_fix_agent(ai_vuln_details, config.REPO_ROOT)
        
        # Check if the fix agent encountered an error
        if ai_fix_summary_full.startswith("Error during AI fix agent execution:"):
            print("Fix agent encountered an unrecoverable error. Skipping this vulnerability.")
            error_message = ai_fix_summary_full[len("Error during AI fix agent execution:"):].strip()
            print(f"Error details: {error_message}")
            
            # If no filesystem tools are available, or a fatal MCP/ADK error occurred, don't try to create a branch/PR
            if ("No filesystem tools" in error_message or
                "cannot make changes to files" in error_message or
                "MCPToolset' has no attribute 'from_server" in error_message or # Specific check for the new error
                error_message.startswith("FATAL:")): # General check for other fatal errors from agent_handler
                print("Cannot proceed with vulnerability fix due to critical agent error (e.g., filesystem tools unavailable or ADK/MCP setup issue). Skipping branch creation.")
            else:
                # Clean up any branch that might have been created (though unlikely if agent failed early)
                print(f"Cleaning up any potentially created branch...")
                run_command(["git", "checkout", config.BASE_BRANCH], check=False)
                # No need to delete branch if we haven't created one yet or if creation failed.
                
            continue # Move to the next vulnerability
            
        # The ai_fix_summary_full variable now directly contains the intended PR body
        # (either extracted from <pr_body> tags by agent_handler or the full agent response).
        # The redundant extraction block previously here has been removed.

        # --- Git and GitHub Operations ---
        print("\n--- Proceeding with Git & GitHub Operations ---", flush=True)
        # Note: Git user config moved to the start of main

        new_branch_name = git_handler.generate_branch_name(vuln_uuid)
        try:
            git_handler.create_branch(new_branch_name)
        except SystemExit:
             print(f"Error creating branch {new_branch_name}. Switching back to base branch and cleaning up.")
             run_command(["git", "checkout", config.BASE_BRANCH], check=False)
             run_command(["git", "branch", "-D", new_branch_name], check=False)
             continue # Try next vulnerability

        git_handler.stage_changes()

        if git_handler.check_status(): # Only proceed if changes were detected
            commit_message = git_handler.generate_commit_message(vuln_title, vuln_uuid)
            git_handler.commit_changes(commit_message)
            initial_changed_files = git_handler.get_last_commit_changed_files() # Get files changed by fix agent

            # --- QA Loop ---
            if not config.SKIP_QA_REVIEW and build_command:
                debug_print("Proceeding with QA Review as SKIP_QA_REVIEW is false and BUILD_COMMAND is provided.")
                build_success, final_changed_files, used_build_command, qa_summary_log = qa_handler.run_qa_loop(
                    build_command=build_command,
                    repo_root=config.REPO_ROOT,
                    max_qa_attempts=max_qa_attempts_setting, # Use the setting read earlier
                    initial_changed_files=initial_changed_files,
                    formatting_command=formatting_command # <<< ADDED
                )

                # <<< MOVED qa_section generation inside this block >>>
                # <<< MODIFIED: Update PR Body with more detailed QA Results >>>
                qa_section = "\n\n---\n\n## Review \n\n"

                if used_build_command: # Check if build was actually run
                    qa_section += f"*   **Build Run:** Yes (`{used_build_command}`)\n"

                    if build_success:
                        qa_section += "*   **Final Build Status:** Success ✅\n"
                        # Check if QA log is non-empty, implying QA agent ran and fixed it
                        # if qa_summary_log:
                            # qa_section += "*   **Outcome:** Build issues likely introduced by the initial fix were resolved by the QA agent.\n"
                            # Format summaries nicely
                            # full_qa_summary = "\n".join([f"    - {s.strip()}" for s in qa_summary_log])
                            #qa_section += f"*   **QA Agent Summaries:**\n{full_qa_summary}\n"
                        #else: # Passed initially and still passes
                            # qa_section += "*   **Outcome:** Build remained successful after changes.\n"
                    else: # Build failed finally
                        qa_section += "*   **Final Build Status:** Failure ❌\n"
                        # If build failed, QA agent must have run (or initial build failed and QA was skipped)
                        #if qa_summary_log:
                            # qa_section += "*   **Outcome:** Build failed initially. QA agent attempted fixes, but the build still failed.\n"
                            #full_qa_summary = "\n".join([f"    - {s.strip()}" for s in qa_summary_log])
                            #qa_section += f"*   **QA Agent Summaries:**\n{full_qa_summary}\n"
                        #else: # Should only happen if initial build failed and QA loop didn't run
                            #qa_section += "*   **Outcome:** Build failed initially, likely due to the initial fix or pre-existing issues. QA agent did not run or made no corrective changes.\n"

                else: # Build command wasn't run (either not provided or no changes made)
                    qa_section += "*   **Build Run:** No"
                    if not used_build_command: # Check the variable returned from qa_handler
                        qa_section += " (BUILD_COMMAND not provided)\n"
                    # else: # This case shouldn't happen if used_build_command is None/empty
                    #     qa_section += " (Initial agent made no changes)\n" # This logic might need review based on qa_handler
                    qa_section += "\n*   **Final Build Status:** Skipped\n" # Simplified status
                # <<< END moved qa_section generation >>>
                
                # Skip PR creation if QA was run and the build is failing
                # or if the QA agent encountered an error (detected by checking qa_summary_log entries)
                if (used_build_command and not build_success) or any(s.startswith("Error during QA agent execution:") for s in qa_summary_log):
                    print("\n--- Skipping PR creation as QA Agent failed to fix build issues or encountered an error ---")
                    print(f"Cleaning up branch: {new_branch_name}")
                    # Use agent_handler.cleanup_branch which calls run_command
                    agent_handler.cleanup_branch(new_branch_name)
                    continue # Move to the next vulnerability

                # Skip PR creation if QA was run and the build is failing
                if used_build_command and not build_success:
                    print("\n--- Skipping PR creation as QA Agent failed to fix build issues ---")
                    print(f"Cleaning up branch: {new_branch_name}")
                    run_command(["git", "checkout", config.BASE_BRANCH], check=False)
                    run_command(["git", "branch", "-D", new_branch_name], check=False)
                    continue # Move to the next vulnerability

            else: # QA is skipped
                qa_section = "" # Ensure qa_section is empty if QA is skipped
                if config.SKIP_QA_REVIEW:
                    print("Skipping QA Review based on SKIP_QA_REVIEW setting.")
                elif not build_command:
                    print("Skipping QA Review as no BUILD_COMMAND was provided.")
                # If both conditions for skipping are met, the SKIP_QA_REVIEW message will be prioritized
                # if it's checked first, or handled by the elif as it is now.

            # --- Create Pull Request ---
            pr_title = git_handler.generate_pr_title(vuln_title)
            # Use the result from agent_handler.run_ai_fix_agent directly as the base PR body.
            # agent_handler.run_ai_fix_agent is expected to return the PR body content
            # (extracted from <pr_body> tags) or the full agent summary if extraction fails.
            pr_body_base = ai_fix_summary_full
            debug_print("Using agent's output (processed by agent_handler) as PR body base.")

            # --- Push and Create PR ---
            git_handler.push_branch(new_branch_name) # Push the final commit (original or amended)

            label_name, label_desc, label_color = git_handler.generate_label_details(vuln_uuid)
            label_created = git_handler.ensure_label(label_name, label_desc, label_color)
            
            if not label_created:
                print(f"Warning: Could not create GitHub label '{label_name}'. PR will be created without a label.")
                label_name = ""  # Clear label_name to avoid using it in PR creation

            pr_title = git_handler.generate_pr_title(vuln_title)

            # <<< PR Body Update >>>
            updated_pr_body = pr_body_base + qa_section
            # <<< END PR Body Update >>>

            try:
                # Set a flag to track if we should try the fallback approach
                pr_creation_success = False
                pr_url = "" # Initialize pr_url
                
                # Try to create the PR using the GitHub CLI
                print("Attempting to create a pull request...")
                # <<< MODIFIED: Capture PR URL >>>
                pr_url = git_handler.create_pr(pr_title, updated_pr_body, new_branch_name, config.BASE_BRANCH, label_name)
                
                if pr_url:
                    pr_creation_success = True

                    # <<< ADDED: Add note to Contrast API >>>
                    if not config.SKIP_COMMENTS:
                        note_content = f"Contrast AI SmartFix opened remediation PR: {pr_url}"
                        note_added = contrast_api.add_note_to_vulnerability(
                            vuln_uuid=vuln_uuid,
                            note_content=note_content,
                            contrast_host=config.CONTRAST_HOST,
                            contrast_org_id=config.CONTRAST_ORG_ID,
                            contrast_app_id=config.CONTRAST_APP_ID, # Ensure CONTRAST_APP_ID is available in config
                            contrast_auth_key=config.CONTRAST_AUTHORIZATION_KEY,
                            contrast_api_key=config.CONTRAST_API_KEY
                        )
                        if note_added:
                            print(f"Successfully added note to Contrast for vulnerability {vuln_uuid}.")
                        else:
                            print(f"Warning: Failed to add note to Contrast for vulnerability {vuln_uuid}.")
                    else:
                        print(f"Skipping adding note to Contrast due to SKIP_COMMENTS setting.")
                    # <<< END ADDED: Add note to Contrast API >>>
                else:
                    # This case should ideally be handled by create_pr exiting or returning empty
                    # and then the logic below for SKIP_PR_ON_FAILURE would trigger.
                    # However, if create_pr somehow returns without a URL but doesn't cause an exit:
                    print("PR creation did not return a URL. Assuming failure.")
                    pr_creation_success = False
                # <<< END MODIFIED >>>
                
                if not pr_creation_success and os.getenv('SKIP_PR_ON_FAILURE') == 'true':
                    print("\n--- PR creation failed, but changes were pushed to branch ---")
                    print(f"Branch name: {new_branch_name}")
                    print("Changes can be manually viewed and merged if needed.")
                
                processed_one = True # Mark that we successfully processed one
                break # Exit the loop after successfully creating a PR for one vulnerability
            except Exception as e:
                print(f"Error creating PR: {e}")
                print("\n--- PR creation failed, but changes were pushed to branch ---")
                print(f"Branch name: {new_branch_name}")
                print("Changes can be manually viewed and merged if needed.")
                processed_one = True # Consider this a success as we pushed the changes
                break
        else:
            print("Skipping commit, push, and PR creation as no changes were detected by the agent.")
            # Clean up the branch if no changes were made
            print(f"Cleaning up unused branch: {new_branch_name}")
            run_command(["git", "checkout", config.BASE_BRANCH], check=False)
            run_command(["git", "branch", "-D", new_branch_name], check=False)
            continue # Try the next vulnerability

    # Calculate total runtime
    end_time = datetime.now()
    total_runtime = end_time - start_time
    
    if not processed_one:
        print("\n--- No vulnerabilities were processed in this run (either none found, all skipped, agent made no changes, or runtime limit exceeded). ---")
    else:
        print("\n--- Successfully processed one vulnerability and created a PR. ---")

    print(f"\n--- Script finished (total runtime: {total_runtime}) ---")

if __name__ == "__main__":
    main()
