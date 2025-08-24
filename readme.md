# **LAMAX Action Camera WiFi Controller**

## **Overview**

This project provides a Python-based command-line interface (CLI) to control a LAMAX action camera over its WiFi hotspot. The control protocol was determined by reverse-engineering the HTTP GET requests sent by the official mobile application (LAMAX Hi Cam (`com.gku.alex`) version `3.5-build20190329`).

The primary goal of this tool is to enable programmatic control and synchronization of multiple cameras, making it ideal for multi-camera setups like 360° video rigs, custom time-lapse sequences, or any application requiring automated camera operation.

## **Supported Devices**

LAMAX X9.1 Action Camera (Hi3559V200), SW Version V1.0.0.6

## **Features**

* **Full Camera Control:** Start/stop recording and take pictures.  
* **Device Status Monitoring:** Get real-time information on SD card status, battery level, WiFi settings, and device attributes.  
* **File Management:** List all files on the SD card, download high-resolution videos and low-resolution proxies. You can also delete files directly from the camera.  
* **Settings Configuration:** Change the camera's work mode (e.g., NormalVideo, PhotoLapse) and modify specific parameters like resolution, loop recording, and lens angle.  
* **Extensible Architecture:** The project is built with a modular class-based system, making it easy to add new commands as more of the camera's API is discovered.  
* **Automated Endurance Testing:** Includes a standalone script to perform long-duration recording tests, monitoring the camera's stability and logging its status until it either completes the test or fails.

## **Project Structure**

The project is organized into the following key files:

* main.py: The main entry point for the CLI. It dynamically discovers and loads all available commands.  
* camera\_api.py: Contains the core CameraAPI class that handles all low-level communication with the camera, including sending commands and parsing responses.  
* actions.py: Defines all the user-facing commands. Each command is its own class that inherits from a base Action class.  
* stresstest.py: A standalone script for running automated recording tests.

## **Setup and Installation**

### **Prerequisites**

* Python 3.6+  
* The requests library

### **Installation**

1. **Clone the repository**  

2. **(optionally) Create and enter venv:**

   ```
   python3 -m venv venv
   source venv/bin/activate
   ```

2. **Install the required Python libraries:**  
   `pip install -r requirements.txt`


## **Usage**

All interaction with the camera is done through the main script. You must be connected to the camera's WiFi network for the script to work. If needed, change the camera's IP address in `main.py`.

### **Basic Command Structure**

python main.py \<command\> \[arguments...\]

### **Examples**

* **Get a general status update:**  
  python main.py sdcardstatus

* **List all files on the SD card:**  
  python main.py getfilelist

* **Start recording:**  
  python main.py startrecording

* **Change the video resolution:**  
  python main.py setparameter NormalVideo Resolution 4K30

* **Download a high-resolution video:**  
  python main.py downloadfile "sd/DCIM/100GKUDV/NORM0001.MP4" my\_video.mp4

## **Available Commands**

Below is a list of all implemented commands.

### **Basic Controls & Status**

| Command | Description |
| :---- | :---- |
| startrecording | Starts video recording. |
| stoprecording | Stops video recording. |
| takepicture | Takes a single photo (or starts recording when in video workmode). |
| sdcardstatus | Gets the SD card's state, total space, and used space. |
| getbatterystatus | Gets the current battery capacity and charging status. |
| getdeviceinfo | Retrieves model name, serial number, and firmware version. |
| setsystemtime | Syncs the camera's clock with your computer's time. |

### **File Management**

| Command | Description |
| :---- | :---- |
| getfilecount | Returns the total number of files on the SD card. |
| getfilelist | Displays a formatted list of all files. |
| getfileinfo \<path\> | Gets detailed information for a specific file. |
| deletefile \<path\> | Deletes a specific file from the SD card. |
| downloadfile \<remote\> \[local\] | Downloads a file. |
| sdcardformat | Formats the SD card (requires confirmation). |

### **Settings & Configuration**

| Command | Description |
| :---- | :---- |
| getallworkmodes | Lists all available camera modes (e.g., NormalVideo). |
| getcurrentworkmode | Shows the currently active work mode. |
| setworkmode \<mode\> | Switches the camera to a different work mode. |
| getprimarymenu \<mode\> | Lists the main settings available for a given work mode. |
| getsecondarymenu \<mode\> \<name\> | Lists the available options for a specific setting. |
| setparameter \<mode\> \<name\> \<value\> | Sets a new value for a specific setting. |

## **Stress Test Script**

The stresstest.py script is designed to test the camera's stability over a long recording session.

### **How it Works**

1. **Prompts** the user to format the SD card.  
2. **Gathers** initial status information (battery, SD card).  
3. **Configures** the camera to record in NormalVideo at 4K30 with loop recording on.  
4. **Starts** recording.  
5. **Monitors** the camera's status every 10 seconds, logging elapsed time, battery, and SD card usage.  
6. **Stops** if the camera becomes unresponsive or after 60 minutes.  
7. **Provides** a final summary of the test run.

### **How to Run**

python stresstest.py  
