import argparse
import sys
import time
from camera_api import CameraAPI
from main import discover_models

def clear_line():
    """Clears the current line in the console."""
    sys.stdout.write("\r\x1b[K")
    sys.stdout.flush()

def log(message):
    """Prints a message with a timestamp."""
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] {message}")

def main():
    """Main function to run the camera stress test."""
    available_models = discover_models()
    if not available_models:
        log("Error: No camera models found. Exiting.")
        sys.exit(1)

    parser = argparse.ArgumentParser(description="Run a recording stress test on a supported action camera.")
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera.")
    parser.add_argument(
        "--model",
        required=True,
        choices=available_models.keys(),
        help="The model of the camera to test."
    )
    parser.add_argument("--duration", type=int, default=60, help="Test duration in minutes.")
    args = parser.parse_args()

    log(f"--- Starting Camera Recording Stress Test for Model: {args.model} ---")
    
    selected_model = available_models[args.model]
    camera_api = CameraAPI(camera_ip=args.ip, model=selected_model)

    # 1. Get test scenario from model
    scenario = selected_model.get_stresstest_scenario()
    if not scenario:
        log("Error: No stress test scenario defined for this model. Exiting.")
        sys.exit(1)
    
    log(f"Test scenario: Workmode='{scenario['workmode']}', Settings={scenario['settings']}")

    # 2. Check SD Card and prompt for format
    log("Checking SD card status...")
    camera_api.call('get_sd_card_status')
    
    format_sd = input("Do you want to format the SD card before starting? (yes/no): ").lower()
    if format_sd == 'yes':
        log("Formatting SD card...")
        camera_api.call('format_sd_card')
        time.sleep(5)

    # 3. Configure camera for test
    log("Configuring camera for test...")
    camera_api.call('set_workmode', scenario['workmode'])
    time.sleep(1)

    for setting_name, setting_value in scenario['settings']:
        log(f"Applying setting: {setting_name} = {setting_value}")
        camera_api.call('set_parameter', scenario['workmode'], setting_name, setting_value)
        time.sleep(1)

    # 4. Start recording
    log("Starting recording...")
    camera_api.call('start_recording')
    start_time = time.time()
    
    log(f"--- Monitoring for {args.duration} minutes ---")
    print("Press Ctrl+C to stop the test early.")

    try:
        while True:
            elapsed_time_s = time.time() - start_time
            if elapsed_time_s > (args.duration * 60):
                log(f"Test reached {args.duration} minutes. Stopping.")
                break

            # Get and display status
            battery_response = camera_api.call('get_battery_status')
            sd_response = camera_api.call('get_sd_card_status')
            
            battery_capacity = battery_response.get('capacity', 'N/A') if battery_response else 'N/A'
            sd_used = sd_response.get('used', 'N/A') if sd_response else 'N/A'
            
            clear_line()
            sys.stdout.write(
                f"Elapsed: {int(elapsed_time_s // 60)}m {int(elapsed_time_s % 60)}s | "
                f"Battery: {battery_capacity}% | "
                f"SD Used: {sd_used}"
            )
            sys.stdout.flush()

            time.sleep(10)

    except KeyboardInterrupt:
        log("\nTest stopped by user.")
    except Exception as e:
        log(f"\nAn error occurred: {e}")
    finally:
        # 5. Stop recording
        log("\nStopping recording...")
        camera_api.call('stop_recording')
        
        log("\n--- Test Finished ---")
        final_file_count = camera_api.call('get_file_count')
        if final_file_count:
            log(f"Final file count: {final_file_count.get('count', 'N/A')}")

if __name__ == "__main__":
    main()