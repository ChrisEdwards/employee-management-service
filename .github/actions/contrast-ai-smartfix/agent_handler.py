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

# .github/contrast-ai-smartfix/agent_handler.py
import asyncio
import sys
import os
from pathlib import Path
from typing import Optional, Tuple, List
from contextlib import AsyncExitStack
import subprocess
import re # <<< ADDED for command parsing
import logging
import json
# <<< REMOVED: tokencost import >>>

# Import configurations and utilities
import config
from utils import debug_print, run_command # <<< ADDED run_command for cleanup
# <<< CHANGED: Use absolute import for cost_calculator >>>
# <<< END CHANGE >>>

def cleanup_branch(branch_name=None):
    """
    Cleans up by reverting changes and deleting the feature branch if it exists.
    
    Args:
        branch_name: The name of the branch to delete. If None, no branch deletion is performed.
    """
    print(f"\n--- Cleaning up after agent error ---")
    # Revert any uncommitted changes
    run_command(["git", "reset", "--hard"], check=False)
    
    # If a branch name is provided, switch back to base branch and delete the feature branch
    if branch_name:
        run_command(["git", "checkout", config.BASE_BRANCH], check=False)
        run_command(["git", "branch", "-D", branch_name], check=False)
    
    print("--- Cleanup completed ---")

# --- ADK Setup (Conditional Import) ---
ADK_AVAILABLE = False
try:
    # <<< MODIFIED: Reverted to previously working import structure >>>
    from google.adk.agents import Agent # Changed from google.adk.agent
    from google.adk.artifacts.in_memory_artifact_service import InMemoryArtifactService # Changed path
    from google.adk.models.lite_llm import LiteLlm # Changed from google.adk.model
    from google.adk.runners import Runner # Changed from google.adk.runner
    from google.adk.sessions import InMemorySessionService # Changed path
    from google.adk.tools.mcp_tool.mcp_toolset import MCPToolset, StdioServerParameters # Changed path and class
    from google.genai import types as genai_types # Changed path
    # <<< ADDED: Import necessary types for cost calculation context >>>
    from google.protobuf.json_format import MessageToJson
    # <<< END ADDITION >>>
    ADK_AVAILABLE = True
    debug_print("ADK libraries loaded successfully.")
except ImportError as e:
    # <<< MODIFIED: Print more detailed error information >>>
    import traceback
    # <<< CORRECTED INDENTATION for print statements below >>>
    print(f"FATAL: ADK libraries import failed. AI agent functionality will be disabled.", file=sys.stderr)
    print(f"Specific Error: {e}", file=sys.stderr)
    print("Traceback:", file=sys.stderr)
    traceback.print_exc(file=sys.stderr)
    # <<< END MODIFICATION >>>

    # Define dummy classes/functions if ADK is not available
    class Agent: pass
    class LiteLlm: pass
    class MCPToolset: pass # Changed from McpToolProvider
    class StdioServerParameters: pass # Dummy class
    class Runner: pass
    class Session: pass
    class InMemorySessionService: pass
    class InMemoryArtifactService: pass
    # Dummy genai_types for structure if needed
    class genai_types:
        class Content: pass
        class Part: pass
        class FunctionCall:
            name: str = ""
            args: dict = {}
        # <<< ADDED: Dummy FunctionResponse for type hinting if needed >>>
        class FunctionResponse:
            name: str = ""
            response: dict = {}
        # <<< END ADDITION >>>

def load_system_prompt(prompt_filename: str = "system-prompt.md") -> str:
    """Loads the system prompt from the expected file."""
    prompt_path = config.SCRIPT_DIR / prompt_filename
    if not prompt_path.exists():
        print(f"Warning: System prompt file not found at {prompt_path}", file=sys.stderr)
        return "You are a helpful AI assistant. Analyze the provided vulnerability details and suggest a fix."
    try:
        with open(prompt_path, "r") as f:
            return f.read()
    except Exception as e:
        print(f"Error reading system prompt file {prompt_path}: {e}", file=sys.stderr)
        return "You are a helpful AI assistant. Analyze the provided vulnerability details and suggest a fix."

