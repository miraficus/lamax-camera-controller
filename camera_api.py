import requests
import json

class CameraAPI:
    """Handles communication with the camera's API."""

    def __init__(self, cgi_base_url, file_base_url):
        self.cgi_base_url = cgi_base_url
        self.file_base_url = file_base_url

    def _parse_response(self, text_response):
        """
        Parses the camera's line-based text response into a Python dictionary.

        Args:
            text_response (str): The raw text response from the camera.

        Returns:
            dict: A dictionary of the parsed key-value pairs.
        """
        response_data = {}
        if not text_response:
            return response_data
        
        stripped_response = text_response.strip()

        # Handle JSON response for file lists
        if stripped_response.startswith('[') and stripped_response.endswith(']'):
            try:
                return {"data": json.loads(stripped_response)}
            except json.JSONDecodeError:
                print("Error: Could not decode JSON from response.")
                return {"error": "Invalid JSON"}
            
        # Handle var-style responses
        lines = stripped_response.split('\r\n')
        for line in lines:
            clean_line = line.strip().replace('var ', '').rstrip(';')
            if '=' in clean_line:
                # Handle key-value pairs
                parts = clean_line.split('=', 1)
                key = parts[0]
                value = parts[1].strip('"')
                # Sometimes multiple vars are on one line, separated by ';'
                if ';' in value:
                    sub_parts = value.split(';')
                    response_data[key] = sub_parts[0]
                    for sub_part in sub_parts[1:]:
                        if '=' in sub_part:
                            sub_key, sub_val = sub_part.split('=', 1)
                            response_data[sub_key.replace('var ', '')] = sub_val.strip('"')
                else:
                    response_data[key] = value
            else:
                # Handle simple text responses like "Success"
                response_data['status'] = clean_line
        return response_data

    def send_command(self, endpoint, params=None):
        """
        Sends a command to the camera.

        Args:
            endpoint (str): The CGI script to call (e.g., "getsdstate.cgi").
            params (dict, optional): A dictionary of parameters to send.

        Returns:
            dict: The parsed response from the camera, or None on error.
        """
        # The camera API seems to use a mix of query string styles.
        # Sometimes it's `?&-param=value`, sometimes `?-param=value`.
        # We will construct the URL manually from the endpoint string.
        url = self.cgi_base_url + endpoint
        print(f"Executing: {url}")
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            print("Response received.")
            return self._parse_response(response.text)
        except requests.exceptions.RequestException as e:
            print(f"Error communicating with camera: {e}")
            return None

    def download_file(self, remote_path, local_path):
        """Downloads a file from the camera."""
        url = self.file_base_url + remote_path
        
        print(f"Attempting to download '{remote_path}' from '{url}'")

        try:
            print("Using GET for file...")
            response = requests.get(url, timeout=60, stream=True)
            
            response.raise_for_status()

            with open(local_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print(f"Successfully downloaded file to '{local_path}'")
            return True

        except requests.exceptions.RequestException as e:
            print(f"Error downloading file: {e}")
            return False