# **Universal Action Camera WiFi Controller**

## X10 Connect Android preview

The `android` branch includes [X10 Connect](android/README.md), a standalone Android 10+ client with a media gallery, batch downloads, camera controls, experimental modes/settings and RTSP preview. The **Android APK** workflow runs unit tests, lint and produces an installable debug APK. The owner has tested connection, file listing, photo capture and the smaller RTSP preview on Android 16 with V1.0.0.4. The larger RTSP preview failed; downloads through Android and other controls still need testing. See the [device test results](docs/device-tests-v1.0.0.4.md).

## X10.1 APK research and read-only testing

The fork includes [analysis of LAMAX Hi Cam 3.5-build20190329](docs/lamax-hi-cam-3.5/README.md) (Czech), an endpoint inventory with source locations, and a standalone Python 3.9+ client in `tools/lamax_readonly.py`.

This client only queries device status, lists media, and downloads an explicitly selected media file. Its six local HTTP simulator tests pass. Device status, a 78-entry file list, and individual JPG downloads have now been verified on the owner's X10.1 V1.0.0.4; remote photo capture was separately verified with `curl`. See [device test results](docs/device-tests-v1.0.0.4.md) for evidence and limitations. The discovered APK command inventory is not a list of confirmed X10.1 capabilities.

Connect to the camera Wi-Fi, then run from the repository root:

```bash
mkdir -p camera-data
python3 tools/lamax_readonly.py probe > camera-data/probe.json
python3 tools/lamax_readonly.py list > camera-data/files.json
python3 -m unittest discover -s tools -p test_lamax_readonly.py -v
```

The existing `compatibility_test.py` also changes settings; use the standalone read-only client for the first device checks. The basic file-transfer path is device-validated; the Android preview and APK build workflow are available on the `android` branch.

## Camera firmware V1.0.0.4 findings

[Static analysis of files downloaded from the X10.1](docs/firmware-v1.0.0.4/README.md) confirms that `main_app` retains symbols and debug information. Compiled CGI tables map file listing, metadata, photo and record endpoints to named handlers. The X2 archive bundled with the APK uses a different loader and C library; it is not a verified replacement firmware. The report includes comparison hashes and an offline ELF inspection tool. Camera binaries and device-specific data are not committed.

## **Overview**

This project provides a Python-based command-line interface (CLI) to control action cameras over their WiFi hotspots. The control protocols were determined by reverse-engineering the HTTP GET requests sent by the official mobile applications.

The primary goal of this tool is to enable programmatic control and synchronization of multiple cameras, making it ideal for multi-camera setups like 360° video rigs, custom time-lapse sequences, or any application requiring automated camera operation.

What makes this tool unique is its **pluggable architecture**. Adding support for a new camera is as simple as creating a new Python file in the `models` directory.

## **Supported Devices**

*   **LAMAX X9.1** (Hi3559V200), SW Version V1.0.0.6
*   **LAMAX X10.1** (gkuvision-dv-3559-mn34120-linux), SW Version V1.0.0.1

## **Features**

*   **Multi-Model Support:** Control different camera models with the same interface.
*   **Pluggable Architecture:** Easily add support for new cameras by creating a self-contained model file.
*   **Full Camera Control:** Start/stop recording and take pictures.
*   **Device Status Monitoring:** Get real-time information on SD card status, battery level, WiFi settings, and device attributes.
*   **File Management:** List, download, and delete files directly from the camera's SD card.
*   **Settings Configuration:** Change the camera's work mode and modify specific parameters like resolution and flip status.

## **Project Structure**

The project is organized around a simple and extensible plugin architecture:

*   `main.py`: The main entry point for the CLI. It dynamically discovers and loads camera models and their commands.
*   `camera_api.py`: Contains the core `CameraAPI` class that handles low-level communication with the camera. It's model-agnostic.
*   `models/`: This directory contains the plugins. Each `.py` file represents a camera model.
    *   `base.py`: Defines the abstract base classes that all models and actions must implement.
    *   `lamax_x9_1.py`: The plugin for the LAMAX X9.1.
    *   `lamax_x10_1.py`: The plugin for the LAMAX X10.1.