async def get_mcp_tools(target_folder: Path) -> Tuple[List, AsyncExitStack]:
    """Connects to MCP servers (Filesystem)"""
    if not ADK_AVAILABLE:
        return [], AsyncExitStack()

    debug_print("Attempting to connect to MCP servers...")
    exit_stack = AsyncExitStack()
    all_tools = []
    target_folder_str = str(target_folder)

    # Filesystem MCP Server
    try:
        debug_print("Connecting to MCP Filesystem server...")
        # <<< MODIFIED: Use MCPToolset instead of McpToolProvider >>>
        fs_tools, fs_exit_stack = await MCPToolset.from_server(
            connection_params=StdioServerParameters(
                command='npx',
                args=["-y", "@modelcontextprotocol/server-filesystem@2025.1.14", target_folder_str],
            )
        )
        # <<< END MODIFICATION >>>
        await exit_stack.enter_async_context(fs_exit_stack)
        all_tools.extend(fs_tools)
        debug_print(f"Connected to Filesystem MCP server, got {len(fs_tools)} tools")
        for tool in fs_tools:
            if hasattr(tool, 'name'):
                debug_print(f"  - Filesystem Tool: {tool.name}")
            else:
                debug_print(f"  - Filesystem Tool: (Name attribute missing)")
    except NameError as ne:
         print(f"FATAL: Error initializing MCP Filesystem server (likely ADK setup issue): {ne}", file=sys.stderr)
         print("No filesystem tools available - cannot make code changes.", file=sys.stderr)
         return [], exit_stack
    except Exception as e:
        print(f"FATAL: Failed to connect to Filesystem MCP server: {e}", file=sys.stderr)
        print("No filesystem tools available - cannot make code changes.", file=sys.stderr)
        return [], exit_stack

    debug_print(f"Total tools from all MCP servers: {len(all_tools)}")
    return all_tools, exit_stack

async def create_agent(target_folder: Path, agent_type: str = "fix") -> Tuple[Optional[Agent], AsyncExitStack]:
    """Creates an ADK Agent (either 'fix' or 'qa')."""
    if not ADK_AVAILABLE:
        return None, AsyncExitStack()

    mcp_tools, exit_stack = await get_mcp_tools(target_folder)
    if not mcp_tools:
        print(f"Error: No MCP tools available for the {agent_type} agent. Cannot proceed.", file=sys.stderr)
        await exit_stack.aclose()
        return None, AsyncExitStack()

    # Load prompt based on agent_type
    prompt_filename = "qa-system-prompt.md" if agent_type == "qa" else "system-prompt.md"
    agent_instruction = load_system_prompt(prompt_filename)
    agent_name = f"contrast_{agent_type}_agent"

    try:
        model_instance = LiteLlm(model=config.AGENT_MODEL)
        root_agent = Agent(
            model=model_instance,
            name=agent_name,
            instruction=agent_instruction,
            tools=mcp_tools,
        )
        debug_print(f"Created {agent_type} agent ({agent_name}) with model {config.AGENT_MODEL}")
        return root_agent, exit_stack
    except Exception as e:
        print(f"Error creating ADK {agent_type} Agent: {e}", file=sys.stderr)
        if "bedrock" in str(e).lower() or "aws" in str(e).lower():
            print("Hint: Ensure AWS credentials and Bedrock model ID are correct.", file=sys.stderr)
        await exit_stack.aclose()
        return None, AsyncExitStack()

