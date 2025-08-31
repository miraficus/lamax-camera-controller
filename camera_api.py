import requests
import json
from errors import CameraConnectionError, InvalidResponseError, CommandNotSupportedError

class CameraAPI:
    """
    Handles communication with the camera's API.
    This class acts as a thin wrapper that delegates calls to a
    specific camera model's implementation.
    """

    def __init__(self, camera_ip, model):
        """
        Initializes the CameraAPI with a specific camera model instance.

        Args:
            camera_ip (str): The IP address of the camera.
            model (CameraModel): An instance of a CameraModel subclass.
        """
        self.camera_ip = camera_ip
        self.model = model
        self.cgi_base_url = f"http://{self.camera_ip}{self.model.cgi_base_path}"
        self.file_base_url = f"http://{self.camera_ip}/"

    def call(self, method_name, *args, **kwargs):
        """
        Calls a method on the camera model to get a command endpoint,
        sends it to the camera, and returns the parsed response.

        Args:
            method_name (str): The name of the method to call on the model.
            *args: Positional arguments for the model's method.
            **kwargs: Keyword arguments for the model's method.

        Returns:
            dict: The parsed response from the camera.

        Raises:
            CommandNotSupportedError: If the command is not supported by the model.
            CameraConnectionError: If there is a problem connecting to the camera.
            InvalidResponseError: If the camera's response is invalid.
        """
        try:
            method_to_call = getattr(self.model, method_name)
            endpoint = method_to_call(*args, **kwargs)
            return self._send_command(endpoint)
        except (NotImplementedError, AttributeError):
            raise CommandNotSupportedError(method_name, self.model.get_model_name())

    def _parse_response(self, text_response):
        """
        Parses the camera's line-based text response into a Python dictionary.
        """
        response_data = {}
        if not text_response:
            return response_data
        
        stripped_response = text_response.strip()

        if stripped_response.startswith('[') and stripped_response.endswith(']'):
            try:
                return {"data": json.loads(stripped_response)}
            except json.JSONDecodeError:
                raise InvalidResponseError("Could not decode JSON from response.")
            
        lines = stripped_response.split('\r\n')
        for line in lines:
            clean_line = line.strip().replace('var ', '').rstrip(';')
            if '=' in clean_line:
                parts = clean_line.split('=', 1)
                key = parts[0]
                value = parts[1].strip('"')
                if ';' in value:
                    sub_parts = value.split(';')
                    response_data[key] = sub_parts[0]
                    for sub_part in sub_parts[1:]:
                        if '=' in sub_part:
                            sub_key, sub_val = sub_part.split('=', 1)
                            response_data[sub_key.replace('var ', '')] = sub_val.strip('"')
                else:
                    response_data[key] = value
            elif clean_line:
                response_data['status'] = clean_line
        return response_data

    def _send_command(self, endpoint, timeout=10):
        """
        Sends a GET request to the camera's CGI endpoint.
        """
        if not endpoint:
            raise ValueError("No endpoint provided.")

        url = self.cgi_base_url + endpoint
        print(f"Executing: {url}")
        try:
            response = requests.get(url, timeout=timeout)
            response.raise_for_status()
            print("Response received.")
            return self._parse_response(response.text)
        except requests.exceptions.RequestException as e:
            raise CameraConnectionError(f"Error communicating with camera: {e}")

    def download_file(self, remote_path, local_path, timeout=60):
        """
        Downloads a file from the camera.
        """
        url = self.file_base_url + remote_path
        print(f"Attempting to download '{remote_path}' from '{url}'")
        try:
            response = requests.get(url, timeout=timeout, stream=True)
            response.raise_for_status()
            with open(local_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print(f"Successfully downloaded file to '{local_path}'")
            return True
        except requests.exceptions.RequestException as e:
            raise CameraConnectionError(f"Error downloading file: {e}")