#!/bin/bash
# Start dev server and setup ADB reverse for live reload

# Get the directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Setting up ADB reverse for port 8081..."
adb reverse tcp:8081 tcp:8081

echo "Starting dev server..."
cd "$DIR"
node dev-server.cjs
