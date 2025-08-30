import requests
import json

class CameraAPI:
    """Handles communication with the camera's API."""

    def __init__(self, camera_ip, model_definition):
        """
        Initializes the CameraAPI with a specific camera model.

        Args:
            camera_ip (str): The IP address of the camera.
            model_definition (dict): The model-specific definition containing
                                     commands and base paths.
        """
        self.camera_ip = camera_ip
        self.model_definition = model_definition
        self.commands = self.model_definition.get("commands", {})

        cgi_path = self.model_definition.get("cgi_base_path", "/cgi-bin/hi3510/")
        self.cgi_base_url = f"http://{self.camera_ip}{cgi_path}"
        self.file_base_url = f"http://{self.camera_ip}/"


    def get_command(self, action_name, **kwargs):
        """
        Retrieves and formats a command string for a given action.

        Args:
            action_name (str): The generic name of the action (e.g., 'start_recording').
            **kwargs: Keyword arguments to format the command string.

        Returns:
            str: The fully formatted command string.

        Raises:
            KeyError: If the action_name is not found in the model's command set.
        """
        try:
            base_command = self.commands[action_name]
            return base_command.format(**kwargs)
        except KeyError:
            # This provides a clear error message for developers if a command is missing.
            raise KeyError(f"Command '{action_name}' not defined for model '{self.model_definition.get('model_name', 'Unknown')}'.")
        except Exception as e:
            # Catch other potential formatting errors
            print(f"Error formatting command '{action_name}': {e}")
            return None

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

    def send_command(self, endpoint, params=None, timeout=10):
        """
        Sends a command to the camera.

        Args:
            endpoint (str): The CGI script to call (e.g., "getsdstate.cgi").
            params (dict, optional): A dictionary of parameters to send.
            timeout (int, optional): The request timeout in seconds. Defaults to 10.

        Returns:
            dict: The parsed response from the camera, or None on error.
        """
        url = self.cgi_base_url + endpoint
        print(f"Executing: {url}")
        try:
            response = requests.get(url, timeout=timeout)
            response.raise_for_status()
            print("Response received.")
            return self._parse_response(response.text)
        except requests.exceptions.RequestException as e:
            print(f"Error communicating with camera: {e}")
            return None

    def download_file(self, remote_path, local_path, timeout=60):
        """
        Downloads a file from the camera.

        Args:
            remote_path (str): The remote path of the file on the camera.
            local_path (str): The local path to save the file.
            timeout (int, optional): The request timeout in seconds. Defaults to 60.
        """
        url = self.file_base_url + remote_path
        
        print(f"Attempting to download '{remote_path}' from '{url}'")

        try:
            print("Using GET for file...")
            response = requests.get(url, timeout=timeout, stream=True)
            
            response.raise_for_status()

            with open(local_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print(f"Successfully downloaded file to '{local_path}'")
            return True

        except requests.exceptions.RequestException as e:
            print(f"Error downloading file: {e}")
            return False