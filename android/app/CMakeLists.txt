cmake_minimum_required(VERSION 3.22.1)
project(relay_jsi_bridge)

# Create the JSI bridge library
add_library(relay_jsi_bridge SHARED
    src/main/cpp/jsi_bridge.cpp
)

# Link libraries

target_link_libraries(relay_jsi_bridge PRIVATE log)
# Set C++ standard
set_target_properties(relay_jsi_bridge PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
