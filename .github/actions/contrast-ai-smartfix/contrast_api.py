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

import requests
import json
import re       # Added for regular expressions
import sys
import os # <<< ADDED
from typing import Optional # <<< ADDED

# --- Configuration ---
DEBUG_MODE = os.getenv("DEBUG_MODE", "false").lower() == "true" # <<< ADDED

# --- Helper Functions ---
def debug_print(*args, **kwargs): # <<< ADDED
    """Prints only if DEBUG_MODE is True."""
    if DEBUG_MODE:
        print(*args, **kwargs)

REDACTED = "[REDACTED]"

# --- Story Processing Helpers ---
def _get_redacted_text_line(formatted_text, key, variables):
    result = REDACTED # Default in case of error or if key implies redaction
    raw_value = ""
    try:
        # Safely get the variable value, default to empty string if not found or not string
        raw_value = variables.get(key, "")
        if not isinstance(raw_value, str):
             raw_value = str(raw_value) # Ensure it's a string for replacement

        placeholder_str = "{{" + key + "}}"
        if placeholder_str in formatted_text:
            replacement_str = REDACTED if "tainted" in key.lower() else raw_value
            # Replace only the first occurrence
            result = formatted_text.replace(placeholder_str, replacement_str, 1)
        else:
            result = formatted_text # Placeholder not found, return original

    except Exception as e:
        print(
            f"Warning: Error replacing variable '{key}' in vuln overview for text chunk near '{raw_value}'. Exception: {e}",
            file=sys.stderr
        )
        # Attempt to return the text without the problematic placeholder if possible
        try:
            placeholder_str = "{{" + key + "}}"
            result = formatted_text.replace(placeholder_str, REDACTED) # Replace with REDACTED on error
        except Exception:
            result = formatted_text # Fallback to original text on secondary error
    return result

def _assemble_vuln_overview_story_chunk(chunk_node, overview_list, formatted_text_key_name, variables_key_name):
    if not chunk_node or not isinstance(chunk_node, dict):
        return # Skip if chunk_node is missing or not a dictionary

    formatted_text_node = chunk_node.get(formatted_text_key_name)
    if formatted_text_node is None or not isinstance(formatted_text_node, str):
        return # Skip if formatted text is missing or not a string

    formatted_text = formatted_text_node
    # Redact {{#taint}}...{{/taint}} blocks first
    formatted_text = re.sub(r"\{\{#taint\}\}.*?\{\{/taint\}\}", REDACTED, formatted_text, flags=re.DOTALL)

    variables = chunk_node.get(variables_key_name)
    if formatted_text and variables is not None and isinstance(variables, dict):
        keys_to_process = list(variables.keys()) # Get keys to iterate over

        # Iterate through keys and replace placeholders
        for key in keys_to_process:
            placeholder = "{{" + key + "}}"
            # Keep replacing as long as the placeholder exists
            while placeholder in formatted_text:
                 original_text = formatted_text # Store original to detect no change
                 formatted_text = _get_redacted_text_line(formatted_text, key, variables)
                 # Basic protection against infinite loops if replacement fails unexpectedly
                 if formatted_text == original_text or placeholder not in formatted_text:
                     break

        # Replace paragraph and newline markers
        formatted_text = formatted_text.replace("{{#paragraph}}", "\n")
        formatted_text = formatted_text.replace("{{/paragraph}}", "\n")
        formatted_text = formatted_text.replace("{{{nl}}}", "\n")

        # Remove any remaining unresolved placeholders (replace with REDACTED)
        formatted_text = re.sub(r"\{\{.*?\}\}", REDACTED, formatted_text)

        overview_list.append(formatted_text.strip()) # Add processed text to the list

