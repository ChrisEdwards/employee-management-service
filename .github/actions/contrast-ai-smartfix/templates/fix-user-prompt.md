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
4. Where feasible, add or update tests to verify the fix (following the `SecurityTest.<ext>` convention).
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
5. **IMPORTANT:** Conclude your response with analytics data and the complete, formatted Markdown text intended for the body of a GitHub Pull Request surrounded by xml tags, as shown in the example below. This Markdown should include:
    - A link to the Contrast Vulnerability Details: `{contrast_ui_url}`.
    - A section titled `## Vulnerability Summary` containing a **very brief** (1-2 sentences) summary of the vulnerability.
    - A section titled `## Fix Summary` containing:
        - A **very brief** (1-2 sentences) summary of the changes you made.
        - A **concise** explanation of *why* this change mitigates the vulnerability.
    - A section titled `## Testing` describing **briefly** how the fix was tested or why testing wasn't feasible.

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

**Do not include the backticks (```) from the example in your actual response.** Your final response should start directly with `Contrast Vulnerability Details:` and contain only the formatted Markdown for the PR body.