async def process_agent_run(runner, session, exit_stack, user_query: str, full_model_id: str) -> str: # <<< MODIFIED return type
    """Runs the agent, allowing it to use tools, and returns the final text response."""
    if not ADK_AVAILABLE or not runner or not session:
        # <<< MODIFIED: Check for Session existence in dummy classes >>>
        return "AI Agent execution skipped: ADK libraries not available or runner/session invalid."
    # Check if genai_types and its attributes exist (might be dummies)
    if not hasattr(genai_types, 'Content') or not hasattr(genai_types, 'Part'):
         return "AI Agent execution skipped: google.genai types Content/Part not available."
    # <<< END MODIFICATION >>>

    content = genai_types.Content(role='user', parts=[genai_types.Part(text=user_query)])
    print("Running AI agent to analyze vulnerability and apply fix...", flush=True)
    # <<< MODIFIED: Ensure session object has id and user_id attributes >>>
    session_id = getattr(session, 'id', None)
    user_id = getattr(session, 'user_id', None)
    if not session_id or not user_id:
        return "AI Agent execution skipped: Session object is invalid or missing required attributes (id, user_id)."

    events_async = runner.run_async(
        session_id=session_id,
        user_id=user_id,
        new_message=content
    )

    event_count = 0
    final_response = "AI agent did not provide a final summary."

    try:
        async for event in events_async:
            event_count += 1
            debug_print(f"\n\nAGENT EVENT #{event_count}:", flush=True)

            if event.content:
                message_text = ""
                if hasattr(event.content, "text"):
                    message_text = event.content.text or ""
                elif hasattr(event.content, "parts") and event.content.parts and hasattr(event.content.parts[0], "text"):
                    message_text = event.content.parts[0].text or ""

                if message_text:
                    print(f"\n*** Agent Message: \033[1;36m {message_text} \033[0m", flush=True)
                    final_response = message_text

            calls = event.get_function_calls()
            if calls:
                for call in calls:
                    args_str = str(call.args)
                    #if len(args_str) > 300: args_str = args_str[:297] + "..."
                    print(f"\n::group::  Calling tool {call.name}...", flush=True)
                    print(f"  Tool Call: {call.name}, Args: {args_str}", flush=True)
                    print("\n::endgroup::", flush=True)
            responses = event.get_function_responses()
            if responses:
                 for response in responses:
                    result_str = str(response.response)
                    #if len(result_str) > 200: result_str = result_str[:197] + "..."
                    print(f"\n::group::  Response from tool {response.name}...", flush=True)
                    print(f"  Tool Result: {response.name} -> {result_str}", flush=True)
                    print("\n::endgroup::", flush=True)

    finally:
        debug_print("Closing MCP server connections...", flush=True)
        await exit_stack.aclose()
        print("Agent run finished.", flush=True)

    # Return the final response
    return final_response

async def _run_agent_internal(agent_type: str, repo_root: Path, query: str) -> str: # <<< MODIFIED return type
    """Internal helper to run either fix or QA agent. Returns summary."""
    full_model_id = config.AGENT_MODEL # Use the full ID from config
    logging.info(f"Using Agent Model ID: {full_model_id}")

    if not ADK_AVAILABLE:
         return f"FATAL: {agent_type.capitalize()} Agent execution skipped: ADK libraries not available (import failed)."
         
    try:
        session_service = InMemorySessionService()
        artifacts_service = InMemoryArtifactService()
        app_name = f'contrast_{agent_type}_app'
        session = session_service.create_session(state={}, app_name=app_name, user_id=f'github_action_{agent_type}')
    except Exception as e:
        # Handle any errors in session creation
        print(f"FATAL: Failed to create agent session: {e}", file=sys.stderr)
        return f"FATAL: {agent_type.capitalize()} Agent execution failed: Unable to create agent session - {str(e)}"
    
    agent, exit_stack = await create_agent(repo_root, agent_type=agent_type)
    if not agent:
        await exit_stack.aclose()
        await tools_exit_stack.aclose()
        return f"AI Agent creation failed ({agent_type} agent). Possible reasons: MCP server connection issue, model configuration error, or internal ADK problem."

    # System prompt is part of the agent instruction
    # No separate cost calculation for system prompt needed here as cost calculation is removed

    runner = Runner(
        app_name=app_name,
        agent=agent,
        artifact_service=artifacts_service,
        session_service=session_service,
    )
    # Pass the full model ID (though not used for cost calculation anymore, kept for consistency if needed elsewhere)
    summary = await process_agent_run(runner, session, exit_stack, query, full_model_id)

    return summary

