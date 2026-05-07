#!/bin/bash

##############################################################################
# UI Debug Navigation Script
# 
# Provides helper functions for navigating and debugging the JABAuth 
# Diagnostic app UI via ADB shell commands.
# 
# Usage:
#   source scripts/ui-debug-nav.sh
#   nav_to_scanner
#   screenshot "scanner-view"
##############################################################################

# Device dimensions (Samsung SM-S938U)
SCREEN_WIDTH=1080
SCREEN_HEIGHT=2340
SCREEN_CENTER_X=540  # SCREEN_WIDTH / 2
SCREEN_CENTER_Y=1170  # SCREEN_HEIGHT / 2

# Common tap coordinates
DASHBOARD_TAB_X=75
SCANNER_TAB_X=236
SETTINGS_TAB_X=396
BOTTOM_NAV_Y=907

HEADER_BACK_X=60
HEADER_BACK_Y=113

# Scanner screen test buttons
TEST_SUCCESS_X=118
TEST_FAILURE_X=354
TEST_BUTTONS_Y=175

# Dashboard "Start Scanner" button
START_SCANNER_X=236
START_SCANNER_Y=749

# Common scroll positions
SCROLL_START_Y=800
SCROLL_MID_Y=500
SCROLL_END_Y=200

##############################################################################
# Core Functions
##############################################################################

# Wait for specified seconds
wait_for() {
    local seconds=${1:-1}
    sleep "$seconds"
}

# Tap at coordinates
tap() {
    local x=$1
    local y=$2
    local name=${3:-""}
    
    if [ -n "$name" ]; then
        echo "→ Tapping: $name ($x, $y)"
    fi
    
    adb shell input tap "$x" "$y"
}

# Swipe from one point to another
swipe() {
    local x1=$1
    local y1=$2
    local x2=$3
    local y2=$4
    local duration=${5:-300}
    local name=${6:-""}
    
    if [ -n "$name" ]; then
        echo "→ Swiping: $name"
    fi
    
    adb shell input swipe "$x1" "$y1" "$x2" "$y2" "$duration"
}

# Take screenshot and save to /tmp
screenshot() {
    local name=${1:-"screenshot"}
    local output_path="/tmp/${name}.png"
    
    echo "→ Screenshot: $output_path"
    adb exec-out screencap -p > "$output_path"
    echo "  Saved: $output_path"
}

# Restart app
restart_app() {
    echo "→ Restarting app..."
    adb shell am force-stop com.jabauth.diagnostic
    wait_for 1
    adb shell am start -n com.jabauth.diagnostic/.MainActivity
    wait_for 3
    echo "  App restarted"
}

##############################################################################
# Navigation Functions
##############################################################################

# Navigate to Scanner via bottom nav
nav_to_scanner() {
    echo "→ Navigating to Scanner..."
    tap $SCANNER_TAB_X $BOTTOM_NAV_Y "Scanner Tab"
    wait_for 2
}

# Navigate to Dashboard via bottom nav
nav_to_dashboard() {
    echo "→ Navigating to Dashboard..."
    tap $DASHBOARD_TAB_X $BOTTOM_NAV_Y "Dashboard Tab"
    wait_for 2
}

# Navigate to Settings via bottom nav
nav_to_settings() {
    echo "→ Navigating to Settings..."
    tap $SETTINGS_TAB_X $BOTTOM_NAV_Y "Settings Tab"
    wait_for 2
}

# Navigate back via header
nav_back() {
    echo "→ Navigating back..."
    tap $HEADER_BACK_X $HEADER_BACK_Y "Back Button"
    wait_for 1
}

# Start scanner from Dashboard
start_scanner_from_dashboard() {
    echo "→ Starting scanner from Dashboard..."
    
    # Scroll to bottom of dashboard
    scroll_to_bottom
    wait_for 1
    
    # Tap "Start Scanner" button
    tap $START_SCANNER_X $START_SCANNER_Y "Start Scanner Button"
    wait_for 3
}

##############################################################################
# Scroll Functions
##############################################################################