# --- Event Processing Helpers ---
def _append_event_view_fragments(events_list, fragments_node):
    """Processes fragments within an event view line."""
    if not isinstance(fragments_node, list):
        return
    current_line_parts = []
    for fragment_node in fragments_node:
        if not isinstance(fragment_node, dict):
            continue
        type_node = fragment_node.get("type")
        if type_node == "TAINT_VALUE":
            current_line_parts.append(REDACTED)
        else:
            value_node = fragment_node.get("value")
            if value_node is not None:
                value = str(value_node) # Ensure it's a string
                # Replace HTML quote entity
                value = value.replace("&quot;", "\"")
                current_line_parts.append(value)
    # Append the joined fragments for this line
    # Use ''.join for potentially mixed types after ensuring string conversion
    events_list.append("".join(map(str, current_line_parts)))

def _append_event_view(events_list, event_node, view_name):
    """Processes a specific view (like codeView) within an event."""
    if not isinstance(event_node, dict):
        return
    view_node = event_node.get(view_name)
    if isinstance(view_node, dict):
        # Use .get with default False for boolean check
        nested = view_node.get("nested", False)
        if not nested:
            # Append view name only if there are lines to show
            lines_node = view_node.get("lines")
            if isinstance(lines_node, list) and lines_node:
                events_list.append(f"{view_name}: ")
                initial_length = len(events_list)
                for line_node in lines_node:
                    if isinstance(line_node, dict):
                        fragments_node = line_node.get("fragments")
                        _append_event_view_fragments(events_list, fragments_node)
                        events_list.append("\n") # Newline after each line's fragments
                # Remove trailing newline if fragments were added
                if len(events_list) > initial_length and events_list[-1] == "\n":
                    events_list.pop()

def _get_and_append_stacktraces(events_list, vuln_uuid, event_id, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key):
    """Fetches stack trace details for an event and appends them to the list."""
    debug_print(f"--- Fetching stack trace for event ID: {event_id} ---")
    details_api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/traces/{vuln_uuid}/events/{event_id}/details?expand=skip_links"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Accept": "application/json"
    }
    try:
        response = requests.get(details_api_url, headers=headers)
        response.raise_for_status()
        details_json = response.json()

        if details_json.get("success") and "event" in details_json:
            stacktraces = details_json.get("event", {}).get("stacktraces")
            if isinstance(stacktraces, list):
                for trace in stacktraces:
                    description = trace.get("description")
                    if description:
                        # Append stack trace line, indented for clarity
                        events_list.append(f"  at {description}\n")
            else:
                print(f"Warning: 'stacktraces' not found or not a list for event {event_id}.", file=sys.stderr)
        else:
            print(f"Stack trace request failed or missing 'event' data for event {event_id}.", file=sys.stderr)

    except requests.exceptions.RequestException as e:
        print(f"Error fetching stack trace for event {event_id}: {e}", file=sys.stderr)
    except json.JSONDecodeError:
        print(f"Error decoding stack trace JSON for event {event_id}.", file=sys.stderr)
        # Avoid printing potentially large/sensitive response text by default
        # print("Response text:", response.text, file=sys.stderr)

def _append_event(events_list, event_node, vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key):
    """Processes a single event node, including fetching stack traces."""
    if not isinstance(event_node, dict):
        return
    description = event_node.get("description")
    if description is not None:
        events_list.append(str(description) + "\n")

    _append_event_view(events_list, event_node, "codeView")
    _append_event_view(events_list, event_node, "probableStartLocationView")

    # Fetch and append stack traces
    event_id = event_node.get("id")
    if event_id:
        _get_and_append_stacktraces(events_list, vuln_uuid, event_id, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key)
    else:
        print("Warning: Event node missing 'id'. Cannot fetch stack trace.", file=sys.stderr)

