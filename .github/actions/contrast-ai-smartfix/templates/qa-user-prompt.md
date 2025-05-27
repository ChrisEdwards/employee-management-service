The previous agent made changes to the following files:
{changed_files}

Running the build command resulted in the following errors or test failures:
--- BUILD OUTPUT ---
{build_output}
--- END BUILD OUTPUT ---

QA History from previous attempts:
{qa_history_section}

Your task is to analyze the build output and the changes made to the files listed above.
1. Identify the root cause of the compilation errors or test failures within the changed files.
2. Use the available file system tools (e.g., `edit_file`, `read_file`) to correct these issues *only* in the changed files: {', '.join(changed_files)}.
3. Focus solely on fixing the build/test errors reported in the output. Do not introduce unrelated changes or refactor code.
4. IMPORTANT: If you notice the QA history shows a pattern of repeatedly applying and undoing the same fix (oscillating), try a different approach rather than repeating a fix that has been tried before.
5. After applying fixes, conclude your response with a brief summary. Start your response directly with the summary.

Example Summary (Fix Applied): Corrected syntax error in Foo.java line 52.