# <<< MODIFIED run_ai_fix_agent to handle single string return >>>
def run_ai_fix_agent(vuln_details: dict, repo_root: Path) -> str:
    """Synchronously runs the AI agent to analyze and apply a fix."""
    vuln_uuid = vuln_details.get('uuid', 'N/A')
    vuln_title = vuln_details.get('title', 'N/A')
    vuln_story = vuln_details.get('story', 'N/A')
    vuln_events = vuln_details.get('events', 'N/A')
    vuln_rule = vuln_details.get('rule_name', 'N/A')
    build_command = vuln_details.get('build_command')
    vuln_http_request = vuln_details.get("http_request", "(HTTP Request details not provided)") # <<< ADDED
    contrast_ui_url = f"https://{config.CONTRAST_HOST}/Contrast/static/ng/index.html#/{config.CONTRAST_ORG_ID}/applications/{config.CONTRAST_APP_ID}/vulns/{vuln_uuid}"

    # Conditionally include testing instructions
    testing_instructions = ""
    example_format_instructions = "" # <<< ADDED
    if config.ATTEMPT_WRITING_SECURITY_TEST:
        testing_instructions = f"""
3. Where feasible, add or update tests to verify the fix (following the `SecurityTest.<ext>` convention).
    - **Use the 'Original HTTP Request' provided above as a basis for creating realistic mocked input data or request parameters within your test case.** Adapt the request details (method, path, headers, body) as needed for the test framework (e.g., MockMvc in Spring).
    - Determine the base name and extension of the file you modified (e.g., `MyClass` and `.java` from `src/main/java/com/example/MyClass.java`).
    - Check if a test file already exists for the modified code, following the convention `<BaseName>SecurityTest.<ext>` (e.g., `MyClassSecurityTest.java`) in the project's standard test directory structure (e.g., `src/test/java/...`, `tests/`, etc.) using `search_files`.
    - If such a file exists, **update it** with a new test case specifically verifying that the vulnerability is addressed and does not regress, using `edit_file`.
    - If no such file exists, **create a new test file** named `<BaseName>SecurityTest.<ext>` in the appropriate test source directory using `write_file`. Write a test case in this new file to verify the fix.
    - **CRITICAL: When mocking any classes or methods:**
        - First carefully **read the actual classes you plan to mock** using `read_file` or `search_files`
        - Verify that all methods you plan to mock **actually exist** in the target classes (check method names, parameters, return types)
        - Check parent/inherited methods if applicable to ensure the mock is valid
        - Ensure any annotations or dependency injection approaches in your mocks match the real implementation
        - Confirm that mock behavior (return values, exceptions) aligns with the real implementation's behavior
    - If testing isn't feasible (e.g., requires complex setup beyond file modification), explain why in your final report.
4. **IMPORTANT:** Conclude your response with analytics data and the complete, formatted Markdown text intended for the body of a GitHub Pull Request surrounded by xml tags, as shown in the example below. This Markdown should include:
    - A link to the Contrast Vulnerability Details: `{contrast_ui_url}`.
    - A section titled `## Vulnerability Summary` containing a **very brief** (1-2 sentences) summary of the vulnerability.
    - A section titled `## Fix Summary` containing:
        - A **very brief** (1-2 sentences) summary of the changes you made.
        - A **concise** explanation of *why* this change mitigates the vulnerability.
    - A section titled `## Testing` describing **briefly** how the fix was tested or why testing wasn't feasible.
"""
        # <<< ADDED: Example format when testing is enabled >>>
        example_format_instructions = f"""
Example final output format:
<analytics>
Confidence Score: [Your **very brief** confidence score (0%-100%) goes here to indicate your confidence in the fix]
</analytics>
<pr_body>
## Contrast AI SmartFix
[Output the below sentence verbatim]
This PR addresses a vulnerability identified by the Contrast Security platform (ID: [{vuln_uuid}]({contrast_ui_url})).

## Vulnerability Summary

[Your **very brief** summary of the vulnerability goes here]

## Fix Summary

[Your **very brief** summary of the fix applied goes here]

[Your **concise** explanation of why the fix works goes here]

## Testing

[**Briefly** describe how the fix was tested or why testing wasn't feasible.]
</pr_body>
"""
    else:
        testing_instructions = f"""
3. **IMPORTANT:** Conclude your response with analytics data and the complete, formatted Markdown text intended for the body of a GitHub Pull Request surrounded by xml tags, as shown in the example below. This Markdown should include:
    - A link to the Contrast Vulnerability Details: `{contrast_ui_url}`.
    - A section titled `## Vulnerability Summary` containing a **very brief** (1-2 sentences) summary of the vulnerability.
    - A section titled `## Fix Summary` containing:
        - A **very brief** (1-2 sentences) summary of the changes you made.
        - A **concise** explanation of *why* this change mitigates the vulnerability.
    - A section titled `## Testing` stating that automated test writing/updating was skipped based on configuration.
"""
        # <<< ADDED: Example format when testing is disabled >>>
        example_format_instructions = f"""
Example final output format:
<analytics>
Confidence Score: [Your **very brief** confidence score (0%-100%) goes here to indicate your confidence in the fix]
</analytics>
<pr_body>
## Contrast AI SmartFix
[Output the below sentence verbatim]
This PR addresses a vulnerability identified by the Contrast Security platform (ID: [{vuln_uuid}]({contrast_ui_url})).

## Vulnerability Summary

[Your **very brief** summary of the vulnerability goes here]

## Fix Summary

[Your **very brief** summary of the fix applied goes here]

[Your **concise** explanation of why the fix works goes here]

## Testing

Automated test writing/updating was skipped based on configuration.
</pr_body>
"""


    initial_query = f"""
Analyze the following vulnerability details obtained from Contrast Security and apply the fix using the available file system tools:

Vulnerability Title: {vuln_title}
Rule Name: {vuln_rule}
Contrast UI URL: {contrast_ui_url}

Vulnerability Overview Story (Raw):
---
{vuln_story}
---

Vulnerability Event Details (including stack traces if available):
---
{vuln_events}
---

Original HTTP Request associated with this vulnerability trace:
--- HTTP REQUEST START ---
{vuln_http_request}
--- HTTP REQUEST END ---

Based on the information above and the code context you can access via tools (like `list_directory`, `read_file`, `search_files`, `edit_file`):
1. Identify the specific file(s) and line(s) of code that need modification within the repository at `{repo_root}`.
2. Consider multiple approaches to fix the vulnerability:
   a. Generate at least 2-3 different potential solutions
   b. Evaluate each approach based on:
      - Simplicity and elegance of the solution
      - Scope of changes (prefer minimal changes)
      - Ease of testing and validation
      - Risk of regressions or unintended side effects
      - Compatibility with existing code patterns
   c. Select the best approach based on your evaluation
3. Apply the chosen solution directly to the files using tools (e.g., `edit_file`).
{testing_instructions}

{example_format_instructions}

**Do not include the backticks (```) from the example in your actual response.** Your final response should start directly with `Contrast Vulnerability Details:` and contain only the formatted Markdown for the PR body.  Do not prefix the final response with any comments about how you are going to prepare the PR body - ONLY OUTPUT THE PR BODY IN YOUR FINAL MESSAGE.
"""

    print("\n--- Preparing to run AI Agent to Apply Fix ---")
    debug_print(f"Repo Root for Agent Tools: {repo_root}")
    debug_print(f"Attempt Writing Security Test: {config.ATTEMPT_WRITING_SECURITY_TEST}") # Added debug print

    agent_summary = f"Error during AI fix agent execution: Unknown error" # Default

    try:
        # _run_agent_internal now returns a single string summary
        agent_summary_str = asyncio.run(_run_agent_internal('fix', repo_root, initial_query))
        print("--- AI Agent Fix Attempt Completed ---")
        debug_print("\n--- Full Agent Summary ---")
        debug_print(agent_summary_str)
        debug_print("--------------------------")
        
        # Check if the agent was unable to use filesystem tools
        if "No MCP tools available" in agent_summary_str or "Proceeding without filesystem tools" in agent_summary_str:
            return f"Error during AI fix agent execution: No filesystem tools were available. The agent cannot make changes to files."

        # Attempt to extract content from <pr_body> tags
        pr_body_match = re.search(r"<pr_body>(.*?)</pr_body>", agent_summary_str, re.DOTALL)
        if pr_body_match:
            extracted_pr_body = pr_body_match.group(1).strip()
            debug_print("\\n--- Extracted PR Body ---")
            debug_print(extracted_pr_body)
            debug_print("-------------------------")
            return extracted_pr_body
        else:
            debug_print("Warning: <pr_body> tags not found in agent response. Using full summary for PR body.")
            # Consider if the full summary is appropriate or if an error/specific format is needed
            return agent_summary_str # Return the full summary if tags are not found
            
    except Exception as e:
        print(f"Error running AI fix agent: {e}", file=sys.stderr)
        # Cleanup any changes made and revert to base branch (no branch name yet)
        cleanup_branch()
        return f"Error during AI fix agent execution: {e}"