# --- API Functions ---
def get_vulnerabilities_to_process(contrast_host, contrast_org_id, contrast_app_id, contrast_auth_key, contrast_api_key, limit=10, severities=None):
    """Fetches vulnerabilities from Contrast API and returns a list of non-closed ones to consider.
    
    Args:
        contrast_host: The Contrast Security host URL
        contrast_org_id: The organization ID
        contrast_app_id: The application ID
        contrast_auth_key: The Contrast authorization key
        contrast_api_key: The Contrast API key
        limit: The maximum number of vulnerabilities to fetch
        severities: A list of severity levels to filter by (e.g., ["CRITICAL", "HIGH"])
    """
    debug_print("\n--- Fetching data from Contrast API ---")
    # Fetch more than needed initially in case some are filtered out later
    api_url = f"https://{contrast_host}/Contrast/api/ng/organizations/{contrast_org_id}/orgtraces/ui?expand=application%2Csession_metadata&offset=0&limit={limit}&sort=-severity"

    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    
    # Use the provided severities or default to empty list
    if not severities:
        severities = []
    
    debug_print(f"Filtering by severity levels: {severities}")

    data = {
      "quickFilter": "OPEN",
      "modules": [
        contrast_app_id
      ],
      # ... other filters ...
      "servers": [],
      "filterTags": [],
      "severities": severities,
      "status": [],
      "substatus": [],
      "vulnTypes": [],
      "environments": [],
      "urls": [],
      "sinks": [],
      "securityStandards": [],
      "appVersionTags": [],
      "routes": [],
      "tracked": False,
      "untracked": False,
      "technologies": [],
      "applicationTags": [],
      "applicationImportances": [],
      "languages": [],
      "protectStatuses": [],
      "metadataFilters": []
    }

    closed_statuses = {"NotAProblem", "Remediated", "Fixed", "AutoRemediated"}
    vulnerabilities_to_process = []

    try:
        debug_print(f"Making POST request to: {api_url}")
        response = requests.post(api_url, headers=headers, json=data)
        response.raise_for_status() # Raises an HTTPError for bad responses

        debug_print("Contrast API Response Status Code:", response.status_code)
        response_json = response.json()

        if response_json.get("success") and response_json.get("items"):
            for item in response_json.get("items", []):
                vulnerability = item.get("vulnerability", {})
                vuln_status = vulnerability.get("status")

                if vuln_status not in closed_statuses:
                    vuln_uuid = vulnerability.get("uuid")
                    vuln_title = vulnerability.get("title")
                    vuln_rule_name = vulnerability.get("ruleName")
                    vuln_visible = vulnerability.get("visible")
                    vuln_severity = vulnerability.get("severity")

                    if vuln_uuid and vuln_title:
                        debug_print(f"Found potential vulnerability: UUID={vuln_uuid}, Title={vuln_title}, Rule={vuln_rule_name}, Severity={vuln_severity}, Status={vuln_status}")
                        vulnerabilities_to_process.append({
                            "uuid": vuln_uuid,
                            "title": vuln_title,
                            "rule_name": vuln_rule_name,
                            "visible": vuln_visible,
                            "severity": vuln_severity,
                            "status": vuln_status
                        })
                    else:
                        print(f"Found non-closed vulnerability (Status: {vuln_status}) but missing essential UUID or Title. Skipping.")
                        continue

            if not vulnerabilities_to_process:
                print("No non-closed vulnerabilities found in the API response.")
            return vulnerabilities_to_process
        else:
            print("No vulnerabilities found or API request failed.")
            return []

    except requests.exceptions.RequestException as e:
        print(f"Error fetching vulnerabilities: {e}", file=sys.stderr)
        return []
    except json.JSONDecodeError:
        print("Error decoding JSON response from Contrast API.", file=sys.stderr)
        # Avoid printing potentially large/sensitive response text by default
        # print("Response text:", response.text, file=sys.stderr)
        return []