# Scroll down
scroll_down() {
    local distance=${1:-"normal"}  # normal, short, long
    
    case $distance in
        short)
            swipe $SCREEN_CENTER_X $SCROLL_START_Y $SCREEN_CENTER_X 650 300 "Scroll Down (Short)"
            ;;
        long)
            swipe $SCREEN_CENTER_X $SCROLL_START_Y $SCREEN_CENTER_X $SCROLL_END_Y 500 "Scroll Down (Long)"
            ;;
        *)
            swipe $SCREEN_CENTER_X $SCROLL_START_Y $SCREEN_CENTER_X $SCROLL_MID_Y 300 "Scroll Down"
            ;;
    esac
    
    wait_for 0.5
}

# Scroll up
scroll_up() {
    local distance=${1:-"normal"}
    
    case $distance in
        short)
            swipe $SCREEN_CENTER_X 300 $SCREEN_CENTER_X 500 300 "Scroll Up (Short)"
            ;;
        long)
            swipe $SCREEN_CENTER_X $SCROLL_END_Y $SCREEN_CENTER_X $SCROLL_START_Y 500 "Scroll Up (Long)"
            ;;
        *)
            swipe $SCREEN_CENTER_X $SCROLL_MID_Y $SCREEN_CENTER_X $SCROLL_START_Y 300 "Scroll Up"
            ;;
    esac
    
    wait_for 0.5
}

# Scroll to top
scroll_to_top() {
    echo "→ Scrolling to top..."
    for i in {1..3}; do
        scroll_up "long"
    done
}

# Scroll to bottom
scroll_to_bottom() {
    echo "→ Scrolling to bottom..."
    for i in {1..3}; do
        scroll_down "long"
    done
}

##############################################################################
# Scanner Screen Functions
##############################################################################

# Test success result panel
test_success_result() {
    echo "→ Testing success result panel..."
    tap $TEST_SUCCESS_X $TEST_BUTTONS_Y "Test Success Button"
    wait_for 2
}

# Test failure result panel
test_failure_result() {
    echo "→ Testing failure result panel..."
    tap $TEST_FAILURE_X $TEST_BUTTONS_Y "Test Failure Button"
    wait_for 2
}

# Dismiss result panel (swipe down)
dismiss_result_panel() {
    echo "→ Dismissing result panel..."
    swipe $SCREEN_CENTER_X 400 $SCREEN_CENTER_X $SCROLL_START_Y 300 "Swipe Down to Dismiss"
    wait_for 1
}

##############################################################################
# Testing Workflows
##############################################################################

# Full scanner test workflow
test_scanner_workflow() {
    echo "========================================"
    echo "Testing Scanner Workflow"
    echo "========================================"
    
    restart_app
    
    nav_to_dashboard
    screenshot "01-dashboard"
    
    start_scanner_from_dashboard
    screenshot "02-scanner-screen"
    
    test_success_result
    screenshot "03-success-result"
    
    dismiss_result_panel
    wait_for 1
    
    test_failure_result
    screenshot "04-failure-result"
    
    dismiss_result_panel
    
    echo "========================================"
    echo "Scanner workflow test complete"
    echo "Screenshots saved to /tmp/"
    echo "========================================"
}

# Dashboard scroll test
test_dashboard_scroll() {
    echo "========================================"
    echo "Testing Dashboard Scroll"
    echo "========================================"
    
    restart_app
    
    screenshot "dashboard-01-top"
    
    scroll_down
    screenshot "dashboard-02-mid"
    
    scroll_down
    screenshot "dashboard-03-performance"
    
    scroll_down
    screenshot "dashboard-04-alerts"
    
    scroll_down
    screenshot "dashboard-05-live-feed"
    
    scroll_down
    screenshot "dashboard-06-framework"
    
    scroll_down
    screenshot "dashboard-07-start-button"
    
    scroll_to_top
    screenshot "dashboard-08-back-to-top"
    
    echo "========================================"
    echo "Dashboard scroll test complete"
    echo "========================================"
}

