import time
from datetime import datetime
import sys
from types import SimpleNamespace

# You will need to have your CameraAPI and Action classes in files
# named camera_api.py and actions.py in the same directory.
from camera_api import CameraAPI
from actions import (
    Action,
    SetSystemTime,
    GetDeviceInfo,
    GetWifiInfo,
    GetBatteryStatus,
    SDCardStatus,
    SDCardFormat,
    GetAllWorkmodes,
    GetCurrentWorkmode,
    SetWorkmode,
    SetParameter,
    StartRecording,
    StopRecording,
)

# --- Configuration ---
CAMERA_IP = "192.168.0.1"
CAMERA_MODEL = "hi3510"

CGI_BASE_URL = f"http://{CAMERA_IP}/cgi-bin/{CAMERA_MODEL}/"
FILE_BASE_URL = f"http://{CAMERA_IP}/"

TEST_DURATION_MINUTES = 60
MONITORING_INTERVAL_SECONDS = 10

def log(message):
    """Prints a message with a timestamp."""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] {message}")


def main():
    """Main function to run the endurance test."""
    api = CameraAPI(CGI_BASE_URL, FILE_BASE_URL)
    log("--- Starting Camera Endurance Test ---")

    # --- Instantiate all required actions ---
    # We pass a dummy parser object, as it's not used by the execute methods.
    dummy_parser = None
    set_system_time_action = SetSystemTime(dummy_parser, api)
    get_device_info_action = GetDeviceInfo(dummy_parser, api)
    get_wifi_details_action = GetWifiInfo(dummy_parser, api)
    get_battery_status_action = GetBatteryStatus(dummy_parser, api)
    sd_card_status_action = SDCardStatus(dummy_parser, api)
    sd_card_format_action = SDCardFormat(dummy_parser, api)
    get_all_workmodes_action = GetAllWorkmodes(dummy_parser, api)
    get_current_workmode_action = GetCurrentWorkmode(dummy_parser, api)
    set_workmode_action = SetWorkmode(dummy_parser, api)
    set_parameter_action = SetParameter(dummy_parser, api)
    start_recording_action = StartRecording(dummy_parser, api)
    stop_recording_action = StopRecording(dummy_parser, api)
    
    # --- 1. Setup and Initial Status Check ---
    log("--- Phase 1: Setup and Status Check ---")

    # Ask user to format the SD card
    confirm = input("Do you want to format the SD card before starting the test? (yes/no): ")
    if confirm.lower() == 'yes':
        log("Formatting SD card as requested...")
        sd_card_format_action.execute(None)
        log("Waiting 5 seconds for camera to process format...")
        time.sleep(5)
    else:
        log("Skipping SD card format.")

    set_system_time_action.execute(SimpleNamespace(datetime=None))
    get_device_info_action.execute(None)
    get_wifi_details_action.execute(None)
    
    # Get initial state for summary
    initial_battery_response = api.send_command("getbatterycapacity.cgi?")
    initial_sd_response = api.send_command("getsdstate.cgi?")
    
    get_all_workmodes_action.execute(None)
    get_current_workmode_action.execute(None)

    # --- 2. Configure Camera for Recording ---
    log("--- Phase 2: Configuring Camera ---")
    
    # Create dummy 'args' objects to pass parameters to the execute methods
    set_workmode_action.execute(SimpleNamespace(workmode="NormalVideo"))
    set_parameter_action.execute(SimpleNamespace(workmode="NormalVideo", name="Resolution", value="4K30"))
    set_parameter_action.execute(SimpleNamespace(workmode="NormalVideo", name="Loop recording", value="On"))

    # --- 3. Start Recording ---
    log("--- Phase 3: Starting Recording ---")
    start_recording_action.execute(None)

    start_time = time.time()
    test_successful = False
    
    try:
        # --- 4. Monitor Recording ---
        log(f"--- Phase 4: Monitoring for {TEST_DURATION_MINUTES} minutes ---")
        end_time = start_time + (TEST_DURATION_MINUTES * 60)

        while time.time() < end_time:
            elapsed_minutes = (time.time() - start_time) / 60
            log(f"--- Checking status (Elapsed: {elapsed_minutes:.1f}/{TEST_DURATION_MINUTES} min) ---")
            
            sd_response = api.send_command("getsdstate.cgi?")
            battery_response = api.send_command("getbatterycapacity.cgi?")

            if sd_response is None or battery_response is None:
                log("CRITICAL: Camera has stopped responding. Test failed.")
                sys.exit(1)
            
            log("SD Card Status:")
            for key, value in sd_response.items():
                print(f"    - {key.capitalize()}: {value}")
                
            log("Battery Status:")
            for key, value in battery_response.items():
                print(f"    - {key.capitalize()}: {value}")
            
            time.sleep(MONITORING_INTERVAL_SECONDS)
        
        test_successful = True

    finally:
        # --- 5. Stop Recording and Summarize ---
        log("--- Phase 5: Test finished. Stopping recording. ---")
        stop_recording_action.execute(None)
        
        final_battery_response = api.send_command("getbatterycapacity.cgi?")
        final_sd_response = api.send_command("getsdstate.cgi?")
        
        log("--- Endurance Test Summary ---")
        if test_successful:
            log("Result: SUCCESS - Test completed the full duration.")
        else:
            log("Result: FAILED - Test was interrupted or camera became unresponsive.")
        
        total_duration = (time.time() - start_time) / 60
        log(f"Total Test Duration: {total_duration:.2f} minutes.")

        if initial_battery_response and final_battery_response:
            start_cap = initial_battery_response.get('capacity', 'N/A')
            end_cap = final_battery_response.get('capacity', 'N/A')
            log(f"Battery: Started at {start_cap}, Ended at {end_cap}")

        if initial_sd_response and final_sd_response:
            start_used = initial_sd_response.get('used', 'N/A')
            end_used = final_sd_response.get('used', 'N/A')
            log(f"SD Card Usage: Started with {start_used} used, Ended with {end_used} used.")
        
        log("----------------------------")
    
    log("--- Endurance Test Completed Successfully! ---")


if __name__ == "__main__":
    main()