def add_note_to_vulnerability(vuln_uuid: str, note_content: str, contrast_host: str, contrast_org_id: str, contrast_app_id: str, contrast_auth_key: str, contrast_api_key: str) -> bool:
    """Adds a note to a specific vulnerability in Contrast.

    Args:
        vuln_uuid: The UUID of the vulnerability.
        note_content: The content of the note to add.
        contrast_host: The Contrast Security host URL.
        contrast_org_id: The organization ID.
        contrast_app_id: The application ID.
        contrast_auth_key: The Contrast authorization key.
        contrast_api_key: The Contrast API key.

    Returns:
        bool: True if the note was added successfully, False otherwise.
    """
    debug_print(f"--- Adding note to vulnerability {vuln_uuid} ---")
    # The app_id is in the URL structure for notes, ensure it's available
    api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/applications/{contrast_app_id}/traces/{vuln_uuid}/notes?expand=skip_links"

    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    payload = {
        "note": note_content
    }

    try:
        debug_print(f"Making POST request to: {api_url}")
        debug_print(f"Payload: {json.dumps(payload)}") # Log the payload for debugging
        response = requests.post(api_url, headers=headers, json=payload)
        response.raise_for_status()  # Raises an HTTPError for bad responses (4xx or 5xx)

        debug_print(f"Add note API Response Status Code: {response.status_code}")
        response_json = response.json()

        if response_json.get("success"):
            print(f"Successfully added note to vulnerability {vuln_uuid}.")
            return True
        else:
            error_message = response_json.get("messages", ["Unknown error"])[0]
            print(f"Failed to add note to vulnerability {vuln_uuid}. Error: {error_message}", file=sys.stderr)
            return False

    except requests.exceptions.HTTPError as e:
        print(f"HTTP error adding note to vulnerability {vuln_uuid}: {e.response.status_code} - {e.response.text}", file=sys.stderr)
        return False
    except requests.exceptions.RequestException as e:
        print(f"Request error adding note to vulnerability {vuln_uuid}: {e}", file=sys.stderr)
        return False
    except json.JSONDecodeError:
        print(f"Error decoding JSON response when adding note to vulnerability {vuln_uuid}.", file=sys.stderr)
        # print("Response text:", response.text, file=sys.stderr) # Be cautious with logging full response
        return False

def set_vulnerability_status(vuln_uuid: str, status: str, contrast_host: str, contrast_org_id: str, contrast_auth_key: str, contrast_api_key: str) -> bool:
    """Sets the status of a specific vulnerability in Contrast."""
    api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/orgtraces/mark"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    payload = {
        "note": "Contrast AI SmartFix remediated",
        "traces": [vuln_uuid],
        "status": status
    }

    debug_print(f"Setting status for {vuln_uuid} to {status} via URL: {api_url}")
    debug_print(f"Payload for set status: {json.dumps(payload)}")

    try:
        response = requests.put(api_url, headers=headers, json=payload, timeout=30)
        if response.status_code == 200: # Or other success codes like 204
            debug_print(f"Successfully set status for vulnerability {vuln_uuid} to {status}.")
            return True
        else:
            print(f"Error setting status for vulnerability {vuln_uuid}: {response.status_code} - {response.text}", file=sys.stderr)
            return False
    except requests.exceptions.RequestException as e:
        print(f"Request failed while setting status for vulnerability {vuln_uuid}: {e}", file=sys.stderr)
        return False