# Result panel interaction test
test_result_panel() {
    echo "========================================"
    echo "Testing Result Panel Interactions"
    echo "========================================"
    
    restart_app
    nav_to_dashboard
    start_scanner_from_dashboard
    
    echo ""
    echo "--- Testing Success Panel ---"
    test_success_result
    screenshot "result-success-expanded"
    
    # Scroll within panel
    echo "  Scrolling within panel..."
    swipe $SCREEN_CENTER_X 600 $SCREEN_CENTER_X 300 300
    wait_for 1
    screenshot "result-success-scrolled"
    
    # Dismiss
    dismiss_result_panel
    wait_for 1
    
    echo ""
    echo "--- Testing Failure Panel ---"
    test_failure_result
    screenshot "result-failure-expanded"
    
    # Scroll within panel
    swipe $SCREEN_CENTER_X 600 $SCREEN_CENTER_X 300 300
    wait_for 1
    screenshot "result-failure-scrolled"
    
    # Tap close button (approximate coordinates)
    echo "  Tapping close button..."
    tap 420 210 "Close Button"
    wait_for 1
    
    echo "========================================"
    echo "Result panel test complete"
    echo "========================================"
}

##############################################################################
# Utility Functions
##############################################################################

# Show current screen activity
show_current_screen() {
    echo "→ Current screen:"
    adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -2
}

# Show recent logcat for navigation
show_nav_logs() {
    echo "→ Recent navigation logs:"
    adb logcat -d | grep -i "diagnostic\|navigation\|scanner" | tail -20
}

# Clear logcat
clear_logs() {
    echo "→ Clearing logcat..."
    adb logcat -c
}

# List all screenshots in /tmp
list_screenshots() {
    echo "→ Screenshots in /tmp:"
    ls -lh /tmp/*.png 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
}

# Clean up old screenshots
clean_screenshots() {
    echo "→ Cleaning up /tmp screenshots..."
    rm -f /tmp/*.png
    echo "  Done"
}

##############################################################################
# Help
##############################################################################

show_help() {
    cat << EOF
╔═══════════════════════════════════════════════════════════════╗
║          JABAuth Diagnostic UI Navigation Helper              ║
╚═══════════════════════════════════════════════════════════════╝

NAVIGATION:
  nav_to_scanner              Navigate to Scanner screen
  nav_to_dashboard            Navigate to Dashboard screen
  nav_to_settings             Navigate to Settings screen
  nav_back                    Navigate back via header
  start_scanner_from_dashboard  Scroll and tap "Start Scanner"

SCROLLING:
  scroll_down [short|normal|long]  Scroll down
  scroll_up [short|normal|long]    Scroll up
  scroll_to_top               Scroll to top of screen
  scroll_to_bottom            Scroll to bottom of screen

SCANNER TESTING:
  test_success_result         Tap "Test Success" button
  test_failure_result         Tap "Test Failure" button
  dismiss_result_panel        Swipe down to dismiss result

SCREENSHOTS:
  screenshot <name>           Take screenshot to /tmp/<name>.png
  list_screenshots            List all screenshots in /tmp
  clean_screenshots           Delete all /tmp screenshots

WORKFLOWS:
  test_scanner_workflow       Full scanner test with screenshots
  test_dashboard_scroll       Dashboard scroll test
  test_result_panel           Result panel interaction test

UTILITIES:
  restart_app                 Force stop and restart app
  show_current_screen         Show focused activity
  show_nav_logs               Show recent navigation logs
  clear_logs                  Clear logcat
  tap <x> <y> [name]          Custom tap at coordinates
  swipe <x1> <y1> <x2> <y2> [dur] [name]  Custom swipe
  wait_for <seconds>          Wait/sleep

EXAMPLES:
  # Quick scanner test
  restart_app && nav_to_dashboard && start_scanner_from_dashboard

  # Test result panels
  test_result_panel

  # Custom navigation
  restart_app
  wait_for 3
  nav_to_scanner
  screenshot "my-scanner-view"

EOF
}

##############################################################################
# Auto-show help on source
##############################################################################

echo ""
echo "✓ UI Navigation Helper loaded"
echo "  Run: show_help"
echo ""