*   `stresstest.py`: A standalone script for running automated recording tests.

## **Setup and Installation**

### **Prerequisites**

*   Python 3.6+
*   The `requests` library

### **Installation**

1.  **Clone the repository.**
2.  **(Optionally) Create and enter a venv:**
    ```bash
    python3 -m venv venv
    source venv/bin/activate
    ```
3.  **Install the required Python libraries:**
    ```bash
    pip install -r requirements.txt
    ```

## **Usage**

All interaction with the camera is done through `main.py`. You must be connected to the camera's WiFi network for the script to work.

### **Basic Command Structure**

The command structure requires you to specify which camera model you are controlling.

```bash
python main.py --model <model_name> <command> [arguments...]
```

### **Examples**

*   **See available commands for the LAMAX X10.1:**
    ```bash
    python main.py --model X10_1 --help
    ```

*   **Get the battery status from a LAMAX X9.1:**
    ```bash
    python main.py --model X9_1 getbatterystatus
    ```

*   **Get the available resolutions for video on a LAMAX X10.1:**
    ```bash
    python main.py --model X10_1 getparameteroptions video Resolution
    ```

*   **Set the flip status on a LAMAX X10.1:**
    ```bash
    python main.py --model X10_1 setflip on
    ```

*   **Download a file from a LAMAX X9.1:**
    ```bash
    python main.py --model X9_1 downloadfile "sd/DCIM/100GKUDV/NORM0001.MP4" my_video.mp4
    ```

## **Available Commands**

The available commands depend on the selected model. You can view all commands for a model by running:
`python main.py --model <model_name> --help`

### **LAMAX X9.1 Commands**
<!-- COMMANDS_START_X9_1 -->
| Command | Description |
| :--- | :--- |
| `deletefile` | Delete a file from SD card. |
| `downloadfile` | Download a file from the camera. |
| `getallworkmodes` | Get all available work modes. |
| `getbatterystatus` | Get battery status. |
| `getcurrentstatus` | Get current camera status. |
| `getcurrentworkmode` | Get the current work mode. |
| `getdeviceinfo` | Get device information. |
| `getfilecount` | Get file count on SD card. |
| `getfileinfo` | Get file information. |
| `getfilelist` | List files on the SD card. |
| `getparameter` | Get the current value of a specific parameter. |
| `getparameteroptions` | Get all available options for a specific setting. |
| `getwifiinfo` | Get WiFi information. |
| `sdcardformat` | Format SD card. |
| `sdcardstatus` | Get SD card status. |
| `setparameter` | Set the value for a specific setting. |
| `setsystemtime` | Set system time. |
| `setwifisettings` | Set WiFi settings. |
| `setworkmode` | Set the current work mode. |
| `startrecording` | Start video recording. |
| `stoprecording` | Stop video recording. |
| `takepicture` | Take a picture. |
<!-- COMMANDS_END_X9_1 -->

### **LAMAX X10.1 Commands**
<!-- COMMANDS_START_X10_1 -->
| Command | Description |
| :--- | :--- |
| `downloadfile` | Download a file from the camera. |
| `getaudioencode` | Get audio encode status. |
| `getbatterystatus` | Get battery status. |
| `getbootaction` | Get boot action. |
| `getcurrentstatus` | Get current camera status. |
| `getdevicecapabilities` | Get device capabilities. |
| `getdeviceinfo` | Get device information. |
| `getfilecount` | Get file count on SD card. |
| `getfilelist` | List files on the SD card. |
| `getflip` | Get display flip status. |
| `getledstate` | Get LED state. |
| `getparameter` | Get the current value of a specific parameter. |
| `getparameteroptions` | Get all available options for a specific setting. |
| `getscreenautosleep` | Get screen auto sleep time. |
| `gettimeosd` | Get time OSD status (NOT IMPLEMENTED). |
| `getviewfield` | Get field of view. |
| `reset` | Reset the camera. |
| `sdcardformat` | Format SD card. |
| `sdcardstatus` | Get SD card status. |
| `setaudioencode` | Set audio encode status. |
| `setbootaction` | Set boot action. |
| `setflip` | Set display flip. |
| `setledstate` | Set LED state. |
| `setparameter` | Set the value for a specific setting. |
| `setscreenautosleep` | Set screen auto sleep time (NOT IMPLEMENTED). |
| `setsystemtime` | Set system time. |
| `settimeosd` | Set time OSD. |
| `setviewfield` | Set field of view (NOT IMPLEMENTED). |
<!-- COMMANDS_END_X10_1 -->