def get_vulnerability_tags(vuln_uuid: str, contrast_host: str, contrast_org_id: str, contrast_auth_key: str, contrast_api_key: str) -> Optional[list[str]]:
    """Gets the existing tags for a specific vulnerability in Contrast."""
    api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/tags/traces/bulk?expand=skip_links"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    payload = {"traces_uuid": [vuln_uuid]}

    debug_print(f"Getting tags for {vuln_uuid} via URL: {api_url}")
    debug_print(f"Payload for get tags: {json.dumps(payload)}")

    try:
        response = requests.post(api_url, headers=headers, json=payload, timeout=30)
        if response.status_code == 200:
            response_data = response.json()
            if response_data.get("success"):
                tags = response_data.get("tags", [])
                debug_print(f"Successfully retrieved tags for vulnerability {vuln_uuid}: {tags}")
                return tags
            else:
                print(f"API indicated failure while getting tags for {vuln_uuid}: {response_data.get('messages')}", file=sys.stderr)
                return None
        else:
            print(f"Error getting tags for vulnerability {vuln_uuid}: {response.status_code} - {response.text}", file=sys.stderr)
            return None
    except requests.exceptions.RequestException as e:
        print(f"Request failed while getting tags for vulnerability {vuln_uuid}: {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError as e:
        print(f"Failed to decode JSON response while getting tags for {vuln_uuid}: {e}", file=sys.stderr)
        return None


def add_vulnerability_tags(vuln_uuid: str, tags_to_set: list[str], contrast_host: str, contrast_org_id: str, contrast_auth_key: str, contrast_api_key: str) -> bool:
    """Adds tags to a specific vulnerability in Contrast. This will overwrite existing tags if not included in tags_to_set."""
    api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/tags/traces/bulk?expand=skip_links"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    payload = {
        "traces_uuid": [vuln_uuid],
        "tags": tags_to_set,
        "tags_remove": []  # Assuming we don't want to explicitly remove any tags not in the new set this way
    }

    debug_print(f"Setting tags for {vuln_uuid} to {tags_to_set} via URL: {api_url}")
    debug_print(f"Payload for set tags: {json.dumps(payload)}")

    try:
        response = requests.put(api_url, headers=headers, json=payload, timeout=30)
        if response.status_code == 200:
            response_data = response.json()
            if response_data.get("success"):
                debug_print(f"Successfully set tags for vulnerability {vuln_uuid}.")
                return True
            else:
                print(f"API indicated failure while setting tags for {vuln_uuid}: {response_data.get('messages')}", file=sys.stderr)
                return False
        else:
            print(f"Error setting tags for vulnerability {vuln_uuid}: {response.status_code} - {response.text}", file=sys.stderr)
            return False
    except requests.exceptions.RequestException as e:
        print(f"Request failed while setting tags for vulnerability {vuln_uuid}: {e}", file=sys.stderr)
        return False
    except json.JSONDecodeError as e:
        print(f"Failed to decode JSON response while setting tags for {vuln_uuid}: {e}", file=sys.stderr)
        return False

'''
AI wrote this and I'm not sure why; so, I commented it out
# --- Main Vulnerability Fetching Logic ---
def get_vulnerability_details(vuln_uuid: str, contrast_host: str, contrast_org_id: str, contrast_app_id: str, contrast_auth_key: str, contrast_api_key: str) -> Optional[dict]:
    """Fetches detailed information about a specific vulnerability, including its story and events.

    Args:
        vuln_uuid: The UUID of the vulnerability to fetch.
        contrast_host: The Contrast Security host URL.
        contrast_org_id: The organization ID.
        contrast_app_id: The application ID.
        contrast_auth_key: The Contrast authorization key.
        contrast_api_key: The Contrast API key.

    Returns:
        A dictionary containing detailed vulnerability information, or None if an error occurs.
    """
    if not vuln_uuid:
        print("Error: No vulnerability UUID provided to get_vulnerability_details.", file=sys.stderr)
        return None

    debug_print(f"\n--- Fetching details for vulnerability UUID: {vuln_uuid} ---")
    base_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/traces/{vuln_uuid}"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Accept": "application/json"
    }

    try:
        # Fetch the base vulnerability details
        response = requests.get(base_url, headers=headers)
        response.raise_for_status()
        vuln_data = response.json()

        if not vuln_data.get("success"):
            print(f"API indicated failure while fetching vulnerability details: {vuln_data.get('messages')}", file=sys.stderr)
            return None

        # Extract and process the main vulnerability fields
        vulnerability = vuln_data.get("vulnerability", {})
        vuln_details = {
            "uuid": vulnerability.get("uuid"),
            "title": vulnerability.get("title"),
            "description": vulnerability.get("description"),
            "severity": vulnerability.get("severity"),
            "status": vulnerability.get("status"),
            "rule_name": vulnerability.get("ruleName"),
            "visible": vulnerability.get("visible"),
            "tags": [],  # Initialize tags as empty list
            "events": []  # Initialize events as empty list
        }

        # Fetch and attach the story for the vulnerability
        story = get_vulnerability_story(vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key)
        if story:
            vuln_details["story"] = story

        # Fetch and attach the events for the vulnerability
        events = get_vulnerability_events(vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key)
        if events:
            vuln_details["events"] = events

        # Fetch and attach the tags for the vulnerability
        tags = get_vulnerability_tags(vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key)
        if tags is not None:
            vuln_details["tags"] = tags  # Only set if fetch was successful

        debug_print("--- Fetched Vulnerability Details ---")
        debug_print(json.dumps(vuln_details, indent=2))
        debug_print("-----------------------------------")
        return vuln_details

    except requests.exceptions.RequestException as e:
        print(f"Error during vulnerability details fetch: {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError:
        print("Error decoding JSON response during vulnerability details fetch.", file=sys.stderr)
        return None
    except Exception as e:
        print(f"Unexpected error fetching vulnerability details: {e}", file=sys.stderr)
        return None
'''

