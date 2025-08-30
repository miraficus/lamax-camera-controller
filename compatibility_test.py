import argparse
from camera_api import CameraAPI
from camera_models import MODELS

def main():
    """
    Main function to run the compatibility test.
    """
    parser = argparse.ArgumentParser(description="Test camera compatibility with existing command sets.")
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera to test.")
    args = parser.parse_args()

    print(f"--- Starting Compatibility Test for Camera at {args.ip} ---")

    for model_key, model_definition in MODELS.items():
        print(f"\n--- Testing Model: {model_definition['model_name']} ---")
        camera_api = CameraAPI(camera_ip=args.ip, model_definition=model_definition)

        commands_to_test = model_definition.get("commands", {})
        if not commands_to_test:
            print("  No commands defined for this model.")
            continue

        successful_commands = []
        failed_commands = []

        # --- Define dummy values for commands that need them ---
        dummy_params = {
            "workmode": "NormalVideo",
            "name": "Resolution",
            "value": "1080P30",
            "time": "20230101000000",
            "ssid": "test_ssid",
            "password": "test_password",
            "start": "1",
            "end": "1",
            "path": "sd/DCIM/100GKUDV/DUMMY.MP4"
        }

        # --- Define commands to exclude from testing ---
        EXCLUDED_COMMANDS = [
            "set_wifi_settings", # Breaks the connection
            "format_sd_card",    # Destructive
        ]

        for action_name, command_str in commands_to_test.items():
            if action_name in EXCLUDED_COMMANDS:
                print(f"  [SKIPPED] {action_name} (Breaking command)")
                continue

            try:
                # Get the endpoint, filling in placeholders with dummy data
                endpoint = camera_api.get_command(action_name, **dummy_params)

                # Send the command
                response = camera_api.send_command(endpoint, timeout=5) # 5s timeout for testing

                if response is not None:
                    print(f"  [SUCCESS] {action_name}")
                    successful_commands.append(action_name)
                else:
                    print(f"  [FAIL]    {action_name} (No response)")
                    failed_commands.append(action_name)

            except KeyError as e:
                print(f"  [ERROR]   {action_name}: {e}")
                failed_commands.append(action_name)
            except Exception as e:
                print(f"  [ERROR]   {action_name}: An unexpected error occurred - {e}")
                failed_commands.append(action_name)

        # --- Generate Summary Report ---
        print("\n--- Compatibility Summary ---")
        print(f"Total Commands Tested: {len(commands_to_test)}")
        print(f"Successful: {len(successful_commands)}")
        print(f"Failed: {len(failed_commands)}")

        if failed_commands:
            print("\nUnsupported Commands:")
            for cmd in failed_commands:
                print(f"  - {cmd}")

        print(f"--- Finished Testing Model: {model_definition['model_name']} ---")

    print("\n--- Test Complete ---")

if __name__ == "__main__":
    main()