## **Contributing New Camera Models**

This project is designed to be easily extensible. To add support for a new camera, you only need to create a single new file in the `models/` directory.

### **Steps:**

1.  **Create a new file:** e.g., `models/my_camera.py`.
2.  **Define the Model Class:**
    *   Create a class that inherits from `models.base.CameraModel`.
    *   Implement all the abstract methods from the base class (`get_model_name`, `get_cli_name`, `get_actions`, and all the command methods).
    *   The command methods should return the specific CGI endpoint string for your camera. If a command is not supported, raise `NotImplementedError`.
3.  **Define the Action Classes:**
    *   For each command your camera supports, create a class that inherits from `models.base.Action`.
    *   Implement the abstract methods: `get_name`, `get_help`, `add_arguments`, and `execute`.
    *   The `execute` method should call the appropriate method on the `CameraAPI` using `self.camera_api.call('your_command_method', ...)`.
4.  **Link Actions to Model:**
    *   In your model class, the `get_actions` method should return a list of all the `Action` classes you defined.

That's it! The `main.py` script will automatically discover your new model and its commands.

## Contributing New Actions

If you have discovered a new command for an existing camera model, you can easily add it by following these steps:

1.  **Add the command method to the model class:**
    *   Open the model file (e.g., `models/lamax_x10_1.py`).
    *   Add a new method to the model class (e.g., `LamaxX10_1`) that returns the CGI endpoint for the new command. For example:
        ```python
        def get_screen_brightness(self):
            return "getscreenbrightness.cgi"
        ```

2.  **Create a new `Action` class:**
    *   In the same model file, create a new class that inherits from `models.base.Action`.
    *   Implement the `get_name`, `get_help`, `add_arguments`, and `execute` methods.
    *   The `get_name` method should return the command name for the CLI.
    *   The `get_help` method should return a short description of the command.
    *   The `add_arguments` method should define any command-line arguments that the command requires.
    *   The `execute` method should call the new command method on the `CameraAPI` using `self.camera_api.call()`.

    Here's an example for the `get_screen_brightness` command:
    ```python
    class GetScreenBrightness(Action):
        @staticmethod
        def get_name(): return "getscreenbrightness"
        @staticmethod
        def get_help(): return "Get screen brightness level."
        def execute(self, args):
            try:
                response = self.camera_api.call('get_screen_brightness')
                print(f"\nScreen Brightness: {response.get('level', 'N/A')}\n")
            except CameraError as e:
                print(f"Error getting screen brightness: {e}")
    ```

3.  **Add the new action to the model's `get_actions` method:**
    *   In your model class, find the `get_actions` method and add your new `Action` class to the list.
        ```python
        def get_actions(self):
            return [..., GetScreenBrightness, ...]
        ```

That's it! The new command will now be available through the CLI for that camera model.

## **Stress Test Script**

The `stresstest.py` script is designed to test a camera's stability over a long recording session. It has been updated to support the multi-model architecture.

### **How it Works**

The script will fetch a model-specific test scenario (e.g., which workmode and settings to use) from the camera's plugin file. It then configures the camera, starts recording, and monitors its status for a specified duration.

### **How to Run**

You must specify which model you want to test.

`python stresstest.py --model <model_name>`

You can also specify the duration of the test:

`python stresstest.py --model <model_name> --duration 120`