def get_vulnerability_story(vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key):
    """Fetches the story for a given vulnerability UUID, processes it, prints it,
    and returns the processed story."""
    if not vuln_uuid:
        print("Error: No vulnerability UUID provided to get_vulnerability_story.", file=sys.stderr)
        return None # Return None for story

    debug_print(f"\n--- Fetching story for vulnerability UUID: {vuln_uuid} ---")
    # Construct the API URL
    story_api_url = f"http://{contrast_host}/Contrast/api/ng/{contrast_org_id}/traces/{vuln_uuid}/story"

    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Accept": "application/json"
    }

    try:
        debug_print(f"Making GET request to: {story_api_url}")
        response = requests.get(story_api_url, headers=headers)
        response.raise_for_status()

        debug_print("Story API Response Status Code:", response.status_code)
        response_json = response.json()

        if response_json.get("success") and "story" in response_json:
            story_data = response_json.get("story")
            overview_parts = []

            chapters_node = story_data.get("chapters", [])
            if isinstance(chapters_node, list):
                for chapter_node in chapters_node:
                    _assemble_vuln_overview_story_chunk(
                        chapter_node, overview_parts, "introTextFormat", "introTextVariables")
                    _assemble_vuln_overview_story_chunk(
                        chapter_node, overview_parts, "bodyFormat", "bodyFormatVariables")
            else:
                 print("Warning: 'chapters' node is not a list.", file=sys.stderr)

            risk_node = story_data.get("risk")
            _assemble_vuln_overview_story_chunk(
                risk_node, overview_parts, "formattedText", "formattedTextVariables")

            # Process custom_risk (handle potential absence)
            # Assuming custom_risk is at the top level based on Java code structure.
            # Commented out for now as requested
            # custom_risk_node = response_json.get("custom_risk")
            # _assemble_vuln_overview_story_chunk(
            #     custom_risk_node, overview_parts, "formattedText", "formattedTextVariables")

            final_overview = "\n".join(part for part in overview_parts if part)

            debug_print("--- Processed Vulnerability Overview ---")
            debug_print(final_overview)
            debug_print("--------------------------------------")
            return final_overview # Return processed story ONLY
        else:
            print("Could not find 'story' in the API response or request was not successful.", file=sys.stderr)
            return None # Return None for story

    except requests.exceptions.RequestException as e:
        print(f"Error during Contrast Story API request: {e}", file=sys.stderr)
        return None # Return None for story on request error
    except json.JSONDecodeError:
        print("Error decoding JSON response from Contrast Story API.", file=sys.stderr)
        print("Response text:", response.text)
        return None # Return None for story on JSON error

