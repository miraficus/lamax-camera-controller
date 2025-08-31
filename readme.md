# **Universal Action Camera WiFi Controller**

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
*A comprehensive list of commands for the X9.1 will be generated here.*

### **LAMAX X10.1 Commands**
*A comprehensive list of commands for the X10.1 will be generated here.*

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

## **Stress Test Script**

The `stresstest.py` script is designed to test a camera's stability over a long recording session. It has not yet been updated for the new multi-model architecture.

### **How to Run**

`python stresstest.py`