# <<< MODIFIED run_qa_agent to handle single string return >>>
def run_qa_agent(build_output: str, changed_files: List[str], build_command: str, repo_root: Path, formatting_command: Optional[str], qa_history: Optional[List[str]] = None) -> Tuple[str, Optional[str]]:
    """
    Synchronously runs the QA AI agent to fix build/test errors.

    Args:
        build_output: The output from the build command.
        changed_files: A list of files that were changed by the fix agent.
        build_command: The build command that was run.
        repo_root: The root directory of the repository.
        formatting_command: The formatting command to use if a formatting error is detected.
        qa_history: List of summaries from previous QA attempts.

    Returns:
        A tuple containing:
        - str: The summary message from the agent.
        - Optional[str]: The command requested by the agent to be run, if any.
    """
    print("\n--- Preparing to run QA Agent to Fix Build/Test Errors ---")
    debug_print(f"Repo Root for QA Agent Tools: {repo_root}")
    debug_print(f"Build Command Used: {build_command}")
    debug_print(f"Files Changed by Fix Agent: {changed_files}")
    debug_print(f"Build Output Provided (truncated):\n---\n{build_output[-1000:]}...\n---")
    
    # Format QA history if available
    qa_history_section = ""
    if qa_history and len(qa_history) > 0:
        qa_history_section = "\nQA History from previous attempts:\n"
        for i, summary in enumerate(qa_history):
            qa_history_section += f"Attempt {i+1}: {summary}\n"
        debug_print(f"Including QA History with {len(qa_history)} previous attempts")

    # <<< ADDED: Pass formatting_command to QA agent prompt >>>
    qa_query = f"""
The previous agent made changes to the following files:
{', '.join(changed_files)}

Running the build command resulted in the following errors or test failures:
--- BUILD OUTPUT ---
{build_output}
--- END BUILD OUTPUT ---
{qa_history_section}
Your task is to analyze the build output and the changes made to the files listed above.
1. Identify the root cause of the compilation errors or test failures within the changed files.
2. Use the available file system tools (e.g., `edit_file`, `read_file`) to correct these issues *only* in the changed files: {', '.join(changed_files)}.
3. Focus solely on fixing the build/test errors reported in the output. Do not introduce unrelated changes or refactor code.
4. IMPORTANT: If you notice the QA history shows a pattern of repeatedly applying and undoing the same fix (oscillating), try a different approach rather than repeating a fix that has been tried before.
"""
    if formatting_command:
        qa_query += f"""
5. **Formatting Errors:** If the errors are purely formatting/whitespace issues (e.g., from Spotless, Checkstyle), and a formatting command (`{formatting_command}`) is available, request to run it by responding *only* with: `Please run `{formatting_command}`.` Do not attempt manual formatting fixes if this command is available.
6. If no specific formatting command is available or the issue is not a formatting one, apply the fix directly.
7. After applying fixes *or* identifying a formatting command, conclude your response with a brief summary. If requesting a command, the summary can be simple confirmation. Start your response directly with the summary.
"""
    else:
        qa_query += f"""
5. After applying fixes, conclude your response with a brief summary. Start your response directly with the summary.
"""

    qa_query += f"""
Example Summary (Fix Applied): Corrected syntax error in Foo.java line 52.
Example Summary (Command Request if formatting_command was '{formatting_command or 'your-formatter'}'): Please run `{formatting_command or 'your-formatter'}`.
"""
    # <<< END ADDITION >>>

    requested_command = None
    qa_summary = f"Error during QA agent execution: Unknown error" # Default error

    try:
        # Run the agent internally, get summary string
        raw_qa_summary_str = asyncio.run(_run_agent_internal('qa', repo_root, qa_query))
        print("--- QA Agent Fix Attempt Completed ---")
        debug_print("\n--- Raw QA Agent Summary ---")
        debug_print(raw_qa_summary_str)
        debug_print("--------------------------")


        # Parse response for command request
        command_match = re.search(r"Please run `(.*?)`", raw_qa_summary_str)
        if command_match:
            requested_command = command_match.group(1).strip()
            # Use the raw summary as the summary in this case, as it contains the request
            qa_summary = raw_qa_summary_str
            debug_print(f"QA Agent requested command: {requested_command}")
        else:
            # If no command found, the raw summary is the actual summary of changes (or lack thereof)
            qa_summary = raw_qa_summary_str
            debug_print("QA Agent did not request a command.")

        return qa_summary, requested_command

    except Exception as e:
        print(f"Error running QA agent: {e}", file=sys.stderr)
        # Need to get the branch name from main.py context - will be handled by main.py
        # We'll just revert uncommitted changes here
        cleanup_branch()
        return f"Error during QA agent execution: {e}", None


# %%