def get_vulnerability_events(vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key):
    """
    Fetches the event summary, gets stack traces for each event, processes into
    a formatted string, prints it, and returns it.
    """
    if not vuln_uuid:
        print("Error: No vulnerability UUID provided to get_vulnerability_events.", file=sys.stderr)
        return None

    debug_print(f"\n--- Fetching event summary for vulnerability UUID: {vuln_uuid} ---")
    events_api_url = f"https://{contrast_host}/Contrast/api/ng/{contrast_org_id}/traces/{vuln_uuid}/events/summary?expand=skip_links&legacy=false"
    headers = {
        "Authorization": contrast_auth_key,
        "API-Key": contrast_api_key,
        "Accept": "application/json"
    }

    try:
        debug_print(f"Making GET request to: {events_api_url}")
        response = requests.get(events_api_url, headers=headers)
        response.raise_for_status()

        debug_print("Events API Response Status Code:", response.status_code)
        response_json = response.json()

        if response_json.get("success", True):
            events_node = response_json.get("events")
            events_parts = []

            if isinstance(events_node, list):
                for event_node in events_node:
                    # Pass API config details needed for stack trace fetching
                    _append_event(events_parts, event_node, vuln_uuid, contrast_host, contrast_org_id, contrast_auth_key, contrast_api_key)
                    events_parts.append("\n") # Add blank line between events
            else:
                print("Warning: 'events' node not found or not a list in API response.", file=sys.stderr)

            # Join the processed parts into the final string
            # Filter out potential None values and ensure parts are strings
            final_event_details = "".join(str(part) for part in events_parts if part is not None).strip()

            debug_print("--- Processed Vulnerability Event Details ---")
            debug_print(final_event_details)
            debug_print("-------------------------------------------")
            return final_event_details
        else:
            print("Events API request indicated failure.", file=sys.stderr)
            # Optional: Print full response for debugging
            # print(json.dumps(response_json, indent=2))
            return None

    except requests.exceptions.RequestException as e:
        print(f"Error during Contrast Events API request: {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError:
        print("Error decoding JSON response from Contrast Events API.", file=sys.stderr)
        print("Response text:", response.text)
        return None

def get_vulnerability_http_request(vuln_uuid: str, host: str, org_id: str, auth_key: str, api_key: str) -> Optional[str]:
    """
    Fetches the HTTP request details for a given vulnerability trace UUID.

    Args:
        vuln_uuid: The UUID of the vulnerability trace.
        host: The Contrast host (e.g., app.contrastsecurity.com).
        org_id: The Contrast organization ID.
        auth_key: The Contrast Authorization header value.
        api_key: The Contrast API Key header value.

    Returns:
        The raw HTTP request text (including headers and body) as a string,
        or None if fetching fails or the data is not found.
    """
    api_url = f"https://{host}/Contrast/api/ng/{org_id}/traces/{vuln_uuid}/httprequest"
    headers = {
        "Authorization": auth_key,
        "API-Key": api_key,
        "Accept": "application/json"
    }
    params = {"expand": "skip_links"} # Add the query parameter

    debug_print(f"Fetching HTTP request details for vuln UUID: {vuln_uuid}")
    try:
        response = requests.get(api_url, headers=headers, params=params, timeout=30)
        response.raise_for_status() # Raise HTTPError for bad responses (4xx or 5xx)

        data = response.json()

        if data.get("success") and "http_request" in data and "text" in data["http_request"]:
            request_text = data["http_request"]["text"]
            debug_print(f"Successfully fetched HTTP request text (length: {len(request_text)}): \n{request_text[:1000]}...") # Log first 1000 chars
            return request_text
        else:
            error_msg = data.get("messages", ["Unknown error"])[0]
            print(f"Error fetching HTTP request for {vuln_uuid}: API indicated failure or missing data. Message: {error_msg}", file=sys.stderr)
            return None

    except requests.exceptions.RequestException as e:
        print(f"Error during HTTP request fetch for {vuln_uuid}: {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON response for HTTP request fetch ({vuln_uuid}): {e}", file=sys.stderr)
        print(f"Response text: {response.text[:500]}...", file=sys.stderr) # Log part of the response
        return None
    except Exception as e:
        print(f"Unexpected error fetching HTTP request for {vuln_uuid}: {e}", file=sys.stderr)
        return None

# %%
